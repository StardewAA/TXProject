package cn.iocoder.txgy.module.mes.controller.admin.set.pollutioncheck.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - MES 安全环保检测-污染判定记录 Response VO")
@Data
public class MesSetPollutionCheckRespVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "记录编号")
    private String recordNo;

    @Schema(description = "环节")
    private String stage;

    @Schema(description = "关联单号")
    private String bizNo;

    @Schema(description = "批次号")
    private String batchNo;

    @Schema(description = "物料/产品编码")
    private String itemCode;

    @Schema(description = "物料/产品名称")
    private String itemName;

    @Schema(description = "规格")
    private String itemSpec;

    @Schema(description = "AI 初筛结果：CLEAN/POLLUTED/UNCERTAIN")
    private String aiResult;

    @Schema(description = "AI 置信度(%)")
    private Integer aiConfidence;

    @Schema(description = "AI 判定依据")
    private String aiReason;

    @Schema(description = "AI 推荐存储方法")
    private String suggestedStorage;

    @Schema(description = "人工复核结果：CLEAN/POLLUTED，空=待复核")
    private String reviewResult;

    @Schema(description = "最终存储方法")
    private String storageMethod;

    @Schema(description = "处置方式")
    private String disposition;

    @Schema(description = "去向/库位")
    private String location;

    @Schema(description = "是否标记")
    private Boolean marked;

    @Schema(description = "复核人")
    private String reviewBy;

    @Schema(description = "复核时间")
    private LocalDateTime reviewTime;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
