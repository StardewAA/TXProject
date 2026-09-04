package cn.iocoder.txgy.module.bidding.service.certificate;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.api.cert.vo.EccertReqHeadVO;
import cn.iocoder.txgy.module.bidding.api.cert.vo.EccertRespHeadVO;
import cn.iocoder.txgy.module.bidding.api.cert.vo.EccertVerifyInfoReqVO;
import cn.iocoder.txgy.module.bidding.api.cert.vo.EccertVerifyInfoRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertVerifyHistoryDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertificateInfoDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertChangeLogMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertVerifyHistoryMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertificateInfoMapper;
import cn.iocoder.txgy.module.bidding.enums.CertVerifyChannelEnum;
import cn.iocoder.txgy.module.bidding.enums.CertVerifyResultEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateChangeSourceEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateChangeTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateVerificationStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 证照验真服务实现（T 轨；编排 GB/T 36906 verifyinfo 并留痕）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §17.5（验真历史必建，主表只承载最近一次）；
 * GB/T 36906—2018 §6.4（verifyinfo 报文：data.certificateData 键=GB/T 36903 元数据英文名；回执 data.result：
 * 0=校验正确、1 未激活/2 暂时失效/3 已失效、4 信息不全、5 查无此信息）；§13/§18.3（哈希链审计）。
 * <p>设计遵循/针对：① 结果落账映射——0→MATCH（台账 VERIFIED）；1/2/3→MISMATCH（INVALID）；
 * 4/5→UNAVAILABLE（台账保持 UNVERIFIED，不轻易判假，口径呼应 03-字段口径映射 §3.4）；
 * ② 验真进行中互斥（VERIFYING→CERT_VERIFY_IN_PROGRESS）；③ 幂等键 cert_id:channel:yyyyMMddHHmm：
 * 先预查同分钟记录直接幂等返回，网关回执后写历史，并发/重试由 uk_verify_idempotent 唯一约束兜底；
 * ④ 报文快照 JSON 原文留痕（L3，request/result_snapshot）；
 * ⑤ 结果回写落 verification_status 变更审计（CertHashChain 同一公式，op_source=API_SYNC）。
 */
@Slf4j
@Service
public class CertificateVerifyServiceImpl implements CertificateVerifyService {

