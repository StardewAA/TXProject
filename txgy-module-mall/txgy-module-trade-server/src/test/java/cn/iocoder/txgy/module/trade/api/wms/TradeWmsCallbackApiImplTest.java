package cn.iocoder.txgy.module.trade.api.wms;

import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.trade.api.wms.dto.TradeWmsExecutionCallbackItemDTO;
import cn.iocoder.txgy.module.trade.api.wms.dto.TradeWmsExecutionCallbackReqDTO;
import cn.iocoder.txgy.module.trade.dal.dataobject.wms.TradeWmsShipmentDO;
import cn.iocoder.txgy.module.trade.dal.mysql.wms.TradeWmsShipmentMapper;
import cn.iocoder.txgy.module.trade.service.wms.TradeWmsOutboundService;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationDocumentItemDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsOrderRequestDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class TradeWmsCallbackApiImplTest {

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void acceptsOnlyExactSubmittedLines() {
        TenantContextHolder.setTenantId(2009L);
        TradeWmsShipmentMapper mapper = mock(TradeWmsShipmentMapper.class);
        WmsIntegrationDocumentItemDTO submittedLine = new WmsIntegrationDocumentItemDTO()
                .setLineNo(1).setSourceLineNo(1).setMaterialCode("MAT-1").setSkuCode("SKU-1")
                .setRequiredQuantity(new BigDecimal("2"));
        TradeWmsShipmentDO record = new TradeWmsShipmentDO().setOrderNo("o500")
                .setShopId(10L)
                .setSourceOrderNo("M500-S10-W201").setRequestId("REQ-1").setTargetOrderNo("WMS-1")
                .setEventVersion(1L).setRequestJson(JsonUtils.toJsonString(new WmsOrderRequestDTO()
                        .setShopId(10L)
                        .setItems(List.of(submittedLine))));
        when(mapper.selectBySourceOrderNo(record.getSourceOrderNo())).thenReturn(record);
        TradeWmsCallbackApiImpl api = new TradeWmsCallbackApiImpl(mapper);

        TradeWmsExecutionCallbackReqDTO callback = new TradeWmsExecutionCallbackReqDTO()
                .setTenantId(2009L).setBusinessChainNo("o500").setSourceOrderNo(record.getSourceOrderNo())
                .setRequestId("REQ-1").setWmsOrderNo("WMS-1").setEventVersion(1L)
                .setItems(List.of(new TradeWmsExecutionCallbackItemDTO().setLineNo(1).setSourceLineNo(1)
                        .setMaterialCode("MAT-1").setSkuCode("SKU-1").setActualQuantity(new BigDecimal("2"))));
        api.callbackExecutionResult(callback);
        assertEquals(TradeWmsOutboundService.COMPLETED, record.getStatus());
        verify(mapper).updateById(record);

        callback.getItems().getFirst().setActualQuantity(BigDecimal.ONE);
        assertThrows(IllegalArgumentException.class, () -> api.callbackExecutionResult(callback));
    }
}
