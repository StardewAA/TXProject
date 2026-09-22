package cn.iocoder.txgy.module.product.controller.app.portal.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Schema(description = "用户 APP - 平台门户租户商城")
@Data
@Accessors(chain = true)
public class AppPortalTenantRespVO {

    private Long tenantId;
    private String tenantSlug;
    private String tenantName;
    private String tenantLogo;
    private String mainCategories;
}
