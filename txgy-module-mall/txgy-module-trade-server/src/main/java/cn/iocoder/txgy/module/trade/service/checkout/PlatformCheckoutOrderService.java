package cn.iocoder.txgy.module.trade.service.checkout;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.enums.TerminalEnum;
import cn.iocoder.txgy.framework.common.enums.UserTypeEnum;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.member.api.platform.MemberPlatformAccountApi;
import cn.iocoder.txgy.module.pay.api.order.PayOrderApi;
import cn.iocoder.txgy.module.pay.api.order.dto.PayOrderCreateReqDTO;
import cn.iocoder.txgy.module.pay.api.order.dto.PayOrderRespDTO;
import cn.iocoder.txgy.module.pay.enums.order.PayOrderStatusEnum;
import cn.iocoder.txgy.module.product.api.property.dto.ProductPropertyValueDetailRespDTO;
import cn.iocoder.txgy.module.trade.dal.dataobject.checkout.PlatformCheckoutDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.checkout.PlatformCheckoutOrderDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.checkout.PlatformCheckoutPlanDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.checkout.PlatformCheckoutPlanItemDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.checkout.PlatformFundAllocationDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.txgy.module.trade.dal.mysql.cart.PlatformCartMapper;
import cn.iocoder.txgy.module.trade.dal.mysql.checkout.PlatformCheckoutMapper;
import cn.iocoder.txgy.module.trade.dal.mysql.checkout.PlatformCheckoutOrderMapper;
import cn.iocoder.txgy.module.trade.dal.mysql.checkout.PlatformCheckoutPlanItemMapper;
import cn.iocoder.txgy.module.trade.dal.mysql.checkout.PlatformCheckoutPlanMapper;
import cn.iocoder.txgy.module.trade.dal.mysql.checkout.PlatformFundAllocationMapper;
import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.txgy.module.trade.dal.redis.no.TradeNoRedisDAO;
import cn.iocoder.txgy.module.trade.enums.delivery.DeliveryTypeEnum;
import cn.iocoder.txgy.module.trade.enums.order.TradeOrderItemAfterSaleStatusEnum;
import cn.iocoder.txgy.module.trade.enums.order.TradeOrderRefundStatusEnum;
import cn.iocoder.txgy.module.trade.enums.order.TradeOrderStatusEnum;
import cn.iocoder.txgy.module.trade.enums.order.TradeOrderTypeEnum;
import cn.iocoder.txgy.module.trade.framework.order.config.TradeOrderProperties;
import cn.iocoder.txgy.module.trade.service.order.handler.TradeOrderWorkbenchHandler;
import cn.iocoder.txgy.module.trade.service.order.handler.TradeProductSkuOrderHandler;
import cn.iocoder.txgy.module.trade.service.wms.TradeWmsStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.util.servlet.ServletUtils.getClientIP;
import static cn.iocoder.txgy.framework.web.core.util.WebFrameworkUtils.getTerminal;

/** 将已校验的平台拆单快照转换为正式店铺订单和一张统一支付单。 */
@Service
@RequiredArgsConstructor
public class PlatformCheckoutOrderService {

    private static final String PAY_STATUS_UNPAID = "UNPAID";
    private static final String PAY_STATUS_PAID = "PAID";
    private static final String ALLOCATION_PENDING = "PENDING";
    private static final String ALLOCATION_ALLOCATED = "ALLOCATED";

    private final PlatformCheckoutMapper checkoutMapper;
    private final PlatformCheckoutPlanMapper planMapper;
    private final PlatformCheckoutPlanItemMapper planItemMapper;
    private final PlatformCheckoutOrderMapper checkoutOrderMapper;
    private final PlatformFundAllocationMapper allocationMapper;
    private final PlatformCartMapper cartMapper;
    private final TradeOrderMapper orderMapper;
    private final TradeOrderItemMapper orderItemMapper;
    private final TradeNoRedisDAO tradeNoRedisDAO;
    private final TradeWmsStockService wmsStockService;
    private final TradeProductSkuOrderHandler productSkuOrderHandler;
    private final TradeOrderWorkbenchHandler workbenchHandler;
    private final PayOrderApi payOrderApi;
    private final TradeOrderProperties orderProperties;
    private final MemberPlatformAccountApi platformAccountApi;

