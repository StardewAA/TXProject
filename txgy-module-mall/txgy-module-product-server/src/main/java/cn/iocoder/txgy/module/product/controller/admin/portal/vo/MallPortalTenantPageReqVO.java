package cn.iocoder.txgy.module.product.controller.admin.portal.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 商城平台租户目录分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MallPortalTenantPageReqVO extends PageParam {

    @Schema(description = "经营租户编号", example = "2009")
    private Long tenantId;

    @Schema(description = "租户名称", example = "科盾防护科技股份有限公司")
    private String tenantName;

    @Schema(description = "稳定路由标识", example = "kedun")
    private String tenantSlug;

    @Schema(description = "门户开放状态：0 开放，1 关闭", example = "0")
    private Integer openStatus;
}
