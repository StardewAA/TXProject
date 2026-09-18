package cn.iocoder.txgy.module.trade.service.workbench;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.api.supplier.ProductSupplierApi;
import cn.iocoder.txgy.module.trade.controller.admin.order.vo.TradeOrderPageReqVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static cn.iocoder.txgy.module.trade.service.workbench.OrderWorkbenchModels.*;

@Service
public class OrderWorkbenchService {
    private static final int RECOVERY_LIMIT=10000;
    private final JdbcTemplate jdbc;
    private final OrderWorkbenchJournal journal;
    private final OrderWorkbenchSource source;
    private final OrderWorkbenchCountService counts;
    private final ProductSupplierApi suppliers;
    private final SecurityFrameworkService security;
    private final TransactionTemplate tx;
    public OrderWorkbenchService(JdbcTemplate jdbc,OrderWorkbenchJournal journal,OrderWorkbenchSource source,
                                 OrderWorkbenchCountService counts,ProductSupplierApi suppliers,SecurityFrameworkService security,
                                 PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.journal=journal;this.source=source;this.counts=counts;this.suppliers=suppliers;this.security=security;
        tx=new TransactionTemplate(manager);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);tx.setTimeout(20);
    }
    Scope authorize(Role role,Long shop,boolean adminEndpoint) {
        var user=SecurityFrameworkUtils.getLoginUser();
        if(user==null || role==null || adminEndpoint!=(role==Role.ADMIN)
                || !Objects.equals(user.getUserType(),adminEndpoint?2:1)) throw denied();
        long tenant=TenantContextHolder.getRequiredTenantId();
        if(tenant<=0 || !Objects.equals(user.getTenantId(),tenant)) throw denied();
        Set<Action> actions=EnumSet.noneOf(Action.class);
        if(role==Role.BUYER) actions.addAll(List.of(Action.PAYMENT,Action.RECEIVE,Action.COMMENT,Action.BUYER_RETURN));
        if(role==Role.SUPPLIER) {
            if(shop==null || shop<=0 || !Boolean.TRUE.equals(suppliers.validateShop(user.getId(),shop).getCheckedData())) throw denied();
            actions.addAll(List.of(Action.DELIVERY,Action.AFTER_AUDIT,Action.AFTER_RECEIVE));
        }
        if(role==Role.ADMIN) {
            boolean queryOrders=security.hasPermission("trade:order:query");
            boolean queryAfterSales=security.hasPermission("trade:after-sale:query");
            if(!queryOrders && !queryAfterSales) throw denied();
            if(queryOrders) actions.add(Action.DELIVERY);
            if(queryAfterSales) actions.add(Action.AFTER_AUDIT);
            if(queryAfterSales && security.hasPermission("trade:after-sale:refund")) actions.add(Action.PLATFORM_REFUND);
            if(queryOrders && security.hasPermission("trade:order:update")) actions.add(Action.ERP_EXCEPTION);
        }
        return new Scope(tenant,user.getUserType(),user.getId(),role,role==Role.SUPPLIER?shop:0,actions);
    }
    public Summary summary(Role role,Long shop,String afterCursor,List<Long> orderIds,List<Long> afterSaleIds,
                           TradeOrderPageReqVO filters,Boolean commentStatus,boolean adminEndpoint) {
        Scope scope=authorize(role,shop,adminEndpoint);validateIds(orderIds);validateIds(afterSaleIds);
        long requested=cursor(afterCursor);
        boolean mayQueryOrders=scope.role()!=Role.ADMIN || security.hasPermission("trade:order:query");
        var countFilter=mayQueryOrders?counts.prepare(scope,filters,commentStatus):null; // RPC before clock/transaction; 不为售后专员泄露订单统计
        var payments=source.preparePayments(scope);
        return tx.execute(status->{
            long current=synchronize(scope,payments);
            boolean reset=afterCursor==null || requested>current;
            var args=new ArrayList<Object>();String where=eventWhere(scope,args,true);
            var grouped=jdbc.queryForList("SELECT e.action_name,COUNT(*) n FROM trade_order_workbench_event e WHERE "+where+" GROUP BY e.action_name",args.toArray());
            Map<Action,Long> byAction=new EnumMap<>(Action.class);for(Action a:Action.values())byAction.put(a,0L);
            for(var row:grouped)byAction.put(Action.valueOf(row.get("action_name").toString()),OrderWorkbenchSource.num(row,"n"));
            long total=byAction.values().stream().mapToLong(Long::longValue).sum();
            long unread=count(where+" AND "+unread(scope),args);
            var newArgs=new ArrayList<>(args);newArgs.add(requested);
            long newCount=reset?0:count(where+" AND e.sequence_no>?",newArgs);
            var entries=entries(scope,where,args,20,0);
            List<Entry> rows=new ArrayList<>();
            if(orderIds!=null&&!orderIds.isEmpty() || afterSaleIds!=null&&!afterSaleIds.isEmpty()) {
                var rowArgs=new ArrayList<>(args); List<String> ors=new ArrayList<>();
                addIds(ors,rowArgs,"e.order_id",orderIds);addIds(ors,rowArgs,"e.after_sale_id",afterSaleIds);
                rows=entries(scope,where+" AND "+unread(scope)+" AND ("+String.join(" OR ",ors)+")",rowArgs,1000,0);
            }
            return new Summary(scope.key(),System.currentTimeMillis(),String.valueOf(current),reset,20,total,unread,newCount,
                    byAction,countFilter==null?null:counts.count(countFilter),entries,total>entries.size(),rows);
        });
    }
    public PageResult<Entry> page(Role role,Long shop,int pageNo,int pageSize,Action action,boolean unreadOnly,boolean adminEndpoint) {
        Scope scope=authorize(role,shop,adminEndpoint);
        if(pageNo<1 || pageSize<1 || pageSize>100 || (long)(pageNo-1)*pageSize>Integer.MAX_VALUE) throw invalid("分页参数无效");
        var payments=source.preparePayments(scope);
        return tx.execute(status->{
            synchronize(scope,payments);var args=new ArrayList<Object>();String where=eventWhere(scope,args,true);
            if(action!=null){where+=" AND e.action_name=?";args.add(action.name());}
            if(unreadOnly)where+=" AND "+unread(scope);
            return new PageResult<>(entries(scope,where,args,pageSize,(pageNo-1)*pageSize),count(where,args));
        });
    }
    public void read(Read request,boolean adminEndpoint) {
        Scope scope=authorize(request.role(),request.shopId(),adminEndpoint);
        if(request.eventIds()==null || request.eventIds().isEmpty() || request.eventIds().size()>100) throw invalid("请指定1至100条提醒");
        Set<Long> ids=new LinkedHashSet<>();for(String id:request.eventIds()){long n=cursor(id);if(n<=0)throw invalid("提醒编号无效");ids.add(n);}
        tx.executeWithoutResult(status->{
            journal.lock(scope.tenant());var args=new ArrayList<Object>();String where=eventWhere(scope,args,false);
            where+=" AND e.sequence_no IN ("+placeholders(ids.size())+")";args.addAll(ids);
            if(count(where,args)!=ids.size())throw denied();
            for(long id:ids)jdbc.update("INSERT INTO trade_order_workbench_read(tenant_id,user_type,user_id,role_name,shop_id,event_id)"
                    +" VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE event_id=event_id",scope.tenant(),scope.userType(),scope.user(),scope.role().name(),scope.shop(),id);
        });
    }
    /** 当前请求只同步其授权范围；已有scope仅消费变更及仍活动事项，首次有界扫描该范围的历史事实。 */
    long synchronize(Scope scope,Map<Long,cn.iocoder.txgy.module.pay.api.order.dto.PayOrderRespDTO> payments) {
        long current=journal.lock(scope.tenant());
        var positions=jdbc.queryForList("SELECT sequence_no FROM trade_order_workbench_scope WHERE tenant_id=? AND scope_kind=? AND scope_id=?",
                Long.class,scope.tenant(),scope.role().name(),scope.syncId());
        String ownership=orderScope(scope,"o");
        List<Long> ids;
        if(positions.isEmpty()) {
            ids=jdbc.queryForList("SELECT o.id FROM trade_order o WHERE o.tenant_id=? AND o.deleted=0 AND "+ownership
                    +" AND (o.status IN (0,10,20,30) OR EXISTS(SELECT 1 FROM trade_after_sale a WHERE a.tenant_id=o.tenant_id AND a.order_id=o.id AND a.deleted=0 AND a.status IN(10,20,30,40))"
                    +" OR EXISTS(SELECT 1 FROM trade_erp_inventory_request er WHERE er.tenant_id=o.tenant_id AND er.order_id=o.id AND (er.status='REJECTED' OR er.message LIKE 'ERP结果未能应用到商城%')))"
                    +" ORDER BY o.id LIMIT "+(RECOVERY_LIMIT+1),Long.class,scope.tenant());
        } else {
            ids=jdbc.queryForList("SELECT DISTINCT c.order_id FROM trade_order_workbench_change c JOIN trade_order o ON o.id=c.order_id AND o.tenant_id=c.tenant_id"
                    +" WHERE c.tenant_id=? AND c.sequence_no>? AND "+ownership+" ORDER BY c.order_id LIMIT "+(RECOVERY_LIMIT+1),Long.class,scope.tenant(),positions.getFirst());
        }
        Set<Long> affected=new LinkedHashSet<>(ids);
        // 支付RPC快照前后可能有并发下单：上一轮尚无快照的订单即便journal已消费，下一轮仍须重算。
        // 键来自preparePayments的本地tenant/member过滤订单，不信任支付返回中的商户订单编号作范围输入。
        if(scope.role()==Role.BUYER)affected.addAll(payments.keySet());
        var activeArgs=new ArrayList<Object>();
        String activeWhere=eventWhere(scope,activeArgs,true);
        affected.addAll(jdbc.queryForList("SELECT DISTINCT e.order_id FROM trade_order_workbench_event e WHERE "+activeWhere+" LIMIT "+(RECOVERY_LIMIT+1),Long.class,activeArgs.toArray()));
        if(affected.size()>RECOVERY_LIMIT) throw new ServiceException(503,"当前待办恢复范围超过10000笔，请联系平台分批恢复；未返回截断统计");
        List<Long> ordered=new ArrayList<>(affected);
        for(int from=0;from<ordered.size();from+=200) {
            var batch=ordered.subList(from,Math.min(from+200,ordered.size()));
            var desired=source.candidates(scope.tenant(),batch,payments).stream().filter(c->c.role()==scope.role())
                    .filter(c->scope.role()!=Role.BUYER || c.member()==scope.user()).filter(c->scope.role()!=Role.SUPPLIER || c.shop()==scope.shop()).toList();
            var args=new ArrayList<Object>();args.add(scope.tenant());args.addAll(batch);
            args.add(scope.role().name());
            String eventOwnership=scope.role()==Role.BUYER?" AND member_id="+scope.user():scope.role()==Role.SUPPLIER?" AND shop_id="+scope.shop():"";
            var existing=jdbc.queryForList("SELECT sequence_no,event_key,order_id,action_name FROM trade_order_workbench_event WHERE tenant_id=? AND active=1 AND order_id IN ("+placeholders(batch.size())+") AND role_name=?"+eventOwnership,args.toArray());
            var invalidationArgs=new ArrayList<Object>();invalidationArgs.add(scope.tenant());invalidationArgs.addAll(batch);
            var invalidations=jdbc.queryForList("SELECT order_id,MAX(CASE WHEN invalidate_actions LIKE '%|DELIVERY|%' THEN sequence_no ELSE 0 END) delivery_epoch,"
                    +"MAX(CASE WHEN invalidate_actions LIKE '%|ERP_EXCEPTION|%' THEN sequence_no ELSE 0 END) erp_epoch FROM trade_order_workbench_change WHERE tenant_id=? AND order_id IN ("+placeholders(batch.size())+") GROUP BY order_id",invalidationArgs.toArray());
            var desiredKeys=new HashSet<String>();for(var c:desired)desiredKeys.add(c.key());
            var existingKeys=new HashSet<String>();for(var e:existing){
                String key=e.get("event_key").toString();
                boolean invalidated=invalidations.stream().filter(v->Objects.equals(v.get("order_id"),e.get("order_id")))
                        .anyMatch(v->("DELIVERY".equals(e.get("action_name")) && OrderWorkbenchSource.num(v,"delivery_epoch")>OrderWorkbenchSource.num(e,"sequence_no"))
                                || ("ERP_EXCEPTION".equals(e.get("action_name")) && OrderWorkbenchSource.num(v,"erp_epoch")>OrderWorkbenchSource.num(e,"sequence_no")));
                if(!desiredKeys.contains(key) || invalidated)jdbc.update("UPDATE trade_order_workbench_event SET active=0 WHERE tenant_id=? AND sequence_no=?",scope.tenant(),e.get("sequence_no"));
                else existingKeys.add(key);
            }
            for(var c:desired)if(!existingKeys.contains(c.key())) {
                jdbc.update("INSERT INTO trade_order_workbench_event(tenant_id,sequence_no,event_key,role_name,member_id,shop_id,order_id,entity_type,entity_id,after_sale_id,request_id,business_no,action_name,reason)"
                        +" VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",scope.tenant(),++current,c.key(),c.role().name(),c.member(),c.shop(),c.order(),c.type(),c.entity(),c.afterSale(),c.request(),c.no(),c.action().name(),c.reason());
            }
        }
        journal.saveClock(scope.tenant(),current);
        jdbc.update("INSERT INTO trade_order_workbench_scope(tenant_id,scope_kind,scope_id,sequence_no) VALUES(?,?,?,?)"
                +" ON DUPLICATE KEY UPDATE sequence_no=VALUES(sequence_no)",scope.tenant(),scope.role().name(),scope.syncId(),current);
        return current;
    }
    static String orderScope(Scope scope,String alias) {
        if(scope.role()==Role.BUYER)return alias+".user_id="+scope.user();
        if(scope.role()==Role.ADMIN)return "1=1";
        return "("+alias+".shop_id="+scope.shop()+" OR EXISTS(SELECT 1 FROM trade_after_sale own_a WHERE own_a.order_id="+alias+".id AND own_a.tenant_id="+alias+".tenant_id AND own_a.deleted=0 AND own_a.shop_id="+scope.shop()+"))";
    }
    private String eventWhere(Scope scope,List<Object> args,boolean active) {
        args.add(scope.tenant());args.add(scope.role().name());
        String where="e.tenant_id=? AND e.role_name=?"+(active?" AND e.active=1":"");
        if(scope.actions().isEmpty())return where+" AND 1=0";
        where+=" AND e.action_name IN ("+placeholders(scope.actions().size())+")";for(Action a:scope.actions())args.add(a.name());
        if(scope.role()==Role.BUYER){where+=" AND e.member_id=?";args.add(scope.user());}
        if(scope.role()==Role.SUPPLIER){
            where+=" AND e.shop_id=?";args.add(scope.shop());
            where+=" AND ((e.entity_type='ORDER' AND EXISTS(SELECT 1 FROM trade_order_item oi WHERE oi.order_id=e.order_id AND oi.tenant_id=e.tenant_id AND oi.deleted=0)"
                    +" AND NOT EXISTS(SELECT 1 FROM trade_order_item oi WHERE oi.order_id=e.order_id AND oi.tenant_id=e.tenant_id AND oi.deleted=0 AND (oi.shop_id IS NULL OR oi.shop_id<>e.shop_id)))"
                    +" OR (e.entity_type='AFTER_SALE' AND EXISTS(SELECT 1 FROM trade_after_sale sa JOIN trade_order_item oi ON oi.id=sa.order_item_id AND oi.order_id=sa.order_id AND oi.tenant_id=sa.tenant_id"
                    +" WHERE sa.id=e.after_sale_id AND sa.tenant_id=e.tenant_id AND sa.deleted=0 AND oi.deleted=0 AND sa.shop_id=e.shop_id AND oi.shop_id=e.shop_id)))";
        }
        where+=" AND EXISTS(SELECT 1 FROM trade_order o WHERE o.id=e.order_id AND o.tenant_id=e.tenant_id AND o.deleted=0 AND "+orderScope(scope,"o")+")";
        return where;
    }
    private String unread(Scope s) {
        return "NOT EXISTS(SELECT 1 FROM trade_order_workbench_read r WHERE r.tenant_id=e.tenant_id AND r.event_id=e.sequence_no AND r.user_type="+s.userType()
                +" AND r.user_id="+s.user()+" AND r.role_name='"+s.role().name()+"' AND r.shop_id="+s.shop()+")";
    }
    private long count(String where,List<Object> args){return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM trade_order_workbench_event e WHERE "+where,Long.class,args.toArray()));}
    private List<Entry> entries(Scope scope,String where,List<Object> args,int limit,int offset) {
        var bind=new ArrayList<>(args);bind.add(limit);bind.add(offset);
        return jdbc.query("SELECT e.*,CASE WHEN "+unread(scope)+" THEN 1 ELSE 0 END is_unread FROM trade_order_workbench_event e WHERE "+where
                +" ORDER BY e.sequence_no DESC LIMIT ? OFFSET ?",(rs,row)->new Entry(String.valueOf(rs.getLong("sequence_no")),rs.getString("entity_type"),rs.getLong("entity_id"),
                        rs.getLong("order_id"),rs.getObject("after_sale_id",Long.class),rs.getString("request_id"),rs.getString("business_no"),rs.getLong("shop_id"),
                        Action.valueOf(rs.getString("action_name")),rs.getString("reason"),rs.getBoolean("is_unread"),rs.getTimestamp("create_time").toLocalDateTime().toString()),bind.toArray());
    }
    private static void addIds(List<String> ors,List<Object> args,String field,List<Long> ids){if(ids!=null&&!ids.isEmpty()){ors.add(field+" IN ("+placeholders(ids.size())+")");args.addAll(ids);}}
    private static void validateIds(List<Long> ids){if(ids!=null&&(ids.size()>100||ids.stream().anyMatch(i->i==null||i<=0)))throw invalid("当前行编号最多100个且必须为正数");}
    private static String placeholders(int count){return String.join(",",Collections.nCopies(count,"?"));}
    private static long cursor(String value){if(value==null)return 0;try{long n=Long.parseLong(value);if(n<0)throw new NumberFormatException();return n;}catch(NumberFormatException ex){throw invalid("提醒游标无效");}}
    static ServiceException invalid(String msg){return new ServiceException(400,msg);}
    static ServiceException denied(){return new ServiceException(403,"无权查看或处理此订单待办范围");}
}
