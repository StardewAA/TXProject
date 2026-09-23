package cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "员工端 - 招聘 Agent 提交 Request VO")
@Data
public class HrmPortalRecruitAgentSubmitReqVO {

    @Schema(description = "本人已固化的个人简历版本编号")
    private Long resumeVersionId;

    @Schema(description = "个人简历编号；可为在线简历或附件简历")
    private Long resumeId;

    @Schema(description = "目标职位编号，必须是已发布且招聘中的职位")
    @NotNull(message = "目标职位编号不能为空")
    private Long postId;

    @Schema(description = "幂等键，重复提交返回同一任务，不重复调用模型")
    @NotBlank(message = "幂等键不能为空")
    @Size(max = 64, message = "幂等键不能超过 64 个字符")
    private String idempotencyKey;
}
