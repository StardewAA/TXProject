package cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 逐项确认优化建议并固化为人简历新版本。
 *
 * <p>服务端只读取「原值」在源版本中的位置做替换，不会改动源版本本身；
 * 确认结果固化成一个新版本，源版本与历史引用保持不变。</p>
 */
@Schema(description = "员工端 - 简历优化确认 Request VO")
@Data
public class HrmPortalResumeOptimizeApplyReqVO {

    @Schema(description = "被优化的源简历版本编号")
    @NotNull(message = "源简历版本编号不能为空")
    private Long sourceVersionId;

    @Schema(description = "产出这些建议的优化任务编号，用于追溯")
    @NotNull(message = "优化任务编号不能为空")
    private Long taskId;

    @Schema(description = "版本修改摘要")
    @Size(max = 500, message = "修改摘要不能超过 500 个字符")
    private String changeSummary;

    @Schema(description = "幂等键，重复提交不会生成多个版本")
    @NotBlank(message = "幂等键不能为空")
    @Size(max = 64, message = "幂等键不能超过 64 个字符")
    private String idempotencyKey;

    @Schema(description = "逐项确认结果，至少一项")
    @NotEmpty(message = "至少需要一项确认结果")
    @Valid
    private List<Decision> decisions;

    @Schema(description = "单项确认结果")
    @Data
    public static class Decision {

        @Schema(description = "所属模块")
        @Size(max = 32, message = "模块名过长")
        private String module;

        @Schema(description = "字段名")
        @NotBlank(message = "字段名不能为空")
        @Size(max = 64, message = "字段名过长")
        private String fieldPath;

        @Schema(description = "原值，必须与源版本内容完全一致")
        @NotBlank(message = "原值不能为空")
        @Size(max = 2000, message = "原值过长")
        private String originalValue;

        @Schema(description = "建议值")
        @Size(max = 2000, message = "建议值过长")
        private String suggestedValue;

        @Schema(description = "最终值：接受时为建议值，拒绝时为原值，手工调整时为用户填写内容")
        @NotBlank(message = "最终值不能为空")
        @Size(max = 2000, message = "最终值过长")
        private String finalValue;

        @Schema(description = "采纳状态：ACCEPT 接受、REJECT 拒绝、ADJUST 手工调整")
        @NotBlank(message = "采纳状态不能为空")
        private String decision;
    }
}
