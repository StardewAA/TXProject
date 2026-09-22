package cn.iocoder.txgy.module.product.dal.mysql.portal;

import cn.iocoder.txgy.module.product.dal.dataobject.sku.ProductSkuDO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 平台门户专用跨租户只读查询。
 *
 * SQL 必须始终显式列出公开字段和租户/企业/店铺/商品有效性条件，禁止复用普通租户 Mapper。
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface MallPortalCatalogMapper {

    String PUBLIC_PRODUCT_FROM = " FROM mall_portal_tenant pt"
            + " JOIN system_tenant st ON st.id = pt.tenant_id"
            + " JOIN product_spu spu ON spu.tenant_id = pt.tenant_id"
            + " JOIN product_shop ps ON ps.id = spu.shop_id AND ps.tenant_id = spu.tenant_id"
            + " JOIN product_enterprise pe ON pe.id = ps.enterprise_id AND pe.tenant_id = ps.tenant_id";

    String PUBLIC_PRODUCT_WHERE = " WHERE pt.deleted = 0 AND pt.open_status = 0"
            + " AND pt.tenant_id &lt;&gt; 3000"
            + " AND st.deleted = 0 AND st.status = 0"
            + " AND (st.expire_time IS NULL OR st.expire_time &gt; NOW())"
            + " AND pe.deleted = 0 AND pe.audit_status = 1"
            + " AND ps.deleted = 0 AND ps.status = 0"
            + " AND spu.deleted = 0 AND spu.status = 1"
            + " AND spu.review_status = 20"
            + " AND spu.approved_version IS NOT NULL"
            + " AND spu.approved_version = spu.content_version"
            + " AND EXISTS (SELECT 1 FROM product_sku sale_sku"
            + " WHERE sale_sku.spu_id = spu.id AND sale_sku.tenant_id = spu.tenant_id"
            + " AND sale_sku.deleted = 0 AND sale_sku.stock &gt; 0 AND sale_sku.price &gt; 0)";

    @Select({"<script>",
            "SELECT pt.tenant_id AS tenantId, pt.tenant_slug AS tenantSlug,",
            " pt.tenant_name AS tenantName, pt.tenant_logo AS tenantLogo,",
            " pe.id AS enterpriseId, pe.name AS enterpriseName,",
            " ps.id AS shopId, ps.name AS shopName,",
            " spu.id AS spuId, spu.name AS spuName, spu.introduction AS introduction,",
            " spu.pic_url AS picUrl, spu.category_id AS categoryId, spu.price AS price,",
            " (COALESCE(spu.sales_count, 0) + COALESCE(spu.virtual_sales_count, 0)) AS salesCount",
            PUBLIC_PRODUCT_FROM, PUBLIC_PRODUCT_WHERE,
            "<if test='tenantSlug != null'> AND pt.tenant_slug = #{tenantSlug}</if>",
            "<if test='keyword != null'> AND (spu.name LIKE CONCAT('%', #{keyword}, '%')",
            " OR ps.name LIKE CONCAT('%', #{keyword}, '%')",
            " OR pe.name LIKE CONCAT('%', #{keyword}, '%'))</if>",
            "<if test='categoryId != null'> AND spu.category_id = #{categoryId}</if>",
            " ORDER BY spu.sort DESC, spu.id DESC",
            " LIMIT #{pageSize} OFFSET #{offset}",
            "</script>"})
    List<PortalProductRow> selectProducts(@Param("tenantSlug") String tenantSlug,
                                          @Param("keyword") String keyword,
                                          @Param("categoryId") Long categoryId,
                                          @Param("offset") long offset,
                                          @Param("pageSize") int pageSize);

    @Select({"<script>",
            "SELECT pt.tenant_id AS tenantId, pt.tenant_slug AS tenantSlug,",
            " pt.tenant_name AS tenantName, pt.tenant_logo AS tenantLogo,",
            " pe.id AS enterpriseId, pe.name AS enterpriseName,",
            " ps.id AS shopId, ps.name AS shopName,",
            " spu.id AS spuId, spu.name AS spuName, spu.introduction AS introduction,",
            " spu.description AS description, spu.pic_url AS picUrl,",
            " spu.category_id AS categoryId, spu.price AS price,",
            " (COALESCE(spu.sales_count, 0) + COALESCE(spu.virtual_sales_count, 0)) AS salesCount",
            PUBLIC_PRODUCT_FROM, PUBLIC_PRODUCT_WHERE,
            " AND spu.id = #{spuId}",
            "<if test='tenantSlug != null'> AND pt.tenant_slug = #{tenantSlug}</if>",
            " LIMIT 1",
            "</script>"})
    PortalProductRow selectProductById(@Param("tenantSlug") String tenantSlug,
                                       @Param("spuId") Long spuId);

    @Select({"<script>", "SELECT COUNT(1)", PUBLIC_PRODUCT_FROM, PUBLIC_PRODUCT_WHERE,
            "<if test='tenantSlug != null'> AND pt.tenant_slug = #{tenantSlug}</if>",
            "<if test='keyword != null'> AND (spu.name LIKE CONCAT('%', #{keyword}, '%')",
            " OR ps.name LIKE CONCAT('%', #{keyword}, '%')",
            " OR pe.name LIKE CONCAT('%', #{keyword}, '%'))</if>",
            "<if test='categoryId != null'> AND spu.category_id = #{categoryId}</if>",
            "</script>"})
    long countProducts(@Param("tenantSlug") String tenantSlug,
                       @Param("keyword") String keyword,
                       @Param("categoryId") Long categoryId);

    @Select("SELECT pt.tenant_id AS tenantId, pt.tenant_slug AS tenantSlug,"
            + " pt.tenant_name AS tenantName, pt.tenant_logo AS tenantLogo,"
            + " pt.main_categories AS mainCategories"
            + " FROM mall_portal_tenant pt"
            + " JOIN system_tenant st ON st.id = pt.tenant_id"
            + " WHERE pt.tenant_slug = #{tenantSlug}"
            + " AND pt.deleted = 0 AND pt.open_status = 0 AND pt.tenant_id <> 3000"
            + " AND st.deleted = 0 AND st.status = 0"
            + " AND (st.expire_time IS NULL OR st.expire_time > NOW())"
            + " LIMIT 1")
    PortalTenantRow selectTenantBySlug(@Param("tenantSlug") String tenantSlug);

    @Select("SELECT pt.tenant_id AS tenantId, pt.tenant_slug AS tenantSlug,"
            + " pt.tenant_name AS tenantName, pt.tenant_logo AS tenantLogo,"
            + " pt.main_categories AS mainCategories,"
            + " SUM(ts.valid_quantity) AS validQuantity"
            + " FROM mall_portal_tenant pt"
            + " JOIN system_tenant st ON st.id = pt.tenant_id"
            + " JOIN mall_tenant_sales_daily ts ON ts.tenant_id = pt.tenant_id"
            + " WHERE pt.deleted = 0 AND pt.open_status = 0 AND pt.tenant_id <> 3000"
            + " AND st.deleted = 0 AND st.status = 0"
            + " AND (st.expire_time IS NULL OR st.expire_time > NOW())"
            + " AND EXISTS (SELECT 1 FROM product_spu spu"
            + " JOIN product_shop ps ON ps.id = spu.shop_id AND ps.tenant_id = spu.tenant_id"
            + " JOIN product_enterprise pe ON pe.id = ps.enterprise_id AND pe.tenant_id = ps.tenant_id"
            + " WHERE spu.tenant_id = pt.tenant_id"
            + " AND pe.deleted = 0 AND pe.audit_status = 1"
            + " AND ps.deleted = 0 AND ps.status = 0"
            + " AND spu.deleted = 0 AND spu.status = 1 AND spu.review_status = 20"
            + " AND spu.approved_version IS NOT NULL AND spu.approved_version = spu.content_version"
            + " AND EXISTS (SELECT 1 FROM product_sku sale_sku"
            + " WHERE sale_sku.spu_id = spu.id AND sale_sku.tenant_id = spu.tenant_id"
            + " AND sale_sku.deleted = 0 AND sale_sku.stock > 0 AND sale_sku.price > 0))"
            + " AND ts.statistic_date BETWEEN #{fromDate} AND #{toDate}"
            + " GROUP BY pt.tenant_id, pt.tenant_slug, pt.tenant_name, pt.tenant_logo, pt.main_categories"
            + " HAVING SUM(ts.valid_quantity) > 0"
            + " ORDER BY SUM(ts.valid_quantity) DESC, SUM(ts.paid_amount) DESC,"
            + " SUM(ts.paid_order_count) DESC, pt.tenant_id ASC"
            + " LIMIT #{limit}")
    List<PortalHotTenantRow> selectHotTenants(@Param("fromDate") LocalDate fromDate,
                                               @Param("toDate") LocalDate toDate,
                                               @Param("limit") int limit);

    @Select({"<script>",
            "SELECT sku.spu_id AS spuId, sku.id AS skuId, spu.name AS spuName,",
            " sku.properties AS properties,",
            " COALESCE(NULLIF(sku.pic_url, ''), spu.pic_url) AS picUrl, sku.price AS price",
            " FROM product_sku sku",
            " JOIN product_spu spu ON spu.id = sku.spu_id AND spu.tenant_id = sku.tenant_id",
            " JOIN mall_portal_tenant pt ON pt.tenant_id = spu.tenant_id",
            " JOIN system_tenant st ON st.id = pt.tenant_id",
            " JOIN product_shop ps ON ps.id = spu.shop_id AND ps.tenant_id = spu.tenant_id",
            " JOIN product_enterprise pe ON pe.id = ps.enterprise_id AND pe.tenant_id = ps.tenant_id",
            " WHERE sku.deleted = 0 AND spu.id IN",
            "<foreach collection='spuIds' item='spuId' open='(' separator=',' close=')'>#{spuId}</foreach>",
            " AND pt.deleted = 0 AND pt.open_status = 0 AND pt.tenant_id &lt;&gt; 3000",
            " AND st.deleted = 0 AND st.status = 0",
            " AND (st.expire_time IS NULL OR st.expire_time &gt; NOW())",
            " AND pe.deleted = 0 AND pe.audit_status = 1",
            " AND ps.deleted = 0 AND ps.status = 0",
            " AND spu.deleted = 0 AND spu.status = 1 AND spu.review_status = 20",
            " AND spu.approved_version IS NOT NULL AND spu.approved_version = spu.content_version",
            " AND sku.stock &gt; 0 AND sku.price &gt; 0",
            " ORDER BY sku.spu_id ASC, sku.price ASC, sku.id ASC",
            "</script>"})
    @Results(@Result(column = "properties", property = "properties", typeHandler = Jackson3TypeHandler.class))
    List<PortalSkuRow> selectPublicSkus(@Param("spuIds") List<Long> spuIds);

    @Data
    class PortalProductRow {
        private Long tenantId;
        private String tenantSlug;
        private String tenantName;
        private String tenantLogo;
        private Long enterpriseId;
        private String enterpriseName;
        private Long shopId;
        private String shopName;
        private Long spuId;
        private String spuName;
        private String introduction;
        private String description;
        private String picUrl;
        private Long categoryId;
        private Integer price;
        private Integer salesCount;
    }

    @Data
    class PortalSkuRow {
        private Long spuId;
        private Long skuId;
        private String spuName;
        private List<ProductSkuDO.Property> properties;
        private String picUrl;
        private Integer price;
    }

    @Data
    class PortalTenantRow {
        private Long tenantId;
        private String tenantSlug;
        private String tenantName;
        private String tenantLogo;
        private String mainCategories;
    }

    @Data
    class PortalHotTenantRow extends PortalTenantRow {
        private Long validQuantity;
    }
}
