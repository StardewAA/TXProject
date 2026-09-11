package cn.iocoder.txgy.module.bidding.enums;

/**
 * 证照数据来源（certificate_info.source_channel 的 code 取值）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §二 source_channel ENUM（MANUAL/IMPORT/OCR/API_SYNC）；
 * code 存储（《招投标模块开发规范》§5 三-7）。
 * <p>设计遵循/针对：API_SYNC 供后续 GB/T 36906 共享服务对接/四库同步落库使用，与外部核验记录（verify_history）联动。
 */
public enum CertificateSourceChannelEnum {

    /** 手工录入 */
    MANUAL,
    /** 批量导入 */
    IMPORT,
    /** OCR 识别 */
    OCR,
    /** 外部接口同步 */
    API_SYNC
}