    /** 网关侧应用认证标识/令牌（接入真实通道后由配置注入；演示通道不校验） */
    private static final String ACCESS_ID = "bidding-cert-verify";
    private static final String ACCESS_TOKEN = "demo-token";
    private static final DateTimeFormatter KEY_MINUTE = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private CertificateInfoMapper certificateInfoMapper;
    @Resource
    private CertVerifyHistoryMapper certVerifyHistoryMapper;
    @Resource
    private CertChangeLogMapper certChangeLogMapper;
    @Resource
    private CertVerifyGateway certVerifyGateway;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long verifyCertificate(Long certId, String verifyChannel, Long verifyBy) {
        CertificateInfoDO cert = certificateInfoMapper.selectById(certId);
        if (cert == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERTIFICATE_NOT_EXISTS);
        }
        CertVerifyChannelEnum channel = parseChannel(verifyChannel);
        if (CertificateVerificationStatusEnum.VERIFYING.name().equals(cert.getVerificationStatus())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_VERIFY_IN_PROGRESS);
        }
        LocalDateTime now = LocalDateTime.now();
        String idempotentKey = certId + ":" + channel.name() + ":" + KEY_MINUTE.format(now);
        CertVerifyHistoryDO existing = certVerifyHistoryMapper.selectByIdempotentKey(idempotentKey);
        if (existing != null) {
            log.info("[cert-verify][cert={}] 本分钟已验真（{}），幂等返回 {}", certId, idempotentKey, existing.getId());
            return existing.getId(); // 重试/重复调用幂等（§17.5）
        }

        // 1) 组装 36906 请求（verifyinfo）：certificateData 键=GB/T 36903 英文名/短名
        String certificateData = buildCertificateData(cert);
        EccertVerifyInfoReqVO request = new EccertVerifyInfoReqVO(
                new EccertReqHeadVO(ACCESS_ID, ACCESS_TOKEN),
                new EccertVerifyInfoReqVO.Data(certificateData));
        // 2) 调用网关（演示通道或真实通道，由配置切换）
        EccertVerifyInfoRespVO resp = certVerifyGateway.verifyInfo(request);
        String resultCode = resp.data() == null ? null : resp.data().result();
        String message = resp.data() == null ? null : resp.data().message();
        EccertRespHeadVO head = resp.head();
        String headMessage = head == null ? null : head.message();

        // 3) 回执映射并落验真历史（先写=防并发重复的串行点）
        Map<String, String> verdict = mapVerdict(resultCode);
        String verifyResult = verdict.get("verifyResult");
        String targetStatus = verdict.get("targetStatus");
        CertVerifyHistoryDO history = new CertVerifyHistoryDO();
        history.setCertId(certId);
        history.setVerifyChannel(channel.name());
        history.setVerifyResult(verifyResult);
        history.setRequestSnapshot(certificateData);
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("headStatus", head == null ? "" : head.status());
        snapshot.put("headMessage", headMessage == null ? "" : headMessage);
        snapshot.put("result", resultCode == null ? "" : resultCode);
        snapshot.put("message", message == null ? "" : message);
        history.setResultSnapshot(writeJson(snapshot));
        history.setVerifyBy(verifyBy);
        history.setVerifiedAt(now);
        history.setIdempotentKey(idempotentKey);
        try {
            certVerifyHistoryMapper.insert(history);
        } catch (DuplicateKeyException e) {
            // 并发同分钟重复调用：以既有记录为准（事务回滚本次全部写入，幂等语义）
            CertVerifyHistoryDO dup = certVerifyHistoryMapper.selectByIdempotentKey(idempotentKey);
            if (dup != null) {
                return dup.getId();
            }
            throw e;
        }
        // 4) 台账最近一次验真状态回写（§17.5：主表只承载最近一次）
        String oldStatus = cert.getVerificationStatus();
        String newStatus = targetStatus == null ? cert.getVerificationStatus() : targetStatus;
        cert.setVerificationStatus(newStatus);
        cert.setLastVerifiedAt(now);
        cert.setVerifyApiSource(channel.name());
        if (certificateInfoMapper.updateById(cert) == 0) { // @Version CAS
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERTIFICATE_DATA_MODIFIED);
        }
        // 5) verification_status 变更审计（哈希链；每次验真均留一行，状态未变记录同值，形成验真调用链）
        CertHashChain.appendRow(certChangeLogMapper, certId, "verification_status", oldStatus, newStatus,
                CertificateChangeTypeEnum.UPDATE.name(), CertificateChangeSourceEnum.API_SYNC.name(),
                null, "VERIFY:" + history.getId());
        log.info("[cert-verify][cert={}] 验真完成：result={} message={}", certId, verifyResult, message);
        return history.getId();
    }

    // ========== 内部工具 ==========

    /** verifyinfo result → (verifyResult, targetStatus)；UNAVAILABLE 不改台账状态（口径 §3.4 呼应）。 */
    private Map<String, String> mapVerdict(String resultCode) {
        Map<String, String> m = new LinkedHashMap<>();
        if ("0".equals(resultCode)) {
            m.put("verifyResult", CertVerifyResultEnum.MATCH.name());
            m.put("targetStatus", CertificateVerificationStatusEnum.VERIFIED.name());
        } else if ("1".equals(resultCode) || "2".equals(resultCode) || "3".equals(resultCode)) {
            m.put("verifyResult", CertVerifyResultEnum.MISMATCH.name());
            m.put("targetStatus", CertificateVerificationStatusEnum.INVALID.name());
        } else {
            m.put("verifyResult", CertVerifyResultEnum.UNAVAILABLE.name());
            m.put("targetStatus", null); // 信息不全/查无：不判假，保持 UNVERIFIED
        }
        return m;
    }

    /** 组装 certificateData：键=GB/T 36903 英文名/短名（5.2），对照台账字段；L3 快照。 */
    private String buildCertificateData(CertificateInfoDO cert) {
        Map<String, String> data = new LinkedHashMap<>();
        put(data, "ZZMC", cert.getCertName());
        put(data, "ZZBH", cert.getCertNo());
        put(data, "FZJG", cert.getIssuingAuthority());
        put(data, "FZJGDM", cert.getIssuingAuthorityCode());
        put(data, "BFRQ", cert.getIssueDate() == null ? null : String.valueOf(cert.getIssueDate()));
        put(data, "YXQSRQ", cert.getValidFrom() == null ? null : String.valueOf(cert.getValidFrom()));
        put(data, "YXQJZRQ", cert.getValidTo() == null ? null : String.valueOf(cert.getValidTo()));
        put(data, "CZZT", cert.getHolderName());
        return writeJson(data);
    }

    private void put(Map<String, String> m, String key, String value) {
        m.put(key, value == null ? "" : value);
    }

    private CertVerifyChannelEnum parseChannel(String verifyChannel) {
        if (verifyChannel == null || verifyChannel.isBlank()) {
            return CertVerifyChannelEnum.FOUR_PLATFORM; // 缺省四库一平台
        }
        try {
            return CertVerifyChannelEnum.valueOf(verifyChannel);
        } catch (IllegalArgumentException e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_VERIFY_CHANNEL_INVALID);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }
}
