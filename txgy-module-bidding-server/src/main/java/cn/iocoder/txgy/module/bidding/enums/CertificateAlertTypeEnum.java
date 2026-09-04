package cn.iocoder.txgy.module.bidding.enums;

/**
 * 预警类型（cert_alert_config/records.alert_type 的 code 取值）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §九（EXPIRY 到期/REVIEW 年检/RENEWAL 续期）；
 * 第五节联动：enterprise_qualification 到期复用本枚举并扩展 QUAL_EXPIRY（v3.3 §十五-到期预警复用）。
 */
public enum CertificateAlertTypeEnum {
    /** 到期预警（valid_to） */
    EXPIRY,
    /** 年检预警（next_review_date） */
    REVIEW,
    /** 续期预警 */
    RENEWAL
}
