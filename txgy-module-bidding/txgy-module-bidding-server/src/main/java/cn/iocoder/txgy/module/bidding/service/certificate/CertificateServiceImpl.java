package cn.iocoder.txgy.module.bidding.service.certificate;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificatePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertChangeLogDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertificateInfoDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertChangeLogMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertificateInfoMapper;
import cn.iocoder.txgy.module.bidding.enums.CertificateChangeSourceEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateChangeTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateSourceChannelEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateVerificationStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 证照台账服务实现（T 轨；certificate_info 与 cert_change_logs 同事务写入）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §二（台账状态/字段口径）、§13（变更审计哈希链）、
 * §18 v3.4；《招投标模块开发规范》§4.5（乐观锁 CAS、幂等判重）与 §5（表、审计列、只追加日志）。
 * <p>设计遵循/针对：① 建档以 cert_no 租户内唯一 UK 判重（§5 六-1），重复建档抛 CERTIFICATE_NO_DUPLICATE；
 * ② 状态流转只允许 CertificateStatusEnum 迁移表内路径（51号 ZZZT 语义），吊销/注销记 REVOKE、其余记 STATUS_CHANGE；
 * ③ 每次写操作追加哈希链日志（record_hash=SHA-256(证书ID|字段|旧值|新值|类型|来源|IP|批次|prev_hash)，
 * 首条 prev_hash=ROOT），日志与主表同事务、只追加（等保 2.0 三级）；④ @Version 乐观锁由
 * BiddingMybatisConfiguration 补注册的拦截器生效（开发规范 §4.5 乐观锁款、平台核对 2026-09-03），影响行 0 视为冲突。
 */
@Service
public class CertificateServiceImpl implements CertificateService {

    /** 哈希链链首标记（v3.3 §13；prev_hash 缺省 ROOT） */
    private static final String HASH_CHAIN_ROOT = "ROOT";

    @Resource
    private CertificateInfoMapper certificateInfoMapper;
    @Resource
    private CertChangeLogMapper certChangeLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCertificate(CertificateSaveReqVO reqVO) {
        // 幂等判重：证书编号租户内唯一（UK uk_cert_no，开发规范 §4.5/§5 六-1）
        if (certificateInfoMapper.selectByCertNo(reqVO.getCertNo()) != null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERTIFICATE_NO_DUPLICATE);
        }
        CertificateInfoDO cert = new CertificateInfoDO();
        cert.setCertName(reqVO.getCertName());
        cert.setCertNo(reqVO.getCertNo());
        cert.setCertType(reqVO.getCertType());
        cert.setCertCategoryCode(reqVO.getCertCategoryCode());
        cert.setHolderName(reqVO.getHolderName());
        cert.setHolderType(reqVO.getHolderType() == null ? "ORG" : reqVO.getHolderType());
        cert.setHolderId(reqVO.getHolderId());
        applyEditable(cert, reqVO);
        // 台账默认事实：状态 ACTIVE（自查只认 ACTIVE）、验真 UNVERIFIED、来源 MANUAL（v3.3 §二）
        cert.setCertStatus(CertificateStatusEnum.ACTIVE.name());
        cert.setVerificationStatus(CertificateVerificationStatusEnum.UNVERIFIED.name());
        cert.setSourceChannel(CertificateSourceChannelEnum.MANUAL.name());
        certificateInfoMapper.insert(cert);

