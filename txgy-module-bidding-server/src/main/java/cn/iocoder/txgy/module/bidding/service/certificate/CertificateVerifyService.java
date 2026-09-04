package cn.iocoder.txgy.module.bidding.service.certificate;

/**
 * 证照验真服务（GB/T 36906 对接业务编排）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §17.5（验真历史必建，主表承载最近一次状态）；
 * GB/T 36906—2018 §6.4/§6.5；《招投标模块开发规范》§6（外部集成）。
 */
public interface CertificateVerifyService {

    /**
     * 对指定证照发起一次验真（verifyinfo）：
     * 1) 验真状态 VERIFYING 中拒绝重复发起（CERT_VERIFY_IN_PROGRESS）；
     * 2) 幂等键 cert_id:channel:yyyyMMddHHmm：同分钟重试先预查直接返回既有历史；并发由 uk 唯一约束兜底；
     * 3) 按回执更新台账 verification_status（VERIFIED/INVALID）与 last_verified_at/verify_api_source，
     *    并落 verification_status 变更审计（哈希链，op_source=API_SYNC，每次验真留一行）；
     * 4) 回执映射：result=0→MATCH/VERIFIED；1/2/3→MISMATCH/INVALID；4/5→UNAVAILABLE（台账状态保持 UNVERIFIED）。
     * 返回验真历史主键。
     */
    Long verifyCertificate(Long certId, String verifyChannel, Long verifyBy);

    /**
     * 对证照“当前生效文件”发起一次文件验真（verifyfile，GB/T 36906 §6.5）：
     * 取 cert_file_versions 当前生效行，以其 SHA-256 为内容指纹入参调用通道；回执映射与台账回写
     * 同 verifyCertificate（0→MATCH/VERIFIED；1/2/3→MISMATCH/INVALID；4/5→UNAVAILABLE 保持），
     * 幂等键 cert_id:channel:FILE:yyyyMMddHHmm（与 verifyinfo 隔离）。无生效文件报文件不存在。
     * 返回验真历史主键。
     */
    Long verifyCertificateFile(Long certId, String verifyChannel, Long verifyBy);
}
