package cn.iocoder.txgy.module.trade.service.order;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.pay.api.order.PayOrderApi;
import cn.iocoder.txgy.module.pay.api.order.dto.PayOrderRespDTO;
import cn.iocoder.txgy.module.pay.enums.order.PayOrderStatusEnum;
import cn.iocoder.txgy.module.trade.controller.app.order.vo.AppTradeOrderPaymentRespVO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.enums.order.TradeOrderStatusEnum;
import cn.iocoder.txgy.module.trade.framework.order.config.TradeOrderProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * 交易支付计时的唯一规则入口。WMS 当前只做实时库存核验，不等待预占回调。
 * 旧单没有时限快照时只读取已有支付单事实，不在查询期间回填或续期。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TradeOrderPaymentDeadlineService {

    private final TradeOrderProperties properties;
    private final PayOrderApi payOrderApi;

    public void initialize(TradeOrderDO order, LocalDateTime now) {
        order.setPayExpireSeconds(seconds(properties.getPayExpireTime(), "支付"));
        order.setErpWaitExpireTime(null);
    }

    private long seconds(Duration duration, String label) {
        if (duration == null || duration.isNegative() || duration.isZero() || duration.toSeconds() < 1) {
            throw new IllegalStateException(label + "时限必须至少为 1 秒");
        }
        return duration.toSeconds();
    }

    public boolean isNewRule(TradeOrderDO order) {
        return order.getPayExpireSeconds() != null;
    }

    /** 只允许在订单行锁内首次调用，重复调用不续期。存量无支付单时保持原下单计时规则。 */
    public LocalDateTime startPayment(TradeOrderDO order, LocalDateTime now) {
        if (order.getPayExpireTime() != null) return order.getPayExpireTime();
        LocalDateTime deadline;
        if (isNewRule(order)) {
            if (order.getPayExpireSeconds() <= 0) throw new ServiceException(400, "订单支付时限快照无效");
            // trade/pay 使用 DATETIME(0)，先统一整秒，避免数据库舍入后看似延长支付窗口。
            deadline = now.plusSeconds(order.getPayExpireSeconds()).truncatedTo(ChronoUnit.SECONDS);
        } else {
            deadline = legacyDeadlineWithoutPayOrder(order);
        }
        if (deadline == null || !now.isBefore(deadline)) throw new ServiceException(400, "订单支付已到期");
        order.setPayExpireTime(deadline);
        return deadline;
    }

    /** 仅供没有任何支付单的旧订单使用，不能覆盖已有支付单的历史有效期。 */
    public LocalDateTime legacyDeadlineWithoutPayOrder(TradeOrderDO order) {
        return order.getCreateTime() == null ? null
                : order.getCreateTime().plusSeconds(seconds(properties.getPayExpireTime(), "支付"));
    }

    public PayOrderRespDTO getVerifiedPayOrder(TradeOrderDO order) {
        if (order.getPayOrderId() == null) throw new ServiceException(400, "订单尚未生成支付单");
        var result = payOrderApi.getOrder(order.getPayOrderId());
        PayOrderRespDTO pay = result == null ? null : result.getCheckedData();
        if (pay == null || !Objects.equals(pay.getId(), order.getPayOrderId())
                || !Objects.equals(pay.getMerchantOrderId(), String.valueOf(order.getId()))
                || !Objects.equals(pay.getPrice(), order.getPayPrice()) || pay.getExpireTime() == null) {
            throw new ServiceException(400, "支付信息尚未核实，请稍后重试");
        }
        return pay;
    }

    /**
     * 列表新单使用已保存的交易窗口，避免逐条 RPC。详情以及旧的未支付单读实际支付信息；
     * 支付提交仍在支付模块校验，列表按钮不能代替服务端支付授权。
     */
    public void fill(TradeOrderDO order, AppTradeOrderPaymentRespVO response, boolean verifyPayment) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = order.getPayExpireTime();
        String reason = null;
        response.setCancelType(order.getCancelType());
        response.setErpWaitExpireTime(order.getErpWaitExpireTime());
        response.setErpWaitExpireTimestamp(timestamp(order.getErpWaitExpireTime()));
        response.setServerTime(timestamp(now));

        if (!TradeOrderStatusEnum.isUnpaid(order.getStatus()) || Boolean.TRUE.equals(order.getPayStatus())) {
            reason = "ORDER_NOT_UNPAID";
        } else if (order.getPayOrderId() == null) {
            // 旧单没有支付单只保留历史关闭规则，不以页面访问时间构造可支付期限。
            reason = "PAY_ORDER_MISSING";
        } else if (verifyPayment || !isNewRule(order)) {
            try {
                PayOrderRespDTO pay = getVerifiedPayOrder(order);
                // 幂等重试可能命中更早的支付单，始终以持久化支付事实为准，不取当前配置。
                deadline = pay.getExpireTime();
                if (!PayOrderStatusEnum.isWaiting(pay.getStatus())) {
                    reason = PayOrderStatusEnum.isClosed(pay.getStatus()) ? "PAY_EXPIRED" : "PAYMENT_UNCONFIRMED";
                }
            } catch (RuntimeException ex) {
                log.warn("[fillPaymentWindow][order({}) 支付窗口核实失败，禁止显示可付款]", order.getId());
                reason = "PAYMENT_UNCONFIRMED";
                if (!isNewRule(order)) deadline = null;
            }
        }
        if (reason == null && (deadline == null || !now.isBefore(deadline))) reason = "PAY_EXPIRED";
        response.setPayExpireTime(deadline);
        response.setPayExpireTimestamp(timestamp(deadline));
        response.setCanPay(reason == null);
        response.setPaymentBlockReason(reason);
    }

    public static Long timestamp(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
