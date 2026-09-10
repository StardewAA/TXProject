package cn.iocoder.txgy.module.wms.service.integration;

import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationRequestRespDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsMaterialUpsertReqDTO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static cn.iocoder.txgy.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.INTEGRATION_REQUEST_DUPLICATE_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Import(WmsIntegrationRequestService.class)
class WmsIntegrationRequestServiceTest extends BaseDbUnitTest {

    @Resource
    private WmsIntegrationRequestService service;

    @Test
    void identicalReplayReturnsPersistedResultWithoutRepeatingAction() {
        TenantUtils.execute(2005L, () -> {
            AtomicInteger executions = new AtomicInteger();
            WmsMaterialUpsertReqDTO request = request("request-1", "PIPE-001");

            WmsIntegrationRequestRespDTO first = service.execute("MATERIAL", request, () -> {
                executions.incrementAndGet();
                return response("request-1", "PIPE-001");
            });
            WmsIntegrationRequestRespDTO replay = service.execute("MATERIAL", request, () -> {
                executions.incrementAndGet();
                return response("request-1", "WRONG");
            });

            assertEquals(1, executions.get());
            assertEquals("PIPE-001", first.getAggregateNo());
            assertEquals("PIPE-001", replay.getAggregateNo());
            assertEquals("SUCCESS", service.getResult("request-1").getProcessingStatus());
        });
    }

    @Test
    void reusedRequestIdWithChangedPayloadIsRejected() {
        TenantUtils.execute(2005L, () -> {
            service.execute("MATERIAL", request("request-2", "PIPE-001"),
                    () -> response("request-2", "PIPE-001"));

            assertServiceException(() -> service.execute("MATERIAL", request("request-2", "PIPE-002"),
                    () -> response("request-2", "PIPE-002")), INTEGRATION_REQUEST_DUPLICATE_CONFLICT);
        });
    }

    private static WmsMaterialUpsertReqDTO request(String requestId, String materialCode) {
        WmsMaterialUpsertReqDTO request = new WmsMaterialUpsertReqDTO();
        request.setRequestId(requestId);
        request.setTenantId(2005L);
        request.setSourceModule("ERP");
        request.setOccurredAt(LocalDateTime.of(2026, 9, 8, 9, 0));
        request.setMaterialCode(materialCode);
        request.setName("管材");
        request.setMaterialType("FINISHED_GOODS");
        request.setUnitCode("M");
        request.setUnitName("米");
        request.setQualityRequired(false);
        request.setStatus("ENABLED");
        request.setDataVersion(1L);
        return request;
    }

    private static WmsIntegrationRequestRespDTO response(String requestId, String aggregateNo) {
        return new WmsIntegrationRequestRespDTO().setRequestId(requestId).setAggregateType("MATERIAL")
                .setAggregateNo(aggregateNo).setProcessingStatus("SUCCESS").setProcessedEventVersion(1L)
                .setResultCode(0).setResultData(Map.of("accepted", true));
    }
}
