package cn.iocoder.txgy.module.mes.controller.admin.set.pollutioncheck.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - MES 安全环保检测-污染判定记录 AI 初筛 Response VO")
@Data
public class MesSetPollutionCheckAiRespVO {

    @Schema(description = "AI 初筛结果：CLEAN/POLLUTED/UNCERTAIN", example = "UNCERTAIN")
    private String aiResult;

    @Schema(description = "AI 置信度(%)", example = "85")
    private Integer aiConfidence;

    @Schema(description = "AI 判定依据")
    private String aiReason;

    @Schema(description = "AI 推荐存储方法")
    private String suggestedStorage;

}
