package cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "员工端 - 结束招聘沟通 Request VO")
@Data
public class HrmPortalRecruitChatEndReqVO {

    @Schema(description = "会话编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "会话编号不能为空")
    private Long conversationId;

    @Schema(description = "结束原因", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "结束沟通必须填写原因")
    @Size(max = 255, message = "结束原因不能超过 255 个字符")
    private String reason;
}
