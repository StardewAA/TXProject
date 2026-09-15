package cn.iocoder.txgy.module.cms.controller.admin.content.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@Schema(description = "管理后台 - CMS 内容统一审核 Request VO")
@Data
public class CmsAuditReqVO {

    @Schema(description = "内容类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "standard")
    @NotEmpty(message = "内容类型不能为空")
    private String contentType;

    @Schema(description = "内容编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "内容编号不能为空")
    private Long contentId;

    @Schema(description = "审核结果（0待审核 1通过 2驳回）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "审核结果不能为空")
    private Integer auditStatus;

    @Schema(description = "审核备注（驳回时必填原因）", example = "内容无误")
    private String auditRemark;

}