    @Transactional(rollbackFor = Exception.class)
    public PlatformCheckoutDO formalize(PlatformCheckoutDO candidate, Long buyerMemberId, Long sourceTenantId) {
        PlatformCheckoutDO checkout = checkoutMapper.selectByNoAndMemberForUpdate(
                candidate.getCheckoutNo(), candidate.getPlatformMemberId());
        if (checkout == null) throw new ServiceException(404, "结算单不存在");
        if (checkout.getPlatformPayOrderId() != null) return checkout;

        List<PlatformCheckoutPlanDO> plans = planMapper.selectListByCheckout(checkout.getId(), checkout.getCheckoutNo());
        List<PlatformCheckoutPlanItemDO> items = planItemMapper.selectListByCheckout(checkout.getId(), checkout.getCheckoutNo());
        if (plans.isEmpty() || items.isEmpty()) throw new ServiceException(400, "结算商品快照不完整");
        Map<Long, List<PlatformCheckoutPlanItemDO>> itemsByPlan = new LinkedHashMap<>();
        items.forEach(item -> itemsByPlan.computeIfAbsent(item.getPlanId(), ignored -> new ArrayList<>()).add(item));

        LocalDateTime now = LocalDateTime.now();
        long expireSeconds = orderProperties.getPayExpireTime().toSeconds();
        if (expireSeconds < 1) throw new IllegalStateException("订单支付时限必须至少为 1 秒");
        LocalDateTime expireTime = now.plusSeconds(expireSeconds).truncatedTo(ChronoUnit.SECONDS);
        List<CreatedChild> children = new ArrayList<>();
        for (PlatformCheckoutPlanDO plan : plans) {
            List<PlatformCheckoutPlanItemDO> planItems = itemsByPlan.getOrDefault(plan.getId(), List.of());
            if (planItems.isEmpty()) throw new ServiceException(400, "店铺订单商品不能为空");
            children.add(TenantUtils.execute(plan.getTenantId(), () -> createChild(
                    checkout, plan, planItems, buyerMemberId, now, expireSeconds, expireTime)));
        }

        int payAmount = Math.toIntExact(checkout.getPayAmount());
        String ip = getClientIP();
        if (ip == null || ip.isBlank()) ip = "127.0.0.1";
        PayOrderCreateReqDTO payRequest = new PayOrderCreateReqDTO()
                .setAppKey(orderProperties.getPayAppKey()).setUserIp(ip)
                .setUserId(buyerMemberId).setUserType(UserTypeEnum.MEMBER.getValue())
                .setMerchantOrderId(String.valueOf(-checkout.getId()))
                .setSubject(children.size() == 1 ? "商城商品订单" : "商城合并订单")
                .setBody("平台结算单 " + checkout.getCheckoutNo())
                .setPrice(payAmount).setExpireTime(expireTime);
        Long payOrderId = payOrderApi.createOrder(payRequest).getCheckedData();
        PayOrderRespDTO payOrder = payOrderApi.getOrder(payOrderId).getCheckedData();
        if (payOrder == null || !Objects.equals(payOrder.getMerchantOrderId(), String.valueOf(-checkout.getId()))
                || !Objects.equals(payOrder.getPrice(), payAmount)) {
            throw new ServiceException(500, "统一支付单创建结果不一致");
        }

        for (CreatedChild child : children) {
            TenantUtils.execute(child.tenantId(), () -> orderMapper.updateById(new TradeOrderDO()
                    .setId(child.order().getId()).setPayOrderId(payOrderId).setPayExpireTime(payOrder.getExpireTime())));
            PlatformCheckoutOrderDO relation = new PlatformCheckoutOrderDO()
                    .setCheckoutId(checkout.getId()).setCheckoutNo(checkout.getCheckoutNo())
                    .setOrderId(child.order().getId()).setOrderNo(child.order().getNo())
                    .setTenantId(child.tenantId()).setBuyerMemberId(buyerMemberId)
                    .setSellerEnterpriseId(child.plan().getSellerEnterpriseId()).setShopId(child.plan().getShopId())
                    .setGoodsAmount(child.plan().getGoodsAmount()).setFreightAmount(child.plan().getFreightAmount())
                    .setDiscountAmount(child.plan().getDiscountAmount()).setPayAmount(child.plan().getPayAmount())
                    .setRefundedAmount(0L).setOrderStatus(TradeOrderStatusEnum.UNPAID.getStatus())
                    .setAfterSaleStatus(TradeOrderItemAfterSaleStatusEnum.NONE.getStatus());
            checkoutOrderMapper.insert(relation);
            allocationMapper.insert(new PlatformFundAllocationDO()
                    .setCheckoutId(checkout.getId()).setCheckoutNo(checkout.getCheckoutNo())
                    .setOrderId(child.order().getId()).setTenantId(child.tenantId()).setShopId(child.plan().getShopId())
                    .setAllocatedAmount(child.plan().getPayAmount()).setRefundedAmount(0L)
                    .setAllocationStatus(ALLOCATION_PENDING).setVersion(0L));
            planMapper.updateById(new PlatformCheckoutPlanDO().setId(child.plan().getId())
                    .setPlanStatus("ORDER_CREATED").setBlockReason(""));
        }

        checkout.setPayStatus(PAY_STATUS_UNPAID).setAggregateStatus("ALL_UNPAID").setFailureReason(null)
                .setPlatformPayOrderId(payOrderId).setPlatformPayOrderNo(String.valueOf(payOrderId));
        checkoutMapper.updateById(checkout);
        int deleted = cartMapper.hardDeleteByIdsAndMember(items.stream()
                .map(PlatformCheckoutPlanItemDO::getCartItemId).toList(), checkout.getPlatformMemberId());
        if (deleted != items.size()) throw new ServiceException(409, "购物车状态已变化，请重新结算");
        return checkout;
    }

