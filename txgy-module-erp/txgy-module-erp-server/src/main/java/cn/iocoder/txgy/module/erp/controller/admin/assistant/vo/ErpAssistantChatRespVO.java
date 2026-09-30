package cn.iocoder.txgy.module.erp.controller.admin.assistant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Schema(description = "管理后台 - ERP 智能助手对话 Response VO")
@Data
@Accessors(chain = true)
public class ErpAssistantChatRespVO {

    @Schema(description = "回答内容（纯文本，含真实业务数据）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String answer;

    @Schema(description = "命中的意图，用于前端做展示或调试",
            example = "SALE_ORDER / PURCHASE_ORDER / PRODUCT / PARTNER / STOCK / SALE_STAT / PURCHASE_STAT / TODO / HELP")
    private String intent;

    @Schema(description = "可跳转的入口")
    private List<ErpAssistantActionVO> actions;

}
