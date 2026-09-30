package cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 正文结构化 Response（人工触发 /body-structure）。
 */
@Schema(description = "管理后台 - 外部公告正文结构化 Response")
@Data
public class NoticeBodyStructureRespVO {

    @Schema(description = "公告 id")
    private Long id;

    @Schema(description = "正文结构化状态：SUCCESS/FAILED")
    private String bodyBlocksStatus;

    @Schema(description = "生效策略码：HTML_STRUCTURE=常规 HTML 解析 / AI_FALLBACK=AI 兜底排版")
    private String bodyParserCode;

    @Schema(description = "结构化块总数")
    private Integer blockCount;

    @Schema(description = "其中表格块数")
    private Integer tableCount;

    @Schema(description = "是否疑似拍平粘连文本（常规解析无表格且有超长粘连段）——提示可用 AI 兜底")
    private Boolean aiRecommended;

}
