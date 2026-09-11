package cn.iocoder.txgy.module.bidding.enums;

/**
 * 安许整改宽限原因（qualification_safety_permit.grace_reason 的取值来源）。
 *
 * <p>规范出处：用户业务口径"法定代表人变更导致仅剩 1 名 A 证，给 180 天宽限"；
 * 《电子资质七大模块技术方案.md》§1.2（safety.abc.grace.reason.whitelist：允许触发宽限的原因白名单）。
 * <p>为什么宽限要限定原因白名单：宽限会让不达标企业继续参与投标，若不限制原因，
 * 宽限就成了"无限期豁免"的后门；白名单把宽限限定在"非企业主观过错"的情形。
 * <p>业务目标：宽限可给、可查、可收回，且不能成为逃避整改的通道。
 */
public enum SafetyGraceReasonEnum {

    /** 法定代表人变更（原持证人离任，A 类人数临时不足） */
    LEGAL_PERSON_CHANGE,
    /** 人员集中离职（非主观过错，需附证明材料） */
    MASS_RESIGNATION,
    /** 行政办理在途（已提交但发证机关未办结，需附受理凭证） */
    AUTHORITY_PENDING;
}
