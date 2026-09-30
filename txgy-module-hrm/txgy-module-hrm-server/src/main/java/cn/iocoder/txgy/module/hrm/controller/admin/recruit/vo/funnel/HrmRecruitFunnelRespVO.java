package cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.funnel;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * HRM 招聘漏斗 Response VO
 *
 * <p>按职位统计「投递 → 筛选通过 → 进入面试 → 终面通过 → Offer 发出 → 已入职」各阶段人数与转化率，
 * 阶段是否到达依据应聘状态流转日志（hrm_recruit_application_log），另以当前状态兜底。</p>
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - HRM 招聘漏斗 Response VO")
@Data
public class HrmRecruitFunnelRespVO {

    @Schema(description = "招聘职位编号")
    private Long postId;

    @Schema(description = "招聘职位名称")
    private String postName;

    @Schema(description = "投递人数")
    private Long applicationCount;

    @Schema(description = "筛选通过人数")
    private Long screeningPassedCount;

    @Schema(description = "进入面试人数")
    private Long interviewCount;

    @Schema(description = "终面通过人数")
    private Long finalPassedCount;

    @Schema(description = "Offer 已发人数")
    private Long offerSentCount;

    @Schema(description = "已入职人数")
    private Long onboardedCount;

    @Schema(description = "筛选通过率（%，相对投递）")
    private BigDecimal screeningRate;

    @Schema(description = "面试进入率（%，相对筛选通过）")
    private BigDecimal interviewRate;

    @Schema(description = "Offer 发放率（%，相对终面通过）")
    private BigDecimal offerRate;

    @Schema(description = "入职转化率（%，相对投递）")
    private BigDecimal onboardRate;

    @Schema(description = "平均入职耗时（天，投递→入职）")
    private BigDecimal avgOnboardDays;

}
