package cn.iocoder.txgy.module.product.dal.mysql.inquiry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InquiryDeliveryScopeMapperContractTest {

    @Test
    void platformPublicRequiresCategoryAndRealFreightTemplateCoverage() {
        String delivery = InquiryCrossTenantMapper.DELIVERY_SCOPE_MATCH;
        assertTrue(delivery.contains("i.delivery_area_id IS NOT NULL"));
        assertTrue(delivery.contains("seller_spu.delivery_template_id"));
        assertTrue(delivery.contains("trade_delivery_express_template_charge"));
        assertTrue(delivery.contains("delivery_charge.area_ids"));
        assertTrue(delivery.contains("delivery_charge.tenant_id=#{viewerTenantId}"));

        String visibility = InquiryCrossTenantMapper.VISIBILITY_WHITELIST;
        assertTrue(visibility.contains(InquiryCrossTenantMapper.CATEGORY_AND_DELIVERY_MATCH));
        assertTrue(visibility.contains("PLATFORM_PUBLIC"));
        assertFalse(InquiryCrossTenantMapper.CATEGORY_MATCH.contains("trade_delivery_express_template_charge"));
    }
}
