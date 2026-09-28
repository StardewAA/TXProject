package cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 问题项出参（证据与定位必须回传，供人工复核）。 */
@Data
public class BidReviewIssueRespVO {

    private Long id;
    private Long taskId;
    /** 所属任务编号 / 任务名称（跨任务「问题复核」页要直接看到"这是哪个任务的问题"，批量解析一次） */
    private String taskNo;
    private String taskName;
    private Long ruleItemId;
    private String ruleCode;
    private String category;
    private String severity;
    private String issueTitle;
    private String issueDesc;
    private String hitLocation;
    private String evidenceText;
    private Boolean evidenceLack;
    private String suggestion;
    private String source;
    private BigDecimal aiConfidence;
    private String status;
    private Long reviewerId;
    /** 复核人名称（由 reviewer_id 解析，页面直接展示，避免前端再打一次用户接口） */
    private String reviewerName;
    private String reviewRemark;
    private LocalDateTime reviewTime;
}
