package cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Schema(description = "员工端 - 简历优化提交 Request VO")
@Data
public class HrmPortalResumeOptimizeReqVO {

    @Schema(description = "本人已固化的个人简历版本编号")
    @NotNull(message = "简历版本编号不能为空")
    private Long resumeVersionId;

    /**
     * 目标职位编号。
     *
     * <p>与简历诊断一致：不传表示优化整份简历本身，不针对任何岗位调整关键词与表述。</p>
     */
    @Schema(description = "目标职位编号；不传表示按简历本身优化，不针对具体岗位")
    private Long postId;

    @Schema(description = "表达方向：CONCISE 简洁、PROFESSIONAL 专业、TECHNICAL 技术、MANAGEMENT 管理")
    @NotBlank(message = "表达方向不能为空")
    private String style;

    @Schema(description = "优化模块；为空表示整份优化")
    private List<String> modules;

    @Schema(description = "幂等键，重复提交返回同一任务，不重复调用模型")
    @NotBlank(message = "幂等键不能为空")
    @Size(max = 64, message = "幂等键不能超过 64 个字符")
    private String idempotencyKey;
}
