package cn.iocoder.txgy.module.crm.controller.admin.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "管理后台 - CRM AI 意图识别 Response VO")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CrmAiClassifyRespVO {

    @Schema(description = "大模型输出的 JSON 原文（含 intent、name、month、summary 等字段），由前端解析",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

}
