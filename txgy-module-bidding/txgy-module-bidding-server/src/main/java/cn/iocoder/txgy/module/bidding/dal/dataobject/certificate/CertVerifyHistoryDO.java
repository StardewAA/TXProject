package cn.iocoder.txgy.module.bidding.dal.dataobject.certificate;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 验真历史表（T 轨 · 只追加 · 幂等防重）。表：cert_verify_history（DDL 见 sql/history/2026-09-06-cert-verify-history.sql）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §17.5（必建；主表 verification_status 仅承载最近一次状态，
 * 历史调用落本表 + 表 11 usage_type=VERIFY 形成验真调用链审计）；GB/T 36906—2018 §6.4/§6.5（verifyinfo/verifyfile）。
 * <p>设计遵循/针对：request/result_snapshot 为 JSON 原文留痕（L3，报文不丢不改）；idempotent_key 按
 * cert_id:channel:yyyyMMddHHmm 生成（v3.3 §17.5 UNIQUE 防重复调用写入，同一分钟内重试幂等）。
 */
@TableName("cert_verify_history")
@Data
@EqualsAndHashCode(callSuper = true)
public class CertVerifyHistoryDO extends TenantBaseDO {

    /** 主键（雪花 ID；=设计 verify_id） */
    private Long id;
    /** 被验真证照 certificate_info.id */
    private Long certId;
    /** 验真渠道 code：FOUR_PLATFORM/MINISTRY/PROVINCE/THIRD_PARTY */
    private String verifyChannel;
    /** 验真结论 code：MATCH/MISMATCH/UNAVAILABLE */
    private String verifyResult;
    /** 请求参数快照（JSON 字符串，L3） */
    private String requestSnapshot;
    /** 返回结果快照（JSON 字符串，L3） */
    private String resultSnapshot;
    /** 发起人（含 SYSTEM） */
    private Long verifyBy;
    /** 验真完成时间 */
    private LocalDateTime verifiedAt;
    /**
     * 幂等键：{@code cert_id:channel:<窗口桶起点秒>}——**仅作并发兜底**（唯一键 uk_verify_idem）。
     *
     * <p>幂等的主判定是"滑动窗口内最近一次验真"（见 {@code CertVerifyHistoryMapper#selectLatestWithinWindow}）：
     * 窗口长度来自配置 {@code cert.verify.idempotent.window.seconds}，因此键里放的是
     * 「按配置窗口取整的桶起点」而不是固定分钟，改配置后兜底键与主判定口径保持一致。
     */
    private String idempotentKey;
}
