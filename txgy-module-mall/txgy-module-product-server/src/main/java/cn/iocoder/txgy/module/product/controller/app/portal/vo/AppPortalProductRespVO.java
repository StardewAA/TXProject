package cn.iocoder.txgy.module.product.controller.app.portal.vo;

import cn.iocoder.txgy.module.product.dal.dataobject.sku.ProductSkuDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Schema(description = "用户 APP - 平台门户公开商品")
@Data
@Accessors(chain = true)
public class AppPortalProductRespVO {

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
    @Schema(description = "已审核的公开商品详情，仅详情接口返回")
    private String description;
    private String picUrl;
    private Long categoryId;
    /** 最低公开售价，单位：分。 */
    private Integer price;
    private Integer salesCount;
    private Boolean saleable;
    private List<Sku> skus;

    @Data
    @Accessors(chain = true)
    public static class Sku {
        private Long skuId;
        private String skuName;
        private List<ProductSkuDO.Property> properties;
        private String picUrl;
        /** 公开售价，单位：分。 */
        private Integer price;
    }
}
