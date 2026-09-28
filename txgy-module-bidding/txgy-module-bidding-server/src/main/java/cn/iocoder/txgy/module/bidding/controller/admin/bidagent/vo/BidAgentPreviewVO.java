package cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "标书智能体 - 试跑 VO")
public class BidAgentPreviewVO {

    @Schema(description = "试跑入参（BidAgentPreviewReqVO）")
    @Data
    public static class ReqVO {
        @Schema(description = "任务类型：PARSE/OUTLINE/DRAFT/FILL/REVIEW", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "任务类型不能为空")
        private String taskType;
        @Schema(description = "试跑输入（粘贴一段招标文件片段）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "试跑输入不能为空")
        private String input;
        @Schema(description = "是否用检索结果补充上下文（默认 false，避免试跑受资料质量干扰）")
        private Boolean withRetrieve;
    }

    @Schema(description = "试跑出参（BidAgentPreviewRespVO）")
    @Data
    public static class RespVO {
        private String taskType;
        /** 本次实际使用的提示词来源：BID_AGENT 后台配置 / BUILT_IN 内置基线 */
        private String promptSource;
        /** 模型输出原文（不做二次加工，便于专员判断提示词质量） */
        private String output;
    }
}
