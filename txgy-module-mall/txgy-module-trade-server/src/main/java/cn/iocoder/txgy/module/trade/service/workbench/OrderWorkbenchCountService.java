package cn.iocoder.txgy.module.trade.service.workbench;

import cn.iocoder.txgy.module.member.api.user.MemberUserApi;
import cn.iocoder.txgy.module.member.api.platform.MemberPlatformAccountApi;
import cn.iocoder.txgy.module.trade.controller.admin.order.vo.TradeOrderPageReqVO;
import cn.iocoder.txgy.module.trade.controller.admin.aftersale.vo.AfterSalePageReqVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import static cn.iocoder.txgy.module.trade.service.workbench.OrderWorkbenchModels.*;

/** 数据库完整聚合；仅移除主状态，不用当前页内容计算。 */
@Service
class OrderWorkbenchCountService {
    private final JdbcTemplate jdbc; private final MemberUserApi members;
    private final MemberPlatformAccountApi platformAccounts;
    OrderWorkbenchCountService(JdbcTemplate jdbc, MemberUserApi members,
                               MemberPlatformAccountApi platformAccounts) {
        this.jdbc=jdbc;this.members=members;this.platformAccounts=platformAccounts;
    }
    record Filter(String sql,List<Object> args) {}
    Filter prepare(Scope scope,TradeOrderPageReqVO req,Boolean commentStatus) {
        Builder f;
        if(scope.role()==Role.BUYER) {
            Long platformAccountId = resolvePlatformAccountId(scope);
            if(platformAccountId == null) {
                f=new Builder("o.tenant_id=? AND o.deleted=0 AND o.user_id=?",scope.tenant(),scope.user());
            } else {
                f=new Builder("o.deleted=0 AND ((o.tenant_id=? AND o.user_id=?) OR EXISTS ("
                        + "SELECT 1 FROM trade_platform_checkout_order r "
                        + "JOIN trade_platform_checkout c ON c.id=r.checkout_id AND c.deleted=0 "
                        + "WHERE r.deleted=0 AND r.order_id=o.id AND r.tenant_id=o.tenant_id "
                        + "AND c.platform_member_id=?))",scope.tenant(),scope.user(),platformAccountId);
            }
            f.eq("o.comment_status",commentStatus);
        } else {
            f=new Builder("o.tenant_id=? AND o.deleted=0",scope.tenant());
        }
        if(scope.role()==Role.SUPPLIER){
            f.eq("o.shop_id",scope.shop());
            f.sql.append(" AND EXISTS(SELECT 1 FROM trade_order_item i WHERE i.order_id=o.id AND i.tenant_id=o.tenant_id AND i.deleted=0)")
                    .append(" AND NOT EXISTS(SELECT 1 FROM trade_order_item i WHERE i.order_id=o.id AND i.tenant_id=o.tenant_id AND i.deleted=0 AND (i.shop_id IS NULL OR i.shop_id<>?))");f.args.add(scope.shop());
        }
        if(req!=null) {
            f.like("o.no",req.getNo());
            if(scope.role()==Role.ADMIN){
                f.eq("o.shop_id",req.getShopId());f.eq("o.user_id",req.getUserId());f.eq("o.delivery_type",req.getDeliveryType());f.eq("o.type",req.getType());
                f.eq("o.pay_channel_code",req.getPayChannelCode());f.eq("o.terminal",req.getTerminal());f.eq("o.logistics_id",req.getLogisticsId());
                f.in("o.pick_up_store_id",req.getPickUpStoreIds());f.like("o.pick_up_verify_code",req.getPickUpVerifyCode());f.between("o.create_time",req.getCreateTime());
                // 与现有业务列表相同口径，手机号与昵称同时存在使用同一候选集合。
                Set<Long> ids=new HashSet<>();boolean absent=false;
                if(nonempty(req.getUserMobile())){var user=members.getUserByMobile(req.getUserMobile()).getCheckedData();if(user==null)absent=true;else ids.add(user.getId());}
                if(nonempty(req.getUserNickname())){var users=members.getUserListByNickname(req.getUserNickname()).getCheckedData();if(users==null||users.isEmpty())absent=true;else users.forEach(u->ids.add(u.getId()));}
                if(absent)f.sql.append(" AND 1=0");else f.in("o.user_id",ids);
            }
        }
        return new Filter(f.sql.toString(),f.args);
    }
    private Long resolvePlatformAccountId(Scope scope) {
        try {
            var result=platformAccounts.resolve(scope.user(),scope.tenant());
            var account=result==null?null:result.getCheckedData();
            return account==null?null:account.getPlatformAccountId();
        } catch(RuntimeException unavailable) {
            return null;
        }
    }
    Counts count(Filter filter) {
        Map<Integer,Long> counts=new HashMap<>();
        for(var r:jdbc.queryForList("SELECT o.status,COUNT(*) n FROM trade_order o WHERE "+filter.sql()+" GROUP BY o.status",filter.args().toArray()))
            counts.put((int)OrderWorkbenchSource.num(r,"status"),OrderWorkbenchSource.num(r,"n"));
        long uncommented=Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM trade_order o WHERE "+filter.sql()+" AND o.status=30 AND o.comment_status=0",Long.class,filter.args().toArray()));
        return new Counts(counts.values().stream().mapToLong(Long::longValue).sum(),counts.getOrDefault(0,0L),counts.getOrDefault(10,0L),counts.getOrDefault(20,0L),
                counts.getOrDefault(30,0L),counts.getOrDefault(40,0L),uncommented);
    }
    static boolean nonempty(String s){return s!=null&&!s.isEmpty();}
    static final class Builder {
        final StringBuilder sql;final List<Object> args=new ArrayList<>();
        Builder(String initial,Object...values){sql=new StringBuilder(initial);args.addAll(Arrays.asList(values));}
        void eq(String name,Object value){if(value!=null && (!(value instanceof String s)||!s.isEmpty())){sql.append(" AND ").append(name).append("=?");args.add(value);}}
        void like(String name,String value){if(nonempty(value)){sql.append(" AND ").append(name).append(" LIKE ?");args.add("%"+value+"%");}}
        void in(String name,Collection<?> values){if(values!=null&&!values.isEmpty()){sql.append(" AND ").append(name).append(" IN (").append(String.join(",",Collections.nCopies(values.size(),"?"))).append(")");args.addAll(values);}}
        void between(String name,Object[] values){if(values!=null&&values.length>=2){if(values[0]!=null){sql.append(" AND ").append(name).append(">=?");args.add(values[0]);}if(values[1]!=null){sql.append(" AND ").append(name).append("<=?");args.add(values[1]);}}}
    }
}
