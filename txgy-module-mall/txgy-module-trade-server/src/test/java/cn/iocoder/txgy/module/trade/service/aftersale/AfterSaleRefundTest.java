package cn.iocoder.txgy.module.trade.service.aftersale;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.pay.api.order.PayOrderApi;
import cn.iocoder.txgy.module.pay.api.order.dto.PayOrderRespDTO;
import cn.iocoder.txgy.module.pay.api.refund.PayRefundApi;
import cn.iocoder.txgy.module.pay.api.refund.dto.PayRefundRespDTO;
import cn.iocoder.txgy.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.dal.mysql.aftersale.AfterSaleMapper;
import cn.iocoder.txgy.module.trade.enums.aftersale.AfterSaleStatusEnum;
import cn.iocoder.txgy.module.trade.framework.aftersale.core.aop.AfterSaleLogAspect;
import cn.iocoder.txgy.module.trade.framework.order.config.TradeOrderProperties;
import cn.iocoder.txgy.module.trade.service.order.TradeOrderUpdateService;
import cn.iocoder.txgy.module.trade.service.order.TradeOrderQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AfterSaleRefundTest {
    @InjectMocks private AfterSaleServiceImpl service;
    @Mock private AfterSaleMapper tradeAfterSaleMapper;
    @Mock private PayRefundApi payRefundApi;
    @Mock private PayOrderApi payOrderApi;
    @Mock private TradeOrderQueryService tradeOrderQueryService;
    @Mock private TradeOrderUpdateService tradeOrderUpdateService;
    private AfterSaleDO sale;

    @BeforeEach
    void setup() {
        TenantContextHolder.setTenantId(2024L);
        sale = new AfterSaleDO().setId(1L).setOrderId(2L).setOrderItemId(3L)
                .setUserId(4L).setSpuName("测试商品").setRefundPrice(122).setPayRefundId(5L)
                .setStatus(AfterSaleStatusEnum.WAIT_REFUND.getStatus());
        when(tradeAfterSaleMapper.selectById(1L)).thenReturn(sale);
        lenient().when(tradeOrderQueryService.getOrder(2L))
                .thenReturn(new TradeOrderDO().setId(2L).setPayOrderId(7L));
        lenient().when(payOrderApi.getOrderTenantId(7L)).thenReturn(success(2009L));
        lenient().when(payOrderApi.getOrder(7L)).thenReturn(success(new PayOrderRespDTO()
                .setId(7L).setMerchantOrderId("-12").setPrice(122)));
        ReflectionTestUtils.setField(service, "tradeOrderProperties", new TradeOrderProperties());
    }

    @AfterEach
    void clearLogContext() {
        TenantContextHolder.clear();
        ReflectionTestUtils.invokeMethod(AfterSaleLogAspect.class, "clear");
    }

    private void refundResult(int status, int price) {
        when(payRefundApi.getRefund(5L)).thenReturn(success(new PayRefundRespDTO()
                .setId(5L).setStatus(status).setRefundPrice(price).setMerchantRefundId("1")));
    }

    private void allowCompletion() {
        when(tradeAfterSaleMapper.updateByIdAndStatus(eq(1L),
                eq(AfterSaleStatusEnum.WAIT_REFUND.getStatus()), any())).thenReturn(1);
    }

    @Test
    void synchronousSuccessUpdatesOrderUsingCentsAndDoesNotCreateAnotherRefund() {
        refundResult(10, 122);
        allowCompletion();
        service.refundAfterSale(10L, "127.0.0.1", 1L);
        verify(payRefundApi, never()).createRefund(any());
        verify(tradeOrderUpdateService).updateOrderItemWhenAfterSaleSuccess(3L, 122);
    }

    @Test
    void newlyCreatedSynchronousRefundCompletesOrder() {
        sale.setPayRefundId(null);
        when(payRefundApi.createRefund(any())).thenReturn(success(5L));
        refundResult(10, 122);
        allowCompletion();
        service.refundAfterSale(10L, "127.0.0.1", 1L);
        verify(payRefundApi).createRefund(argThat(request -> request.getPrice() == 122
                && "-12".equals(request.getMerchantOrderId())));
        verify(tradeOrderUpdateService).updateOrderItemWhenAfterSaleSuccess(3L, 122);
    }

    @Test
    void pendingRefundDoesNotCompleteOrCreateDuplicate() {
        refundResult(0, 122);
        service.refundAfterSale(10L, "127.0.0.1", 1L);
        verify(payRefundApi, never()).createRefund(any());
        verifyNoInteractions(tradeOrderUpdateService);
    }

    @Test
    void failedRefundDoesNotMarkOrderRefunded() {
        refundResult(20, 122);
        service.refundAfterSale(10L, "127.0.0.1", 1L);
        verifyNoInteractions(tradeOrderUpdateService);
    }

    @Test
    void repeatedSuccessCallbackDoesNotRefundOrderTwice() {
        sale.setStatus(AfterSaleStatusEnum.COMPLETE.getStatus());
        service.updateAfterSaleRefunded(1L, 2L, 5L);
        verifyNoInteractions(payRefundApi, tradeOrderUpdateService);
    }

    @Test
    void completedRecoveryCallbackBindsMissingRefundWithoutRefundingTwice() {
        sale.setStatus(AfterSaleStatusEnum.COMPLETE.getStatus()).setPayRefundId(null);
        refundResult(10, 122);

        service.updateAfterSaleRefunded(1L, 2L, 5L);

        verify(tradeAfterSaleMapper).updateById(argThat((AfterSaleDO update) -> update.getId().equals(1L)
                && update.getPayRefundId().equals(5L)));
        verifyNoInteractions(tradeOrderUpdateService);
    }

    @Test
    void mismatchedAmountIsRejected() {
        refundResult(10, 12200);
        assertThrows(ServiceException.class, () -> service.updateAfterSaleRefunded(1L, 2L, 5L));
        verifyNoInteractions(tradeOrderUpdateService);
    }

    @Test
    void zeroCashRefundAlsoCompletesTheOrderItem() {
        sale.setRefundPrice(0);
        allowCompletion();
        service.refundAfterSale(10L, "127.0.0.1", 1L);
        verify(tradeOrderUpdateService).updateOrderItemWhenAfterSaleSuccess(3L, 0);
        verifyNoInteractions(payRefundApi);
    }
}
