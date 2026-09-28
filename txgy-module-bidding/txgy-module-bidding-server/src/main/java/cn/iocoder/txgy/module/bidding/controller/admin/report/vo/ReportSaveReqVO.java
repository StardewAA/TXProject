package cn.iocoder.txgy.module.bidding.controller.admin.report.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 提交定标书面报告 Request（招标投标法第47条：确定中标人起15日内提交）")
@Data
public class ReportSaveReqVO {

    @Schema(description = "招标项目编号（TENDER_PROJECT_CODE）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "招标项目编号不能为空")
    private String tenderProjectCode;

    @Schema(description = "标段编号（BID_SECTION_CODE，空=项目级）")
    private String bidSectionCode;

    @Schema(description = "报告编号（内部编号，前缀 R）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "报告编号不能为空")
    private String reportCode;

    @Schema(description = "报告名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "报告名称不能为空")
    private String reportName;

    @Schema(description = "报告文件 FileApi 主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "报告文件不能为空")
    private Long fileId;

    @Schema(description = "备注")
    private String remark;
}
