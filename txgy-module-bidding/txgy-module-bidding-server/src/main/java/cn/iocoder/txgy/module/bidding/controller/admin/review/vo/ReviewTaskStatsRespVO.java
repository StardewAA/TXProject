package cn.iocoder.txgy.module.bidding.controller.admin.review.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 平台审查任务统计 Response VO（契约 §5 接口 3 出参：{ pending, todayNew, approved, rejected }）。
 *
 * <p>规范出处：《后端变更请求-平台审查任务中心-2026-09-20》§5。
 * <p>为什么单独一个 VO 而不是 Map：字段是固定四项，用 VO 能与前端 TS 逐字对齐、且契约变更时编译器会提醒。
 * <p>业务目标：审查中心顶部四个数字卡（待审查/今日新增/已通过/已驳回）；租户视角自动限定本企业。
 *
 * @author txgy
 */
@Schema(description = "平台审查任务统计 Response VO")
@Data
public class ReviewTaskStatsRespVO {

    @Schema(description = "待审查数", example = "12")
    private Long pending;

    @Schema(description = "今日新增数", example = "3")
    private Long todayNew;

    @Schema(description = "已通过数", example = "40")
    private Long approved;

    @Schema(description = "已驳回数", example = "5")
    private Long rejected;

}
