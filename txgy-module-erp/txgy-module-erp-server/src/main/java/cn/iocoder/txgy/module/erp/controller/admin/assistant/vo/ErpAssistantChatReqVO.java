package cn.iocoder.txgy.module.erp.controller.admin.assistant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - ERP 智能助手对话 Request VO")
@Data
public class ErpAssistantChatReqVO {

    @Schema(description = "用户输入的自然语言", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "查一下 XSDD-2026-0003")
    @NotBlank(message = "请输入内容")
    @Size(max = 200, message = "提问内容不能超过 200 个字符")
    private String message;

}
