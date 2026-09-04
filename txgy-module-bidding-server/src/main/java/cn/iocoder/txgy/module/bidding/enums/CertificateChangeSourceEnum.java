package cn.iocoder.txgy.module.bidding.enums;

/**
 * 证照变更操作来源（cert_change_logs.op_source 的 code 取值）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §13 op_source ENUM（MANUAL/IMPORT/OCR/API_SYNC/SYSTEM）。
 * <p>设计遵循/针对：与数据来源（CertificateSourceChannelEnum）区分——本枚举含 SYSTEM（定时任务/系统内部流转），
 * 用于审计溯源（等保 2.0 三级）；code 存储（开发规范 §5 三-7）。
 */
public enum CertificateChangeSourceEnum {

    /** 人工操作 */
    MANUAL,
    /** 批量导入 */
    IMPORT,
    /** OCR 识别 */
    OCR,
    /** 外部接口同步 */
    API_SYNC,
    /** 系统内部（定时任务等） */
    SYSTEM
}
