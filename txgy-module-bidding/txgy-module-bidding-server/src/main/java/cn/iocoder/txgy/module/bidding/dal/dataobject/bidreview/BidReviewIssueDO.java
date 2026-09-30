package cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 智慧标书审查问题项（表 bidding_bid_review_issue）。
 *
 * <p>方案依据：§3（领域模型）、§6.3（输出校验与兜底）。
 * <p>设计遵循/针对：每条问题必须带"原文片段 + 定位"，否则人工无法复核；
 * evidence_lack=true 表示模型给不出证据——按方案不计入高危，避免"看起来成功"。
 */
@TableName("bidding_bid_review_issue")
@Data
@EqualsAndHashCode(callSuper = true)
public class BidReviewIssueDO extends TenantBaseDO {

    private Long id;
    /** 审查任务 id */
    private Long taskId;
    /** 命中规则项 id */
    private Long ruleItemId;
    /** 规则编码 */
    private String ruleCode;
    /** 类别 code */
    private String category;
    /** 严重级别 code：HIGH/MEDIUM/LOW */
    private String severity;
    /** 问题标题 */
    private String issueTitle;
    /** 问题说明 */
    private String issueDesc;
    /** 命中位置（页码/章节/段落） */
    private String hitLocation;
    /** 标书原文片段（证据） */
    private String evidenceText;
    /** 是否证据不足 */
    private Boolean evidenceLack;
    /** 整改建议 */
    private String suggestion;
    /** 来源 code：AI/RULE/MANUAL */
    private String source;
    /** AI 置信度 0~1 */
    private BigDecimal aiConfidence;
    /** 复核状态 code：OPEN/CONFIRMED/IGNORED/FIXED */
    private String status;
    /** 复核人 */
    private Long reviewerId;
    /** 复核意见 */
    private String reviewRemark;
    /** 复核时间 */
    private LocalDateTime reviewTime;
}
