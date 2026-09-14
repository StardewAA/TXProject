package cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo;
// 规范依据：设计文档 v0.6 §三；《招投标模块开发规范》§3.1（VO 规约）

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 采集批次 Response")
@Data
public class CrawlJobRespVO {

    @Schema(description = "批次 ID")
    private Long id;
    @Schema(description = "源代码（空=全源）")
    private String sourceCode;
    @Schema(description = "触发类型：SCHEDULE/MANUAL/REPAIR")
    private String triggerType;
    @Schema(description = "范围说明（如按时间窗口）")
    private String scope;
    @Schema(description = "状态：RUNNING/SUCCESS/PARTIAL/FAILED")
    private String status;
    @Schema(description = "汇总 JSON：总数/成功/失败/转人工数")
    private String summaryJson;
    @Schema(description = "结束时间")
    private LocalDateTime finishedAt;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
