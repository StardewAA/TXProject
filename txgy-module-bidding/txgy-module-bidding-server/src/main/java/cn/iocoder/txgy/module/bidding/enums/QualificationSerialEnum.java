package cn.iocoder.txgy.module.bidding.enums;

/**
 * 企业资质序列（enterprise_qualification.qual_serial 的 code 取值）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-1（资质序列：施工/勘察/设计/监理/检测…）；
 * 国标表 110/111（等级/类别）由主系统字典维护（§17.6 码表口径）。
 */
public enum QualificationSerialEnum {
    /** 施工 */
    CONSTRUCTION,
    /** 勘察 */
    SURVEY,
    /** 设计 */
    DESIGN,
    /** 监理 */
    SUPERVISION,
    /** 检测 */
    TESTING
}
