package cn.iocoder.txgy.module.wms.api.integration;

import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.txgy.module.wms.api.integration.dto.*;
import cn.iocoder.txgy.module.wms.service.integration.*;
import cn.iocoder.txgy.module.wms.service.inventory.WmsInventoryService;
import cn.iocoder.txgy.module.wms.service.md.item.*;
import cn.iocoder.txgy.module.wms.service.md.merchant.WmsMerchantService;
import cn.iocoder.txgy.module.wms.service.md.warehouse.WmsWarehouseService;
import cn.iocoder.txgy.module.wms.service.order.receipt.*;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

@Import({WmsIntegrationApiImpl.class, WmsIntegrationRequestService.class,
        WmsReceiptOrderServiceImpl.class, WmsReceiptOrderDetailServiceImpl.class,
        cn.iocoder.txgy.module.wms.service.order.shipment.WmsShipmentOrderServiceImpl.class,
        cn.iocoder.txgy.module.wms.service.integration.WmsShortageService.class,
        cn.iocoder.txgy.module.wms.service.integration.WmsShortageTestConfiguration.class,
        cn.iocoder.txgy.module.wms.service.order.shipment.WmsShipmentOrderDetailServiceImpl.class,
        cn.iocoder.txgy.module.wms.controller.admin.order.receipt.WmsReceiptOrderDetailController.class,
        cn.iocoder.txgy.module.wms.controller.admin.order.receipt.WmsReceiptOrderController.class,
        WmsPurchaseReceiptDraftTest.TenantConfiguration.class})
class WmsPurchaseReceiptDraftTest extends BaseDbUnitTest {
    @org.springframework.boot.test.context.TestConfiguration
    static class TenantConfiguration {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Lazy(false)
        com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor tenantInterceptor(
                com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor interceptor) {
            var tenant = new com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor(
                    new cn.iocoder.txgy.framework.tenant.core.db.TenantDatabaseInterceptor(
                            new cn.iocoder.txgy.framework.tenant.config.TenantProperties()));
            cn.iocoder.txgy.framework.mybatis.core.util.MyBatisUtils.addInterceptor(interceptor, tenant, 0);
            return tenant;
        }
    }
    @Resource private WmsIntegrationApiImpl api;
    @Resource private DataSource dataSource;
    @Resource private cn.iocoder.txgy.module.wms.dal.mysql.integration.WmsIntegrationRequestMapper integrationRequestMapper;
    @Resource private cn.iocoder.txgy.module.wms.controller.admin.order.receipt.WmsReceiptOrderDetailController detailController;
    @Resource private cn.iocoder.txgy.module.wms.controller.admin.order.receipt.WmsReceiptOrderController orderController;
    @MockitoBean private cn.iocoder.txgy.module.system.api.user.AdminUserApi adminUserApi;
    @MockitoBean private WmsMaterialIntegrationService materialService;
    @MockitoBean private WmsPartnerIntegrationService partnerService;
    @MockitoBean private WmsWarehouseIntegrationService warehouseIntegrationService;
    @MockitoBean private WmsWarehouseService warehouseService;
    @MockitoBean private cn.iocoder.txgy.module.wms.service.md.warehouse.WmsWarehousePersonnelService warehousePersonnelService;
    @MockitoBean private WmsMerchantService merchantService;
    @MockitoBean private WmsItemSkuService itemSkuService;
    @MockitoBean private WmsItemService itemService;
    @MockitoBean private WmsItemQualityReportService qualityReportService;
    @MockitoBean private WmsInventoryService inventoryService;
    @MockitoBean private BpmProcessInstanceApi processInstanceApi;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private WmsReceiptOrderDetailServiceImpl detailService;
    private JdbcTemplate jdbc;

    @BeforeEach
    void fixtures() {
        TenantContextHolder.setTenantId(2005L);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO wms_warehouse (id,code,name,tenant_id) VALUES (77,'WH-1','原料仓',2005)");
        jdbc.update("INSERT INTO wms_item (id,code,name,category_id,material_type,tenant_id) VALUES (55,'MAT-1','钢管',1,'RAW_MATERIAL',2005)");
        jdbc.update("INSERT INTO wms_item_sku (id,item_id,code,name,tenant_id) VALUES (66,55,'MAT-1-DEFAULT','标准',2005)");
    }

