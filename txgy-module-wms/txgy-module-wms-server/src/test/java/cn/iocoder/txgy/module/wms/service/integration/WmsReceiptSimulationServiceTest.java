package cn.iocoder.txgy.module.wms.service.integration;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.wms.api.integration.WmsIntegrationApiImpl;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationAcceptedRespDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsOrderRequestDTO;
import cn.iocoder.txgy.module.wms.controller.admin.order.receipt.vo.order.WmsReceiptSimulationReqVO;
import cn.iocoder.txgy.module.wms.dal.mysql.md.warehouse.WmsWarehouseMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.*;
import cn.iocoder.txgy.module.wms.dal.mysql.order.receipt.WmsReceiptOrderMapper;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.warehouse.WmsWarehouseDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.*;
import cn.iocoder.txgy.module.wms.dal.dataobject.order.receipt.WmsReceiptOrderDO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class WmsReceiptSimulationServiceTest {
    @InjectMocks private WmsReceiptSimulationService service;
    @Mock private WmsIntegrationApiImpl integrationApi;
    @Mock private WmsReceiptOrderMapper receiptMapper;
    @Mock private WmsWarehouseMapper warehouseMapper;
    @Mock private WmsItemSkuMapper skuMapper;
    @Mock private WmsItemMapper itemMapper;
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void mesSimulationUsesProductionReceipt() {
        TenantContextHolder.setTenantId(2005L);
        var req = new WmsReceiptSimulationReqVO().setWarehouseId(7L).setSourceOrderNo("MES-TEST")
                .setDetails(List.of(new WmsReceiptSimulationReqVO.Line().setSkuId(8L).setQuantity(BigDecimal.ONE)));
        assertDoesNotThrow(() -> req.getClass().getMethod("setOrderType", String.class).invoke(req, "PRODUCTION_RECEIPT"));
        when(warehouseMapper.selectById(7L)).thenReturn(new WmsWarehouseDO().setId(7L).setCode("FG"));
        when(skuMapper.selectById(8L)).thenReturn(new WmsItemSkuDO().setId(8L).setItemId(9L).setCode("FG-SKU"));
        when(itemMapper.selectById(9L)).thenReturn(new WmsItemDO().setId(9L).setCode("FG-MAT")
                .setName("成品").setUnit("件").setMaterialType("FINISHED_GOODS").setQualityRequired(false));
        when(integrationApi.createProductionReceipt(any())).thenReturn(CommonResult.success(new WmsIntegrationAcceptedRespDTO()));
        when(receiptMapper.selectByNo("WMS-MES-SIM-MES-TEST")).thenReturn(null,
                new WmsReceiptOrderDO().setId(11L).setNo("WMS-MES-SIM-MES-TEST"));
        assertEquals(11L, service.simulate(req).get("id"));
        verify(integrationApi).createProductionReceipt(argThat(r -> "MES".equals(r.getSourceModule())));
        verify(receiptMapper).updateById(argThat((WmsReceiptOrderDO o) -> Boolean.TRUE.equals(o.getSimulation())));
    }
    @Test void simulationUsesRealIntegrationAndMarksIsolatedSource() {
        TenantContextHolder.setTenantId(2005L);
        var req=new WmsReceiptSimulationReqVO().setWarehouseId(7L).setSourceOrderNo("TEST-1")
                .setDetails(List.of(new WmsReceiptSimulationReqVO.Line().setSkuId(8L).setQuantity(new BigDecimal("10")).setPrice(new BigDecimal("12.50"))));
        when(warehouseMapper.selectById(7L)).thenReturn(new WmsWarehouseDO().setId(7L).setCode("RAW"));
        when(skuMapper.selectById(8L)).thenReturn(new WmsItemSkuDO().setId(8L).setItemId(9L).setCode("RAW-SKU"));
        when(itemMapper.selectById(9L)).thenReturn(new WmsItemDO().setId(9L).setCode("RAW-MAT")
                .setName("原料").setUnit("根").setMaterialType("RAW_MATERIAL").setQualityRequired(true));
        when(integrationApi.createPurchaseReceipt(any())).thenReturn(CommonResult.success(
                new WmsIntegrationAcceptedRespDTO().setTargetOrderNo("WMS-ERP-SIM-TEST-1")));
        when(receiptMapper.selectByNo("WMS-ERP-SIM-TEST-1")).thenReturn(null,
                new WmsReceiptOrderDO().setId(10L).setNo("WMS-ERP-SIM-TEST-1"));
        assertEquals(10L,service.simulate(req).get("id"));
        var captured=ArgumentCaptor.forClass(WmsOrderRequestDTO.class);
        verify(integrationApi).createPurchaseReceipt(captured.capture());
        assertEquals("SIM-TEST-1",captured.getValue().getSourceOrderNo());
        assertEquals(2005L,captured.getValue().getTenantId());
        assertEquals(new BigDecimal("12.50"), captured.getValue().getItems().getFirst().getPrice());
        assertTrue(captured.getValue().getItems().getFirst().getQualityRequiredSnapshot());
        verify(receiptMapper).updateById(argThat((WmsReceiptOrderDO order)-> Boolean.TRUE.equals(order.getSimulation())));
    }
}
