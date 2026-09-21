package cn.iocoder.txgy.module.wms.controller.admin.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - WMS AI 智能识别 Request VO")
@Data
public class WmsAiRecognizeReqVO {

    @Schema(description = "用户自然语言指令（文字与清单图片至少提供其一）",
            example = "采购入库 200 米 预制直埋保温管 DN200 到 金岩原辅材料库，供应商河北华美")
    private String text;

    @Schema(description = "清单图片列表（base64 data URL，如 data:image/jpeg;base64,....；"
            + "支持打印/电子表格/手写清单，可传多张按顺序识别）")
    private List<String> images;

    @Schema(description = "会话标识：悬浮球同一轮连续对话传同一个值，未传则按登录用户隔离会话",
            example = "ai-m2xk9f1q")
    private String sessionId;

}