    @AfterEach
    void clearTenant() { TenantContextHolder.clear(); }

    @Test
    void createsThreeAdditionalFlowsAsIdempotentDrafts() {
        for (String type : List.of("PRODUCTION_RECEIPT", "SALE_SHIPMENT", "PRODUCTION_SHIPMENT")) {
            String method = switch (type) {
                case "PRODUCTION_RECEIPT" -> "createProductionReceipt";
                case "SALE_SHIPMENT" -> "createSaleShipment";
                default -> "createProductionShipment";
            };
            boolean raw = type.equals("PRODUCTION_SHIPMENT");
            String materialType = raw ? "RAW_MATERIAL" : "FINISHED_GOODS";
            jdbc.update("UPDATE wms_item SET material_type=? WHERE id=55", materialType);
            jdbc.update("UPDATE wms_warehouse SET warehouse_type=? WHERE id=77", materialType);
            var req = request(type);
            req.setSourceModule(type.equals("SALE_SHIPMENT") ? "ERP" : "MES");
            req.setOrderType(type);
            req.getItems().getFirst().setMaterialType(materialType);
            req.getItems().getFirst().setPrice(new BigDecimal("12.50"));
            assertDoesNotThrow(() -> api.getClass().getMethod(method, WmsOrderRequestDTO.class).invoke(api, req));
            assertEquals("DRAFT", api.getOrderExecution(2005L, req.getSourceOrderNo(), type)
                    .getCheckedData().getProcessingStatus());
            req.setRequestId(type + "-retry");
            assertDoesNotThrow(() -> api.getClass().getMethod(method, WmsOrderRequestDTO.class).invoke(api, req));
        }
        assertEquals(1, count("wms_receipt_order"));
        assertEquals(2, count("wms_shipment_order"));
        assertEquals(2, count("wms_shipment_order_detail"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wms_shipment_order_detail WHERE price IS NULL OR total_price IS NULL", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wms_receipt_order_detail WHERE price IS NULL OR total_price IS NULL", Integer.class));
        assertEquals(0, count("wms_inventory"));
        assertEquals(0, count("wms_inventory_history"));
        verifyNoInteractions(inventoryService, processInstanceApi);
    }

    @Test
    void createsMallShipmentAsAnIsolatedIdempotentDraft() {
        jdbc.update("UPDATE wms_item SET material_type='FINISHED_GOODS' WHERE id=55");
        jdbc.update("UPDATE wms_warehouse SET warehouse_type='FINISHED_GOODS' WHERE id=77");
        WmsOrderRequestDTO request = request("mall-outbound-request-1");
        request.setSourceModule("MALL");
        request.setSourceOrderNo("MALL-ORDER-1");
        request.setOrderType("SALE_SHIPMENT");
        request.getItems().getFirst().setMaterialType("FINISHED_GOODS");

        var accepted = api.createMallShipment(request).getCheckedData();
        assertEquals("WMS-MALL-MALL-ORDER-1", accepted.getTargetOrderNo());
        assertEquals("DRAFT", api.getMallOrderExecution(2005L, "MALL-ORDER-1")
                .getCheckedData().getProcessingStatus());

        request.setRequestId("mall-outbound-request-2");
        api.createMallShipment(request);
        assertEquals(1, count("wms_shipment_order"));
        assertEquals("mall-outbound-request-1", integrationRequestMapper.selectSourceRequest(
                "SALE_SHIPMENT_CREATE", accepted.getTargetOrderNo()).getRequestId());
        assertEquals("MALL", jdbc.queryForObject("SELECT source_module FROM wms_shipment_order", String.class));
        assertEquals(0, count("wms_inventory_history"));
        verifyNoInteractions(inventoryService, processInstanceApi);
    }

