package cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI 结构化抽取结果（管理端触发 ai-extract 端点返回）。
 */
@Schema(description = "管理后台 - 外部公告 AI 抽取结果 VO")
@Data
public class NoticeExternalAiExtractRespVO {

    @Schema(description = "公告 id")
    private Long id;

    @Schema(description = "AI 抽取状态：SUCCESS/FAILED")
    private String aiExtractionStatus;

    @Schema(description = "结构化字段数")
    private Integer metaFieldCount;

    @Schema(description = "全文转写长度（字符数，无转写为 0）")
    private Integer fullTextLength;

}
