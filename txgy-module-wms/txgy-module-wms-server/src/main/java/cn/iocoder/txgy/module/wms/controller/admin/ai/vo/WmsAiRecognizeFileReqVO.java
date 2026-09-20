package cn.iocoder.txgy.module.wms.controller.admin.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - WMS AI 清单文件识别 Request VO")
@Data
public class WmsAiRecognizeFileReqVO {

    @Schema(description = "用户补充指令（可空）", example = "按这个清单采购入库")
    private String text;

    @Schema(description = "清单文件列表（支持 xlsx/xls/csv/txt/pdf，url 为 base64 data URL）")
    private List<FileDoc> files;

    @Schema(description = "会话标识：悬浮球同一轮连续对话传同一个值，未传则按登录用户隔离会话",
            example = "ai-m2xk9f1q")
    private String sessionId;

    @Schema(description = "清单文件")
    @Data
    public static class FileDoc {

        @Schema(description = "文件名（含扩展名）", example = "入库清单.xlsx")
        private String name;

        @Schema(description = "文件内容 base64 data URL，如 data:application/octet-stream;base64,....")
        private String url;

    }

}
