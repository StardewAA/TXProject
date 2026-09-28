package cn.iocoder.txgy.module.bidding.service.certificate;

import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertChangeLogDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertChangeLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * 变更审计日志哈希链工具（台账建档/更新/状态流转与验真结果回写共用同一公式，保证链路唯一）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §13：record_hash=SHA-256(cert_id|field_name|old_value|
 * new_value|change_type|op_source|op_ip|batch_id|prev_hash)，prev_hash 首条取 ROOT（§18.3-1 定根）；
 * 只追加、防篡改（等保 2.0 三级），链上 UK(cert_id, prev_hash, …) 防覆盖。
 * <p>设计遵循/针对：纯函数便于建档（多行连续）、验真（单行）两侧复用同一散列规则；
 * 摘要以 UTF-8 十六进制小写输出（64 位）。
 */
public final class CertHashChain {

    /** 链首固定根哈希（§18.3-1） */
    public static final String HASH_CHAIN_ROOT = "ROOT";

    private CertHashChain() {
    }

    /**
     * 在既有链尾后追加一行日志（单行场景；多行场景请自维护 prev 连续调用 {@link #recordHash}）。
     */
    public static void appendRow(CertChangeLogMapper mapper, Long certId, String fieldName, String oldValue,
                                 String newValue, String changeType, String source, String opIp, String batchId) {
        String prev = mapper.selectLastRecordHash(certId);
        if (prev == null) {
            prev = HASH_CHAIN_ROOT;
        }
        CertChangeLogDO log = new CertChangeLogDO();
        log.setCertId(certId);
        log.setFieldName(fieldName);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setChangeType(changeType);
        log.setOpSource(source);
        log.setOpIp(opIp);
        log.setBatchId(batchId);
        log.setPrevHash(prev);
        log.setRecordHash(recordHash(certId, fieldName, oldValue, newValue, changeType, source, opIp, batchId, prev));
        mapper.insert(log);
    }

    /**
     * 单行记录哈希（SHA-256，公式同 v3.3 §13）——**全仓唯一实现**，写入与回验必须都走这里。
     *
     * <p>⚠️ 历史缺陷（2026-09-29 定位）：本方法原先直接字符串拼接，Java 会把 {@code null} 拼成**字面量 "null"**；
     * 而写入方 {@code CertificateServiceImpl.appendChangeLog} 又自行把 opIp/batchId 预归一为空串 ——
     * **同一公式被拆成两处各写一半**，导致回验永远对不上（实测：全部证照首行即断链，
     * 掩盖了"链到底有没有被改"这个真问题）。
     * <p>现口径：**所有字段 null → 空串**，由 {@link #nz} 在此统一收口；
     * 调用方**不得**再自行做 null 处理（否则又会漂）。金标向量见 CertHashChainTest。
     */
    public static String recordHash(Long certId, String fieldName, String oldValue, String newValue,
                                    String changeType, String source, String opIp, String batchId, String prev) {
        return sha256(nz(certId == null ? null : String.valueOf(certId)) + "|" + nz(fieldName) + "|" + nz(oldValue)
                + "|" + nz(newValue) + "|" + nz(changeType) + "|" + nz(source) + "|" + nz(opIp) + "|"
                + nz(batchId) + "|" + nz(prev));
    }

    /** 字段归一：null → 空串（**唯一口径**；写入与回验共用，调用方不得自行预处理）。 */
    private static String nz(String v) {
        return v == null ? "" : v;
    }

    /**
     * **回验整条哈希链**（v3.3 §13 / v3.4 §18.3）——逐行重算 record_hash 并核对 prev_hash 链接。
     *
     * <p>为什么必须做：链只在写入时算，**没有任何回验**时，"防篡改"只是口号 ——
     * 真有人改了中间某一行，除非人工比对否则系统不会发现。本方法把"能验"变成"会验"。
     *
     * <p>口径：按 id 升序（= 追加顺序）遍历；expectedPrev 自 {@link #HASH_CHAIN_ROOT} 起，
     * 逐行校验 ①prev_hash == expectedPrev ②record_hash == 重算值；任一不满足即判**断链**并带上
     * 断链行 id 与期望/实际值（便于定位被改动的那一行）。
     *
     * @return 回验结果（ok=true 表示整条链自洽；否则 brokenAtId 指出第一处断链）
     */
    public static VerifyResult verifyChain(CertChangeLogMapper mapper, Long certId) {
        List<CertChangeLogDO> rows = mapper.selectList(new LambdaQueryWrapper<CertChangeLogDO>()
                .eq(CertChangeLogDO::getCertId, certId)
                .orderByAsc(CertChangeLogDO::getId));
        String expectedPrev = HASH_CHAIN_ROOT;
        int checked = 0;
        for (CertChangeLogDO row : rows) {
            checked++;
            if (!Objects.equals(expectedPrev, row.getPrevHash())) {
                return new VerifyResult(false, checked, row.getId(), "PREV_HASH_MISMATCH",
                        expectedPrev, row.getPrevHash());
            }
            String recalculated = recordHash(row.getCertId(), row.getFieldName(), row.getOldValue(),
                    row.getNewValue(), row.getChangeType(), row.getOpSource(), row.getOpIp(),
                    row.getBatchId(), row.getPrevHash());
            if (!Objects.equals(recalculated, row.getRecordHash())) {
                return new VerifyResult(false, checked, row.getId(), "RECORD_HASH_MISMATCH",
                        recalculated, row.getRecordHash());
            }
            expectedPrev = row.getRecordHash();
        }
        return new VerifyResult(true, checked, null, null, expectedPrev, expectedPrev);
    }

    /** 哈希链回验结果。 */
    public record VerifyResult(boolean ok, int checked, Long brokenAtId, String reason,
                              String expected, String actual) {
    }

    private static String sha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
