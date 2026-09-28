package cn.iocoder.txgy.module.bidding.controller.admin.safety.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 安许风险条目工单分页查询入参（五页改造缺接口契约 Q18，2026-09-28）。
 *
 * <p>规范出处：Q18 契约——入参 riskLevel/status/assigneeId/keyword/分页。
 */
@Schema(description = "管理后台 - 安许风险条目工单分页入参")
@Data
@EqualsAndHashCode(callSuper = true)
public class SafetyRiskItemPageReqVO extends PageParam {

    @Schema(description = "影响企业主体 id（可空=全部）", example = "1")
    private Long entId;

    @Schema(description = "风险等级 code：LOW/MEDIUM/HIGH/MAJOR（字典 risk_level）", example = "HIGH")
    private String riskLevel;

    @Schema(description = "工单状态 code：PENDING/ASSIGNED/HANDLING/HANDLED/IGNORED（字典 risk_item_status）", example = "PENDING")
    private String status;

    @Schema(description = "责任人 id（可空=全部）", example = "1024")
    private Long assigneeId;

    @Schema(description = "关键字：风险描述/证照快照 模糊匹配", example = "安许")
    private String keyword;
}
