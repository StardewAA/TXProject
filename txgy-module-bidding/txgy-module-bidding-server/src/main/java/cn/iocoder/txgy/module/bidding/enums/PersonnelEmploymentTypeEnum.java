package cn.iocoder.txgy.module.bidding.enums;

/**
 * 用工形式（bidding_personnel_profile.employment_type 的 code 取值）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-3（`is_full_time` 是否本单位在职=挂靠核查）；
 * 《建筑业企业资质标准》（建市〔2014〕159号）人员指标口径——注册建造师等指标人员须为本企业正式员工。
 * <p>为什么这样写：把"是否全职"从布尔升级为用工形式码，既保留 15-3 的 is_full_time 语义（FULL_TIME 才为真），
 * 又能区分劳务派遣/兼职等高风险形态，供合规核验给出差异化风险提示。
 * <p>业务目标：杜绝"人证分离""挂证"——劳务派遣/兼职人员即使证书齐全，也应在核验中判为风险项。
 */
public enum PersonnelEmploymentTypeEnum {

    /** 全职（本企业正式员工，资质指标可用） */
    FULL_TIME,
    /** 兼职 */
    PART_TIME,
    /** 劳务派遣（劳动关系在派遣单位，属挂靠高风险形态） */
    LABOR_DISPATCH,
    /** 退休返聘 */
    RETIRED_REHIRE,
    /** 其他 */
    OTHER;

    /** 是否可计入资质人员指标（等价于 15-3 is_full_time=TRUE 口径）。 */
    public static boolean isFullTime(String code) {
        return FULL_TIME.name().equals(code);
    }

    /** 是否属于挂靠/人证分离高风险用工形式。 */
    public static boolean isOutsourceRisk(String code) {
        return LABOR_DISPATCH.name().equals(code) || PART_TIME.name().equals(code);
    }
}
