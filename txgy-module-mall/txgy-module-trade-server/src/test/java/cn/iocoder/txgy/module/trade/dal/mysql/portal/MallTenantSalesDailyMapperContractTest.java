package cn.iocoder.txgy.module.trade.dal.mysql.portal;

import org.apache.ibatis.annotations.Insert;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MallTenantSalesDailyMapperContractTest {

    @Test
    void aggregationOnlyCountsCurrentlyPublicOrderItemsAndExplicitlyExcludesTests() throws Exception {
        String sql = aggregationSql();

        // 每一条被统计的订单项都必须能回查到当前公开的 SKU、SPU、店铺与企业。
        assertContainsAll(sql,
                "join product_sku sku", "sku.id=oi.sku_id", "sku.spu_id=oi.spu_id",
                "sku.deleted=false",
                "join product_spu spu", "spu.id=oi.spu_id", "spu.deleted=false",
                "spu.status=1", "spu.review_status=20",
                "spu.approved_version is not null", "spu.approved_version=spu.content_version",
                "join product_shop ps", "ps.id=oi.shop_id", "ps.id=spu.shop_id",
                "ps.deleted=false", "ps.status=0",
                "join product_enterprise pe", "pe.id=ps.enterprise_id",
                "pe.deleted=false", "pe.audit_status=1",
                "join system_tenant st", "st.deleted=false", "st.status=0");

        // mock 是可靠的模拟支付标记；其他测试单必须进入显式排除表，不能按名称或备注猜测。
        assertContainsAll(sql, "pay_channel_code", "<>'mock'",
                "mall_portal_sales_order_exclusion", "ex.tenant_id=o.tenant_id",
                "ex.order_id=o.id", "ex.deleted=false");
        assertFalse(sql.contains("spu_name like"));
        assertFalse(sql.contains("user_remark like"));
        assertFalse(sql.contains("remark like"));
    }

    @Test
    void aggregationDoesNotErasePaidHistoryWhenCurrentSkuSellsOutOrPriceChanges() throws Exception {
        String sql = aggregationSql();

        // 库存和现价是会变化的经营状态，不能反向改变已经支付订单的历史销量。
        // 租户当前是否仍可展示、是否至少有一个可售商品，由门户榜单查询单独校验。
        assertFalse(sql.contains("sku.stock"));
        assertFalse(sql.contains("sku.price"));
        assertContainsAll(sql, "sum(oi.count)", "sum(coalesce(oi.pay_price,0))");
    }

    @Test
    void aggregationKeepsPaidCancelFullRefundAndPrivacyContract() throws Exception {
        String sql = aggregationSql();

        assertContainsAll(sql, "o.pay_status=true", "o.status=40", "o.refund_status=20",
                "items.paid_quantity", "items.paid_amount", "o.refund_price",
                "o.tenant_id<>3000", "mall_tenant_sales_daily");
        // 部分退款时订单本身仍是 PART；必须按已完成售后单的实际退款数量扣减，不能把整条订单项都扣掉。
        assertContainsAll(sql, "trade_after_sale", "af.status=50", "sum(af.count)",
                "least(oi.count,coalesce(refunds.refunded_quantity,0))",
                "greatest(oi.count-coalesce(refunds.refunded_quantity,0),0)",
                "items.refunded_quantity", "items.valid_quantity", "items.valid_quantity&gt;0");
        assertFalse(sql.contains("receiver_mobile"));
        assertFalse(sql.contains("receiver_detail_address"));
    }

    private static String aggregationSql() throws Exception {
        Method method = MallTenantSalesDailyMapper.class.getMethod(
                "insertRange", LocalDate.class, LocalDate.class);
        Insert insert = method.getAnnotation(Insert.class);
        assertNotNull(insert);
        return String.join(" ", insert.value()).toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private static void assertContainsAll(String text, String... fragments) {
        for (String fragment : fragments) {
            assertTrue(text.contains(fragment), () -> "SQL 缺少约束: " + fragment + "\n" + text);
        }
    }
}
