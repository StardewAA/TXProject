package cn.iocoder.txgy.module.product.dal.mysql.portal;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MallPortalCatalogMapperContractTest {

    @Test
    void dedicatedMapperExplicitlyOwnsTheCrossTenantBoundary() {
        InterceptorIgnore ignore = MallPortalCatalogMapper.class.getAnnotation(InterceptorIgnore.class);
        assertTrue(ignore != null && "true".equals(ignore.tenantLine()));
    }

    @Test
    void productQueriesUseExplicitPublicProjectionAndFullEligibilityBoundary() throws Exception {
        String pageSql = sql("selectProducts", String.class, String.class, Long.class,
                String.class, Boolean.class, long.class, int.class);
        String countSql = sql("countProducts", String.class, String.class, Long.class);
        String skuSql = sql("selectPublicSkus", java.util.List.class);
        String detailSql = sql("selectProductById", String.class, Long.class);

        assertFalse(pageSql.matches("(?s).*select\\s+[^;]*\\*.*"));
        assertFalse(skuSql.matches("(?s).*select\\s+[^;]*\\*.*"));
        assertFalse(pageSql.contains("cost_price"));
        assertFalse(skuSql.contains("cost_price"));
        assertFalse(skuSql.matches("(?s).*select\\s+[^;]*sku\\.stock[^;]*from.*"));
        assertEligibility(pageSql);
        assertEligibility(countSql);
        assertEligibility(skuSql);
        assertEligibility(detailSql);
        assertSaleableSkuBoundary(pageSql);
        assertSaleableSkuBoundary(countSql);
        assertSaleableSkuBoundary(detailSql);
        assertTrue(skuSql.contains("sku.stock &gt; 0"));
        assertTrue(skuSql.contains("sku.price &gt; 0"));
        assertTrue(detailSql.contains("spu.id = #{spuid}"));
        assertTrue(detailSql.contains("pt.tenant_slug = #{tenantslug}"));
        assertTrue(pageSql.contains("pt.tenant_slug = #{tenantslug}"));
        assertTrue(pageSql.contains("sortfield == 'price'"));
        assertTrue(pageSql.contains("sortfield == 'sales'"));
        assertTrue(pageSql.contains("order by spu.price asc"));
        assertTrue(pageSql.contains("order by salescount desc"));
    }

    @Test
    void tenantAndHotQueriesExcludePlatformTenantAndOnlyExposeOpenTenants() throws Exception {
        String tenantSql = sql("selectTenantBySlug", String.class);
        String hotSql = sql("selectHotTenants", java.time.LocalDate.class,
                java.time.LocalDate.class, int.class);

        assertTenantBoundary(tenantSql);
        assertTenantBoundary(hotSql);
        assertTrue(hotSql.contains("left join mall_tenant_sales_daily ts"));
        assertTrue(hotSql.contains("ts.statistic_date between #{fromdate} and #{todate}"));
        assertTrue(hotSql.contains("coalesce(sum(ts.valid_quantity), 0)"));
        assertTrue(hotSql.contains("exists (select 1 from product_spu spu"));
        assertTrue(hotSql.contains("pe.audit_status = 1"));
        assertTrue(hotSql.contains("ps.status = 0"));
        assertTrue(hotSql.contains("spu.status = 1"));
        assertTrue(hotSql.contains("spu.review_status = 20"));
        assertSaleableSkuBoundary(hotSql);
        assertFalse(hotSql.contains("&gt;"));
        assertFalse(hotSql.contains("having sum(ts.valid_quantity) > 0"));
        assertTrue(hotSql.contains("order by coalesce(sum(ts.valid_quantity), 0) desc,"));
        assertTrue(hotSql.contains("coalesce(sum(ts.paid_order_count), 0) desc, pt.tenant_id asc"));
    }

    private static void assertEligibility(String sql) {
        assertTenantBoundary(sql);
        assertTrue(sql.contains("pe.audit_status = 1"));
        assertTrue(sql.contains("ps.status = 0"));
        assertTrue(sql.contains("spu.status = 1"));
        assertTrue(sql.contains("spu.review_status = 20"));
        assertTrue(sql.contains("spu.approved_version = spu.content_version"));
    }

    private static void assertSaleableSkuBoundary(String sql) {
        assertTrue(sql.contains("exists (select 1 from product_sku sale_sku"));
        assertTrue(sql.contains("sale_sku.spu_id = spu.id"));
        assertTrue(sql.contains("sale_sku.tenant_id = spu.tenant_id"));
        assertTrue(sql.contains("sale_sku.stock &gt; 0") || sql.contains("sale_sku.stock > 0"));
        assertTrue(sql.contains("sale_sku.price &gt; 0") || sql.contains("sale_sku.price > 0"));
    }

    private static void assertTenantBoundary(String sql) {
        assertTrue(sql.contains("pt.open_status = 0"));
        assertTrue(sql.contains("pt.tenant_id <> 3000") || sql.contains("pt.tenant_id &lt;&gt; 3000"));
        assertTrue(sql.contains("st.status = 0"));
        assertTrue(sql.contains("st.expire_time is null or st.expire_time > now()")
                || sql.contains("st.expire_time is null or st.expire_time &gt; now()"));
    }

    private static String sql(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = MallPortalCatalogMapper.class.getMethod(methodName, parameterTypes);
        return String.join(" ", Arrays.stream(method.getAnnotation(Select.class).value())
                        .map(String::trim).toList())
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}
