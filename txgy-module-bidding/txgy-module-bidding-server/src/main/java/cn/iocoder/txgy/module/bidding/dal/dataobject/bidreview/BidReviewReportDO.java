package cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 智慧标书审查报告（表 bidding_bid_review_report）。
 *
 * <p>方案依据：§4.2 第 4 步（REPORT）、§12 第 3 条（报告为内部参考件）。
 * <p>设计遵循/针对：report_no 业务唯一；ai_generated=true 时报告页脚必须标注
 * "AI 生成，需人工复核"，不允许把 AI 结论当正式件对外。
 */
@TableName("bidding_bid_review_report")
@Data
@EqualsAndHashCode(callSuper = true)
public class BidReviewReportDO extends TenantBaseDO {

    private Long id;
    /** 审查任务 id */
    private Long taskId;
    /** 报告编号（业务唯一键） */
    private String reportNo;
    /** 结论 code：PASS/RISK/FAIL */
    private String verdict;
    /** 自评总分（AI 预估） */
    private BigDecimal scoreTotal;
    /** 分项得分 JSON */
    private String scoreJson;
    /** 问题摘要 JSON */
    private String issueSummaryJson;
    /** 总体结论与建议（Markdown） */
    private String conclusion;
    /** 报告正文（HTML，供页面渲染与打印） */
    private String htmlContent;
    /** 报告 PDF file_id */
    private Long pdfFileId;
    /** 报告 Word file_id */
    private Long docxFileId;
    /** 报告 Word 访问地址（生成时快照，便于直接下载/预览） */
    private String docxFileUrl;
    /** 报告模板 code */
    private String templateCode;
    /** 是否 AI 生成 */
    private Boolean aiGenerated;
    /** 乐观锁版本 */
    @Version
    private Integer version;
}
