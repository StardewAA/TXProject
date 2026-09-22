package cn.iocoder.txgy.module.product.dal.mysql.sku;

import cn.iocoder.txgy.framework.mybatis.core.type.IntegerListTypeHandler;
import cn.iocoder.txgy.module.product.dal.dataobject.sku.ProductSkuDO;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 平台购物车专用的跨租户 SKU 归属查询。
 *
 * <p>调用方法必须处于 {@code @TenantIgnore} 边界内；SQL 自身仍强制门户白名单、有效系统租户，
 * 并永久排除原公共租户 3000，不能退化为无条件跨租户查询。</p>
 */
@Mapper
public interface ProductPublicSkuMapper {

    @Select({"<script>",
            "SELECT sku.id AS skuId, sku.spu_id AS spuId, sku.tenant_id AS tenantId,",
            " pt.tenant_name AS tenantName, ps.enterprise_id AS enterpriseId,",
            " pe.name AS enterpriseName, spu.shop_id AS shopId, ps.name AS shopName,",
            " spu.name AS spuName, sku.properties AS properties,",
            " COALESCE(NULLIF(sku.pic_url, ''), spu.pic_url) AS picUrl,",
            " sku.price AS price, sku.stock AS stock, sku.weight AS weight, sku.volume AS volume,",
            " spu.delivery_types AS deliveryTypes, spu.delivery_template_id AS deliveryTemplateId,",
            " sku.deleted AS skuDeleted, spu.deleted AS spuDeleted,",
            " spu.status AS spuStatus, spu.review_status AS reviewStatus,",
            " spu.content_version AS contentVersion, spu.approved_version AS approvedVersion,",
            " ps.deleted AS shopDeleted, ps.status AS shopStatus,",
            " pe.deleted AS enterpriseDeleted, pe.audit_status AS enterpriseAuditStatus",
            " FROM product_sku sku",
            " JOIN mall_portal_tenant pt ON pt.tenant_id = sku.tenant_id",
            " JOIN system_tenant st ON st.id = pt.tenant_id",
            " LEFT JOIN product_spu spu ON spu.id = sku.spu_id AND spu.tenant_id = sku.tenant_id",
            " LEFT JOIN product_shop ps ON ps.id = spu.shop_id AND ps.tenant_id = spu.tenant_id",
            " LEFT JOIN product_enterprise pe ON pe.id = ps.enterprise_id AND pe.tenant_id = ps.tenant_id",
            " WHERE sku.id IN",
            " <foreach collection='skuIds' item='skuId' open='(' separator=',' close=')'>#{skuId}</foreach>",
            " AND pt.deleted = 0 AND pt.open_status = 0 AND pt.tenant_id &lt;&gt; 3000",
            " AND st.deleted = 0 AND st.status = 0",
            " AND (st.expire_time IS NULL OR st.expire_time &gt; NOW())",
            " ORDER BY sku.id ASC",
            "</script>"})
    @Results({
            @Result(column = "properties", property = "properties", typeHandler = Jackson3TypeHandler.class),
            @Result(column = "deliveryTypes", property = "deliveryTypes", typeHandler = IntegerListTypeHandler.class)
    })
    List<PublicSkuRow> selectBySkuIds(@Param("skuIds") Collection<Long> skuIds);

    @Data
    class PublicSkuRow {
        private Long tenantId;
        private String tenantName;
        private Long enterpriseId;
        private String enterpriseName;
        private Long shopId;
        private String shopName;
        private Long spuId;
        private String spuName;
        private Long skuId;
        private List<ProductSkuDO.Property> properties;
        private String picUrl;
        private Integer price;
        private Integer stock;
        private List<Integer> deliveryTypes;
        private Long deliveryTemplateId;
        private Double weight;
        private Double volume;
        private Boolean skuDeleted;
        private Boolean spuDeleted;
        private Integer spuStatus;
        private Integer reviewStatus;
        private Long contentVersion;
        private Long approvedVersion;
        private Boolean shopDeleted;
        private Integer shopStatus;
        private Boolean enterpriseDeleted;
        private Integer enterpriseAuditStatus;
    }

}
