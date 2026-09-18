package cn.iocoder.txgy.module.trade.service.supplier;

import cn.iocoder.txgy.framework.common.enums.UserTypeEnum;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.*;
import cn.iocoder.txgy.framework.ip.core.utils.AreaUtils;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.product.api.supplier.ProductSupplierApi;
import cn.iocoder.txgy.module.trade.controller.admin.order.vo.TradeOrderDeliveryReqVO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.*;
import cn.iocoder.txgy.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.iocoder.txgy.module.trade.dal.mysql.order.*;
import cn.iocoder.txgy.module.trade.dal.mysql.aftersale.AfterSaleMapper;
import cn.iocoder.txgy.module.trade.service.order.TradeOrderUpdateService;
import cn.iocoder.txgy.module.trade.service.delivery.DeliveryExpressService;
import cn.iocoder.txgy.module.trade.service.aftersale.AfterSaleService;
import cn.iocoder.txgy.module.trade.controller.admin.aftersale.vo.AfterSaleDisagreeReqVO;
import cn.iocoder.txgy.module.trade.controller.admin.aftersale.vo.AfterSaleRefuseReqVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import cn.iocoder.txgy.module.trade.service.workbench.OrderActionPolicy;
import cn.iocoder.txgy.module.trade.service.erp.ErpInventoryModels.Entry;

