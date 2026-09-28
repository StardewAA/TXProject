package cn.iocoder.txgy.module.bidding.controller.admin.safety.vo;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 安许风险条目工单处理入参（五页改造缺接口契约 Q18，2026-09-28）。
 *
 * <p>规范出处：Q18 契约——入参 id/status/remark，乐观锁 version。
 * <p>为什么加 @JsonAlias：前端已发布类型 {@code RiskHandleReq} 用的字段名是 {@code targetStatus}
 * （apps/web-antd/src/api/bidding/safetyRisk/index.ts），且该对象**原样作为请求体**发送；
 * 契约字段名为 {@code status}。两者都接受 → 契约不破、前端已发布的按钮不用改就能用。
 * <p>业务目标：处理动作（处理中/已处理/已忽略）一次调用成功，不因字段名分歧报参数校验失败。
 */
@Schema(description = "管理后台 - 安许风险条目工单处理入参")
@Data
public class SafetyRiskItemHandleReqVO {

    @Schema(description = "工单主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "工单不能为空")
    private Long id;

    @Schema(description = "目标状态 code：HANDLING 处理中 / HANDLED 已处理 / IGNORED 已忽略"
            + "（须满足状态迁移表：PENDING→ASSIGNED/HANDLING/HANDLED/IGNORED、ASSIGNED→HANDLING/HANDLED/IGNORED、"
            + "HANDLING→HANDLED/IGNORED；同值提交按幂等放行）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "HANDLED")
    @NotBlank(message = "目标状态不能为空")
    @JsonAlias("targetStatus")
    private String status;

    @Schema(description = "处理备注（HANDLED 建议必填，便于留痕）")
    private String remark;

    @Schema(description = "乐观锁版本", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "版本不能为空")
    private Integer version;
}
