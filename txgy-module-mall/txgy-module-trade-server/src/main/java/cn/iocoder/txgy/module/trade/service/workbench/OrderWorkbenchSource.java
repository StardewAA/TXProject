package cn.iocoder.txgy.module.trade.service.workbench;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import cn.iocoder.txgy.module.pay.api.order.PayOrderApi;
import cn.iocoder.txgy.module.pay.api.order.dto.PayOrderRespDTO;
import static cn.iocoder.txgy.module.trade.service.workbench.OrderWorkbenchModels.*;

/** 只读商城及本地 WMS 履约事实。每次最多 200 个订单，所有关联表显式约束租户。 */
@Service
class OrderWorkbenchSource {
    private final JdbcTemplate jdbc;
    private final PayOrderApi payOrders;
    OrderWorkbenchSource(JdbcTemplate jdbc,PayOrderApi payOrders){this.jdbc=jdbc;this.payOrders=payOrders;}
    /** 仅买家查询，在任何事务/时钟锁之前通过原支付RPC核实。原RPC以租户上下文隔离。 */
    Map<Long,PayOrderRespDTO> preparePayments(Scope scope) {
        if(scope.role()!=Role.BUYER)return Map.of();
        var rows=jdbc.queryForList("SELECT id,pay_order_id FROM trade_order WHERE tenant_id=? AND user_id=? AND deleted=0 AND status=0 AND pay_status=0 AND pay_order_id IS NOT NULL"
                +" AND (pay_expire_time IS NULL OR pay_expire_time>CURRENT_TIMESTAMP) ORDER BY id LIMIT 201",scope.tenant(),scope.user());
        if(rows.size()>200)throw new ServiceException(503,"待付款信息超过200笔，请联系平台分批核实；未返回不完整提醒");
        Map<Long,PayOrderRespDTO> verified=new HashMap<>();
        try {for(var row:rows){var response=payOrders.getOrder(num(row,"pay_order_id"));
            if(response==null)throw new IllegalStateException("支付服务未响应");var pay=response.getCheckedData();if(pay!=null)verified.put(num(row,"id"),pay);
        }} catch(RuntimeException ex){throw new ServiceException(503,"支付信息暂无法核实，待办暂无法更新，请稍后重试");}
        return verified;
    }
    List<Candidate> candidates(long tenant,List<Long> ids,Map<Long,PayOrderRespDTO> payments) {
        if(ids.isEmpty()) return List.of();
        if(ids.size()>200) throw new IllegalArgumentException("每批最多200个订单");
        String in=String.join(",",Collections.nCopies(ids.size(),"?"));
        var args=new ArrayList<Object>(); args.add(tenant); args.addAll(ids);
        var orders=jdbc.queryForList("SELECT id,no,user_id,shop_id,status,pay_status,pay_order_id,pay_expire_time,"
                +"pay_expire_seconds,pay_price,delivery_type,refund_status,comment_status FROM trade_order WHERE tenant_id=? AND deleted=0 AND id IN ("+in+")",args.toArray());
        var items=group(jdbc.queryForList("SELECT id,order_id,shop_id,after_sale_status,after_sale_id FROM trade_order_item WHERE tenant_id=? AND deleted=0 AND order_id IN ("+in+")",args.toArray()),"order_id");
        var sales=group(jdbc.queryForList("SELECT id,no,order_id,order_item_id,user_id,shop_id,status,way,pay_refund_id FROM trade_after_sale WHERE tenant_id=? AND deleted=0 AND order_id IN ("+in+")",args.toArray()),"order_id");
        List<Candidate> result=new ArrayList<>(); LocalDateTime now=LocalDateTime.now();
        for(var o:orders) {
            long id=num(o,"id"), member=num(o,"user_id"), shop=num(o,"shop_id");
            String no=Objects.toString(o.get("no"),String.valueOf(id)); int status=(int)num(o,"status");
            var its=items.getOrDefault(id,List.of());
            boolean ownShop=shop>0 && !its.isEmpty() && its.stream().allMatch(i->num(i,"shop_id")==shop);
            var pay=payments.get(id);
            if(status==0 && !flag(o,"pay_status") && pay!=null && Objects.equals(pay.getId(),num(o,"pay_order_id"))
                    && String.valueOf(id).equals(pay.getMerchantOrderId()) && Objects.equals(pay.getPrice(),(int)num(o,"pay_price"))
                    && Integer.valueOf(0).equals(pay.getStatus()) && pay.getExpireTime()!=null && now.isBefore(pay.getExpireTime())
                    && (o.get("pay_expire_seconds")==null || time(o,"pay_expire_time")!=null)
                    && (time(o,"pay_expire_time")==null || now.isBefore(time(o,"pay_expire_time"))))
                result.add(order(Role.BUYER,member,shop,id,no,Action.PAYMENT,"订单可付款，请在支付期限内完成"));
            if(status==20) result.add(order(Role.BUYER,member,shop,id,no,Action.RECEIVE,"商品已发货，请核实后确认收货"));
            if(status==30 && !flag(o,"comment_status")) result.add(order(Role.BUYER,member,shop,id,no,Action.COMMENT,"订单已完成，待评价"));
            boolean wmsCompleted = status == 10 && flag(o,"pay_status")
                    && !its.isEmpty()
                    && Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM trade_wms_shipment w WHERE w.tenant_id=? AND w.order_id=? AND w.deleted=0)"
                            + " AND NOT EXISTS(SELECT 1 FROM trade_wms_shipment w WHERE w.tenant_id=? AND w.order_id=? AND w.deleted=0 AND w.status<>'WMS_OUTBOUND_COMPLETED')",
                            Boolean.class, tenant, id, tenant, id));
            if (wmsCompleted && ownShop && num(o,"delivery_type")==1 && num(o,"refund_status")==0
                    && its.stream().allMatch(i -> num(i,"after_sale_status")==0)) {
                result.add(order(Role.SUPPLIER,0,shop,id,no,Action.DELIVERY,"WMS 出库已完成，待登记物流并确认发货"));
                result.add(order(Role.ADMIN,0,shop,id,no,Action.DELIVERY,"WMS 出库已完成，待登记物流并确认发货"));
            }
            for(var a:sales.getOrDefault(id,List.of())) {
                long aid=num(a,"id"), ash=num(a,"shop_id"); int ast=(int)num(a,"status");
                boolean owned=ash>0 && its.stream().anyMatch(i->num(i,"id")==num(a,"order_item_id") && num(i,"shop_id")==ash
                        && num(i,"after_sale_id")==aid && num(i,"after_sale_status")==10);
                if(ast==20 && num(a,"way")==20) result.add(sale(Role.BUYER,num(a,"user_id"),ash,id,a,Action.BUYER_RETURN,"商家已同意，请寄回商品并填写退货物流"));
                if(owned && ast==10 && (num(a,"way")==10 || num(a,"way")==20)) result.add(sale(Role.SUPPLIER,0,ash,id,a,Action.AFTER_AUDIT,"本店新售后申请待审核"));
                if(ast==10 && (num(a,"way")==10 || num(a,"way")==20)) result.add(sale(Role.ADMIN,0,ash,id,a,Action.AFTER_AUDIT,"新售后退款申请待审核"));
                // WMS 退货入库接口尚未开放，不生成可执行的收货入库待办。
                if(ast==40 && num(a,"pay_refund_id")==0) result.add(sale(Role.ADMIN,0,ash,id,a,Action.PLATFORM_REFUND,"商家已完成售后处理，待平台确认退款"));
            }
        }
        return result;
    }
    private Candidate order(Role role,long member,long shop,long id,String no,Action action) { return order(role,member,shop,id,no,action,action.name()); }
    private Candidate order(Role role,long member,long shop,long id,String no,Action action,String reason) {
        return new Candidate(role+":ORDER:"+id+":"+action,role,member,shop,id,"ORDER",id,null,null,no,action,reason);
    }
    private Candidate sale(Role role,long member,long shop,long order,Map<String,Object> a,Action action,String reason) {
        long id=num(a,"id");return new Candidate(role+":AFTER:"+id+":"+action,role,member,shop,order,"AFTER_SALE",id,id,null,
                Objects.toString(a.get("no"),String.valueOf(id)),action,reason);
    }
    static boolean blocked(List<Map<String,Object>> rows,String op,String key) {
        return rows.stream().anyMatch(e->op.equals(e.get("operation")) && (key==null || key.equals(e.get("business_key"))) && !"CONFIRMED".equals(e.get("status")));
    }
    static long num(Map<String,Object> row,String key) { var value=row.get(key);return value instanceof Number n?n.longValue():0; }
    private boolean flag(Map<String,Object> row,String key) { var v=row.get(key);return Boolean.TRUE.equals(v) || v instanceof Number n&&n.intValue()!=0; }
    private LocalDateTime time(Map<String,Object> row,String key) {
        var v=row.get(key);return v instanceof Timestamp t?t.toLocalDateTime():v instanceof LocalDateTime d?d:null;
    }
    static Map<Long,List<Map<String,Object>>> group(List<Map<String,Object>> rows,String key) {
        Map<Long,List<Map<String,Object>>> out=new HashMap<>();for(var row:rows) out.computeIfAbsent(num(row,key),x->new ArrayList<>()).add(row);return out;
    }
}
