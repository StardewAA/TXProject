package cn.iocoder.txgy.module.bidding.service.certificate;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertVerifyHistoryPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertVerifyHistoryRespVO;

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
     * 2) 幂等：先按"滑动窗口内最近一次验真"复用既有结论（窗口长度取配置 cert.verify.idempotent.window.seconds）；
     *    并发由唯一键 uk_verify_idem 兜底，键形如 cert_id:channel:<按窗口取整的桶起点秒>；
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
     * 幂等窗口与键口径同 verifyCertificate，键以 FILE: 前缀与 verifyinfo 隔离。无生效文件报文件不存在。
     * 返回验真历史主键。
     */
    Long verifyCertificateFile(Long certId, String verifyChannel, Long verifyBy);

    /**
     * 批量验真（五页改造契约 Q2，2026-09-28）：certIds[] 逐条调用 verifyCertificate——
     * 单条失败（不存在/验真中/通道未就绪）记入该行结果，不影响其余；返回行级回执。
     */
    cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertVerifyBatchRespVO
            verifyCertificateBatch(java.util.List<Long> certIds, String verifyChannel, Long verifyBy);

    /**
     * 验真历史分页查询（管理端追溯）。
     *
     * <p>为什么需要它：两个验真接口每次都返回"验真历史主键"，但此前没有读出口 —— 留痕只进不出，
     * 事后追溯只能查库。本方法补上读出口；入参只做等值过滤（证照/渠道/结论），按验真时间倒序。
     * L3 报文原文不下发（含可识别信息），仅以布尔位提示"是否留痕"。
     */
    PageResult<CertVerifyHistoryRespVO> getVerifyHistoryPage(CertVerifyHistoryPageReqVO reqVO);
}
