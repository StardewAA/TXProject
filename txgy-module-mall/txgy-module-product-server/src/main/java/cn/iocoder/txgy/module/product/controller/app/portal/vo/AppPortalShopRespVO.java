package cn.iocoder.txgy.module.product.controller.app.portal.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Schema(description = "用户 APP - 平台公开店铺")
@Data
@Accessors(chain = true)
public class AppPortalShopRespVO {
    private Long tenantId;
    private String tenantSlug;
    private String tenantName;
    private String tenantLogo;
    private Long enterpriseId;
    private String enterpriseName;
    private Long shopId;
    private String shopName;
    private String logoUrl;
    private String introduction;
    private Long productCount;
}
