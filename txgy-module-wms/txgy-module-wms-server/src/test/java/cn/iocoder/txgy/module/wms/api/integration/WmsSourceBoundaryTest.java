package cn.iocoder.txgy.module.wms.api.integration;

import cn.iocoder.txgy.module.wms.controller.admin.order.receipt.WmsReceiptOrderController;
import cn.iocoder.txgy.module.wms.controller.admin.order.shipment.WmsShipmentOrderController;
import cn.iocoder.txgy.module.wms.controller.admin.order.receipt.vo.order.WmsReceiptOrderSaveReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.order.shipment.vo.order.WmsShipmentOrderSaveReqVO;
import cn.iocoder.txgy.module.wms.service.order.receipt.WmsReceiptOrderService;
import cn.iocoder.txgy.module.wms.service.order.shipment.WmsShipmentOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WmsSourceBoundaryTest {
    @Test void normalShipmentCreationCannotForgeExternalSource() {
        var controller = new WmsShipmentOrderController();
        var service = mock(WmsShipmentOrderService.class);
        ReflectionTestUtils.setField(controller, "shipmentOrderService", service);
        assertThrows(IllegalArgumentException.class, () -> controller.createShipmentOrder(
                new WmsShipmentOrderSaveReqVO().setSourceModule("ERP").setBizOrderNo("FORGED")));
        verifyNoInteractions(service);
    }
    @Test void normalReceiptCreationCannotForgeExternalSource() {
        var controller = new WmsReceiptOrderController();
        var service = mock(WmsReceiptOrderService.class);
        ReflectionTestUtils.setField(controller, "receiptOrderService", service);
        assertThrows(IllegalArgumentException.class, () -> controller.createReceiptOrder(
                new WmsReceiptOrderSaveReqVO().setSourceModule("MES").setBizOrderNo("FORGED")));
        verifyNoInteractions(service);
    }
}
