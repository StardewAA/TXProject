package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 企业资质看板统计出参（五页改造缺接口契约 Q5，2026-09-18）。
 *
 * <p>规范出处：Q5 契约（原型卡片：资质总数/正常/临期预警/异常过期/未关联人员；推荐出参 Q5a）。
 * <p>口径说明（Q5b 二选一）：{@code unlinkedStaff} = 「无绑定人员的资质数」（本企业主体下，
 * qualification_staff_config 中没有任何人员配置行的资质主档数）；不是「未关联任何资质的人员数」
 * （后者属员工域统计口径，与本接口的企业资质看板语义不符）。
 * <p>为什么这样写：看板数字与列表条数同源（同 entId 同 deleted 过滤），验收标准 §7.2 要求
 * “看板数字与列表条数一致”——直接复用 page 同款查询条件，不另起口径。
 */
@Data
public class QualificationStatsRespVO {

    @Schema(description = "资质总数", example = "12")
    private Long total;

    @Schema(description = "正常（VALID）", example = "8")
    private Long valid;

    @Schema(description = "临期预警（EXPIRING_SOON）", example = "2")
    private Long expiring;

    @Schema(description = "异常过期（EXPIRED）", example = "1")
    private Long expired;

    @Schema(description = "未关联人员（无任何 qualification_staff_config 行的资质数）", example = "3")
    private Long unlinkedStaff;

    // ===== 前端已发布契约字段（2026-09-28 对账补齐）=====
    @Schema(description = "按 qualStatus 逐码计数（前端契约字段名；六态全量，未出现补 0；前端不硬编码状态集合）")
    private Map<String, Long> byStatus;
}