    public void validatePayment(Long checkoutId, Long payOrderId, Long memberId, Long sourceTenantId) {
        PlatformCheckoutDO checkout = checkoutMapper.selectById(checkoutId);
        var account = platformAccountApi.resolve(memberId, sourceTenantId).getCheckedData();
        if (checkout == null || account == null || !Objects.equals(checkout.getPlatformMemberId(), account.getPlatformAccountId())
                || !Objects.equals(checkout.getPlatformPayOrderId(), payOrderId)
                || !PAY_STATUS_UNPAID.equals(checkout.getPayStatus())) {
            throw new ServiceException(404, "平台结算单或支付关联不存在");
        }
        for (PlatformCheckoutOrderDO child : checkoutOrderMapper.selectListByCheckout(checkout.getId(), checkout.getCheckoutNo())) {
            TenantUtils.execute(child.getTenantId(), () -> {
                TradeOrderDO order = orderMapper.selectById(child.getOrderId());
                if (order == null || !TradeOrderStatusEnum.isUnpaid(order.getStatus()) || Boolean.TRUE.equals(order.getPayStatus())) {
                    throw new ServiceException(400, "订单当前不可支付");
                }
                wmsStockService.validateAvailable(orderItemMapper.selectListByOrderId(order.getId()));
            });
        }
    }

    /** Called only after the controller has authorized the requested child order. */
    @Transactional(rollbackFor = Exception.class)
    public boolean syncChildPayment(Long orderId) {
        var relation = checkoutOrderMapper.selectByOrder(orderId);
        if (relation == null) return false;
        var checkout = checkoutMapper.selectById(relation.getCheckoutId());
        if (checkout == null || checkout.getPlatformPayOrderId() == null) return true;
        TenantUtils.execute(checkout.getAddressSourceTenantId(), () -> {
            var pay = payOrderApi.getOrder(checkout.getPlatformPayOrderId()).getCheckedData();
            if (pay != null && PayOrderStatusEnum.isSuccess(pay.getStatus())) {
                updatePaid(checkout.getId(), checkout.getPlatformPayOrderId());
            }
        });
        return true;
    }

