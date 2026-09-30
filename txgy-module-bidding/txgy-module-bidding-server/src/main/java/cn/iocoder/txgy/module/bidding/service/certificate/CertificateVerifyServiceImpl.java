package cn.iocoder.txgy.module.bidding.service.certificate;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.ruleconfig.RuleConfigKeys;
import cn.iocoder.txgy.module.bidding.api.cert.vo.EccertReqHeadVO;
import cn.iocoder.txgy.module.bidding.api.cert.vo.EccertRespHeadVO;
import cn.iocoder.txgy.module.bidding.api.cert.vo.EccertVerifyFileReqVO;
import cn.iocoder.txgy.module.bidding.api.cert.vo.EccertVerifyFileRespVO;
import cn.iocoder.txgy.module.bidding.api.cert.vo.EccertVerifyInfoReqVO;
import cn.iocoder.txgy.module.bidding.api.cert.vo.EccertVerifyInfoRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertFileVersionDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertVerifyHistoryDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertificateInfoDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertChangeLogMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertFileVersionMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertVerifyHistoryMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertificateInfoMapper;
import cn.iocoder.txgy.module.bidding.enums.CertVerifyChannelEnum;
import cn.iocoder.txgy.module.bidding.enums.CertVerifyResultEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateChangeSourceEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateChangeTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateVerificationStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.service.ruleconfig.RuleConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertVerifyBatchItemRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertVerifyBatchRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertVerifyHistoryPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertVerifyHistoryRespVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.util.StringUtils;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * 验真幂等窗口判定的场景标记（写入 bidding_rule_config_audit.biz_scene）。
     *
     * <p>为什么留证：窗口决定"这次验真是否真的打了外部网关"，争议时需要回答用的是哪一版窗口值。
     */
    private static final String BIZ_SCENE_CERT_VERIFY = "CERT_VERIFY";

    /** 幂等键的业务种类前缀（**结构约定**，不是业务取值）：验信息。 */
    private static final String VERIFY_KIND_INFO = "INFO:";

    /** 幂等键的业务种类前缀（**结构约定**）：验文件。 */
    private static final String VERIFY_KIND_FILE = "FILE:";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private CertificateInfoMapper certificateInfoMapper;
    @Resource
    private CertVerifyHistoryMapper certVerifyHistoryMapper;
    @Resource
    private CertFileVersionMapper certFileVersionMapper;
    @Resource
    private CertChangeLogMapper certChangeLogMapper;
    @Resource
    private CertVerifyGateway certVerifyGateway;

    @Resource
    private RuleConfigService ruleConfigService;

    /**
     * 取验真幂等窗口（秒）。
     *
     * <p>规范出处：《电子资质七大模块技术方案.md》§8.1（阈值集中受控）/§8.2（读取留证）。
     * <p>为什么带审计读取：该值决定"是否跳过外部网关调用"，直接影响外部配额与结论一致性，
     * 是会影响对外结论的判定点，不能走直连读取。
     */
    private int resolveVerifyIdempotentWindowSeconds() {
        Object value = ruleConfigService
                .resolveWithAudit(RuleConfigKeys.CERT_VERIFY_IDEMPOTENT_WINDOW_SECONDS, BIZ_SCENE_CERT_VERIFY)
                .getValue();
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw ServiceExceptionUtil.exception(ErrorCodeConstants.RULE_CONFIG_VALUE_FORMAT_INVALID,
                RuleConfigKeys.CERT_VERIFY_IDEMPOTENT_WINDOW_SECONDS);
    }

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
        // 幂等窗口来自规则配置中心（cert.verify.idempotent.window.seconds，默认 60 秒）。
        // 规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §17.5；《电子资质七大模块技术方案.md》§8.2（读取留证）。
        // 为什么必须可配：窗口直接决定"多快会再打一次外部网关"，各地政务网关配额与稳定性差异很大，
        // 调窗口属于运维动作，不应改代码发版。
        int windowSeconds = resolveVerifyIdempotentWindowSeconds();
        // 主判定：滑动窗口内最近一次验真（同证同渠道）→ 复用既有结论，不再打网关。
        // 为什么不用"按分钟取整的键"做主判定：分钟桶不等于窗口，跨分钟边界只差 1 秒也会重复调网关。
        String keyPrefix = VERIFY_KIND_INFO + certId + ":" + channel.name() + ":";
        CertVerifyHistoryDO existing = certVerifyHistoryMapper.selectLatestWithinWindow(keyPrefix,
                now.minusSeconds(windowSeconds));
        if (existing != null) {
            log.info("[cert-verify][cert={}] {} 秒内已验真（历史 {}，verifiedAt={}），幂等返回",
                    certId, windowSeconds, existing.getId(), existing.getVerifiedAt());
            return existing.getId(); // 重试/重复调用幂等（§17.5）
        }
        // 兜底键：按**配置窗口**取整的桶起点（不是固定分钟），仅供唯一键 uk_verify_idem 兜并发；
        // 若并发请求同时走到这里，后到者插入冲突后按此键取回既有记录。
        long epochSecond = now.toEpochSecond(ZoneOffset.UTC);
        String idempotentKey = keyPrefix + (epochSecond - epochSecond % windowSeconds);

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
            // 并发同窗口桶重复调用：以既有记录为准（事务回滚本次全部写入，幂等语义）
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long verifyCertificateFile(Long certId, String verifyChannel, Long verifyBy) {
        CertificateInfoDO cert = certificateInfoMapper.selectById(certId);
        if (cert == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERTIFICATE_NOT_EXISTS);
        }
        CertVerifyChannelEnum channel = parseChannel(verifyChannel);
        if (CertificateVerificationStatusEnum.VERIFYING.name().equals(cert.getVerificationStatus())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_VERIFY_IN_PROGRESS);
        }
        // 取当前生效文件（v3.3 §三 is_current）；无文件无法做文件验真
        CertFileVersionDO file = certFileVersionMapper.selectAnyCurrentByCertId(certId);
        if (file == null || file.getFileHash() == null || file.getFileHash().isBlank()) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_FILE_NOT_EXISTS);
        }
        LocalDateTime now = LocalDateTime.now();
        // 幂等窗口与验信息同源（配置键 cert.verify.idempotent.window.seconds）；
        // 种类放在**键首**（FILE:）与验信息（INFO:）隔离：两条路径的 verify_channel 相同，
        // 只有前缀能可靠区分，否则"验文件"的结论会被"验信息"复用（反之亦然）。
        int windowSeconds = resolveVerifyIdempotentWindowSeconds();
        String keyPrefix = VERIFY_KIND_FILE + certId + ":" + channel.name() + ":";
        CertVerifyHistoryDO existing = certVerifyHistoryMapper.selectLatestWithinWindow(keyPrefix,
                now.minusSeconds(windowSeconds));
        if (existing != null) {
            log.info("[cert-verify-file][cert={}] {} 秒内已验文件（历史 {}），幂等返回",
                    certId, windowSeconds, existing.getId());
            return existing.getId();
        }
        long epochSecond = now.toEpochSecond(ZoneOffset.UTC);
        String idempotentKey = keyPrefix + (epochSecond - epochSecond % windowSeconds);
        // 1) 组装 36906 verifyfile 请求：demo 通道以 SHA-256 为内容指纹占位；
        //    真实通道接入后应改为经 FileApi 回传文件流（GB/T 36905 原件/加注件），并核对 hash 与底账一致
        String fileFingerprint = file.getFileHash();
        EccertVerifyFileReqVO request = new EccertVerifyFileReqVO(
                new EccertReqHeadVO(ACCESS_ID, ACCESS_TOKEN),
                new EccertVerifyFileReqVO.Data(fileFingerprint));
        // 2) 调用网关（演示或真实通道，配置切换）
        EccertVerifyFileRespVO resp = certVerifyGateway.verifyFile(request);
        String resultCode = resp.data() == null ? null : resp.data().result();
        String message = resp.data() == null ? null : resp.data().message();
        EccertRespHeadVO head = resp.head();
        // 3) 回执映射并落验真历史（先写=防并发重复的串行点；result_snapshot 记录 method=verifyfile）
        Map<String, String> verdict = mapVerdict(resultCode);
        String verifyResult = verdict.get("verifyResult");
        String targetStatus = verdict.get("targetStatus");
        CertVerifyHistoryDO history = new CertVerifyHistoryDO();
        history.setCertId(certId);
        history.setVerifyChannel(channel.name());
        history.setVerifyResult(verifyResult);
        history.setRequestSnapshot(fileFingerprint);
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("method", "verifyfile");
        snapshot.put("headStatus", head == null ? "" : head.status());
        snapshot.put("headMessage", head == null ? "" : head.message());
        snapshot.put("result", resultCode == null ? "" : resultCode);
        snapshot.put("message", message == null ? "" : message);
        snapshot.put("fileFormat", file.getFileFormat() == null ? "" : file.getFileFormat());
        history.setResultSnapshot(writeJson(snapshot));
        history.setVerifyBy(verifyBy);
        history.setVerifiedAt(now);
        history.setIdempotentKey(idempotentKey);
        try {
            certVerifyHistoryMapper.insert(history);
        } catch (DuplicateKeyException e) {
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
        // 5) verification_status 变更审计（哈希链；verifyfile 触发同样留痕）
        CertHashChain.appendRow(certChangeLogMapper, certId, "verification_status", oldStatus, newStatus,
                CertificateChangeTypeEnum.UPDATE.name(), CertificateChangeSourceEnum.API_SYNC.name(),
                null, "VERIFY-FILE:" + history.getId());
        log.info("[cert-verify-file][cert={}] 文件验真完成：result={} sha256={}", certId, verifyResult,
                fileFingerprint);
        return history.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CertVerifyBatchRespVO verifyCertificateBatch(List<Long> certIds, String verifyChannel, Long verifyBy) {
        // 规范出处：五页改造契约 Q2（2026-09-28）——批量验真：逐条调用单验真（幂等窗口/回执落库复用），
        // 行级回执：单条失败（不存在/验真中/通道未就绪）记入该行，不影响其余。
        // 为什么这样写：批量是"逐条验真的顺序执行"，不新建第二条验真管线，避免结论口径分叉。
        String channel = verifyChannel == null ? "FOUR_PLATFORM" : verifyChannel;
        CertVerifyBatchRespVO resp = new CertVerifyBatchRespVO();
        List<CertVerifyBatchItemRespVO> results = new ArrayList<>();
        if (certIds != null) {
            for (Long certId : certIds) {
                CertVerifyBatchItemRespVO item = new CertVerifyBatchItemRespVO();
                item.setCertId(certId);
                item.setVerifyChannel(channel);
                // 证书名先取：失败行（不存在/验真中）前端同样要显示是哪张证，不能只在成功时才给
                CertificateInfoDO cert = certificateInfoMapper.selectById(certId);
                if (cert != null) {
                    item.setCertName(cert.getCertName());
                }
                try {
                    Long historyId = verifyCertificate(certId, channel, verifyBy);
                    item.setSuccess(true);
                    item.setAccepted(true);
                    // 结论取自刚落库的验真历史：前端要的是 verifyResult（MATCH/MISMATCH/UNAVAILABLE），
                    // "受理成功"只说明这次调用没报错，不等于"信息一致"（契约 Q2 明确区分这两个概念）。
                    CertVerifyHistoryDO history = historyId == null ? null
                            : certVerifyHistoryMapper.selectById(historyId);
                    if (history != null) {
                        item.setVerifyResult(history.getVerifyResult());
                        if (history.getVerifyChannel() != null) {
                            item.setVerifyChannel(history.getVerifyChannel());
                        }
                    }
                } catch (Exception e) {
                    log.warn("[cert-verify][batch] certId={} 验真失败: {}", certId, e.getMessage());
                    item.setSuccess(false);
                    item.setAccepted(false);
                    item.setMessage(e instanceof ServiceException se ? se.getMessage()
                            : "验真失败：" + e.getMessage());
                }
                results.add(item);
            }
        }
        resp.setResults(results);
        // 前端契约容器名是 items（api/bidding/certificate/index.ts 的 batchVerifyCertificates）——
        // 两个都发：results 兼容既有调用方，items 让已发布前端不用改代码
        resp.setItems(results);
        resp.setSuccessCount((int) results.stream().filter(CertVerifyBatchItemRespVO::getSuccess).count());
        resp.setFailureCount(results.size() - resp.getSuccessCount());
        log.info("[cert-verify][batch] 完成：{} 成功，{} 失败，operator={}",
                resp.getSuccessCount(), resp.getFailureCount(), verifyBy);
        return resp;
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

    /**
     * 验真历史分页查询（补上的"读出口"）。
     *
     * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §17.5（历史表只追加）；GB/T 36906—2018 §6.4。
     *
     * <p>为什么这样写：① 只做等值过滤（证照/渠道/结论）+ 按验真时间倒序 —— 追溯场景是"某张证被验过几次、结论如何"，
     * 不需要模糊搜；② 证照名用一次 in 查询批量回填（不是逐行查），行数再多也只有两次 SQL；
     * ③ L3 报文原文不出接口，只回一个布尔位（含编号等可识别信息，界面上高频展示不合适）。
     */
    @Override
    public PageResult<CertVerifyHistoryRespVO> getVerifyHistoryPage(CertVerifyHistoryPageReqVO reqVO) {
        LambdaQueryWrapper<CertVerifyHistoryDO> wrapper = new LambdaQueryWrapper<CertVerifyHistoryDO>()
                .eq(reqVO.getCertId() != null, CertVerifyHistoryDO::getCertId, reqVO.getCertId())
                .eq(StringUtils.hasText(reqVO.getVerifyChannel()), CertVerifyHistoryDO::getVerifyChannel, reqVO.getVerifyChannel())
                .eq(StringUtils.hasText(reqVO.getVerifyResult()), CertVerifyHistoryDO::getVerifyResult, reqVO.getVerifyResult())
                .orderByDesc(CertVerifyHistoryDO::getVerifiedAt);
        Page<CertVerifyHistoryDO> page = certVerifyHistoryMapper.selectPage(
                new Page<>(reqVO.getPageNo(), reqVO.getPageSize()), wrapper);
        return new PageResult<>(toHistoryRespList(page.getRecords()), page.getTotal());
    }

    /** 历史行 → VO（证照名批量回填，避免 N+1）。 */
    private List<CertVerifyHistoryRespVO> toHistoryRespList(List<CertVerifyHistoryDO> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        Set<Long> certIds = records.stream().map(CertVerifyHistoryDO::getCertId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> certNames = certIds.isEmpty() ? Map.of()
                : certificateInfoMapper.selectBatchIds(certIds).stream()
                        .collect(Collectors.toMap(CertificateInfoDO::getId,
                                c -> c.getCertName() == null ? "" : c.getCertName(), (a, b) -> a));
        return records.stream().map(row -> {
            CertVerifyHistoryRespVO vo = new CertVerifyHistoryRespVO();
            vo.setId(row.getId());
            vo.setCertId(row.getCertId());
            vo.setCertName(certNames.get(row.getCertId()));
            vo.setVerifyChannel(row.getVerifyChannel());
            vo.setVerifyResult(row.getVerifyResult());
            vo.setVerifyBy(row.getVerifyBy());
            vo.setVerifiedAt(row.getVerifiedAt());
            vo.setSnapshotAvailable(StringUtils.hasText(row.getRequestSnapshot())
                    || StringUtils.hasText(row.getResultSnapshot()));
            return vo;
        }).toList();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }
}
