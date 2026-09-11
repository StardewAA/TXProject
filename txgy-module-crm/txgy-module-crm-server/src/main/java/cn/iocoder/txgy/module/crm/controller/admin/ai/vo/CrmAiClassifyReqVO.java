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

}
