package cn.iocoder.txgy.module.bidding.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 企业材料完备性 · 逐项状态。
 *
 * <p>规范出处：《招投标模块开发规范》§4.7（枚举集中定义、经 meta 字典下发）；
 * 取值逐字来自 {@code EnterpriseMaterialCompletenessRespVO.Item#status} 的 @Schema 说明
 * （"状态：READY 齐备/EXPIRING 临近到期/MISSING 缺失/EXPIRED 已过期"）。
 *
 * <p>为什么这样写：与总体结论同理，逐项状态此前是裸 String，没有字典与 meta 下发，
 * 前端不得不硬编码四种中文与颜色。固化为枚举后，标签/颜色/排序全部可后台配置。
 *
 * <p>业务目标：材料逐项明细能显示"齐备/临近到期/缺失/已过期"及颜色，且 EXPIRING/EXPIRED 的
 * 判定窗口由规则配置（企业材料到期预警天数）驱动，展示与判定口径一致。
 */
@Getter
@AllArgsConstructor
public enum EnterpriseMaterialStatusEnum {

    /** 齐备：材料在册且未临近到期 */
    READY("齐备", "success"),
    /** 临近到期：进入预警窗口 */
    EXPIRING("临近到期", "warning"),
    /** 缺失：未匹配到任何在册材料 */
    MISSING("缺失", "danger"),
    /** 已过期：有效期止早于基准日 */
    EXPIRED("已过期", "danger");

    /** 中文标签（字典种子的 label 来源） */
    private final String label;
    /** 前端标签颜色（字典种子的 color_type 来源） */
    private final String tagType;
}
