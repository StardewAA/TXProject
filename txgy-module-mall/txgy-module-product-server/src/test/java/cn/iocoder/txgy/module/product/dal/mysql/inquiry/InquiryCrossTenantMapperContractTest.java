package cn.iocoder.txgy.module.product.dal.mysql.inquiry;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InquiryCrossTenantMapperContractTest {

    @Test
    void everyAnnotatedQueryCanBeParsedByMyBatis() {
        Configuration configuration = new Configuration();
        XMLLanguageDriver languageDriver = new XMLLanguageDriver();
        Arrays.stream(InquiryCrossTenantMapper.class.getMethods())
                .filter(method -> method.isAnnotationPresent(Select.class))
                .forEach(method -> {
                    Select select = method.getAnnotation(Select.class);
                    SqlSource ignored = languageDriver.createSqlSource(configuration,
                            String.join(" ", select.value()), Object.class);
                });
    }

    @Test
    void opportunityStatementsAreRegisteredByMyBatis() {
        Configuration configuration = new Configuration();
        configuration.addMapper(InquiryCrossTenantMapper.class);
        String namespace = InquiryCrossTenantMapper.class.getName() + ".";
        assertTrue(configuration.hasStatement(namespace + "selectVisibleOpportunities"));
        assertTrue(configuration.hasStatement(namespace + "countVisibleOpportunities"));
    }

    @Test
    void tenantInterceptorBypassIsLimitedByExplicitActiveAndVisibilityGuards() {
        InterceptorIgnore ignore = InquiryCrossTenantMapper.class.getAnnotation(InterceptorIgnore.class);
        assertTrue(ignore != null && "true".equals(ignore.tenantLine()));

        String opportunity = InquiryCrossTenantMapper.OPPORTUNITY_FROM;
        assertTrue(opportunity.contains("i.tenant_id&lt;&gt;3000"));
        assertTrue(opportunity.contains("viewer_tenant.status=0"));
        assertTrue(opportunity.contains("buyer_enterprise.audit_status=1"));
        assertTrue(opportunity.contains("viewer_shop.status=0"));
        assertTrue(InquiryCrossTenantMapper.VISIBILITY_WHITELIST.contains("PLATFORM_PUBLIC"));
        assertTrue(InquiryCrossTenantMapper.VISIBILITY_WHITELIST.contains("INVITED_SHOPS"));
        assertTrue(InquiryCrossTenantMapper.VISIBILITY_WHITELIST.contains(
                InquiryCrossTenantMapper.CROSS_TENANT_DIRECTED_INVITATION));
        assertTrue(InquiryCrossTenantMapper.CROSS_TENANT_DIRECTED_INVITATION
                .contains("product_purchase_inquiry_invited_scope"));
        assertTrue(InquiryCrossTenantMapper.CROSS_TENANT_DIRECTED_INVITATION
                .contains("directed_scope.target_type='TENANT'"));
        assertTrue(InquiryCrossTenantMapper.CROSS_TENANT_DIRECTED_INVITATION
                .contains("directed_scope.target_enterprise_id=#{viewerEnterpriseId}"));
        assertTrue(InquiryCrossTenantMapper.CROSS_TENANT_DIRECTED_INVITATION
                .contains("directed_scope.target_shop_id=#{shopId}"));

        String quoteScope = InquiryCrossTenantMapper.ACTIVE_QUOTE_SCOPE;
        assertTrue(quoteScope.contains("COALESCE(q.supplier_tenant_id,q.tenant_id)&lt;&gt;3000"));
        assertTrue(quoteScope.contains("supplier_tenant.status=0"));
        assertTrue(quoteScope.contains("buyer_enterprise.audit_status=1"));
        assertTrue(quoteScope.contains("PLATFORM_PUBLIC"));
        assertTrue(quoteScope.contains("INVITED_SHOPS"));
        assertTrue(quoteScope.contains(InquiryCrossTenantMapper.QUOTE_DIRECTED_SCOPE));
        assertTrue(quoteScope.contains("buyer_portal.open_status=0"));
        assertTrue(quoteScope.contains("supplier_portal.open_status=0"));
        assertTrue(InquiryCrossTenantMapper.SUPPLIER_QUOTE_FROM.contains(quoteScope));
        assertTrue(InquiryCrossTenantMapper.SUPPLIER_TENANT_QUOTE_FROM.contains(quoteScope));

        String history = InquiryCrossTenantMapper.HISTORICAL_SUPPLIER_QUOTE_FROM;
        assertTrue(history.contains("q.deleted=b'0'"));
        assertTrue(history.contains("COALESCE(q.supplier_tenant_id,q.tenant_id)=#{supplierTenantId}"));
        assertTrue(history.contains("q.enterprise_id=#{enterpriseId}"));
        assertTrue(history.contains("q.shop_id=#{shopId}"));
        assertFalse(history.contains(quoteScope));
        assertFalse(history.contains("mall_portal_tenant"));
        assertFalse(history.contains("supplier_shop.status=0"));
        assertFalse(history.contains("supplier_enterprise.audit_status=1"));
    }

    @Test
    void crossTenantWhitelistQueryReturnsOnlyOpaqueReferencesBeforeAuthorization() throws Exception {
        Select select = InquiryCrossTenantMapper.class.getMethod("selectVisibleOpportunity",
                Long.class, Long.class, Long.class, Long.class).getAnnotation(Select.class);
        String sql = String.join(" ", Arrays.asList(select.value())).toLowerCase();
        assertTrue(sql.contains("i.id as inquiryid"));
        assertTrue(sql.contains("i.tenant_id as inquirytenantid"));
        assertFalse(sql.contains("contact_mobile"));
        assertFalse(sql.contains("contact_name"));
        assertFalse(sql.contains("i.address"));
        assertFalse(sql.contains("i.remark"));
        assertFalse(sql.contains("i.title"));
    }

    @Test
    void platformPublicDetailUsesTheXmlLanguageDriverForEscapedComparisonOperators() throws Exception {
        Select select = InquiryCrossTenantMapper.class.getMethod("selectPlatformPublicInquiry",
                Long.class).getAnnotation(Select.class);
        String sql = String.join(" ", Arrays.asList(select.value())).toLowerCase();
        assertTrue(sql.contains("<script>"));
        assertTrue(sql.contains("</script>"));
        assertTrue(InquiryCrossTenantMapper.PLATFORM_PUBLIC_FROM.contains("&lt;&gt;"));
    }

    @Test
    void opportunityRequiresEveryInquiryLineToHaveAnApprovedOnSaleDeliverableProduct() {
        String categoryAndDelivery = InquiryCrossTenantMapper.CATEGORY_AND_DELIVERY_MATCH;
        assertTrue(categoryAndDelivery.contains("NOT EXISTS (SELECT 1 FROM product_purchase_inquiry_item requested_item"));
        assertTrue(categoryAndDelivery.contains("AND NOT EXISTS (SELECT 1 FROM product_spu seller_spu"));
        assertTrue(categoryAndDelivery.contains("seller_spu.status=1"));
        assertFalse(categoryAndDelivery.contains("seller_spu.status IN (0,1)"));
        assertTrue(categoryAndDelivery.contains("seller_spu.review_status=20"));
        assertTrue(categoryAndDelivery.contains("seller_spu.approved_version=seller_spu.content_version"));
        assertTrue(categoryAndDelivery.contains("requested_item.category"));
        assertTrue(categoryAndDelivery.contains(InquiryCrossTenantMapper.FREE_TEXT_PRODUCT_NAME_MATCH));
        assertTrue(InquiryCrossTenantMapper.FREE_TEXT_PRODUCT_NAME_MATCH.contains("requested_item.name"));
        assertTrue(InquiryCrossTenantMapper.FREE_TEXT_PRODUCT_NAME_MATCH.contains("REPLACE(requested_item.name,'防腐','')"));
        assertFalse(categoryAndDelivery.contains("LIKE CONCAT"));

        String delivery = InquiryCrossTenantMapper.DELIVERY_SCOPE_MATCH;
        assertTrue(delivery.contains("trade_delivery_express_template_charge"));
        assertTrue(delivery.contains("trade_delivery_express_template_free"));
        assertTrue(delivery.contains("i.delivery_area_id"));
        assertTrue(delivery.contains("i.delivery_city_id"));
        assertTrue(delivery.contains("i.delivery_province_id"));
        assertTrue(delivery.contains("COLLATE utf8mb4_unicode_ci"));
        assertTrue(hasBalancedParentheses(InquiryCrossTenantMapper.CATEGORY_MATCH));
        assertTrue(hasBalancedParentheses(categoryAndDelivery));
        assertTrue(hasBalancedParentheses(InquiryCrossTenantMapper.VISIBILITY_WHITELIST));
        assertTrue(hasBalancedParentheses(delivery));
    }

    @Test
    void quoteLineEligibilityBindsExactItemSkuShopCategoryAndCurrentDeliveryScope() {
        String eligibility = InquiryCrossTenantMapper.QUOTE_ITEM_ELIGIBILITY_FROM;
        assertTrue(eligibility.contains("requested_item.id=#{inquiryItemId}"));
        assertTrue(eligibility.contains("seller_sku.id=#{skuId}"));
        assertTrue(eligibility.contains("seller_spu.shop_id=#{shopId}"));
        assertTrue(eligibility.contains("seller_spu.status=1"));
        assertTrue(eligibility.contains("seller_spu.review_status=20"));
        assertTrue(eligibility.contains("requested_spu.category_id"));
        assertTrue(eligibility.contains(InquiryCrossTenantMapper.FREE_TEXT_PRODUCT_NAME_MATCH));
        assertFalse(eligibility.contains("LIKE CONCAT"));

        String delivery = InquiryCrossTenantMapper.QUOTE_ITEM_DELIVERY_SCOPE;
        assertTrue(delivery.contains("trade_delivery_express_template_charge"));
        assertTrue(delivery.contains("trade_delivery_express_template_free"));
        assertTrue(delivery.contains("i.delivery_area_id"));
        assertTrue(delivery.contains("i.delivery_city_id"));
        assertTrue(delivery.contains("i.delivery_province_id"));
        assertTrue(delivery.contains("COLLATE utf8mb4_unicode_ci"));
        assertTrue(hasBalancedParentheses(eligibility));
        assertTrue(hasBalancedParentheses(delivery));
    }

    private static boolean hasBalancedParentheses(String sql) {
        int depth = 0;
        for (int index = 0; index < sql.length(); index++) {
            char current = sql.charAt(index);
            if (current == '(') depth++;
            if (current == ')' && --depth < 0) return false;
        }
        return depth == 0;
    }
}
