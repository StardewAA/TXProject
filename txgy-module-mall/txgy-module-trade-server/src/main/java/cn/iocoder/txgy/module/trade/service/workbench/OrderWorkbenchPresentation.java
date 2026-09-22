package cn.iocoder.txgy.module.trade.service.workbench;

import cn.iocoder.txgy.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.trade.controller.admin.order.vo.TradeOrderBaseVO;
import cn.iocoder.txgy.module.trade.controller.admin.aftersale.vo.*;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.*;
import cn.iocoder.txgy.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.iocoder.txgy.module.trade.service.erp.ErpInventoryModels.Entry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class OrderWorkbenchPresentation {
    private final SecurityFrameworkService security;
    private final JdbcTemplate jdbc;
    public OrderWorkbenchPresentation(SecurityFrameworkService security,JdbcTemplate jdbc){this.security=security;this.jdbc=jdbc;}
    public void fill(TradeOrderDO order,List<TradeOrderItemDO> items,List<Entry> erp,TradeOrderBaseVO response) {
        String block=OrderActionPolicy.deliveryBlock(order,items,erp);
        if(!security.hasPermission("trade:order:update"))block="当前账号没有订单发货权限";
        response.setDeliverable(block==null);response.setDeliveryBlockReason(block);response.setErpStatus(OrderActionPolicy.erpStatus(erp));
        String pickup=OrderActionPolicy.erpBlock(erp,"OUTBOUND",null);
        if(!Integer.valueOf(10).equals(order.getStatus()) || !Boolean.TRUE.equals(order.getPayStatus())
                || !Integer.valueOf(2).equals(order.getDeliveryType()) || !Integer.valueOf(0).equals(order.getRefundStatus())
                || items.isEmpty() || items.stream().anyMatch(i->!Integer.valueOf(0).equals(i.getAfterSaleStatus())))pickup="订单不满足已付款、无退款售后的待核销条件";
        if(!security.hasPermission("trade:order:pick-up"))pickup="当前账号没有自提核销权限";
        response.setPickupable(pickup==null);response.setPickupBlockReason(pickup);
    }
    public void fill(AfterSaleDO sale,List<Entry> erp,AfterSaleBaseVO response) {
        String block=OrderActionPolicy.afterSaleBlock(sale,erp);
        if(Integer.valueOf(40).equals(sale.getStatus())) block=sale.getPayRefundId()==null?null:"退款请求已提交，等待支付处理结果";
        boolean allowed=switch(sale.getStatus()) {
            case 10 -> security.hasPermission("trade:after-sale:agree") || security.hasPermission("trade:after-sale:disagree");
            case 30 -> security.hasPermission("trade:after-sale:receive");
            case 40 -> security.hasPermission("trade:after-sale:refund");
            default -> false;
        };
        if(!allowed)block="当前账号无此阶段处理权限或事项已处理";
        response.setProcessable(block==null);response.setProcessBlockReason(block);response.setErpStatus(OrderActionPolicy.erpStatus(erp));
    }
    public Map<String,Long> afterSaleCounts(AfterSalePageReqVO req) {
        var f=new OrderWorkbenchCountService.Builder("tenant_id=? AND deleted=0",TenantContextHolder.getRequiredTenantId());
        f.eq("shop_id",req.getShopId());f.eq("user_id",req.getUserId());f.eq("type",req.getType());f.eq("way",req.getWay());
        f.like("no",req.getNo());f.like("order_no",req.getOrderNo());f.like("spu_name",req.getSpuName());f.between("create_time",req.getCreateTime());
        Map<String,Long> counts=new LinkedHashMap<>();for(String key:List.of("all","10","20","30","40","50","61","62","63"))counts.put(key,0L);
        for(var row:jdbc.queryForList("SELECT status,COUNT(*) n FROM trade_after_sale WHERE "+f.sql+" GROUP BY status",f.args.toArray())){
            long n=OrderWorkbenchSource.num(row,"n");counts.put(String.valueOf(OrderWorkbenchSource.num(row,"status")),n);counts.put("all",counts.get("all")+n);
        }return counts;
    }
}
