package cn.iocoder.txgy.module.bidding.enums;

/**
 * 人员级风险标签（bidding_personnel_profile.person_risk_flag 的 code 取值）。
 *
 * <p>规范出处：用户业务口径"人员 12 个月内更换受聘企业 ≥4 次，打一年异常标签"，
 * 且明确"企业级 ent_risk_flag 与人员级 person_risk_flag 分离"；
 * 《资质管理/06-…-2026-09-10.md》§二-2（附录A §3.1）。
 * <p>为什么必须与企业级分离：企业级回答"这家企业能不能投标"，人员级回答"这名人员能不能上岗"；
 * 混在一列会导致两种处置互相覆盖（企业进宽限期时人员异常被掩盖），也无法分别授权查看。
 * <p>业务目标：挂证/职业化挂靠人员可被单独识别并阻断其上岗与配证。
 */
public enum PersonRiskFlagEnum {

    /** 正常 */
    NORMAL,
    /** 一年异常（窗口内更换受聘企业次数达阈值） */
    ABNORMAL,
    /** 频繁异常（多次触发异常，趋势性标签） */
    FREQUENT;

    /** 是否属于"异常"（上岗/配证拦截判断使用）。 */
    public boolean isAbnormal() {
        return this != NORMAL;
    }
}
