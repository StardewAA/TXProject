package cn.iocoder.txgy.module.wms.service.integration;

import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsWarehouseUpsertReqDTO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.warehouse.WmsWarehouseDO;
import cn.iocoder.txgy.module.wms.dal.mysql.md.warehouse.WmsWarehouseMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static cn.iocoder.txgy.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.WAREHOUSE_VERSION_CONFLICT;
import static org.junit.jupiter.api.Assertions.*;

@Import(WmsWarehouseIntegrationService.class)
class WmsWarehouseIntegrationServiceTest extends BaseDbUnitTest {

    @Resource private WmsWarehouseIntegrationService service;
    @Resource private WmsWarehouseMapper mapper;

    @Test
    void createsAuthorityCodeAndUpdatesWithExpectedVersion() {
        TenantUtils.execute(2005L, () -> {
            var created = service.upsert(request(null, null, "原料仓"));
            assertTrue(created.getTargetOrderNo().startsWith("WMS-WH-"));
            assertEquals(1L, created.getProcessedVersion());
            WmsWarehouseDO warehouse = mapper.selectByCode(created.getTargetOrderNo());
            mapper.updateById(new WmsWarehouseDO().setId(warehouse.getId()).setRemark("WMS保留备注"));

            var updated = service.upsert(request(created.getTargetOrderNo(), 1L, "新原料仓"));
            WmsWarehouseDO saved = mapper.selectById(warehouse.getId());
            assertEquals("新原料仓", saved.getName());
            assertEquals("WMS保留备注", saved.getRemark());
            assertEquals(2L, updated.getProcessedVersion());
        });
    }

    @Test
    void rejectsMismatchedExpectedVersion() {
        TenantUtils.execute(2005L, () -> {
            var created = service.upsert(request(null, null, "原料仓"));
            assertServiceException(() -> service.upsert(request(created.getTargetOrderNo(), 9L, "错误更新")),
                    WAREHOUSE_VERSION_CONFLICT);
        });
    }

    private static WmsWarehouseUpsertReqDTO request(String code, Long expectedVersion, String name) {
        WmsWarehouseUpsertReqDTO request = new WmsWarehouseUpsertReqDTO();
        request.setRequestId("warehouse-" + name);
        request.setTenantId(2005L);
        request.setSourceModule("ERP");
        request.setOccurredAt(LocalDateTime.of(2026, 9, 8, 10, 0));
        request.setWarehouseCode(code);
        request.setName(name);
        request.setWarehouseType("RAW_MATERIAL");
        request.setAddress("厂区一号");
        request.setPrincipal("仓库主管");
        request.setStatus("ENABLED");
        request.setSort(10);
        request.setDataVersion(expectedVersion == null ? 1L : expectedVersion + 1);
        request.setExpectedVersion(expectedVersion);
        return request;
    }
}
