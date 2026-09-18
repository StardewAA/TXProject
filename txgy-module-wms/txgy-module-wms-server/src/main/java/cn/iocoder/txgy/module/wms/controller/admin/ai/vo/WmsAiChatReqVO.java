package cn.iocoder.txgy.module.wms.controller.admin.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Schema(description = "管理后台 - WMS AI 全域问答 Request VO")
@Data
public class WmsAiChatReqVO {

    @Schema(description = "用户关于 WMS 数据的任意提问",
            example = "鸡爪库存还有多少？低于 20 的要提醒", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "提问内容不能为空")
    private String text;

    @Schema(description = "会话标识：同一轮连续对话传同一个值，用于多轮上下文与指代消解；不传则按当前登录用户隔离",
            example = "session-1726300000000")
    private String sessionId;

}
