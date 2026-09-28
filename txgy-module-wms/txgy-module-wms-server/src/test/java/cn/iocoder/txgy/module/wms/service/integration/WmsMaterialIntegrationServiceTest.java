package cn.iocoder.txgy.module.wms.service.integration;

import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsMaterialUpsertReqDTO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemSkuDO;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.WmsItemMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.WmsItemSkuMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static cn.iocoder.txgy.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.INTEGRATION_MASTER_DATA_MAPPING_CONFLICT;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.SYNC_VERSION_STALE;
import static org.junit.jupiter.api.Assertions.*;

@Import(WmsMaterialIntegrationService.class)
class WmsMaterialIntegrationServiceTest extends BaseDbUnitTest {

    @Resource private WmsMaterialIntegrationService service;
    @Resource private WmsItemMapper itemMapper;
    @Resource private WmsItemSkuMapper skuMapper;

    @Test
    void createsMaterialAndItsSingleDefaultSku() {
        TenantUtils.execute(2005L, () -> {
            var response = service.upsert(request(1L, "新管材"));
            WmsItemDO item = itemMapper.selectByCode("PIPE-001");
            assertNotNull(item);
            assertEquals("新管材", item.getName());
            assertEquals("FINISHED_GOODS", item.getMaterialType());
            assertEquals(1L, item.getSourceVersion());
            var skus = skuMapper.selectListByItemId(item.getId());
            assertEquals(1, skus.size());
            assertEquals("PIPE-001-DEFAULT", skus.getFirst().getCode());
            assertEquals("PIPE-001", response.getTargetOrderNo());
        });
    }

    @Test
    void newerVersionPreservesWmsExtensionsAndRejectsOlderVersion() {
        TenantUtils.execute(2005L, () -> {
            service.upsert(request(1L, "旧名称"));
            WmsItemDO item = itemMapper.selectByCode("PIPE-001");
            itemMapper.updateById(new WmsItemDO().setId(item.getId()).setCategoryId(88L).setBrandId(99L));
            WmsItemSkuDO sku = skuMapper.selectListByItemId(item.getId()).getFirst();
            skuMapper.updateById(new WmsItemSkuDO().setId(sku.getId()).setSellingPrice(new BigDecimal("12.50")));

            service.upsert(request(2L, "新名称"));
            WmsItemDO updated = itemMapper.selectById(item.getId());
            assertEquals("新名称", updated.getName());
            assertEquals(88L, updated.getCategoryId());
            assertEquals(99L, updated.getBrandId());
            assertEquals(new BigDecimal("12.50"), skuMapper.selectById(sku.getId()).getSellingPrice());
            assertServiceException(() -> service.upsert(request(1L, "过期名称")), SYNC_VERSION_STALE);
        });
    }

    @Test
    void ambiguousExistingSkuMappingIsRejected() {
        TenantUtils.execute(2005L, () -> {
            WmsItemDO item = WmsItemDO.builder().code("PIPE-001").name("存量").categoryId(1L)
                    .materialType("FINISHED_GOODS").sourceVersion(0L).status(0).build();
            itemMapper.insert(item);
            skuMapper.insert(WmsItemSkuDO.builder().itemId(item.getId()).code("OTHER-A").name("A").build());
            skuMapper.insert(WmsItemSkuDO.builder().itemId(item.getId()).code("OTHER-B").name("B").build());
            assertServiceException(() -> service.upsert(request(1L, "新名称")),
                    INTEGRATION_MASTER_DATA_MAPPING_CONFLICT);
        });
    }

    private static WmsMaterialUpsertReqDTO request(Long version, String name) {
        WmsMaterialUpsertReqDTO request = new WmsMaterialUpsertReqDTO();
        request.setRequestId("material-" + version);
        request.setTenantId(2005L);
        request.setSourceModule("ERP");
        request.setOccurredAt(LocalDateTime.of(2026, 9, 8, 9, 0));
        request.setMaterialCode("PIPE-001");
        request.setName(name);
        request.setMaterialType("FINISHED_GOODS");
        request.setSpecification("DN20");
        request.setUnitCode("M");
        request.setUnitName("米");
        request.setBarCode("690000000001");
        request.setQualityRequired(true);
        request.setStatus("ENABLED");
        request.setDataVersion(version);
        return request;
    }
}
