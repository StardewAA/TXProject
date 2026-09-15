package cn.iocoder.txgy.module.bidding.service.certificate;

import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertChangeLogDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertChangeLogMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

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

    /** 单行记录哈希（SHA-256，公式同 v3.3 §13）。 */
    public static String recordHash(Long certId, String fieldName, String oldValue, String newValue,
                                    String changeType, String source, String opIp, String batchId, String prev) {
        return sha256(certId + "|" + fieldName + "|" + oldValue + "|" + newValue + "|"
                + changeType + "|" + source + "|" + opIp + "|" + batchId + "|" + prev);
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
