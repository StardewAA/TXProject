package cn.iocoder.txgy.module.wms.api.integration;

import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.wms.api.integration.dto.*;
import cn.iocoder.txgy.module.wms.service.integration.*;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static cn.iocoder.txgy.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.INTEGRATION_TENANT_MISMATCH;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.SYNC_VERSION_STALE;
import static org.junit.jupiter.api.Assertions.*;

@Import({WmsIntegrationApiImpl.class, WmsIntegrationRequestService.class, WmsMaterialIntegrationService.class,
        WmsPartnerIntegrationService.class, WmsWarehouseIntegrationService.class})
class WmsIntegrationContractTest extends BaseDbUnitTest {

    @Resource private WmsIntegrationApiImpl api;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private cn.iocoder.txgy.module.wms.service.order.receipt.WmsReceiptOrderService receiptOrderService;

    @Test
    void publicApiSupportsMasterDataLifecycleAndRequestLookup() {
        TenantUtils.execute(2005L, () -> {
            WmsMaterialUpsertReqDTO material = material("contract-material-1", 1L, "钢管");
            var materialResult = api.upsertMaterial(material).getCheckedData();
            assertEquals("MAT-CONTRACT-001", materialResult.getTargetOrderNo());
            assertEquals(materialResult, api.upsertMaterial(material).getCheckedData());

            var partnerResult = api.upsertPartner(partner()).getCheckedData();
            assertEquals("PARTNER-CONTRACT-001", partnerResult.getTargetOrderNo());

            var warehouseResult = api.upsertWarehouse(warehouse()).getCheckedData();
            assertTrue(warehouseResult.getTargetOrderNo().startsWith("WMS-WH-"));
            assertEquals(1L, warehouseResult.getProcessedVersion());

            WmsIntegrationRequestRespDTO ledger = api.getRequestResult("contract-material-1").getCheckedData();
            assertEquals("MATERIAL", ledger.getAggregateType());
            assertEquals("MAT-CONTRACT-001", ledger.getAggregateNo());
            assertEquals("SUCCESS", ledger.getProcessingStatus());

            assertServiceException(() -> api.upsertMaterial(material("contract-material-stale", 0L, "旧钢管")),
                    SYNC_VERSION_STALE);
            WmsMaterialUpsertReqDTO wrongTenant = material("contract-wrong-tenant", 2L, "钢管");
            wrongTenant.setTenantId(9999L);
            assertServiceException(() -> api.upsertMaterial(wrongTenant), INTEGRATION_TENANT_MISMATCH);
        });
    }

    private static WmsMaterialUpsertReqDTO material(String requestId, Long version, String name) {
        WmsMaterialUpsertReqDTO request = base(new WmsMaterialUpsertReqDTO(), requestId);
        request.setMaterialCode("MAT-CONTRACT-001");
        request.setName(name);
        request.setMaterialType("RAW_MATERIAL");
        request.setSpecification("DN20");
        request.setUnitCode("UNIT-PC");
        request.setUnitName("件");
        request.setQualityRequired(true);
        request.setStatus("ENABLED");
        request.setDataVersion(version);
        return request;
    }

    private static WmsPartnerUpsertReqDTO partner() {
        WmsPartnerUpsertReqDTO request = base(new WmsPartnerUpsertReqDTO(), "contract-partner-1");
        request.setPartnerCode("PARTNER-CONTRACT-001");
        request.setName("契约客户供应商");
        request.setPartnerType("BOTH");
        request.setContact("联系人");
        request.setStatus("ENABLED");
        request.setDataVersion(1L);
        return request;
    }

    private static WmsWarehouseUpsertReqDTO warehouse() {
        WmsWarehouseUpsertReqDTO request = base(new WmsWarehouseUpsertReqDTO(), "contract-warehouse-1");
        request.setName("契约原料仓");
        request.setWarehouseType("RAW_MATERIAL");
        request.setAddress("厂区一号");
        request.setPrincipal("仓库主管");
        request.setStatus("ENABLED");
        request.setSort(10);
        request.setDataVersion(1L);
        return request;
    }

    private static <T extends WmsIntegrationBaseReqDTO> T base(T request, String requestId) {
        request.setRequestId(requestId);
        request.setTenantId(2005L);
        request.setSourceModule("ERP");
        request.setOccurredAt(LocalDateTime.of(2026, 9, 8, 10, 0));
        return request;
    }
}
