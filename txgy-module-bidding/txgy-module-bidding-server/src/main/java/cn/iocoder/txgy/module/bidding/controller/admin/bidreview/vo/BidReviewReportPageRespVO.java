package cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 审查报告台账行。
 *
 * <p>为什么单独出 VO 而不是复用 {@link BidReviewReportRespVO}：台账要的是"报告 + 它属于哪个任务"
 * 的扁平行（任务编号/任务名称），而报告详情不需要这两列；且台账不能带 htmlContent（几十 KB 正文）。
 */
@Schema(description = "管理后台 - 标书审查报告台账 Response VO")
@Data
public class BidReviewReportPageRespVO {

    private Long id;
    private Long taskId;
    @Schema(description = "任务编号")
    private String taskNo;
    @Schema(description = "任务名称")
    private String taskName;
    @Schema(description = "报告编号")
    private String reportNo;
    @Schema(description = "结论：PASS/RISK/FAIL")
    private String verdict;
    @Schema(description = "是否废标（结论 FAIL 即 true）")
    private Boolean rejected;
    @Schema(description = "自评总分（AI 预估）")
    private BigDecimal scoreTotal;
    @Schema(description = "是否 AI 生成")
    private Boolean aiGenerated;
    @Schema(description = "报告生成时间")
    private LocalDateTime createTime;
    @Schema(description = "是否已生成 Word 文档（决定「下载 Word」按钮是否可点）")
    private Boolean docxReady;
}
