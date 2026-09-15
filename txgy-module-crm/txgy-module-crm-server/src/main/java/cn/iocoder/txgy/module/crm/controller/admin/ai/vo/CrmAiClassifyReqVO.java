package cn.iocoder.txgy.module.crm.controller.admin.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "管理后台 - CRM AI 意图识别 Request VO")
@Data
public class CrmAiClassifyReqVO {

    @Schema(description = "用户提问内容", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "今日待办要联系的客户有哪些")
    @NotBlank(message = "提问内容不能为空")
    private String query;

    @Schema(description = "最近几轮对话记录，供模型理解追问的上下文（形如「用户：xxx / 助手：xxx」的多行文本）",
            example = "用户：我的下属都有哪些客户\n助手：已统计到 12 位下属名下共 26 个客户")
    private String history;

}
