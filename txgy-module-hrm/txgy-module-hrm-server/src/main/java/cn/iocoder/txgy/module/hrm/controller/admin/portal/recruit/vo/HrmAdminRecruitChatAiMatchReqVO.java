package cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 招聘沟通 AI 匹配分析 Request VO")
@Data
public class HrmAdminRecruitChatAiMatchReqVO {

    @Schema(description = "沟通会话编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "沟通会话编号不能为空")
    private Long conversationId;

    /** 重新分析时换一个新的幂等键即可；同一个键重复提交不会重复消耗模型额度 */
    @Schema(description = "幂等键", requiredMode = Schema.RequiredMode.REQUIRED, example = "agent-1a2b3c")
    @NotBlank(message = "幂等键不能为空")
    private String idempotencyKey;
}
