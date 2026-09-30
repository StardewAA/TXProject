package cn.iocoder.txgy.module.trade.service.checkout;

import cn.iocoder.txgy.module.trade.dal.dataobject.checkout.PlatformCheckoutDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.checkout.PlatformCheckoutOrderDO;
import cn.iocoder.txgy.module.trade.enums.order.TradeOrderStatusEnum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlatformCheckoutAggregateStatusResolverTest {

    @Test
    void keepsPlanningSnapshotAsDraftWithoutInventingOrders() {
        PlatformCheckoutDO checkout = new PlatformCheckoutDO().setAggregateStatus("DRAFT_ONLY");

        String status = PlatformCheckoutAggregateStatusResolver.resolve(checkout, List.of());

        assertEquals("DRAFT_ONLY", status);
    }

    @Test
    void rejectsNonDraftCheckoutWithoutChildOrderFacts() {
        PlatformCheckoutDO checkout = new PlatformCheckoutDO().setAggregateStatus("CREATING");

        assertThrows(IllegalArgumentException.class,
                () -> PlatformCheckoutAggregateStatusResolver.resolve(checkout, List.of()));
    }

    @Test
    void derivesProgressFromAllChildOrdersInsteadOfStoredHeader() {
        PlatformCheckoutDO checkout = new PlatformCheckoutDO().setAggregateStatus("CREATED");
        List<PlatformCheckoutOrderDO> orders = List.of(
                order(TradeOrderStatusEnum.COMPLETED, 1000L, 0L),
                order(TradeOrderStatusEnum.DELIVERED, 2000L, 0L));

        String status = PlatformCheckoutAggregateStatusResolver.resolve(checkout, orders);

        assertEquals("PARTIALLY_COMPLETED", status);
    }

    @Test
    void refundFactsTakePrecedenceOverFulfilmentProgress() {
        PlatformCheckoutDO checkout = new PlatformCheckoutDO().setAggregateStatus("CREATED");
        List<PlatformCheckoutOrderDO> partial = List.of(
                order(TradeOrderStatusEnum.COMPLETED, 1000L, 1000L),
                order(TradeOrderStatusEnum.COMPLETED, 2000L, 200L));
        List<PlatformCheckoutOrderDO> all = List.of(
                order(TradeOrderStatusEnum.COMPLETED, 1000L, 1000L),
                order(TradeOrderStatusEnum.CANCELED, 2000L, 2000L));

        assertEquals("PARTIALLY_REFUNDED",
                PlatformCheckoutAggregateStatusResolver.resolve(checkout, partial));
        assertEquals("ALL_REFUNDED",
                PlatformCheckoutAggregateStatusResolver.resolve(checkout, all));
    }

    @Test
    void rejectsUnknownChildOrderStatus() {
        PlatformCheckoutDO checkout = new PlatformCheckoutDO().setAggregateStatus("CREATED");

        assertThrows(IllegalArgumentException.class, () -> PlatformCheckoutAggregateStatusResolver.resolve(
                checkout, List.of(new PlatformCheckoutOrderDO()
                        .setOrderStatus(999).setPayAmount(100L).setRefundedAmount(0L))));
    }

    private static PlatformCheckoutOrderDO order(TradeOrderStatusEnum status, long payAmount,
                                                  long refundedAmount) {
        return new PlatformCheckoutOrderDO().setOrderStatus(status.getStatus())
                .setPayAmount(payAmount).setRefundedAmount(refundedAmount);
    }

}