    @Test
    void createsDraftAndDetailsWithoutStockOrApproval() {
        assertEquals("WMS-ERP-RK-ERP-1", api.createPurchaseReceipt(request("req-1")).getCheckedData().getTargetOrderNo());
        assertEquals(1, count("wms_receipt_order"));
        assertEquals(1, count("wms_receipt_order_detail"));
        assertEquals(0, new BigDecimal("10").compareTo(jdbc.queryForObject("SELECT quantity FROM wms_receipt_order_detail", BigDecimal.class)));
        assertEquals(-1, jdbc.queryForObject("SELECT approval_status FROM wms_receipt_order", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT execution_status FROM wms_receipt_order", Integer.class));
        assertEquals("ERP", jdbc.queryForObject("SELECT source_module FROM wms_receipt_order", String.class));
        assertEquals("BC-1", jdbc.queryForObject("SELECT business_chain_no FROM wms_receipt_order", String.class));
        assertEquals(11, jdbc.queryForObject("SELECT source_line_no FROM wms_receipt_order_detail", Integer.class));
        assertEquals(2005L, jdbc.queryForObject("SELECT tenant_id FROM wms_receipt_order", Long.class));
        assertEquals(0, count("wms_inventory"));
        assertEquals(0, count("wms_inventory_history"));
        verifyNoInteractions(inventoryService, processInstanceApi);
    }

    @Test
    void sourcePriceIsStoredAndInconsistentAmountRejected() {
        var invalid = request("bad-price");
        invalid.getItems().getFirst().setPrice(new BigDecimal("-1"));
        assertThrows(Exception.class, () -> api.createPurchaseReceipt(invalid));
        invalid.getItems().getFirst().setPrice(new BigDecimal("12.50")).setTotalPrice(new BigDecimal("0.01"));
        assertThrows(Exception.class, () -> api.createPurchaseReceipt(invalid));
        assertEquals(0, count("wms_receipt_order"));
        var req = request("source-price");
        req.getItems().getFirst().setPrice(new BigDecimal("12.50"));
        api.createPurchaseReceipt(req);
        var row = jdbc.queryForMap("SELECT price,total_price,source_snapshot_json FROM wms_receipt_order_detail");
        assertEquals(0, new BigDecimal("12.50").compareTo((BigDecimal) row.get("price")));
        assertEquals(0, req.getItems().getFirst().getRequiredQuantity().multiply(new BigDecimal("12.50"))
                .compareTo((BigDecimal) row.get("total_price")));
        assertTrue(row.get("source_snapshot_json").toString().contains("12.50"));
        assertEquals(0, count("wms_inventory"));
    }

    @Test
    void sourceSnapshotSurvivesMasterChangesAndBusinessRetryInBothReadEndpoints() {
        WmsOrderRequestDTO request = request("snapshot");
        var source = request.getItems().getFirst();
        source.setSpecificationSnapshot("DN100").setBatchNo("BATCH-20260912")
                .setQualityRequiredSnapshot(true).setAttachments(List.of(new WmsIntegrationAttachmentDTO()
                        .setFileId(101L).setUrl("https://example.test/receipt.jpg")
                        .setFileName("到货照片.jpg").setMimeType("image/jpeg").setSha256("original-sha")));
        api.createPurchaseReceipt(request);
        jdbc.update("UPDATE wms_item SET name='新名称',unit='吨',quality_required=false WHERE id=55");
        request.setRequestId("snapshot-retry");
        request.setOccurredAt(request.getOccurredAt().plusHours(1));
        api.createPurchaseReceipt(request);
        Long orderId = jdbc.queryForObject("SELECT id FROM wms_receipt_order", Long.class);
        assertSourceSnapshot(source, detailController.getReceiptOrderDetailListByOrderId(orderId).getCheckedData().getFirst());
        assertSourceSnapshot(source, orderController.getReceiptOrder(orderId).getCheckedData().getDetails().getFirst());
        assertEquals(1, count("wms_receipt_order_detail"));
        verifyNoInteractions(inventoryService, processInstanceApi);
    }

    @Test
    void explicitNoQualityRequirementIsPreservedDespiteCurrentMaterialSetting() {
        jdbc.update("UPDATE wms_item SET quality_required=true WHERE id=55");
        WmsOrderRequestDTO request = request("no-quality");
        request.getItems().getFirst().setQualityRequiredSnapshot(false);
        api.createPurchaseReceipt(request);
        Long orderId = jdbc.queryForObject("SELECT id FROM wms_receipt_order", Long.class);
        assertSourceSnapshot(request.getItems().getFirst(),
                detailController.getReceiptOrderDetailListByOrderId(orderId).getCheckedData().getFirst());
    }

    private void assertSourceSnapshot(WmsIntegrationDocumentItemDTO expected, Object response) {
        var json = cn.iocoder.txgy.framework.common.util.json.JsonUtils.toJsonString(response);
        var fields = cn.iocoder.txgy.framework.common.util.json.JsonUtils.parseObject(json, java.util.Map.class);
        assertNotNull(fields.get("sourceSnapshot"), "查询响应必须包含创建时的来源明细快照");
        var actual = cn.iocoder.txgy.framework.common.util.json.JsonUtils.parseObject(
                cn.iocoder.txgy.framework.common.util.json.JsonUtils.toJsonString(fields.get("sourceSnapshot")),
                WmsIntegrationDocumentItemDTO.class);
        assertEquals(expected, actual);
        assertFalse(fields.containsKey("sourceSnapshotJson"), "内部 JSON 存储字段不应暴露给页面");
    }

    @Test
    void newRequestIdForSameBusinessReturnsOriginalDraft() {
        String first = api.createPurchaseReceipt(request("req-1")).getCheckedData().getTargetOrderNo();
        WmsOrderRequestDTO retry = request("req-2");
        retry.setOccurredAt(retry.getOccurredAt().plusMinutes(1));
        assertEquals(first, api.createPurchaseReceipt(retry).getCheckedData().getTargetOrderNo());
        assertEquals(1, count("wms_receipt_order"));
        assertEquals(1, count("wms_receipt_order_detail"));
    }

    @Test
    void repeatedRequestReturnsOriginalDraft() {
        api.createPurchaseReceipt(request("req-1"));
        api.createPurchaseReceipt(request("req-1"));
        assertEquals(1, count("wms_receipt_order"));
    }

    @Test
    void rejectsNullAndNonPositiveQuantitiesWithoutPartialData() {
        for (BigDecimal quantity : java.util.Arrays.asList(null, BigDecimal.ZERO, new BigDecimal("-1"))) {
            WmsOrderRequestDTO request = request("invalid-quantity");
            request.getItems().getFirst().setRequiredQuantity(quantity);
            assertThrows(RuntimeException.class, () -> api.createPurchaseReceipt(request));
            assertNoDraft();
        }
    }

    @Test
    void rejectsSkuBelongingToDifferentMaterial() {
        WmsOrderRequestDTO request = request("invalid-material");
        request.getItems().getFirst().setMaterialCode("ANOTHER-MATERIAL");
        assertThrows(RuntimeException.class, () -> api.createPurchaseReceipt(request));
        assertNoDraft();
    }

    @Test
    void rejectsDuplicateLineNumbers() {
        WmsOrderRequestDTO request = request("duplicate-line");
        request.setItems(List.of(item(), item()));
        assertThrows(RuntimeException.class, () -> api.createPurchaseReceipt(request));
        assertNoDraft();
    }

    @Test
    void rejectsMixedWarehousesAndRollsBackLedger() {
        WmsOrderRequestDTO request = request("mixed-warehouse");
        request.setItems(List.of(item(), item().setLineNo(2).setWarehouseCode("WH-2")));
        assertThrows(RuntimeException.class, () -> api.createPurchaseReceipt(request));
        assertNoDraft();
    }

    @Test
    void changedBusinessContentOrChainCannotOverwriteOriginal() {
        api.createPurchaseReceipt(request("original"));
        WmsOrderRequestDTO changed = request("changed");
        changed.getItems().getFirst().setRequiredQuantity(new BigDecimal("20"));
        assertThrows(RuntimeException.class, () -> api.createPurchaseReceipt(changed));
        WmsOrderRequestDTO wrongChain = request("wrong-chain").setBusinessChainNo("BC-OTHER");
        assertThrows(RuntimeException.class, () -> api.createPurchaseReceipt(wrongChain));
        assertEquals(1, count("wms_receipt_order"));
        assertEquals(0, new BigDecimal("10").compareTo(jdbc.queryForObject("SELECT quantity FROM wms_receipt_order_detail", BigDecimal.class)));
    }

    @Test
    void tenantCannotResolveAnotherTenantsWarehouseOrSku() {
        TenantContextHolder.setTenantId(2006L);
        WmsOrderRequestDTO request = request("foreign");
        request.setTenantId(2006L);
        assertThrows(RuntimeException.class, () -> api.createPurchaseReceipt(request));
        assertNoDraft();
    }

    @Test
    void sameRequestIdAndSourceAreIndependentAcrossTenants() {
        api.createPurchaseReceipt(request("same-request"));
        jdbc.update("INSERT INTO wms_warehouse (id,code,name,tenant_id) VALUES (78,'WH-1','原料仓2',2006)");
        jdbc.update("INSERT INTO wms_item (id,code,name,category_id,material_type,tenant_id) VALUES (56,'MAT-1','钢管2',1,'RAW_MATERIAL',2006)");
        jdbc.update("INSERT INTO wms_item_sku (id,item_id,code,name,tenant_id) VALUES (67,56,'MAT-1-DEFAULT','标准2',2006)");
        TenantContextHolder.setTenantId(2006L);
        WmsOrderRequestDTO request = request("same-request");
        request.setTenantId(2006L);
        api.createPurchaseReceipt(request);
        assertEquals(2, count("wms_receipt_order"));
        assertEquals(78L, jdbc.queryForObject("SELECT warehouse_id FROM wms_receipt_order WHERE tenant_id=2006", Long.class));
    }

    @Test
    void detailFailureRollsBackHeaderDetailsAndBothLedgers() {
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("injected failure after detail insert");
        }).when(detailService).createReceiptOrderDetailList(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
        assertThrows(IllegalStateException.class, () -> api.createPurchaseReceipt(request("rollback")));
        assertNoDraft();
    }

    @Test
    void quantityPrecisionCannotBeSilentlyRounded() {
        WmsOrderRequestDTO request = request("precision");
        request.getItems().getFirst().setRequiredQuantity(new BigDecimal("0.001"));
        assertThrows(RuntimeException.class, () -> api.createPurchaseReceipt(request));
        assertNoDraft();
    }

    @Test
    void concurrentDifferentRequestIdsCreateOnlyOneDraft() throws Exception {
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<String> firstCall = () -> {
                start.await(10, java.util.concurrent.TimeUnit.SECONDS);
                return cn.iocoder.txgy.framework.tenant.core.util.TenantUtils.execute(2005L,
                        () -> api.createPurchaseReceipt(request("concurrent-1")).getCheckedData().getTargetOrderNo());
            };
            var first = pool.submit(firstCall);
            var second = pool.submit(() -> {
                start.await(10, java.util.concurrent.TimeUnit.SECONDS);
                return cn.iocoder.txgy.framework.tenant.core.util.TenantUtils.execute(2005L,
                        () -> api.createPurchaseReceipt(request("concurrent-2")).getCheckedData().getTargetOrderNo());
            });
            start.countDown();
            assertEquals(first.get(30, java.util.concurrent.TimeUnit.SECONDS), second.get(30, java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(1, count("wms_receipt_order"));
        assertEquals(1, count("wms_receipt_order_detail"));
    }

    private void assertNoDraft() {
        assertEquals(0, count("wms_receipt_order"));
        assertEquals(0, count("wms_receipt_order_detail"));
        assertEquals(0, count("wms_integration_request"));
        verifyNoInteractions(inventoryService, processInstanceApi);
    }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    private static WmsOrderRequestDTO request(String requestId) {
        WmsOrderRequestDTO request = new WmsOrderRequestDTO().setBusinessChainNo("BC-1")
                .setSourceOrderNo("RK-ERP-1").setOrderType("PURCHASE_RECEIPT").setEventVersion(1L).setItems(List.of(item()));
        request.setRequestId(requestId);
        request.setTenantId(2005L);
        request.setSourceModule("ERP");
        request.setOccurredAt(LocalDateTime.of(2026, 9, 12, 10, 0));
        return request;
    }
    private static WmsIntegrationDocumentItemDTO item() {
        return new WmsIntegrationDocumentItemDTO().setLineNo(1).setSourceLineNo(11)
                .setMaterialCode("MAT-1").setSkuCode("MAT-1-DEFAULT").setMaterialType("RAW_MATERIAL")
                .setMaterialNameSnapshot("钢管").setUnitCode("PC").setUnitNameSnapshot("件")
                .setWarehouseCode("WH-1").setRequiredQuantity(new BigDecimal("10"));
    }
}
