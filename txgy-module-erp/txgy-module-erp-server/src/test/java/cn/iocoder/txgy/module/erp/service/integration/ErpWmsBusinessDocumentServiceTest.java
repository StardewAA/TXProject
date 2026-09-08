package cn.iocoder.txgy.module.erp.service.integration;

import cn.iocoder.txgy.module.erp.api.wms.dto.ErpWmsDocumentItemDTO;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpWmsExecutionCallbackReqDTO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseInDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseInItemDO;
import cn.iocoder.txgy.module.erp.dal.mysql.purchase.ErpPurchaseInItemMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.purchase.ErpPurchaseInMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.txgy.module.erp.service.integration.ErpWmsBusinessDocumentService.PURCHASE_RECEIPT;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class ErpWmsBusinessDocumentServiceTest {

    private final ErpPurchaseInMapper documentMapper = mock(ErpPurchaseInMapper.class);
    private final ErpPurchaseInItemMapper itemMapper = mock(ErpPurchaseInItemMapper.class);
    private final ErpWmsIntegrationRecordService recordService = mock(ErpWmsIntegrationRecordService.class);
    private final ErpWmsBusinessDocumentService service = new ErpWmsBusinessDocumentService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "purchaseInMapper", documentMapper);
        ReflectionTestUtils.setField(service, "purchaseInItemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "recordService", recordService);
    }

    @Test
    void shouldPersistQualifiedAndUnqualifiedReceiptResult() {
        when(documentMapper.selectByNo("CGRK-TEST-1")).thenReturn(
                ErpPurchaseInDO.builder().id(1L).no("CGRK-TEST-1").wmsEventVersion(1L).build());
        when(itemMapper.selectListByInId(1L)).thenReturn(List.of(
                ErpPurchaseInItemDO.builder().id(2L).count(new BigDecimal("10.000000")).build()));
        ErpWmsExecutionCallbackReqDTO request = callback(new BigDecimal("10"),
                new BigDecimal("9"), new BigDecimal("1"));

        service.receiveExecutionResult(request);

        verify(itemMapper).updateById(argThat((ErpPurchaseInItemDO item) -> item.getWmsActualCount().compareTo(new BigDecimal("10")) == 0
                && item.getQualifiedCount().compareTo(new BigDecimal("9")) == 0
                && item.getUnqualifiedCount().compareTo(BigDecimal.ONE) == 0));
        verify(documentMapper).updateById(argThat((ErpPurchaseInDO document) -> "COMPLETED".equals(document.getWmsStatus())
                && "RK-WMS-1".equals(document.getWmsOrderNo()) && document.getWmsEventVersion() == 2L));
        verify(recordService).createInboundSuccess(eq("CALLBACK-1"), eq(PURCHASE_RECEIPT),
                eq("CGRK-TEST-1"), eq(2L), same(request));
    }

    @Test
    void shouldRejectInvalidQualityQuantityEquation() {
        when(documentMapper.selectByNo("CGRK-TEST-1")).thenReturn(
                ErpPurchaseInDO.builder().id(1L).no("CGRK-TEST-1").wmsEventVersion(1L).build());
        when(itemMapper.selectListByInId(1L)).thenReturn(List.of(
                ErpPurchaseInItemDO.builder().id(2L).count(new BigDecimal("10.000000")).build()));

        assertThrows(IllegalArgumentException.class,
                () -> service.receiveExecutionResult(callback(new BigDecimal("10"),
                        new BigDecimal("8"), BigDecimal.ONE)));
        verify(documentMapper, never()).updateById(any(ErpPurchaseInDO.class));
    }

    private ErpWmsExecutionCallbackReqDTO callback(BigDecimal actual, BigDecimal qualified,
                                                    BigDecimal unqualified) {
        ErpWmsExecutionCallbackReqDTO request = new ErpWmsExecutionCallbackReqDTO()
                .setSourceOrderNo("CGRK-TEST-1").setWmsOrderNo("RK-WMS-1")
                .setOrderType(PURCHASE_RECEIPT).setExecutionStatus("COMPLETED")
                .setItems(List.of(new ErpWmsDocumentItemDTO().setLineNo(1)
                        .setActualQuantity(actual).setQualifiedQuantity(qualified)
                        .setUnqualifiedQuantity(unqualified)));
        request.setRequestId("CALLBACK-1");
        request.setTenantId(1L);
        request.setOccurredAt(LocalDateTime.now());
        request.setEventVersion(2L);
        return request;
    }
}
