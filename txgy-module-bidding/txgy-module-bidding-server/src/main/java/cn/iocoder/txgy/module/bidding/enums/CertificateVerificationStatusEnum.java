package cn.iocoder.txgy.module.bidding.enums;

/**
 * 证照验真状态（certificate_info.verification_status 的 code 取值）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §二（verification_status 与 cert_status 相互独立，
 * 验真口径以官方平台底账为准；自查不认 INVALID）；对接外部验真的结果说明见 GB/T 36906—2018 §6.4/6.5（result 0~5）。
 * <p>设计遵循/针对：验真动作与台账状态解耦——验真只改本状态与 last_verified_at/verify_api_source，
 * 不直接改动 cert_status；code 存储（开发规范 §5 三-7）。
 */
public enum CertificateVerificationStatusEnum {

    /** 未验真（默认） */
    UNVERIFIED,
    /** 验真中（异步/外部接口未返回） */
    VERIFYING,
    /** 验真通过（信息正确且有效） */
    VERIFIED,
    /** 验真不通过/查无此证（对照 36906 §6.4 result≠0 与查无信息） */
    INVALID
}