@Service
public class SupplierTradeService {
    @Resource private cn.iocoder.txgy.module.trade.service.erp.TradeErpInventoryService erpInventory;
    @Resource private ProductSupplierApi authorization;
    @Resource private TradeOrderMapper orders;
    @Resource private TradeOrderItemMapper items;
    @Resource private AfterSaleMapper afterSales;
    @Resource private TradeOrderUpdateService orderUpdate;
    @Resource private DeliveryExpressService expressService;
    @Resource private AfterSaleService afterSaleUpdate;
    public static ServiceException invalid(String msg) { return new ServiceException(400, msg); }
    public static Long memberId() {
        var user = SecurityFrameworkUtils.getLoginUser();
        if (user == null || !Objects.equals(user.getUserType(), UserTypeEnum.MEMBER.getValue()))
            throw new ServiceException(403, "请使用商城供应商会员账号");
        return user.getId();
    }
    private void authorize(Long member, Long shop) {
        if (shop == null || !Boolean.TRUE.equals(authorization.validateShop(member, shop).getCheckedData()))
            throw new ServiceException(403, "未获得该店铺销售权限");
    }
    public record OrderRow(Long id, String no, Integer status, Boolean payStatus, Integer payPrice,
                           Integer refundPrice, Integer deliveryType, LocalDateTime createTime,
                           boolean deliverable,String deliveryBlockReason,String erpStatus) {}
    public record Item(Long id, String name, String picUrl, String specification, Integer count,
                       Integer price, Integer payPrice, Integer afterSaleStatus) {}
    public record OrderDetail(OrderRow order, String receiverName, String receiverMobile, String address,
                              String userRemark, Long logisticsId, String logisticsNo, LocalDateTime deliveryTime,
                              boolean deliverable, List<Item> items,
                              List<cn.iocoder.txgy.module.trade.service.erp.ErpInventoryModels.Entry> erpInventory) {}
    public record Express(Long id, String name) {}
    public enum AfterSaleAction { AGREE, REJECT, RECEIVE, REFUSE }
    public record AfterSaleDetail(AfterSaleRow row, List<String> pictures, String receiveReason,
                                  String returnReceiverName, String returnReceiverMobile, String returnAddress,
                                  List<cn.iocoder.txgy.module.trade.service.erp.ErpInventoryModels.Entry> erpInventory) {}
    public record AfterSaleRow(Long id, String no, Long orderId, String orderNo, String spuName, Integer count,
                               Integer status, Integer way, Integer refundPrice, String applyReason,
                               String applyDescription, String auditReason, Long logisticsId, String logisticsNo,
                               LocalDateTime createTime,boolean processable,String processBlockReason,String erpStatus) {}
    private OrderRow row(TradeOrderDO o,List<TradeOrderItemDO> list,List<Entry> erp) {
        String block=OrderActionPolicy.deliveryBlock(o,list,erp);
        return new OrderRow(o.getId(), o.getNo(), o.getStatus(), o.getPayStatus(), o.getPayPrice(),
                o.getRefundPrice(), o.getDeliveryType(), o.getCreateTime(),block==null,block,OrderActionPolicy.erpStatus(erp));
    }
    public PageResult<OrderRow> page(Long member, Long shop, PageParam page, String no, Integer status) {
        authorize(member, shop);
        var q = new LambdaQueryWrapperX<TradeOrderDO>().eq(TradeOrderDO::getShopId, shop)
                .likeIfPresent(TradeOrderDO::getNo, no).eqIfPresent(TradeOrderDO::getStatus, status)
                // 历史混店/无店铺快照订单交平台处理，不能向供应商暴露其他店铺数据。
                .apply("EXISTS (SELECT 1 FROM trade_order_item i WHERE i.order_id = trade_order.id AND i.tenant_id=trade_order.tenant_id AND i.deleted = 0)")
                .apply("NOT EXISTS (SELECT 1 FROM trade_order_item i WHERE i.order_id = trade_order.id AND i.tenant_id=trade_order.tenant_id AND i.deleted = 0 AND (i.shop_id IS NULL OR i.shop_id <> {0}))", shop)
                .orderByDesc(TradeOrderDO::getId);
        var result = orders.selectPage(page, q);
        var ids=result.getList().stream().map(TradeOrderDO::getId).toList();
        var allItems=ids.isEmpty()?List.<TradeOrderItemDO>of():items.selectListByOrderId(ids);
        var erps=erpInventory.listByOrderIds(ids);
        return new PageResult<>(result.getList().stream().map(o->row(o,allItems.stream().filter(i->Objects.equals(i.getOrderId(),o.getId())).toList(),
                erps.getOrDefault(o.getId(),List.of()))).toList(), result.getTotal());
    }
    private TradeOrderDO owned(Long member, Long shop, Long id, boolean lock) {
        authorize(member, shop);
        var q = new LambdaQueryWrapperX<TradeOrderDO>().eq(TradeOrderDO::getId, id).eq(TradeOrderDO::getShopId, shop);
        if (lock) q.last("FOR UPDATE");
        var order = orders.selectOne(q);
        if (order == null) throw invalid("订单不存在或无权访问");
        return order;
    }
    private List<TradeOrderItemDO> ownItems(Long shop, Long id) {
        var list = items.selectListByOrderId(id);
        if (list.isEmpty() || list.stream().anyMatch(i -> !Objects.equals(i.getShopId(), shop)))
            throw invalid("订单明细店铺归属不完整，请联系平台处理");
        return list;
    }
    public static boolean deliverable(TradeOrderDO order, List<TradeOrderItemDO> list) {
        return Integer.valueOf(10).equals(order.getStatus()) && Boolean.TRUE.equals(order.getPayStatus())
                && Integer.valueOf(1).equals(order.getDeliveryType()) && Integer.valueOf(0).equals(order.getRefundStatus())
                && !list.isEmpty() && list.stream().allMatch(i -> Integer.valueOf(0).equals(i.getAfterSaleStatus()));
    }
    public OrderDetail detail(Long member, Long shop, Long id) {
        var o = owned(member, shop, id, false);
        var list = ownItems(shop, id);
        var erp=erpInventory.list(id);var row=row(o,list,erp);
        return new OrderDetail(row, o.getReceiverName(), o.getReceiverMobile(),
                Objects.toString(AreaUtils.format(o.getReceiverAreaId()), "") + Objects.toString(o.getReceiverDetailAddress(), ""),
                o.getUserRemark(), o.getLogisticsId(), o.getLogisticsNo(), o.getDeliveryTime(), row.deliverable(),
                list.stream().map(i -> new Item(i.getId(), i.getSpuName(), i.getPicUrl(), i.getProperties() == null ? "" :
                        String.join(" / ", i.getProperties().stream().map(p -> p.getPropertyName()+":"+p.getValueName()).toList()),
                        i.getCount(), i.getPrice(), i.getPayPrice(), i.getAfterSaleStatus())).toList(), erp);
    }
    public List<Express> express(Long member, Long shop) {
        authorize(member, shop);
        return expressService.getDeliveryExpressListByStatus(0).stream().map(e -> new Express(e.getId(), e.getName())).toList();
    }
    @Transactional(rollbackFor = Exception.class)
    public void deliver(Long member, Long shop, Long id, Long logisticsId, String logisticsNo) {
        var order = owned(member, shop, id, true);
        var list = ownItems(shop, id);
        if (logisticsId == null || logisticsId <= 0 || logisticsNo == null || !logisticsNo.matches("[A-Za-z0-9-]{5,64}"))
            throw invalid("请选择快递公司并填写5至64位字母、数字或短横线运单号");
        if (Integer.valueOf(20).equals(order.getStatus()) && Objects.equals(order.getLogisticsId(), logisticsId)
                && Objects.equals(order.getLogisticsNo(), logisticsNo)) return; // 相同发货请求幂等
        String blocked=OrderActionPolicy.deliveryBlock(order,list,erpInventory.list(id));
        if (blocked!=null) throw invalid(blocked);
        expressService.validateDeliveryExpress(logisticsId);
        // 复用真实订单状态流转、库存处理和日志；不调用后台HTTP接口、不更换登录身份。
        orderUpdate.deliveryOrder(new TradeOrderDeliveryReqVO().setId(id).setLogisticsId(logisticsId).setLogisticsNo(logisticsNo));
    }
    public PageResult<AfterSaleRow> afterSales(Long member, Long shop, PageParam page, String no, Integer status) {
        authorize(member, shop);
        var q = new LambdaQueryWrapperX<AfterSaleDO>().eq(AfterSaleDO::getShopId, shop)
                .likeIfPresent(AfterSaleDO::getOrderNo, no).eqIfPresent(AfterSaleDO::getStatus, status)
                .apply("EXISTS (SELECT 1 FROM trade_order_item i WHERE i.id = trade_after_sale.order_item_id AND i.order_id = trade_after_sale.order_id AND i.tenant_id=trade_after_sale.tenant_id AND i.shop_id = {0} AND i.deleted = 0)", shop)
                .orderByDesc(AfterSaleDO::getId);
        var result = afterSales.selectPage(page, q);
        var erps=erpInventory.listByOrderIds(result.getList().stream().map(AfterSaleDO::getOrderId).distinct().toList());
        return new PageResult<>(result.getList().stream().map(a->afterSaleRow(a,erps.getOrDefault(a.getOrderId(),List.of()))).toList(), result.getTotal());
    }
    private AfterSaleRow afterSaleRow(AfterSaleDO a,List<Entry> erp) {
        String blocked=OrderActionPolicy.afterSaleBlock(a,erp);
        return new AfterSaleRow(a.getId(), a.getNo(), a.getOrderId(),
                a.getOrderNo(), a.getSpuName(), a.getCount(), a.getStatus(), a.getWay(), a.getRefundPrice(),
                a.getApplyReason(), a.getApplyDescription(), a.getAuditReason(), a.getLogisticsId(), a.getLogisticsNo(), a.getCreateTime(),
                blocked==null,blocked,OrderActionPolicy.erpStatus(erp));
    }
    private AfterSaleDO ownedAfterSale(Long member, Long shop, Long id, boolean lock) {
        authorize(member, shop);
        var q = new LambdaQueryWrapperX<AfterSaleDO>().eq(AfterSaleDO::getId, id).eq(AfterSaleDO::getShopId, shop);
        if (lock) q.last("FOR UPDATE");
        var sale = afterSales.selectOne(q);
        if (sale == null) throw invalid("售后单不存在或无权访问");
        var item = items.selectById(sale.getOrderItemId());
        if (item == null || !Objects.equals(item.getShopId(), shop) || !Objects.equals(item.getOrderId(), sale.getOrderId()))
            throw invalid("售后商品归属不完整，请联系平台处理");
        if (lock && (!Objects.equals(item.getAfterSaleId(), id) || !Integer.valueOf(10).equals(item.getAfterSaleStatus())))
            throw invalid("该售后已失效，请刷新后重试");
        return sale;
    }
    public AfterSaleDetail afterSaleDetail(Long member, Long shop, Long id) {
        var sale = ownedAfterSale(member, shop, id, false);
        var erp=erpInventory.list(sale.getOrderId());
        return new AfterSaleDetail(afterSaleRow(sale,erp), sale.getApplyPicUrls(), sale.getReceiveReason(),
                sale.getReturnReceiverName(),sale.getReturnReceiverMobile(),sale.getReturnAddress(), erp);
    }
    @Transactional(rollbackFor = Exception.class)
    public void processAfterSale(Long member, Long shop, Long id, AfterSaleAction action, String reason) {
        var sale = ownedAfterSale(member, shop, id, true);
        String blocked=OrderActionPolicy.afterSaleBlock(sale,erpInventory.list(sale.getOrderId()));
        if(blocked!=null)throw invalid(blocked);
        if (action == null) throw invalid("请选择售后操作");
        boolean audit = action == AfterSaleAction.AGREE || action == AfterSaleAction.REJECT;
        if (!Integer.valueOf(audit ? 10 : 30).equals(sale.getStatus())
                || (!audit && !Integer.valueOf(20).equals(sale.getWay()))
                || (audit && !List.of(10, 20).contains(sale.getWay())))
            throw invalid("售后状态已变化，请刷新后重试");
        String memo = reason == null ? "" : reason.trim();
        if ((action == AfterSaleAction.REJECT || action == AfterSaleAction.REFUSE) && (memo.isEmpty() || memo.length() > 200))
            throw invalid("请填写1至200字的拒绝原因");
        if(action == AfterSaleAction.AGREE && Integer.valueOf(20).equals(sale.getWay())) {
            var address = authorization.returnAddress(member,shop).getCheckedData();
            if(address==null || address.receiverName()==null || address.receiverName().isBlank()
                    || address.receiverMobile()==null || address.receiverMobile().isBlank()
                    || address.address()==null || address.address().isBlank())
                throw invalid("请先维护完整的店铺退货地址");
            // 同一事务中先保存地址快照，再同意退货；任一步失败均回滚。
            afterSales.updateById(new AfterSaleDO().setId(id).setReturnReceiverName(address.receiverName())
                    .setReturnReceiverMobile(address.receiverMobile()).setReturnAddress(address.address()));
        }
        // audit_user_id 专指后台管理员，不能写入可能重号的会员ID。
        // 售后日志切面从当前登录上下文记录真实 MEMBER 类型及会员ID，不切换管理员身份。
        switch (action) {
            case AGREE -> afterSaleUpdate.agreeAfterSale(null, id);
            case REJECT -> afterSaleUpdate.disagreeAfterSale(null, new AfterSaleDisagreeReqVO().setId(id).setAuditReason(memo));
            case RECEIVE -> afterSaleUpdate.receiveAfterSale(member, id);
            case REFUSE -> afterSaleUpdate.refuseAfterSale(member, new AfterSaleRefuseReqVO().setId(id).setRefuseMemo(memo));
        }
        // 不调用 refundAfterSale；同意仅流转到待退货或等待平台退款。
    }
}
