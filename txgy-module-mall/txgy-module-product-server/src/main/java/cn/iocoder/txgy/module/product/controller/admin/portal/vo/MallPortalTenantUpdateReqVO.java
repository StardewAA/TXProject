package cn.iocoder.txgy.module.product.controller.admin.portal.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 更新商城平台租户目录 Request VO")
@Data
public class MallPortalTenantUpdateReqVO {

    @Schema(description = "目录编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "目录编号不能为空")
    private Long id;

    @Schema(description = "稳定路由标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "kedun")
    @NotBlank(message = "路由标识不能为空")
    @Size(max = 64, message = "路由标识长度不能超过 64 个字符")
    private String tenantSlug;

    @Schema(description = "公开 Logo")
    @Size(max = 512, message = "Logo 地址长度不能超过 512 个字符")
    private String tenantLogo;

    @Schema(description = "主营分类摘要")
    @Size(max = 512, message = "主营分类长度不能超过 512 个字符")
    private String mainCategories;

    @Schema(description = "门户开放状态：0 开放，1 关闭", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "门户开放状态不能为空")
    @Min(value = 0, message = "门户开放状态不正确")
    @Max(value = 1, message = "门户开放状态不正确")
    private Integer openStatus;

    @Schema(description = "展示排序", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @NotNull(message = "展示排序不能为空")
    @Min(value = 0, message = "展示排序不能小于 0")
    @Max(value = 999999, message = "展示排序不能超过 999999")
    private Integer sort;
}
