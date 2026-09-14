package cn.iocoder.txgy.module.wms.service.integration;

import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsPartnerUpsertReqDTO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.merchant.WmsMerchantDO;
import cn.iocoder.txgy.module.wms.dal.mysql.md.merchant.WmsMerchantMapper;
import cn.iocoder.txgy.module.wms.enums.md.WmsMerchantTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static cn.iocoder.txgy.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.SYNC_VERSION_STALE;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Import(WmsPartnerIntegrationService.class)
class WmsPartnerIntegrationServiceTest extends BaseDbUnitTest {

    @Resource private WmsPartnerIntegrationService service;
    @Resource private WmsMerchantMapper mapper;

    @Test
    void customerAndSupplierWithSameCodeMergeWithoutLosingWmsLevel() {
        TenantUtils.execute(2005L, () -> {
            service.upsert(request("CUSTOMER", 2L, "客户联系人"));
            WmsMerchantDO merchant = mapper.selectByCode("PARTNER-001");
            mapper.updateById(new WmsMerchantDO().setId(merchant.getId()).setLevel("A"));

            service.upsert(request("SUPPLIER", 1L, "供应商联系人"));
            WmsMerchantDO merged = mapper.selectById(merchant.getId());
            assertEquals(WmsMerchantTypeEnum.CUSTOMER_SUPPLIER.getType(), merged.getType());
            assertEquals("A", merged.getLevel());
            assertEquals("供应商联系人", merged.getContact());
            assertEquals(2L, merged.getCustomerSourceVersion());
            assertEquals(1L, merged.getSupplierSourceVersion());

            service.upsert(request("CUSTOMER", 2L, "客户联系人"));
            assertEquals(2L, mapper.selectById(merchant.getId()).getCustomerSourceVersion());
        });
    }

    @Test
    void staleVersionIsCheckedPerPartnerRole() {
        TenantUtils.execute(2005L, () -> {
            service.upsert(request("CUSTOMER", 2L, "新版"));
            assertServiceException(() -> service.upsert(request("CUSTOMER", 1L, "旧版")), SYNC_VERSION_STALE);
            service.upsert(request("SUPPLIER", 1L, "供应商"));
        });
    }

    @Test
    void explicitBothUpdatesBothRoleVersions() {
        TenantUtils.execute(2005L, () -> {
            service.upsert(request("BOTH", 3L, "共同联系人"));
            WmsMerchantDO merchant = mapper.selectByCode("PARTNER-001");
            assertEquals(WmsMerchantTypeEnum.CUSTOMER_SUPPLIER.getType(), merchant.getType());
            assertEquals(3L, merchant.getCustomerSourceVersion());
            assertEquals(3L, merchant.getSupplierSourceVersion());
        });
    }

    private static WmsPartnerUpsertReqDTO request(String type, Long version, String contact) {
        WmsPartnerUpsertReqDTO request = new WmsPartnerUpsertReqDTO();
        request.setRequestId(type + "-" + version);
        request.setTenantId(2005L);
        request.setSourceModule("ERP");
        request.setOccurredAt(LocalDateTime.of(2026, 9, 8, 10, 0));
        request.setPartnerCode("PARTNER-001");
        request.setName("天鑫企业");
        request.setPartnerType(type);
        request.setContact(contact);
        request.setMobile("13800000000");
        request.setAddress("测试地址");
        request.setStatus("ENABLED");
        request.setDataVersion(version);
        return request;
    }
}
