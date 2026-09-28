package cn.iocoder.txgy.module.bidding.enums;

/**
 * 证照变更日志类型（cert_change_logs.change_type 的 code 取值）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §13 change_type ENUM（CREATE/UPDATE/STATUS_CHANGE/REVOKE）；
 * code 存储（《招投标模块开发规范》§5 三-7）。
 * <p>设计遵循/针对：吊销/注销类状态迁记录为 REVOKE，其余状态迁记录为 STATUS_CHANGE（v3.3 §13 口径）。
 */
public enum CertificateChangeTypeEnum {

    /** 建档（创建证照台账） */
    CREATE,
    /** 字段变更 */
    UPDATE,
    /** 状态流转（ACTIVE→SUSPENDED/EXPIRED 等） */
    STATUS_CHANGE,
    /** 吊销/注销 */
    REVOKE
}
