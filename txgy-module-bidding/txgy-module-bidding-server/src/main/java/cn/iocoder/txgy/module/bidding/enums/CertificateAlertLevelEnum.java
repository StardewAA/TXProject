package cn.iocoder.txgy.module.bidding.enums;

/**
 * 预警档位（cert_alert_records.alert_level 的 code 取值）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §九：阈值→档位映射
 * 命中 90/60 天 → INFO、命中 30 天 → WARNING、命中 7 天 → CRITICAL；多档同时命中取最严重一档。
 */
public enum CertificateAlertLevelEnum {
    INFO,
    WARNING,
    CRITICAL
}