    /** A single child owns the whole payment. Never close a shared multi-shop payment here. */
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelSingleChildByBuyer(Long orderId, Long memberId, Long sourceTenantId) {
        PlatformCheckoutOrderDO relation = checkoutOrderMapper.selectByOrder(orderId);
        if (relation == null) return false;
        var account = platformAccountApi.resolve(memberId, sourceTenantId).getCheckedData();
        if (account == null || !Objects.equals(account.getLegacyMemberId(), memberId)
                || !Objects.equals(account.getSourceTenantId(), sourceTenantId)) {
            throw new ServiceException(404, "交易订单不存在");
        }
        PlatformCheckoutDO checkout = checkoutMapper.selectByNoAndMemberForUpdate(
                relation.getCheckoutNo(), account.getPlatformAccountId());
        if (checkout == null) throw new ServiceException(404, "交易订单不存在");
        List<PlatformCheckoutOrderDO> children = checkoutOrderMapper.selectListByCheckout(checkout.getId(), checkout.getCheckoutNo());
        if (children.size() != 1 || !Objects.equals(children.get(0).getOrderId(), orderId)) {
            throw new ServiceException(400, "该订单属于多店铺合并支付，不能单独取消，请联系平台客服处理整单");
        }
        TenantUtils.execute(relation.getTenantId(), () -> {
            TradeOrderDO order = orderMapper.selectByIdForUpdate(orderId);
            if (order == null || !Objects.equals(order.getUserId(), relation.getBuyerMemberId())) {
                throw new ServiceException(404, "交易订单不存在");
            }
            if ("CLOSED".equals(checkout.getPayStatus()) && TradeOrderStatusEnum.isCanceled(order.getStatus())) return;
            if (!TradeOrderStatusEnum.isUnpaid(order.getStatus()) || Boolean.TRUE.equals(order.getPayStatus())
                    || !PAY_STATUS_UNPAID.equals(checkout.getPayStatus())
                    || !Objects.equals(checkout.getPlatformPayOrderId(), order.getPayOrderId())) {
                throw new ServiceException(400, "订单已支付或已关闭，不可取消");
            }
            TenantUtils.execute(checkout.getAddressSourceTenantId(), () -> {
                Long payId = checkout.getPlatformPayOrderId();
                PayOrderRespDTO pay = payOrderApi.getOrder(payId).getCheckedData();
                String merchantId = String.valueOf(-checkout.getId());
                if (pay == null || !Objects.equals(pay.getMerchantOrderId(), merchantId)
                        || !Objects.equals(pay.getPrice(), Math.toIntExact(checkout.getPayAmount()))
                        || PayOrderStatusEnum.isSuccessOrRefund(pay.getStatus())) {
                    throw new ServiceException(409, "支付结果未确认，请刷新后重试");
                }
                if (!PayOrderStatusEnum.isClosed(pay.getStatus())) {
                    if (!Boolean.TRUE.equals(payOrderApi.closeOrder(payId, orderProperties.getPayAppKey(), merchantId).getCheckedData())) {
                        throw new ServiceException(409, "支付结果未确认，请刷新后重试");
                    }
                    pay = payOrderApi.getOrder(payId).getCheckedData();
                    if (pay == null || !PayOrderStatusEnum.isClosed(pay.getStatus())) {
                        throw new ServiceException(409, "支付结果未确认，请刷新后重试");
                    }
                }
            });
            int changed = orderMapper.updateByIdAndStatus(orderId, TradeOrderStatusEnum.UNPAID.getStatus(), new TradeOrderDO()
                    .setStatus(TradeOrderStatusEnum.CANCELED.getStatus()).setCancelTime(LocalDateTime.now())
                    .setCancelType(cn.iocoder.txgy.module.trade.enums.order.TradeOrderCancelTypeEnum.MEMBER_CANCEL.getType()));
            if (changed != 1) throw new ServiceException(409, "订单状态已变化，请刷新");
            order.setStatus(TradeOrderStatusEnum.CANCELED.getStatus());
            // Platform children never credited the seller-tenant member's points/coupons.
            workbenchHandler.afterCancelOrder(order, orderItemMapper.selectListByOrderId(orderId));
        });
        checkoutOrderMapper.updateById(new PlatformCheckoutOrderDO().setId(relation.getId())
                .setOrderStatus(TradeOrderStatusEnum.CANCELED.getStatus()));
        checkoutMapper.updateById(checkout.setPayStatus("CLOSED").setAggregateStatus("ALL_CANCELED"));
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePaid(Long checkoutId, Long payOrderId) {
        PlatformCheckoutDO checkout = checkoutMapper.selectById(checkoutId);
        if (checkout != null) checkout = checkoutMapper.selectByNoAndMemberForUpdate(
                checkout.getCheckoutNo(), checkout.getPlatformMemberId());
        if (checkout == null || !Objects.equals(checkout.getPlatformPayOrderId(), payOrderId)) {
            throw new ServiceException(404, "平台结算单或支付关联不存在");
        }
        if (PAY_STATUS_PAID.equals(checkout.getPayStatus())) return;
        if (!PAY_STATUS_UNPAID.equals(checkout.getPayStatus())) throw new ServiceException(409, "结算单已关闭，不能更新为已支付");
        PayOrderRespDTO pay = payOrderApi.getOrder(payOrderId).getCheckedData();
        if (pay == null || !PayOrderStatusEnum.isSuccess(pay.getStatus())
                || !Objects.equals(pay.getMerchantOrderId(), String.valueOf(-checkoutId))
                || !Objects.equals(pay.getPrice(), Math.toIntExact(checkout.getPayAmount()))) {
            throw new ServiceException(400, "统一支付结果未核实");
        }
        List<PlatformCheckoutOrderDO> children = checkoutOrderMapper.selectListByCheckout(checkout.getId(), checkout.getCheckoutNo());
        for (PlatformCheckoutOrderDO child : children) {
            TenantUtils.execute(child.getTenantId(), () -> markChildPaid(child, payOrderId, pay));
            child.setOrderStatus(TradeOrderStatusEnum.UNDELIVERED.getStatus());
            checkoutOrderMapper.updateById(child);
        }
        for (PlatformFundAllocationDO allocation : allocationMapper.selectListByCheckout(checkout.getId(), checkout.getCheckoutNo())) {
            allocationMapper.updateById(new PlatformFundAllocationDO().setId(allocation.getId())
                    .setAllocationStatus(ALLOCATION_ALLOCATED));
        }
        checkout.setPayStatus(PAY_STATUS_PAID).setPaidAmount(checkout.getPayAmount())
                .setAggregateStatus("ALL_UNDELIVERED");
        checkoutMapper.updateById(checkout);
    }

    private CreatedChild createChild(PlatformCheckoutDO checkout, PlatformCheckoutPlanDO plan,
                                     List<PlatformCheckoutPlanItemDO> snapshots, Long buyerMemberId,
                                     LocalDateTime now, long expireSeconds, LocalDateTime expireTime) {
        Map<Long, Integer> required = new LinkedHashMap<>();
        snapshots.forEach(item -> required.merge(item.getSkuId(), item.getQuantity(), Math::addExact));
        wmsStockService.validateSkuCounts(required);
        int goods = Math.toIntExact(plan.getGoodsAmount());
        int freight = Math.toIntExact(plan.getFreightAmount());
        int pay = Math.toIntExact(plan.getPayAmount());
        TradeOrderDO order = new TradeOrderDO().setNo(tradeNoRedisDAO.generate(TradeNoRedisDAO.TRADE_ORDER_NO_PREFIX))
                .setShopId(plan.getShopId()).setShopName(limit(plan.getShopName() == null ? "店铺" : plan.getShopName(), 64))
                .setType(TradeOrderTypeEnum.NORMAL.getType()).setSourceType(TradeOrderDO.SOURCE_TYPE_NORMAL)
                .setTerminal(getTerminal() == null ? TerminalEnum.UNKNOWN.getTerminal() : getTerminal())
                .setUserId(buyerMemberId).setUserIp(defaultIp()).setUserRemark(limit(checkout.getRemark(), 200))
                .setStatus(TradeOrderStatusEnum.UNPAID.getStatus()).setProductCount(plan.getTotalQuantity())
                .setCommentStatus(false).setPayStatus(false).setTotalPrice(goods).setDiscountPrice(0)
                .setDeliveryPrice(freight).setAdjustPrice(0).setPayPrice(pay)
                .setPayExpireSeconds(expireSeconds).setPayExpireTime(expireTime)
                .setDeliveryType(DeliveryTypeEnum.EXPRESS.getType()).setReceiverName(limit(checkout.getReceiverName(), 20))
                .setReceiverMobile(limit(checkout.getReceiverMobile(), 20)).setReceiverAreaId(Math.toIntExact(checkout.getReceiverAreaId()))
                .setReceiverDetailAddress(limit(checkout.getReceiverDetailAddress(), 255))
                .setReceiverLongitude(checkout.getReceiverLongitude()).setReceiverLatitude(checkout.getReceiverLatitude())
                .setReceiverCoordSystem(checkout.getReceiverCoordSystem()).setRefundStatus(TradeOrderRefundStatusEnum.NONE.getStatus())
                .setRefundPrice(0).setCouponPrice(0).setUsePoint(0).setPointPrice(0).setGivePoint(0).setVipPrice(0);
        orderMapper.insert(order);
        List<TradeOrderItemDO> orderItems = new ArrayList<>();
        for (int i = 0; i < snapshots.size(); i++) {
            PlatformCheckoutPlanItemDO snapshot = snapshots.get(i);
            List<ProductPropertyValueDetailRespDTO> sourceProperties = JsonUtils.parseArray(
                    snapshot.getPropertiesJson(), ProductPropertyValueDetailRespDTO.class);
            List<TradeOrderItemDO.Property> properties = sourceProperties == null ? List.of() : sourceProperties.stream()
                    .map(source -> new TradeOrderItemDO.Property().setPropertyId(source.getPropertyId())
                            .setPropertyName(source.getPropertyName()).setValueId(source.getValueId())
                            .setValueName(source.getValueName())).toList();
            int itemFreight = i == 0 ? freight : 0;
            TradeOrderItemDO item = new TradeOrderItemDO().setUserId(buyerMemberId).setOrderId(order.getId())
                    .setSpuId(snapshot.getSpuId()).setShopId(plan.getShopId()).setShopName(order.getShopName())
                    .setSpuName(limit(snapshot.getSpuName(), 255)).setSkuId(snapshot.getSkuId()).setProperties(properties)
                    .setPicUrl(limit(snapshot.getPicUrl(), 255)).setCount(snapshot.getQuantity()).setCommentStatus(false)
                    .setPrice(snapshot.getUnitPrice()).setDiscountPrice(0).setDeliveryPrice(itemFreight).setAdjustPrice(0)
                    .setPayPrice(Math.addExact(Math.toIntExact(snapshot.getGoodsAmount()), itemFreight))
                    .setCouponPrice(0).setPointPrice(0).setUsePoint(0).setGivePoint(0).setVipPrice(0)
                    .setAfterSaleStatus(TradeOrderItemAfterSaleStatusEnum.NONE.getStatus());
            orderItems.add(item);
        }
        orderItemMapper.insertBatch(orderItems);
        // 跨租户子单中的 userId 是平台买家引用，目标租户未必存在同号会员；这里只执行
        // 订单工作台和 WMS 生命周期，避免错误发放目标租户积分、优惠券或分销权益。
        productSkuOrderHandler.afterOrderCreate(order, orderItems);
        workbenchHandler.afterOrderCreate(order, orderItems);
        return new CreatedChild(plan.getTenantId(), plan, order);
    }

    private void markChildPaid(PlatformCheckoutOrderDO child, Long payOrderId, PayOrderRespDTO pay) {
        TradeOrderDO order = orderMapper.selectByIdForUpdate(child.getOrderId());
        if (order == null) throw new ServiceException(404, "平台子订单不存在");
        if (Boolean.TRUE.equals(order.getPayStatus())) return;
        int updated = orderMapper.updateByIdAndStatus(order.getId(), order.getStatus(), new TradeOrderDO()
                .setStatus(TradeOrderStatusEnum.UNDELIVERED.getStatus()).setPayStatus(true)
                .setPayOrderId(payOrderId).setPayTime(pay.getSuccessTime()).setPayChannelCode(pay.getChannelCode()));
        if (updated != 1) throw new ServiceException(409, "平台子订单支付状态更新冲突");
        order.setStatus(TradeOrderStatusEnum.UNDELIVERED.getStatus()).setPayStatus(true)
                .setPayOrderId(payOrderId).setPayTime(pay.getSuccessTime()).setPayChannelCode(pay.getChannelCode());
        List<TradeOrderItemDO> items = orderItemMapper.selectListByOrderId(order.getId());
        productSkuOrderHandler.afterPayOrder(order, items);
        workbenchHandler.afterPayOrder(order, items);
    }

    private static String defaultIp() {
        String ip = getClientIP();
        return ip == null || ip.isBlank() ? "127.0.0.1" : ip;
    }

    private static String limit(String value, int maxLength) {
        return value == null ? null : StrUtil.maxLength(value, maxLength);
    }

    private record CreatedChild(Long tenantId, PlatformCheckoutPlanDO plan, TradeOrderDO order) {}
}
