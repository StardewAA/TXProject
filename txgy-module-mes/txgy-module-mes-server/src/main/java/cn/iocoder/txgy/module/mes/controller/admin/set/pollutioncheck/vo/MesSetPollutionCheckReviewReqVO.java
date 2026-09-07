package cn.iocoder.txgy.module.mes.controller.admin.set.pollutioncheck.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - MES 安全环保检测-污染判定记录 人工复核 Request VO")
@Data
public class MesSetPollutionCheckReviewReqVO {

    @Schema(description = "判定记录编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "判定记录编号不能为空")
    private Long id;

    @Schema(description = "人工复核结果（终态，二值）：CLEAN(无污染)/POLLUTED(有污染)",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "POLLUTED")
    @NotEmpty(message = "人工复核结果不能为空")
    private String reviewResult;

    @Schema(description = "最终存储方法（留空则按复核结果取默认/沿用 AI 建议）")
    private String storageMethod;

    @Schema(description = "处置方式：NORMAL_INBOUND/CONTROLLED_STORAGE/ISSUE_ALLOWED/REJECT_ISSUE/REUSE/DISCHARGE/ISOLATE_STORAGE/MARKED_STORAGE（留空按环节+结果取默认）")
    private String disposition;

    @Schema(description = "去向/库位")
    private String location;

    @Schema(description = "是否标记（留空则：有污染标记，无污染不标记）")
    private Boolean marked;

    @Schema(description = "复核备注")
    private String remark;

}
