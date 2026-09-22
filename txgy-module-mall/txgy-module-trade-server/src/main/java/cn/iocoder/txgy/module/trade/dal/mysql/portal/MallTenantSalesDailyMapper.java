package cn.iocoder.txgy.module.trade.dal.mysql.portal;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 平台门户热门租户销量汇总。
 *
 * <p>这是受控的跨租户聚合写入，只读取订单公开统计事实，不返回订单、会员或库存明细。</p>
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface MallTenantSalesDailyMapper {

    @Delete("""
        DELETE FROM mall_tenant_sales_daily
        WHERE statistic_date BETWEEN #{fromDate} AND #{toDate}
        """)
    int deleteRange(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    /**
     * Rebuilds paid-order facts for the requested window.
     *
     * <p>Historical order eligibility deliberately does not depend on the SKU's current stock or
     * current price. Those values are mutable after payment; tenant visibility and the presence of
     * at least one currently saleable product are enforced by the portal ranking query.</p>
     */
    @Insert("""
        INSERT INTO mall_tenant_sales_daily
          (tenant_id,statistic_date,paid_quantity,refunded_quantity,valid_quantity,
           paid_order_count,paid_amount,creator,updater,deleted)
        SELECT o.tenant_id,DATE(o.pay_time),
               SUM(items.paid_quantity) AS paid_quantity,
               SUM(CASE WHEN o.status=40 OR o.refund_status=20
                        THEN items.paid_quantity ELSE items.refunded_quantity END) AS refunded_quantity,
               SUM(CASE WHEN o.status=40 OR o.refund_status=20
                        THEN 0 ELSE items.valid_quantity END) AS valid_quantity,
               COUNT(DISTINCT CASE WHEN o.status<>40 AND o.refund_status<>20 AND items.valid_quantity&gt;0
                                   THEN o.id END) AS paid_order_count,
               SUM(CASE WHEN o.status=40 OR o.refund_status=20 THEN 0
                        ELSE GREATEST(COALESCE(items.paid_amount,0)-COALESCE(o.refund_price,0),0) END)
                 AS paid_amount,
               'mall-portal-sales-job','mall-portal-sales-job',FALSE
        FROM trade_order o
        JOIN (
          SELECT oi.tenant_id,oi.order_id,
                 SUM(oi.count) AS paid_quantity,
                 SUM(LEAST(oi.count,COALESCE(refunds.refunded_quantity,0))) AS refunded_quantity,
                 SUM(GREATEST(oi.count-COALESCE(refunds.refunded_quantity,0),0)) AS valid_quantity,
                 SUM(COALESCE(oi.pay_price,0)) AS paid_amount
          FROM trade_order_item oi
          LEFT JOIN (
            SELECT af.tenant_id,af.order_item_id,SUM(af.count) AS refunded_quantity
            FROM trade_after_sale af
            WHERE af.deleted=FALSE AND af.status=50
            GROUP BY af.tenant_id,af.order_item_id
          ) refunds ON refunds.tenant_id=oi.tenant_id AND refunds.order_item_id=oi.id
          JOIN product_sku sku ON sku.id=oi.sku_id
            AND sku.tenant_id=oi.tenant_id AND sku.spu_id=oi.spu_id
            AND sku.deleted=FALSE
          JOIN product_spu spu ON spu.id=oi.spu_id
            AND spu.tenant_id=oi.tenant_id AND spu.id=sku.spu_id
            AND spu.deleted=FALSE AND spu.status=1 AND spu.review_status=20
            AND spu.approved_version IS NOT NULL
            AND spu.approved_version=spu.content_version
          JOIN product_shop ps ON ps.id=oi.shop_id
            AND ps.id=spu.shop_id AND ps.tenant_id=oi.tenant_id
            AND ps.deleted=FALSE AND ps.status=0
          JOIN product_enterprise pe ON pe.id=ps.enterprise_id
            AND pe.tenant_id=oi.tenant_id
            AND pe.deleted=FALSE AND pe.audit_status=1
          WHERE oi.deleted=FALSE
          GROUP BY oi.tenant_id,oi.order_id
        ) items ON items.order_id=o.id AND items.tenant_id=o.tenant_id
        JOIN mall_portal_tenant pt ON pt.tenant_id=o.tenant_id
          AND pt.open_status=0 AND pt.deleted=FALSE
        JOIN system_tenant st ON st.id=o.tenant_id
          AND st.deleted=FALSE AND st.status=0
          AND (st.expire_time IS NULL OR st.expire_time&gt;NOW())
        WHERE o.deleted=FALSE AND o.pay_status=TRUE
          AND o.pay_time>=#{fromDate} AND o.pay_time&lt;DATE_ADD(#{toDate},INTERVAL 1 DAY)
          AND o.tenant_id<>3000
          AND LOWER(TRIM(COALESCE(o.pay_channel_code,'')))<>'mock'
          AND NOT EXISTS (
            SELECT 1 FROM mall_portal_sales_order_exclusion ex
            WHERE ex.tenant_id=o.tenant_id AND ex.order_id=o.id AND ex.deleted=FALSE
          )
        GROUP BY o.tenant_id,DATE(o.pay_time)
        """)
    int insertRange(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
}
