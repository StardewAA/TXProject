package cn.iocoder.txgy.module.mes.controller.admin.set.pollutionledger.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - MES 安全环保检测-污染/危废暂存台账 Response VO")
@Data
public class MesPollutionLedgerRespVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "来源判定记录ID")
    private Long sourceCheckId;

    @Schema(description = "来源判定记录编号(PC-...)")
    private String sourceRecordNo;

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

    @Schema(description = "处置方式")
    private String disposition;

    @Schema(description = "最终存储方法")
    private String storageMethod;

    @Schema(description = "去向/库位")
    private String location;

    @Schema(description = "是否标记")
    private Boolean marked;

    @Schema(description = "台账状态：STORED(暂存)/PROCESSING(处置中)/REUSED(已回用)/DISCHARGED(已排放)/DISPOSED(已处置)")
    private String status;

    @Schema(description = "最近流转人")
    private String statusBy;

    @Schema(description = "最近流转时间")
    private LocalDateTime statusTime;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
