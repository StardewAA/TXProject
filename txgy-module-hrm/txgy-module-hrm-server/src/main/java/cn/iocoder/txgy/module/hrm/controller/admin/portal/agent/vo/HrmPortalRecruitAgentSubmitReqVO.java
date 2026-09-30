package cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "员工端 - 招聘 Agent 提交 Request VO")
@Data
public class HrmPortalRecruitAgentSubmitReqVO {

    @Schema(description = "本人已固化的个人简历版本编号")
    private Long resumeVersionId;

    @Schema(description = "个人简历编号；可为在线简历或附件简历")
    private Long resumeId;

    /**
     * 目标职位编号。
     *
     * <p>这里刻意不加 {@code @NotNull}：简历诊断与简历优化对整份简历本身就成立，
     * 不需要先挑一个职位。传了就按「针对该职位的诊断/优化」走（职位必须已发布且在招），
     * 不传则改用不含职位匹配维度的提示词与校验器。</p>
     *
     * <p>职位匹配与投递前检查**必须**有职位，那两项的必填校验放在各自的
     * Service 方法里做（见 HrmRecruitAgentAnalyzeServiceImpl），
     * 避免为了放开诊断而把这两个场景的约束一起放宽。</p>
     */
    @Schema(description = "目标职位编号；不传表示对整份简历做诊断，不做职位匹配")
    private Long postId;

    @Schema(description = "幂等键，重复提交返回同一任务，不重复调用模型")
    @NotBlank(message = "幂等键不能为空")
    @Size(max = 64, message = "幂等键不能超过 64 个字符")
    private String idempotencyKey;
}
