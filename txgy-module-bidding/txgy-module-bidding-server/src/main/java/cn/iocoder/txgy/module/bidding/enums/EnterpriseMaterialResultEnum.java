package cn.iocoder.txgy.module.bidding.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 企业材料完备性 · 总体结论。
 *
 * <p>规范出处：《招投标模块开发规范》§4.7（枚举集中定义、经 meta 字典下发，前端不硬编码）；
 * 取值逐字来自 {@code EnterpriseMaterialCompletenessRespVO#result} 的 @Schema 说明
 * （"总体结论：PASS 齐备/RISK 部分缺失或临近到期/FAIL 必备材料缺失"）。
 *
 * <p>为什么这样写：该字段此前只是 VO 上的裸 String，字典表与 meta 接口都没有它——
 * 前端拿不到标签与颜色，只能自己写死中文，违反"枚举与阈值一律动态配置"的硬约束。
 * 现把它固化为枚举：既作为/ meta/dicts 的兜底下发源，也作为 bidding_dict_data 种子的取值依据。
 *
 * <p>业务目标：材料完备性自查卡片（企业资质页）能按结论显示"齐备/有风险/不齐备"与对应颜色，
 * 且文案与阈值由后台配置驱动，运营改字典即改展示。
 */
@Getter
@AllArgsConstructor
public enum EnterpriseMaterialResultEnum {

    /** 齐备：必备材料全部在有效期内 */
    PASS("齐备", "success"),
    /** 部分缺失或临近到期：可投标但需提示风险 */
    RISK("部分缺失或临近到期", "warning"),
    /** 必备材料缺失：不满足投标条件 */
    FAIL("必备材料缺失", "danger");

    /** 中文标签（字典种子的 label 来源） */
    private final String label;
    /** 前端标签颜色（字典种子的 color_type 来源） */
    private final String tagType;
}
