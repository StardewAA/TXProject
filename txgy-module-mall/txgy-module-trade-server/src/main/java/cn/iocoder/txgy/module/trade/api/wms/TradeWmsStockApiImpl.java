package cn.iocoder.txgy.module.trade.api.wms;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.txgy.module.trade.enums.order.TradeOrderStatusEnum;
import cn.iocoder.txgy.module.trade.service.wms.TradeWmsStockService;
import cn.iocoder.txgy.module.trade.service.checkout.PlatformCheckoutOrderService;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@RestController
@RequiredArgsConstructor
public class TradeWmsStockApiImpl implements TradeWmsStockApi {
    private final TradeOrderMapper orderMapper;
    private final TradeOrderItemMapper orderItemMapper;
    private final TradeWmsStockService stockService;
    private final PlatformCheckoutOrderService platformCheckoutOrderService;

    @Override
    public CommonResult<Boolean> validatePayment(Long orderId, Long payOrderId, Long memberId) {
        if (orderId != null && orderId < 0) {
            platformCheckoutOrderService.validatePayment(-orderId, payOrderId, memberId,
                    TenantContextHolder.getRequiredTenantId());
            return success(true);
        }
        var order = orderMapper.selectById(orderId);
        if (order == null || !Objects.equals(order.getUserId(), memberId)
                || !Objects.equals(order.getPayOrderId(), payOrderId)) {
            throw new ServiceException(404, "商城订单或支付关联不存在");
        }
        if (!TradeOrderStatusEnum.isUnpaid(order.getStatus()) || Boolean.TRUE.equals(order.getPayStatus())) {
            throw new ServiceException(400, "订单当前不可支付");
        }
        stockService.validateAvailable(orderItemMapper.selectListByOrderId(orderId));
        return success(true);
    }
}
