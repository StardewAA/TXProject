package cn.iocoder.txgy.module.trade.service.checkout;

import cn.iocoder.txgy.module.trade.dal.dataobject.checkout.PlatformCheckoutDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.checkout.PlatformCheckoutOrderDO;
import cn.iocoder.txgy.module.trade.enums.order.TradeOrderStatusEnum;

import java.util.List;
import java.util.Objects;

/**
 * 根据平台结算单下的子订单事实计算聚合状态。
 *
 * <p>该解析器是纯计算，不创建订单、不修改支付或库存事实，也不会把拆单规划解释为真实订单。
 * 后续真实子订单接通后，订单事件和对账任务可以复用同一计算规则重算物化状态。</p>
 */
public final class PlatformCheckoutAggregateStatusResolver {

    public static final String DRAFT_ONLY = "DRAFT_ONLY";
    public static final String ALL_UNPAID = "ALL_UNPAID";
    public static final String PARTIALLY_PAID = "PARTIALLY_PAID";
    public static final String ALL_UNDELIVERED = "ALL_UNDELIVERED";
    public static final String PARTIALLY_DELIVERED = "PARTIALLY_DELIVERED";
    public static final String ALL_DELIVERED = "ALL_DELIVERED";
    public static final String PARTIALLY_COMPLETED = "PARTIALLY_COMPLETED";
    public static final String ALL_COMPLETED = "ALL_COMPLETED";
    public static final String PARTIALLY_CANCELED = "PARTIALLY_CANCELED";
    public static final String ALL_CANCELED = "ALL_CANCELED";
    public static final String PARTIALLY_REFUNDED = "PARTIALLY_REFUNDED";
    public static final String ALL_REFUNDED = "ALL_REFUNDED";

    private PlatformCheckoutAggregateStatusResolver() {
    }

    public static String resolve(PlatformCheckoutDO checkout, List<PlatformCheckoutOrderDO> orders) {
        Objects.requireNonNull(checkout, "checkout");
        Objects.requireNonNull(orders, "orders");
        if (orders.isEmpty()) {
            if (DRAFT_ONLY.equals(checkout.getAggregateStatus())) {
                return DRAFT_ONLY;
            }
            throw new IllegalArgumentException("没有子订单事实，不能推导正式平台结算状态");
        }

        int unpaid = 0;
        int undelivered = 0;
        int delivered = 0;
        int completed = 0;
        int canceled = 0;
        long payAmount = 0L;
        long refundedAmount = 0L;
        for (PlatformCheckoutOrderDO order : orders) {
            if (order == null || order.getOrderStatus() == null) {
                throw new IllegalArgumentException("子订单状态不能为空");
            }
            if (TradeOrderStatusEnum.isUnpaid(order.getOrderStatus())) {
                unpaid++;
            } else if (TradeOrderStatusEnum.isUndelivered(order.getOrderStatus())) {
                undelivered++;
            } else if (TradeOrderStatusEnum.isDelivered(order.getOrderStatus())) {
                delivered++;
            } else if (TradeOrderStatusEnum.isCompleted(order.getOrderStatus())) {
                completed++;
            } else if (TradeOrderStatusEnum.isCanceled(order.getOrderStatus())) {
                canceled++;
            } else {
                throw new IllegalArgumentException("未知子订单状态：" + order.getOrderStatus());
            }
            if (order.getPayAmount() == null || order.getPayAmount() < 0
                    || order.getRefundedAmount() == null || order.getRefundedAmount() < 0
                    || order.getRefundedAmount() > order.getPayAmount()) {
                throw new IllegalArgumentException("子订单退款事实无效");
            }
            try {
                payAmount = Math.addExact(payAmount, order.getPayAmount());
                refundedAmount = Math.addExact(refundedAmount, order.getRefundedAmount());
            } catch (ArithmeticException overflow) {
                throw new IllegalArgumentException("子订单金额汇总溢出", overflow);
            }
        }

        int total = orders.size();
        if (payAmount > 0 && refundedAmount == payAmount) {
            return ALL_REFUNDED;
        }
        if (refundedAmount > 0) {
            return PARTIALLY_REFUNDED;
        }
        if (completed == total) {
            return ALL_COMPLETED;
        }
        if (completed > 0) {
            return PARTIALLY_COMPLETED;
        }
        if (canceled == total) {
            return ALL_CANCELED;
        }
        if (canceled > 0) {
            return PARTIALLY_CANCELED;
        }
        if (delivered == total) {
            return ALL_DELIVERED;
        }
        if (delivered > 0) {
            return PARTIALLY_DELIVERED;
        }
        if (undelivered == total) {
            return ALL_UNDELIVERED;
        }
        if (undelivered > 0) {
            return PARTIALLY_PAID;
        }
        if (unpaid == total) {
            return ALL_UNPAID;
        }
        throw new IllegalArgumentException("无法根据子订单事实推导聚合状态");
    }

}
