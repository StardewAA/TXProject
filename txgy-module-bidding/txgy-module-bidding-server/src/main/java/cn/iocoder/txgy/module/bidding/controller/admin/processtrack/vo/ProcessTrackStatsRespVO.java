package cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo;

// [契约]《后端变更请求-投标流程跟踪管理端总览-2026-09-18.md》§5 接口2
// ongoing = 意向投标 + 资质审核 + 待投标 三态之和（与页面 KPI 卡口径一致）

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Schema(description = "管理后台 - 投标流程跟踪 KPI 统计")
@Data
public class ProcessTrackStatsRespVO {

    @Schema(description = "公司数（租户数）")
    private Long tenantCount;
    @Schema(description = "项目总数（跟踪行数）")
    private Long projectCount;
    @Schema(description = "进行中（意向+审核+待投标 三态之和）")
    private Long ongoing;
    @Schema(description = "已投标")
    private Long bidded;
    @Schema(description = "已结束（流程结束）")
    private Long terminated;
    @Schema(description = "负责人数（去重）")
    private Long ownerCount;
    @Schema(description = "按阶段分布 {INTENTION: n, ...}")
    private Map<String, Long> byState;
}
