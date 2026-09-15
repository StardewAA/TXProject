package cn.iocoder.txgy.module.bidding.enums;

/**
 * 社保缴纳状态（personnel_social_security.pay_status 的 code 取值）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-3（`social_security_status/social_security_tenant`
 * 社保状态与缴纳单位，须与投标主体一致）；《建筑业企业资质管理规定》（住建部令第22号）人员指标核验要求
 * 申报人员须由申报企业缴纳社保。
 * <p>为什么这样写：把"是否在缴"与"缴在哪家单位"分开建模——前者由状态码表达，后者由记录行上的
 * 缴纳单位主体 id 表达，这样才能判定"人证分离/挂证"（证书在本企业、社保在他企业）。
 * <p>业务目标：资质申报与投标前自动核验社保主体一致性，从源头拦截挂证风险。
 */
public enum PersonnelSocialSecurityStatusEnum {

    /** 正常缴纳（唯一满足核验的状态） */
    NORMAL,
    /** 停缴（离职过渡/断缴，核验判不满足） */
    SUSPENDED,
    /** 已转出（社保关系已迁出本单位） */
    TRANSFERRED,
    /** 无记录/未参保 */
    NONE;

    /** 是否满足"在本单位正常缴纳"口径。 */
    public static boolean isNormal(String code) {
        return NORMAL.name().equals(code);
    }
}
