package cn.iocoder.txgy.module.bidding.controller.admin.objection.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 提出异议 Request（校验提出时限：资格预审文件申请截止前2日/招标文件投标截止前10日）")
@Data
public class ObjectionSaveReqVO {

    @Schema(description = "招标项目编号（TENDER_PROJECT_CODE）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "招标项目编号不能为空")
    private String tenderProjectCode;

    @Schema(description = "标段编号（BID_SECTION_CODE，异议落实到标段以对齐截止时限）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "标段编号不能为空（异议需落实到标段计算截止时限）")
    private String bidSectionCode;

    @Schema(description = "异议类型（OBJECTION_TYPE：PREQUAL_FILE 资格预审文件 / TENDER_FILE 招标文件）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "异议类型不能为空")
    private String objectionType;

    @Schema(description = "提出主体ID（bidding_subject.id）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "提出主体不能为空")
    private Long subjectId;

    @Schema(description = "异议内容", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "异议内容不能为空")
    private String content;

    @Schema(description = "备注")
    private String remark;
}
