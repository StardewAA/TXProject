package cn.iocoder.txgy.module.erp.service.integration;

import cn.iocoder.txgy.module.erp.controller.admin.integration.vo.ErpWmsCollaborationBConfirmReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.integration.ErpWmsCollaborationBDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.integration.ErpWmsCollaborationBItemDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseOrderDO;
import cn.iocoder.txgy.module.erp.dal.mysql.integration.ErpWmsCollaborationBItemMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.integration.ErpWmsCollaborationBMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.product.ErpProductMapper;
import cn.iocoder.txgy.module.erp.service.purchase.ErpPurchaseOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ErpWmsCollaborationBServiceTest {

    private final ErpWmsCollaborationBMapper collaborationMapper = mock(ErpWmsCollaborationBMapper.class);
    private final ErpWmsCollaborationBItemMapper itemMapper = mock(ErpWmsCollaborationBItemMapper.class);
    private final ErpProductMapper productMapper = mock(ErpProductMapper.class);
    private final ErpPurchaseOrderService purchaseOrderService = mock(ErpPurchaseOrderService.class);
    private final ErpWmsCollaborationBService service = new ErpWmsCollaborationBService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "collaborationMapper", collaborationMapper);
        ReflectionTestUtils.setField(service, "collaborationItemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "productMapper", productMapper);
        ReflectionTestUtils.setField(service, "purchaseOrderService", purchaseOrderService);
    }

    @Test
    void shouldCreateOnePurchaseDraftAfterBusinessAcceptance() {
        ErpWmsCollaborationBDO collaboration = ErpWmsCollaborationBDO.builder().id(10L)
                .collaborationNo("B-TEST-1").businessStatus("PENDING_CONFIRMATION").build();
        when(collaborationMapper.selectById(10L)).thenReturn(collaboration);
        when(collaborationMapper.updateByIdAndBusinessStatus(eq(10L), eq("PENDING_CONFIRMATION"), any()))
                .thenReturn(1);
        when(itemMapper.selectListByCollaborationId(10L)).thenReturn(List.of(
                ErpWmsCollaborationBItemDO.builder().lineNo(1).materialCode("MAT-RAW-1")
                        .shortageQuantity(new BigDecimal("25.000000")).build()));
        when(productMapper.selectByMaterialCode("MAT-RAW-1"))
                .thenReturn(ErpProductDO.builder().id(20L).unitId(30L).build());
        when(purchaseOrderService.createPurchaseOrder(any())).thenReturn(40L);
        when(purchaseOrderService.getPurchaseOrder(40L))
                .thenReturn(ErpPurchaseOrderDO.builder().id(40L).no("CGDD-TEST-1").build());

        ErpWmsCollaborationBConfirmReqVO request = new ErpWmsCollaborationBConfirmReqVO()
                .setId(10L).setAction("ACCEPTED").setSupplierId(50L).setOrderTime(LocalDateTime.now())
                .setItems(List.of(new ErpWmsCollaborationBConfirmReqVO.Item().setLineNo(1)
                        .setProductPrice(new BigDecimal("12.50")).setTaxPercent(new BigDecimal("13"))));

        var response = service.confirm(request);

        assertEquals("ACCEPTED", response.getBusinessStatus());
        assertEquals(40L, response.getPurchaseOrderId());
        assertEquals("CGDD-TEST-1", response.getPurchaseOrderNo());
        verify(purchaseOrderService, times(1)).createPurchaseOrder(argThat(order ->
                order.getItems().size() == 1
                        && order.getItems().getFirst().getCount().compareTo(new BigDecimal("25.000000")) == 0));
    }

    @Test
    void shouldRejectWithoutCreatingPurchaseOrder() {
        ErpWmsCollaborationBDO collaboration = ErpWmsCollaborationBDO.builder().id(11L)
                .collaborationNo("B-TEST-2").businessStatus("PENDING_CONFIRMATION").build();
        when(collaborationMapper.selectById(11L)).thenReturn(collaboration);
        when(collaborationMapper.updateByIdAndBusinessStatus(eq(11L), eq("PENDING_CONFIRMATION"), any()))
                .thenReturn(1);

        var response = service.confirm(new ErpWmsCollaborationBConfirmReqVO()
                .setId(11L).setAction("REJECTED").setReason("采购计划不接受"));

        assertEquals("REJECTED", response.getBusinessStatus());
        verifyNoInteractions(purchaseOrderService);
    }
}
