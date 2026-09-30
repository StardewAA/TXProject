package cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 审查报告出参。 */
@Data
public class BidReviewReportRespVO {

    private Long id;
    private Long taskId;
    private String reportNo;
    private String verdict;
    private BigDecimal scoreTotal;
    private String scoreJson;
    private String issueSummaryJson;
    private String conclusion;
    private String htmlContent;
    private Long pdfFileId;
    private Long docxFileId;
    private Boolean aiGenerated;
    private LocalDateTime createTime;
    /** 报告页脚提示（内容固定，避免前端各写各的） */
    private String disclaimer;
}
