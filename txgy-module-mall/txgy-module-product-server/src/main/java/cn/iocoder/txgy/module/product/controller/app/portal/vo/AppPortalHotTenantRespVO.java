package cn.iocoder.txgy.module.product.controller.app.portal.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Schema(description = "用户 APP - 平台门户热门租户")
@Data
@Accessors(chain = true)
public class AppPortalHotTenantRespVO {

    private Integer rank;
    private Long tenantId;
    private String tenantSlug;
    private String tenantName;
    private String tenantLogo;
    private String mainCategories;
    /** 榜单窗口内的有效销量；不公开支付金额和订单数。 */
    private Long validQuantity;
}
