package cn.iocoder.txgy.module.bidding.enums;

/**
 * 达标核查三档结论（qualification_compliance_review.review_result 的 code 取值）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-6（PASS/RISK/FAIL 三档）；
 * 结论由 15-3/15-4/15-5 要素时点聚合对照 15-2 标准生成（同 §十五“要素聚合”），element_snapshot 留证可复算。
 */
public enum QualificationReviewResultEnum {
    PASS, RISK, FAIL
}
