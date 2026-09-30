package cn.iocoder.txgy.module.bidding.controller.admin.safety.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 安许风险条目工单指派入参（五页改造缺接口契约 Q18，2026-09-28）。
 *
 * <p>规范出处：Q18 契约——指派入参 id/assigneeId/remark；乐观锁 version 契约在 handle 明确要求，
 * 指派同样回传更稳：前端 {@code RiskAssignReq} 已带 version，后端据此做 CAS（防基于过期视图指派）。
 */
@Schema(description = "管理后台 - 安许风险条目工单指派入参")
@Data
public class SafetyRiskItemAssignReqVO {

    @Schema(description = "工单主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "工单不能为空")
    private Long id;

    @Schema(description = "责任人 id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "责任人不能为空")
    private Long assigneeId;

    @Schema(description = "指派备注（可选）")
    private String remark;

    @Schema(description = "乐观锁版本（前端回传 row.version；传了则做 CAS，不传则不加版本约束）", example = "0")
    private Integer version;
}
