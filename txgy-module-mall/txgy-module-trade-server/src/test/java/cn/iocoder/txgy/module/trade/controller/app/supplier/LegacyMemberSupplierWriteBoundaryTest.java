package cn.iocoder.txgy.module.trade.controller.app.supplier;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.trade.controller.app.settlement.AppShopSettlementController;
import cn.iocoder.txgy.module.trade.framework.compatibility.LegacyMemberSupplierWriteGuard;
import cn.iocoder.txgy.module.trade.service.settlement.SettlementModels;
import cn.iocoder.txgy.module.trade.service.settlement.ShopSettlementService;
import cn.iocoder.txgy.module.trade.service.supplier.SupplierTradeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.MEMBER_SUPPLIER_WRITE_DISABLED;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class LegacyMemberSupplierWriteBoundaryTest {

    private LegacyMemberSupplierWriteGuard guard;

    @BeforeEach
    void setUp() {
        guard = new LegacyMemberSupplierWriteGuard(); // boolean 默认 false，即生产安全默认值
    }

    @Test
    void oldMemberDeliveryAndAfterSaleWritesAreClosedByDefault() {
        SupplierTradeService service = mock(SupplierTradeService.class);
        AppSupplierTradeController controller = new AppSupplierTradeController();
        ReflectionTestUtils.setField(controller, "service", service);
        ReflectionTestUtils.setField(controller, "legacyWriteGuard", guard);

        ServiceException delivery = assertThrows(ServiceException.class, () -> controller.deliver(
                new AppSupplierTradeController.Delivery(1L, 2L, 3L, "SF12345")));
        ServiceException afterSale = assertThrows(ServiceException.class, () -> controller.afterSaleProcess(
                new AppSupplierTradeController.AfterSaleProcess(1L, 2L,
                        SupplierTradeService.AfterSaleAction.AGREE, null)));

        assertEquals(MEMBER_SUPPLIER_WRITE_DISABLED.getCode(), delivery.getCode());
        assertEquals(MEMBER_SUPPLIER_WRITE_DISABLED.getCode(), afterSale.getCode());
        verifyNoInteractions(service);
    }

    @Test
    void oldMemberSettlementConfirmationIsClosedByDefault() {
        ShopSettlementService service = mock(ShopSettlementService.class);
        AppShopSettlementController controller = new AppShopSettlementController();
        ReflectionTestUtils.setField(controller, "service", service);
        ReflectionTestUtils.setField(controller, "legacyWriteGuard", guard);

        ServiceException error = assertThrows(ServiceException.class,
                () -> controller.confirm(new SettlementModels.Confirm(1L, 2L, 1)));

        assertEquals(MEMBER_SUPPLIER_WRITE_DISABLED.getCode(), error.getCode());
        verifyNoInteractions(service);
    }

    @Test
    void compatibilityCanOnlyBeReopenedExplicitly() {
        ReflectionTestUtils.setField(guard, "enabled", true);
        assertDoesNotThrow(guard::requireEnabled);
    }
}
