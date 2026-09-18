package cn.iocoder.txgy.module.trade.service.workbench;

import cn.iocoder.txgy.module.trade.dal.dataobject.order.*;
import cn.iocoder.txgy.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.iocoder.txgy.module.trade.service.erp.ErpInventoryModels.Entry;
import java.util.*;

/** 列表、详情、待办与经营操作共享的履约门槛；不推进业务、不模拟ERP成功。 */
public final class OrderActionPolicy {
    private OrderActionPolicy() {}
    public static String deliveryBlock(TradeOrderDO order,List<TradeOrderItemDO> items,List<Entry> erp) {
        String blocked=erpBlock(erp,"OUTBOUND",null);
        if (blocked!=null) return blocked;
        if (!Integer.valueOf(10).equals(order.getStatus()) || !Boolean.TRUE.equals(order.getPayStatus())) return "订单尚未进入已支付待发货阶段";
        if (!Integer.valueOf(1).equals(order.getDeliveryType())) return "自提订单请使用原核销流程";
        if (!Integer.valueOf(0).equals(order.getRefundStatus()) || items.isEmpty()
                || items.stream().anyMatch(i->!Integer.valueOf(0).equals(i.getAfterSaleStatus()))) return "订单存在退款或售后，暂不能发货";
        return null;
    }
    public static String afterSaleBlock(AfterSaleDO sale,List<Entry> erp) {
        if (Integer.valueOf(10).equals(sale.getStatus()) && List.of(10,20).contains(sale.getWay())) return null;
        if (Integer.valueOf(30).equals(sale.getStatus()) && Integer.valueOf(20).equals(sale.getWay()))
            return erpBlock(erp,"RETURN",String.valueOf(sale.getId()));
        return "该阶段无需商家审核或收货";
    }
    public static String erpBlock(List<Entry> erp,String operation,String businessKey) {
        return erp.stream().filter(e->operation.equals(e.operation()) && (businessKey==null || businessKey.equals(e.businessKey())))
                .filter(e->!e.confirmed()).findFirst().map(e->"REJECTED".equals(e.status())
                        ? "ERP已拒绝，请由平台核实处理" : "已提交ERP，等待确认，请勿重复操作").orElse(null);
    }
    public static String erpStatus(List<Entry> entries) {
        if(entries.stream().anyMatch(e->"REJECTED".equals(e.status()))) return "REJECTED";
        if(entries.stream().anyMatch(e->"PENDING".equals(e.status()))) return "PENDING";
        return entries.isEmpty()?null:"CONFIRMED";
    }
}
