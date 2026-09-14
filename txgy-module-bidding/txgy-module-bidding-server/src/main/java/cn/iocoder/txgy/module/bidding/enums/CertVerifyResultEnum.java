package cn.iocoder.txgy.module.bidding.enums;

/**
 * 验真结论（cert_verify_history.verify_result 的 code 取值）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §17.5（MATCH/MISMATCH/UNAVAILABLE）；
 * GB/T 36906—2018 §6.4 verifyinfo result：0=校验正确→MATCH；1/2/3（未激活/暂时失效/已失效）→MISMATCH；
 * 4（信息不全）/5（查无此信息）→UNAVAILABLE。口径呼应《03-字段口径映射与差异建议.md》§3.4。
 */
public enum CertVerifyResultEnum {
    /** 与官方底账一致 */
    MATCH,
    /** 与官方底账不一致/状态异常 */
    MISMATCH,
    /** 不可得（信息不全/查无此信息，无法判定） */
    UNAVAILABLE
}
