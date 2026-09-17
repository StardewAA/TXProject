package cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "员工端 - 发送招聘沟通消息 Request VO")
@Data
public class HrmPortalRecruitChatSendReqVO {
    @Schema(description = "会话编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @NotNull(message = "会话编号不能为空")
    private Long conversationId;

    @Schema(description = "内容类型：1 文本，2 简历", example = "1")
    private Integer contentType;

    @Schema(description = "消息内容", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息内容长度不能超过 2000 字")
    private String content;
}
