package cn.iocoder.txgy.module.bidding.enums;

/**
 * 通知发件箱业务类型（cert_notify_outbox.biz_type 的 code 取值）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §十一 表12（ALERT/CHECK_REMIND/REVIEW_REMIND）。
 */
public enum CertNotifyBizTypeEnum {
    /** 预警通知（关联 cert_alert_records.alert_id） */
    ALERT,
    /** 自查提醒 */
    CHECK_REMIND,
    /** 审查/复核提醒 */
    REVIEW_REMIND
}
