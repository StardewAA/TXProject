package cn.iocoder.txgy.module.hrm.controller.admin.recruit.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 招聘 Agent 按应聘记录提交 Request VO")
@Data
public class HrmRecruitAgentApplicationReqVO {

    @Schema(description = "应聘记录编号，Agent 结论与结果都关联到该应聘记录")
    @NotNull(message = "应聘记录编号不能为空")
    private Long applicationId;

    @Schema(description = "幂等键，重复提交返回同一任务，不重复调用模型；重新分析请换新键")
    @NotBlank(message = "幂等键不能为空")
    @Size(max = 64, message = "幂等键不能超过 64 个字符")
    private String idempotencyKey;
}