        // CREATE 变更日志：主事实字段（哈希链，v3.3 §13）
        List<Change> facts = new ArrayList<>();
        facts.add(new Change("cert_no", "", reqVO.getCertNo()));
        facts.add(new Change("cert_name", "", reqVO.getCertName()));
        facts.add(new Change("holder_name", "", reqVO.getHolderName()));
        facts.add(new Change("cert_status", "", CertificateStatusEnum.ACTIVE.name()));
        appendChangeLog(cert.getId(), CertificateChangeTypeEnum.CREATE, null, facts);
        return cert.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCertificate(CertificateSaveReqVO reqVO) {
        CertificateInfoDO db = certificateInfoMapper.selectById(reqVO.getId());
        if (db == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERTIFICATE_NOT_EXISTS);
        }
        ensureVersionMatch(reqVO.getVersion(), db);
        // 只记录实际变化的可编辑字段（v3.3 §13 UPDATE 行）
        List<Change> diffs = diffEditable(db, reqVO);
        if (diffs.isEmpty()) {
            return;
        }
        applyEditable(db, reqVO);
        int rows = certificateInfoMapper.updateById(db);
        if (rows == 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERTIFICATE_DATA_MODIFIED);
        }
        appendChangeLog(db.getId(), CertificateChangeTypeEnum.UPDATE, null, diffs);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long certificateId, String targetStatus, Integer version, String reason,
                             CertificateChangeSourceEnum opSource, String opIp) {
        CertificateStatusEnum target;
        try {
            target = CertificateStatusEnum.valueOf(targetStatus);
        } catch (IllegalArgumentException e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERTIFICATE_STATUS_TRANSITION_INVALID);
        }
        CertificateInfoDO db = certificateInfoMapper.selectById(certificateId);
        if (db == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERTIFICATE_NOT_EXISTS);
        }
        String from = db.getCertStatus();
        if (target.name().equals(from)) {
            return; // 幂等放行：目标状态即当前状态
        }
        if (!CertificateStatusEnum.isTransitionAllowed(from, target.name())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERTIFICATE_STATUS_TRANSITION_INVALID);
        }
        ensureVersionMatch(version, db);

        db.setCertStatus(target.name());
        int rows = certificateInfoMapper.updateById(db);
        if (rows == 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERTIFICATE_DATA_MODIFIED);
        }
        // 吊销/注销记 REVOKE，其余状态流转记 STATUS_CHANGE（v3.3 §13）
        CertificateChangeTypeEnum type = (target == CertificateStatusEnum.REVOKED
                || target == CertificateStatusEnum.CANCELLED)
                ? CertificateChangeTypeEnum.REVOKE : CertificateChangeTypeEnum.STATUS_CHANGE;
        List<Change> logs = new ArrayList<>();
        logs.add(new Change("cert_status", from, target.name()));
        if (reason != null && !reason.isBlank()) {
            logs.add(new Change("reason", "", reason));
        }
        appendChangeLog(certificateId, type, opSource == null ? CertificateChangeSourceEnum.MANUAL : opSource,
                opIp, logs);
    }

    @Override
    public CertificateRespVO getCertificate(Long id) {
        CertificateInfoDO cert = certificateInfoMapper.selectById(id);
        if (cert == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERTIFICATE_NOT_EXISTS);
        }
        return toRespVO(cert);
    }

    @Override
    public PageResult<CertificateRespVO> getCertificatePage(CertificatePageReqVO reqVO) {
        LambdaQueryWrapper<CertificateInfoDO> query = new LambdaQueryWrapper<>();
        query.eq(StringUtils.hasText(reqVO.getCertNo()), CertificateInfoDO::getCertNo, reqVO.getCertNo());
        query.eq(StringUtils.hasText(reqVO.getCertType()), CertificateInfoDO::getCertType, reqVO.getCertType());
        query.eq(StringUtils.hasText(reqVO.getCertStatus()), CertificateInfoDO::getCertStatus, reqVO.getCertStatus());
        query.eq(StringUtils.hasText(reqVO.getVerificationStatus()),
                CertificateInfoDO::getVerificationStatus, reqVO.getVerificationStatus());
        query.like(StringUtils.hasText(reqVO.getCertName()), CertificateInfoDO::getCertName, reqVO.getCertName());
        query.like(StringUtils.hasText(reqVO.getHolderName()), CertificateInfoDO::getHolderName, reqVO.getHolderName());
        query.orderByDesc(CertificateInfoDO::getId);
        Page<CertificateInfoDO> page = certificateInfoMapper.selectPage(
                new Page<>(reqVO.getPageNo(), reqVO.getPageSize()), query);
        return new PageResult<>(page.getRecords().stream().map(this::toRespVO).toList(), page.getTotal());
    }

    /** DO → 自建响应 VO（转换统一在服务层，开发规范 §3.1/§4.2）。 */
    private CertificateRespVO toRespVO(CertificateInfoDO cert) {
        CertificateRespVO resp = new CertificateRespVO();
        resp.setId(cert.getId());
        resp.setVersion(cert.getVersion());
        resp.setCertName(cert.getCertName());
        resp.setCertNo(cert.getCertNo());
        resp.setCertType(cert.getCertType());
        resp.setCertCategoryCode(cert.getCertCategoryCode());
        resp.setCertLevel(cert.getCertLevel());
        resp.setCertScope(cert.getCertScope());
        resp.setHolderName(cert.getHolderName());
        resp.setHolderType(cert.getHolderType());
        resp.setHolderId(cert.getHolderId());
        resp.setIssuingAuthority(cert.getIssuingAuthority());
        resp.setIssuingAuthorityCode(cert.getIssuingAuthorityCode());
        resp.setIssueDate(cert.getIssueDate());
        resp.setValidFrom(cert.getValidFrom());
        resp.setValidTo(cert.getValidTo());
        resp.setCertStatus(cert.getCertStatus());
        resp.setVerificationStatus(cert.getVerificationStatus());
        resp.setLastVerifiedAt(cert.getLastVerifiedAt());
        resp.setVerifyApiSource(cert.getVerifyApiSource());
        resp.setOfficialVerifyUrl(cert.getOfficialVerifyUrl());
        resp.setSourceChannel(cert.getSourceChannel());
        resp.setECertIdent(cert.getECertIdent());
        resp.setRemark(cert.getRemark());
        resp.setCreateTime(cert.getCreateTime());
        resp.setUpdateTime(cert.getUpdateTime());
        return resp;
    }

    // ========== 内部工具 ==========

    /** 可编辑字段集合（业务键 certNo/certType/holder 建档后不可改，v3.3 §二/§七） */
    private static final List<String> EDITABLE = List.of(
            "cert_name", "cert_level", "cert_scope", "issuing_authority", "issuing_authority_code",
            "issue_date", "valid_from", "valid_to", "remark");

    private void applyEditable(CertificateInfoDO cert, CertificateSaveReqVO reqVO) {
        cert.setCertName(reqVO.getCertName());
        cert.setCertLevel(reqVO.getCertLevel());
        cert.setCertScope(reqVO.getCertScope());
        cert.setIssuingAuthority(reqVO.getIssuingAuthority());
        cert.setIssuingAuthorityCode(reqVO.getIssuingAuthorityCode());
        cert.setIssueDate(reqVO.getIssueDate());
        cert.setValidFrom(reqVO.getValidFrom());
        cert.setValidTo(reqVO.getValidTo());
        cert.setRemark(reqVO.getRemark());
    }

    private List<Change> diffEditable(CertificateInfoDO db, CertificateSaveReqVO reqVO) {
        List<Change> diffs = new ArrayList<>();
        if (!eq(db.getCertName(), reqVO.getCertName())) diffs.add(new Change("cert_name", v(db.getCertName()), v(reqVO.getCertName())));
        if (!eq(db.getCertLevel(), reqVO.getCertLevel())) diffs.add(new Change("cert_level", v(db.getCertLevel()), v(reqVO.getCertLevel())));
        if (!eq(db.getCertScope(), reqVO.getCertScope())) diffs.add(new Change("cert_scope", v(db.getCertScope()), v(reqVO.getCertScope())));
        if (!eq(db.getIssuingAuthority(), reqVO.getIssuingAuthority())) diffs.add(new Change("issuing_authority", v(db.getIssuingAuthority()), v(reqVO.getIssuingAuthority())));
        if (!eq(db.getIssuingAuthorityCode(), reqVO.getIssuingAuthorityCode())) diffs.add(new Change("issuing_authority_code", v(db.getIssuingAuthorityCode()), v(reqVO.getIssuingAuthorityCode())));
        if (!eq(d(db.getIssueDate()), d(reqVO.getIssueDate()))) diffs.add(new Change("issue_date", d(db.getIssueDate()), d(reqVO.getIssueDate())));
        if (!eq(d(db.getValidFrom()), d(reqVO.getValidFrom()))) diffs.add(new Change("valid_from", d(db.getValidFrom()), d(reqVO.getValidFrom())));
        if (!eq(d(db.getValidTo()), d(reqVO.getValidTo()))) diffs.add(new Change("valid_to", d(db.getValidTo()), d(reqVO.getValidTo())));
        if (!eq(db.getRemark(), reqVO.getRemark())) diffs.add(new Change("remark", v(db.getRemark()), v(reqVO.getRemark())));
        return diffs;
    }

    /** 乐观锁预检：页面回传版本与库内不一致即为已被他人修改（§4.5），最终一致性仍由 updateById CAS 兜底。 */
    private void ensureVersionMatch(Integer reqVersion, CertificateInfoDO db) {
        if (reqVersion != null && !reqVersion.equals(db.getVersion())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERTIFICATE_DATA_MODIFIED);
        }
    }

    /** 追加哈希链日志（与主表同事务；只追加，防重由 UK(cert_id, prev_hash, …) 链式保证 —— v3.3 §13、v3.4 §18.3-1）。 */
    private void appendChangeLog(Long certId, CertificateChangeTypeEnum type,
                                 CertificateChangeSourceEnum source, List<Change> changes) {
        appendChangeLog(certId, type, source == null ? CertificateChangeSourceEnum.MANUAL : source,
                null, null, changes);
    }

    private void appendChangeLog(Long certId, CertificateChangeTypeEnum type, CertificateChangeSourceEnum source,
                                 String opIp, List<Change> changes) {
        appendChangeLog(certId, type, source, opIp, null, changes);
    }

    private void appendChangeLog(Long certId, CertificateChangeTypeEnum type, CertificateChangeSourceEnum source,
                                 String opIp, String batchId, List<Change> changes) {
        if (changes == null || changes.isEmpty()) {
            return;
        }
        String prev = certChangeLogMapper.selectLastRecordHash(certId);
        if (prev == null) {
            prev = HASH_CHAIN_ROOT;
        }
        String typeName = type.name();
        String sourceName = source == null ? CertificateChangeSourceEnum.MANUAL.name() : source.name();
        for (Change change : changes) {
            CertChangeLogDO log = new CertChangeLogDO();
            log.setCertId(certId);
            log.setFieldName(change.field);
            log.setOldValue(change.oldValue);
            log.setNewValue(change.newValue);
            log.setChangeType(typeName);
            log.setOpSource(sourceName);
            log.setOpIp(opIp);
            log.setBatchId(batchId);
            log.setPrevHash(prev);
            // 记录哈希：与验真/其余域共用 CertHashChain 同一公式（v3.3 §13）
            String recordHash = CertHashChain.recordHash(certId, change.field, change.oldValue, change.newValue,
                    typeName, sourceName, opIp == null ? "" : opIp,
                    batchId == null ? "" : batchId, prev);
            log.setRecordHash(recordHash);
            certChangeLogMapper.insert(log);
            prev = recordHash;
        }
    }

    private static boolean eq(String a, String b) {
        return (a == null ? "" : a).equals(b == null ? "" : b);
    }

    private static String v(String value) {
        return value == null ? "" : value;
    }

    private static String d(LocalDate value) {
        return value == null ? "" : value.toString();
    }

    private static String sha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 单字段变更（field：old → new），用于变更审计日志行（v3.3 §13）。 */
    private record Change(String field, String oldValue, String newValue) {
    }
}
