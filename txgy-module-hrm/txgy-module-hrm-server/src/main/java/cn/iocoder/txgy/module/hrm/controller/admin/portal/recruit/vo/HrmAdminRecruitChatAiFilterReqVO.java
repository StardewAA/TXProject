package cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 招聘沟通 AI 筛选 Request VO")
@Data
public class HrmAdminRecruitChatAiFilterReqVO {

    @Schema(description = "招聘方的自然语言筛选要求", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "帮我找户籍地是北京且是985大学毕业的")
    @NotBlank(message = "请输入筛选要求")
    @Size(max = 200, message = "筛选要求不能超过 200 个字符")
    private String query;

    /** 同一句话重复提交时复用同一个键，避免重复触发模型调用 */
    @Schema(description = "幂等键", requiredMode = Schema.RequiredMode.REQUIRED, example = "agent-1a2b3c")
    @NotBlank(message = "幂等键不能为空")
    private String idempotencyKey;
}
