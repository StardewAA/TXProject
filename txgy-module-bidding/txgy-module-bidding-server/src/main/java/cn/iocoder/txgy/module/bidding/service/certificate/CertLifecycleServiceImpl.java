package cn.iocoder.txgy.module.bidding.service.certificate;

import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertLifecycleFileRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertLifecycleRespVO;
import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertLifecycleApplyReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertLifecyclePageReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertFileVersionDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertNotifyOutboxDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertificateInfoDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.CertLifecycleRecordDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelCertificateDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.EnterpriseQualificationDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationSafetyPermitDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertFileVersionMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertNotifyOutboxMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertificateInfoMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.CertLifecycleRecordMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelCertificateMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.EnterpriseQualificationMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationSafetyPermitMapper;
import cn.iocoder.txgy.module.bidding.enums.CertLifecycleBizDomainEnum;
import cn.iocoder.txgy.module.bidding.enums.CertLifecycleStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.CertLifecycleTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.CertNotifyBizTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.CertNotifySendStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.CertRenewalTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.CertRevokeTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.PersonnelCertStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 证件/资质全生命周期服务实现（状态机 CAS + 域适配回写 + 版本管理）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 3/5/6/7（四类流程字段与回写口径，
 * 表结构归并与差异说明见 sql/2026-09-14-cert-lifecycle.sql 头部）；
 * 《招投标模块开发规范》§4.5（状态流转接口化 + 乐观锁 CAS + 写操作同事务）、§5 六-1（幂等键 UK）。
 * <p>设计遵循/针对：① 状态机显式（CertLifecycleStatusEnum 迁移表），"待办 → 直接生效"必须被拒；
 * ② 变更按字段拆行、共用 change_group_id，生效时按组一次性回写，保证"一次申请=一个原子变更"；
 * ③ 回写按 bizDomain 分派（CERT → certificate_info、PERSON_CERT → personnel_certificate、
 * ENTERPRISE_QUAL → enterprise_qualification、SAFETY_PERMIT → qualification_safety_permit），
 * 主档更新失败（版本 CAS 冲突）即抛错回滚，绝不出现"记录已生效、台账没变"；
 * ④ 续期生效时同步刷新 next_review_date（若续期单里给了新复审日）与状态回 VALID——续期即"重新有效"；
 * ⑤ 注销/吊销按 CertRevokeTypeEnum 映射目标状态（吊销 REVOKED / 注销 CANCELLED / 暂扣 SUSPENDED /
 * 恢复 VALID），与安全许可证状态词表保持同词表。
 * <p>业务目标：四类生命周期操作全部"有申请、有审批、有回写、可追溯"，杜绝直接改库绕过流程。
 */
@Slf4j
@Service
public class CertLifecycleServiceImpl implements CertLifecycleService {

    private static final DateTimeFormatter APPLY_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Resource
    private CertLifecycleRecordMapper certLifecycleRecordMapper;
    @Resource
    private CertificateInfoMapper certificateInfoMapper;
    @Resource
    private PersonnelCertificateMapper personnelCertificateMapper;
    @Resource
    private EnterpriseQualificationMapper enterpriseQualificationMapper;
    @Resource
    private QualificationSafetyPermitMapper qualificationSafetyPermitMapper;
    @Resource
    private CertNotifyOutboxMapper certNotifyOutboxMapper;
    @Resource
    private CertFileVersionMapper certFileVersionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long apply(CertLifecycleApplyReqVO reqVO) {
        CertLifecycleBizDomainEnum domain = parseDomain(reqVO.getBizDomain());
        CertLifecycleTypeEnum type = parseType(reqVO.getLifecycleType());
        // 业务对象必须存在：否则会留下一堆指向空气的流程记录，事后无法回溯
        RefSnapshot snapshot = loadRefSnapshot(domain, reqVO.getBizId());
        String applyNo = (reqVO.getApplyNo() == null || reqVO.getApplyNo().isBlank())
                ? domain.name() + "-" + APPLY_NO_DATE.format(LocalDateTime.now())
                : reqVO.getApplyNo();
        String groupId = type == CertLifecycleTypeEnum.CHANGE
                ? domain.name() + "-" + reqVO.getBizId() + "-" + applyNo : null;

        if (type == CertLifecycleTypeEnum.CHANGE) {
            if (reqVO.getChanges() == null || reqVO.getChanges().isEmpty()) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_CHANGE_EMPTY);
            }
            Long firstId = null;
            for (CertLifecycleApplyReqVO.FieldChange change : reqVO.getChanges()) {
                if (change.getFieldName() == null || change.getFieldName().isBlank()) {
                    throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_TARGET_REQUIRED,
                            "变更字段名");
                }
                CertLifecycleRecordDO row = buildBaseRecord(domain, type, reqVO, snapshot, applyNo);
                row.setChangeGroupId(groupId);
                row.setFieldName(change.getFieldName());
                row.setOldValue(change.getOldValue());
                row.setNewValue(change.getNewValue());
                row.setChangeSummary(reqVO.getChangeSummary());
                insertWithIdempotency(row, applyNo, change.getFieldName());
                firstId = firstId == null ? row.getId() : firstId;
            }
            return firstId;
        }

        // 非变更类：按类型校验必备字段（缺字段直接拒绝，避免"生效时才发现没数据"）
        CertLifecycleRecordDO row = buildBaseRecord(domain, type, reqVO, snapshot, applyNo);
        switch (type) {
            case RENEW -> {
                if (reqVO.getNewValidTo() == null) {
                    throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_TARGET_REQUIRED, "续期后有效期止");
                }
                row.setRenewalType(reqVO.getRenewalType() == null
                        ? CertRenewalTypeEnum.RENEW.name() : reqVO.getRenewalType());
                row.setOldValidFrom(snapshot.validFrom());
                row.setOldValidTo(snapshot.validTo());
                row.setNewValidFrom(reqVO.getNewValidFrom());
                row.setNewValidTo(reqVO.getNewValidTo());
                row.setNewNextReviewDate(reqVO.getNewNextReviewDate());
                row.setNewCertNo(reqVO.getNewCertNo());
                row.setResultNo(reqVO.getResultNo());
            }
            case REVOKE -> {
                if (reqVO.getRevokeType() == null || reqVO.getRevokeType().isBlank()) {
                    throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_TARGET_REQUIRED, "注销吊销类型");
                }
                parseRevokeType(reqVO.getRevokeType());
                row.setRevokeType(reqVO.getRevokeType());
                row.setRevokeAuthority(reqVO.getRevokeAuthority());
                row.setRevokeReason(reqVO.getRevokeReason());
                row.setRevokeEffectiveAt(reqVO.getRevokeEffectiveAt());
            }
            case ANNUAL -> {
                if (reqVO.getReviewYear() == null) {
                    throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_TARGET_REQUIRED, "年检年度");
                }
                row.setReviewYear(reqVO.getReviewYear());
                row.setReviewDate(reqVO.getReviewDate() == null ? LocalDate.now() : reqVO.getReviewDate());
                row.setReviewResult(reqVO.getReviewResult());
            }
            default -> throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_BIZ_DOMAIN_INVALID,
                    reqVO.getLifecycleType());
        }
        insertWithIdempotency(row, applyNo, null);
        return row.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id, String targetStatus, Integer version, String remark) {
        CertLifecycleRecordDO record = validateExists(id);
        if (!Objects.equals(version, record.getVersion())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_DATA_MODIFIED);
        }
        CertLifecycleStatusEnum from = parseStatus(record.getStatus());
        CertLifecycleStatusEnum to = parseStatus(targetStatus);
        if (!CertLifecycleStatusEnum.isTransitionAllowed(from, to)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_STATUS_INVALID);
        }
        record.setStatus(to.name());
        record.setHandledBy(currentUserId());
        record.setHandledAt(LocalDateTime.now());
        record.setRemark(appendRemark(record.getRemark(), remark));
        updateWithCas(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void effect(Long id, Integer version, String remark) {
        CertLifecycleRecordDO record = validateExists(id);
        if (!Objects.equals(version, record.getVersion())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_DATA_MODIFIED);
        }
        if (CertLifecycleStatusEnum.EFFECTIVE == parseStatus(record.getStatus())) {
            // 已生效不允许再次生效：重复生效会导致台账被回写两次（如有效期被推后两轮）
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_STATUS_INVALID);
        }
        CertLifecycleStatusEnum from = parseStatus(record.getStatus());
        if (!CertLifecycleStatusEnum.isTransitionAllowed(from, CertLifecycleStatusEnum.EFFECTIVE)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_STATUS_INVALID);
        }
        CertLifecycleBizDomainEnum domain = parseDomain(record.getBizDomain());
        CertLifecycleTypeEnum type = parseType(record.getLifecycleType());
        // 变更流程：按组取出全部字段行，逐项回写（一次申请=一个原子变更）
        if (type == CertLifecycleTypeEnum.CHANGE && record.getChangeGroupId() != null) {
            List<CertLifecycleRecordDO> group = certLifecycleRecordMapper.selectByChangeGroup(
                    record.getChangeGroupId());
            // 按组一次性回写：一次变更申请 = 一次原子写库（详见 writeBackChangeGroup 注释）
            writeBackChangeGroup(domain, group);
            for (CertLifecycleRecordDO row : group) {
                row.setStatus(CertLifecycleStatusEnum.EFFECTIVE.name());
                row.setHandledBy(currentUserId());
                row.setHandledAt(LocalDateTime.now());
                row.setEffectiveAt(LocalDateTime.now());
                row.setRemark(appendRemark(row.getRemark(), remark));
                updateWithCas(row);
            }
            return;
        }
        writeBackProcess(domain, type, record);
        record.setStatus(CertLifecycleStatusEnum.EFFECTIVE.name());
        record.setHandledBy(currentUserId());
        record.setHandledAt(LocalDateTime.now());
        record.setEffectiveAt(LocalDateTime.now());
        record.setRemark(appendRemark(record.getRemark(), remark));
        updateWithCas(record);
        log.info("[cert-lifecycle][effect] type={} domain={} refId={} recordId={}",
                type, domain, record.getBizId(), record.getId());
    }

    @Override
    public CertLifecycleRecordDO getRecord(Long id) {
        return validateExists(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void urge(Long id, Long operatorId) {
        // 规范出处：五页改造缺接口契约 Q15（2026-09-18）——催办对进行中流程派发审查提醒；
        // 幂等口径：同一天对同一记录重复催办不叠加（验收 §7.4）。
        CertLifecycleRecordDO record = validateExists(id);
        String status = record.getStatus();
        // 仅进行中（待办/审核中）可催办；终态（驳回/已生效）催办无意义，直接报状态非法
        if (!CertLifecycleStatusEnum.PENDING.name().equals(status)
                && !CertLifecycleStatusEnum.REVIEWING.name().equals(status)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_STATUS_INVALID);
        }
        // 幂等：今日已对该记录派发过催办则跳过（不叠加）
        Long urgedToday = certNotifyOutboxMapper.selectCount(new LambdaQueryWrapper<CertNotifyOutboxDO>()
                .eq(CertNotifyOutboxDO::getBizType, CertNotifyBizTypeEnum.REVIEW_REMIND.name())
                .eq(CertNotifyOutboxDO::getBizId, id)
                .ge(CertNotifyOutboxDO::getCreateTime, LocalDate.now().atStartOfDay()));
        if (urgedToday != null && urgedToday > 0) {
            log.info("[lifecycle][urge] id={} 今日已催办，幂等跳过", id);
            return;
        }
        // 派发审查提醒（发件箱可靠投递：PENDING → 渠道发送 → SUCCESS/FAILED 退避重试）
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bizType", CertNotifyBizTypeEnum.REVIEW_REMIND.name());
        payload.put("bizId", id);
        payload.put("refName", record.getRefName() == null ? "" : record.getRefName());
        payload.put("refNo", record.getRefNo() == null ? "" : record.getRefNo());
        payload.put("lifecycleType", record.getLifecycleType());
        payload.put("status", status);
        payload.put("urgedBy", operatorId);
        payload.put("urgedAt", LocalDateTime.now().toString());
        CertNotifyOutboxDO outbox = new CertNotifyOutboxDO();
        outbox.setBizType(CertNotifyBizTypeEnum.REVIEW_REMIND.name());
        outbox.setBizId(id);
        outbox.setChannel("IN_APP");
        outbox.setReceiver(String.valueOf(operatorId));
        outbox.setPayload(JsonUtils.toJsonString(payload));
        outbox.setSendStatus(CertNotifySendStatusEnum.PENDING.name());
        outbox.setRetryCount(0);
        outbox.setMaxRetry(5);
        certNotifyOutboxMapper.insert(outbox);
        log.info("[lifecycle][urge] id={} 催办完成 operator={}", id, operatorId);
    }

    @Override
    public PageResult<CertLifecycleRecordDO> pageRecord(CertLifecyclePageReqVO reqVO) {
        LambdaQueryWrapper<CertLifecycleRecordDO> query = new LambdaQueryWrapper<CertLifecycleRecordDO>()
                .eq(reqVO.getBizDomain() != null && !reqVO.getBizDomain().isBlank(),
                        CertLifecycleRecordDO::getBizDomain, reqVO.getBizDomain())
                .eq(reqVO.getBizId() != null, CertLifecycleRecordDO::getBizId, reqVO.getBizId())
                .eq(reqVO.getLifecycleType() != null && !reqVO.getLifecycleType().isBlank(),
                        CertLifecycleRecordDO::getLifecycleType, reqVO.getLifecycleType())
                .eq(reqVO.getStatus() != null && !reqVO.getStatus().isBlank(),
                        CertLifecycleRecordDO::getStatus, reqVO.getStatus())
                .eq(reqVO.getApplyNo() != null && !reqVO.getApplyNo().isBlank(),
                        CertLifecycleRecordDO::getApplyNo, reqVO.getApplyNo())
                .orderByDesc(CertLifecycleRecordDO::getAppliedAt)
                .orderByDesc(CertLifecycleRecordDO::getId);
        Page<CertLifecycleRecordDO> page = certLifecycleRecordMapper.selectPage(
                new Page<>(reqVO.getPageNo(), reqVO.getPageSize()), query);
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    @Override
    public List<CertLifecycleRecordDO> listByBiz(String bizDomain, Long bizId) {
        parseDomain(bizDomain);
        return certLifecycleRecordMapper.selectByBiz(bizDomain, bizId);
    }

    // ========== 主档回写 ==========

    /**
     * 变更类回写：按组一次性落库（同一次申请含多字段时只写一次主档）。
     *
     * <p>为什么按组写而不是逐字段写：一次变更申请常含多个字段（如"名称 + 等级"），逐字段各自 updateById
     * 会产生 N 次写库，且中途失败会留下"改了一半"的台账；这里按 bizId 归组，加载一次主档、套用全部字段、
     * 再做一次 updateById（版本 CAS），保证"一次申请 = 一次原子回写"。
     * <p>为什么受控字段白名单外的字段直接拒绝：未知字段名无法映射到主档列，静默忽略会制造
     * "流程已生效、台账没变"的假象，必须显式报错而不是吞掉。
     */
    private void writeBackChangeGroup(CertLifecycleBizDomainEnum domain, List<CertLifecycleRecordDO> rows) {
        Map<Long, List<CertLifecycleRecordDO>> rowsByBizId = rows.stream()
                .collect(Collectors.groupingBy(CertLifecycleRecordDO::getBizId, LinkedHashMap::new,
                        Collectors.toList()));
        rowsByBizId.forEach((bizId, groupRows) -> {
            switch (domain) {
                case CERT -> {
                    CertificateInfoDO cert = requireCertificate(bizId);
                    for (CertLifecycleRecordDO row : groupRows) {
                        switch (row.getFieldName()) {
                            case "certName" -> cert.setCertName(row.getNewValue());
                            case "certNo" -> cert.setCertNo(row.getNewValue());
                            case "certLevel" -> cert.setCertLevel(row.getNewValue());
                            case "certScope" -> cert.setCertScope(row.getNewValue());
                            case "holderName" -> cert.setHolderName(row.getNewValue());
                            case "validFrom" -> cert.setValidFrom(parseDate(row.getNewValue()));
                            case "validTo" -> cert.setValidTo(parseDate(row.getNewValue()));
                            case "nextReviewDate" -> cert.setNextReviewDate(parseDate(row.getNewValue()));
                            case "certStatus" -> cert.setCertStatus(row.getNewValue());
                            default -> throw ServiceExceptionUtil.exception(
                                    ErrorCodeConstants.CERT_LIFECYCLE_BIZ_DOMAIN_INVALID, row.getFieldName());
                        }
                    }
                    if (certificateInfoMapper.updateById(cert) == 0) {
                        throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_DATA_MODIFIED);
                    }
                }
                case PERSON_CERT -> {
                    PersonnelCertificateDO cert = requirePersonnelCertificate(bizId);
                    for (CertLifecycleRecordDO row : groupRows) {
                        switch (row.getFieldName()) {
                            case "certName" -> cert.setCertName(row.getNewValue());
                            case "certNo" -> cert.setCertNo(row.getNewValue());
                            case "certLevel" -> cert.setCertLevel(row.getNewValue());
                            case "certSubType" -> cert.setCertSubType(row.getNewValue());
                            case "major" -> cert.setMajor(row.getNewValue());
                            case "registerUnit" -> cert.setRegisterUnit(row.getNewValue());
                            case "registerNo" -> cert.setRegisterNo(row.getNewValue());
                            case "validFrom" -> cert.setValidFrom(parseDate(row.getNewValue()));
                            case "validTo" -> cert.setValidTo(parseDate(row.getNewValue()));
                            case "nextReviewDate" -> cert.setNextReviewDate(parseDate(row.getNewValue()));
                            case "certStatus" -> cert.setCertStatus(row.getNewValue());
                            default -> throw ServiceExceptionUtil.exception(
                                    ErrorCodeConstants.CERT_LIFECYCLE_BIZ_DOMAIN_INVALID, row.getFieldName());
                        }
                    }
                    if (personnelCertificateMapper.updateById(cert) == 0) {
                        throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_DATA_MODIFIED);
                    }
                }
                case ENTERPRISE_QUAL -> {
                    EnterpriseQualificationDO qual = requireQualification(bizId);
                    for (CertLifecycleRecordDO row : groupRows) {
                        switch (row.getFieldName()) {
                            case "certNo" -> qual.setCertNo(row.getNewValue());
                            case "qualGrade" -> qual.setQualGrade(row.getNewValue());
                            case "approvalScope" -> qual.setApprovalScope(row.getNewValue());
                            case "validTo" -> qual.setValidTo(parseDate(row.getNewValue()));
                            default -> throw ServiceExceptionUtil.exception(
                                    ErrorCodeConstants.CERT_LIFECYCLE_BIZ_DOMAIN_INVALID, row.getFieldName());
                        }
                    }
                    if (enterpriseQualificationMapper.updateById(qual) == 0) {
                        throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_DATA_MODIFIED);
                    }
                }
                case SAFETY_PERMIT -> {
                    QualificationSafetyPermitDO permit = requireSafetyPermit(bizId);
                    for (CertLifecycleRecordDO row : groupRows) {
                        switch (row.getFieldName()) {
                            case "permitNo" -> permit.setPermitNo(row.getNewValue());
                            case "legalPerson" -> permit.setLegalPerson(row.getNewValue());
                            case "validTo" -> permit.setValidTo(parseDate(row.getNewValue()));
                            default -> throw ServiceExceptionUtil.exception(
                                    ErrorCodeConstants.CERT_LIFECYCLE_BIZ_DOMAIN_INVALID, row.getFieldName());
                        }
                    }
                    if (qualificationSafetyPermitMapper.updateById(permit) == 0) {
                        throw ServiceExceptionUtil.exception(ErrorCodeConstants.SAFETY_PERMIT_STATUS_INVALID);
                    }
                }
                default -> throw ServiceExceptionUtil.exception(
                        ErrorCodeConstants.CERT_LIFECYCLE_BIZ_DOMAIN_INVALID, domain.name());
            }
        });
    }

    /** 续期/注销/年检生效回写。 */
    private void writeBackProcess(CertLifecycleBizDomainEnum domain, CertLifecycleTypeEnum type,
                                  CertLifecycleRecordDO record) {
        switch (type) {
            case RENEW -> writeBackRenew(domain, record);
            case REVOKE -> writeBackRevoke(domain, record);
            case ANNUAL -> writeBackAnnual(domain, record);
            default -> throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_BIZ_DOMAIN_INVALID,
                    type.name());
        }
    }

    /**
     * 续期生效：刷新有效期 +（补换证时）新证书编号 + 状态回到有效。
     *
     * <p>为什么续期要顺带把状态拉回 VALID：续期业务含义就是"证件重新有效"，若只改日期不改状态，
     * 台账会停留在 EXPIRED/EXPIRING_SOON，预警与配证判断都会继续误判。
     */
    private void writeBackRenew(CertLifecycleBizDomainEnum domain, CertLifecycleRecordDO record) {
        LocalDate newValidFrom = record.getNewValidFrom();
        LocalDate newValidTo = record.getNewValidTo();
        LocalDate newReviewDate = record.getNewNextReviewDate();
        String newCertNo = record.getNewCertNo();
        switch (domain) {
            case CERT -> {
                CertificateInfoDO cert = requireCertificate(record.getBizId());
                if (newValidFrom != null) {
                    cert.setValidFrom(newValidFrom);
                }
                cert.setValidTo(newValidTo);
                if (newReviewDate != null) {
                    cert.setNextReviewDate(newReviewDate);
                }
                if (newCertNo != null && !newCertNo.isBlank()) {
                    cert.setCertNo(newCertNo);
                }
                cert.setCertStatus("ACTIVE");
                if (certificateInfoMapper.updateById(cert) == 0) {
                    throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_DATA_MODIFIED);
                }
            }
            case PERSON_CERT -> {
                PersonnelCertificateDO cert = requirePersonnelCertificate(record.getBizId());
                if (newValidFrom != null) {
                    cert.setValidFrom(newValidFrom);
                }
                cert.setValidTo(newValidTo);
                if (newReviewDate != null) {
                    cert.setNextReviewDate(newReviewDate);
                }
                if (newCertNo != null && !newCertNo.isBlank()) {
                    cert.setCertNo(newCertNo);
                }
                cert.setCertStatus(PersonnelCertStatusEnum.VALID.name());
                if (personnelCertificateMapper.updateById(cert) == 0) {
                    throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_DATA_MODIFIED);
                }
            }
            case ENTERPRISE_QUAL -> {
                EnterpriseQualificationDO qual = requireQualification(record.getBizId());
                qual.setValidTo(newValidTo);
                qual.setQualStatus("ACTIVE");
                if (enterpriseQualificationMapper.updateById(qual) == 0) {
                    throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_DATA_MODIFIED);
                }
            }
            case SAFETY_PERMIT -> {
                QualificationSafetyPermitDO permit = requireSafetyPermit(record.getBizId());
                permit.setValidTo(newValidTo);
                permit.setPermitStatus("VALID");
                if (qualificationSafetyPermitMapper.updateById(permit) == 0) {
                    throw ServiceExceptionUtil.exception(ErrorCodeConstants.SAFETY_PERMIT_STATUS_INVALID);
                }
            }
            default -> throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_BIZ_DOMAIN_INVALID,
                    domain.name());
        }
    }

    /** 注销/吊销生效：状态按 CertRevokeTypeEnum 映射（恢复=RESTORE 回到有效）。 */
    private void writeBackRevoke(CertLifecycleBizDomainEnum domain, CertLifecycleRecordDO record) {
        String targetStatus = parseRevokeType(record.getRevokeType()).getTargetStatus();
        switch (domain) {
            case CERT -> {
                CertificateInfoDO cert = requireCertificate(record.getBizId());
                cert.setCertStatus(targetStatus);
                if (certificateInfoMapper.updateById(cert) == 0) {
                    throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_DATA_MODIFIED);
                }
            }
            case PERSON_CERT -> {
                PersonnelCertificateDO cert = requirePersonnelCertificate(record.getBizId());
                cert.setCertStatus(targetStatus);
                if (personnelCertificateMapper.updateById(cert) == 0) {
                    throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_DATA_MODIFIED);
                }
            }
            case ENTERPRISE_QUAL -> {
                EnterpriseQualificationDO qual = requireQualification(record.getBizId());
                qual.setQualStatus(targetStatus);
                if (enterpriseQualificationMapper.updateById(qual) == 0) {
                    throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_DATA_MODIFIED);
                }
            }
            case SAFETY_PERMIT -> {
                QualificationSafetyPermitDO permit = requireSafetyPermit(record.getBizId());
                permit.setPermitStatus(targetStatus);
                if (qualificationSafetyPermitMapper.updateById(permit) == 0) {
                    throw ServiceExceptionUtil.exception(ErrorCodeConstants.SAFETY_PERMIT_STATUS_INVALID);
                }
            }
            default -> throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_BIZ_DOMAIN_INVALID,
                    domain.name());
        }
    }

    /**
     * 年检生效：通过时把"下次年检日"推进一个周期，未通过时保持状态并留痕。
     *
     * <p>为什么下次年检日优先取流程里填写的值：ISO 监督审核间隔、特种设备复审周期常按证书实际规则约定，
     * 由办理人填写比按"固定周期推算"更可靠；未填写才回落到周期推算（certificate_info.annualReviewCycle）。
     */
    private void writeBackAnnual(CertLifecycleBizDomainEnum domain, CertLifecycleRecordDO record) {
        boolean passed = record.getReviewResult() == null || record.getReviewResult() == 1;
        switch (domain) {
            case CERT -> {
                CertificateInfoDO cert = requireCertificate(record.getBizId());
                if (passed && record.getNewNextReviewDate() != null) {
                    cert.setNextReviewDate(record.getNewNextReviewDate());
                } else if (passed && cert.getAnnualReviewCycle() != null && record.getReviewDate() != null) {
                    cert.setNextReviewDate(record.getReviewDate().plusMonths(cert.getAnnualReviewCycle()));
                }
                if (!passed) {
                    cert.setCertStatus("SUSPENDED");
                }
                if (certificateInfoMapper.updateById(cert) == 0) {
                    throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_DATA_MODIFIED);
                }
            }
            case PERSON_CERT -> {
                PersonnelCertificateDO cert = requirePersonnelCertificate(record.getBizId());
                if (passed && record.getNewNextReviewDate() != null) {
                    cert.setNextReviewDate(record.getNewNextReviewDate());
                } else if (passed && record.getReviewDate() != null) {
                    cert.setNextReviewDate(record.getReviewDate().plusYears(1));
                }
                if (!passed) {
                    cert.setCertStatus(PersonnelCertStatusEnum.SUSPENDED.name());
                }
                if (personnelCertificateMapper.updateById(cert) == 0) {
                    throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_DATA_MODIFIED);
                }
            }
            case ENTERPRISE_QUAL, SAFETY_PERMIT -> {
                // 企业资质/安许证的年检（动态核查）由各自扫描任务负责档位与风险标记，此处仅留痕年检结论
                log.info("[cert-lifecycle][annual] domain={} refId={} result={}", domain, record.getBizId(),
                        record.getReviewResult());
            }
            default -> throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_BIZ_DOMAIN_INVALID,
                    domain.name());
        }
    }

    // ========== 存储形态转换（VO List<Long> ↔ DB json 文本）==========

    /** List&lt;Long&gt; → JSON 串；空集合落 NULL（避免 DB 里出现 "[]" 与 NULL 两种"空"）。 */
    private String toJsonIds(java.util.List<Long> ids) {
        return (ids == null || ids.isEmpty()) ? null : JsonUtils.toJsonString(ids);
    }

    /** JSON 串 → List&lt;Long&gt;；脏数据不抛异常（返回空列表），避免一条坏记录让整页 500。 */
    private java.util.List<Long> parseIds(String json) {
        if (json == null || json.isBlank()) {
            return java.util.Collections.emptyList();
        }
        try {
            java.util.List<Long> ids = JsonUtils.parseArray(json, Long.class);
            return ids == null ? java.util.Collections.emptyList() : ids;
        } catch (Exception ex) {
            log.warn("[cert-lifecycle][parseIds] 佐证材料 JSON 解析失败，已按空处理：{}", ex.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    @Override
    public CertLifecycleRespVO toRespVO(CertLifecycleRecordDO record) {
        if (record == null) {
            return null;
        }
        CertLifecycleRespVO vo = new CertLifecycleRespVO();
        org.springframework.beans.BeanUtils.copyProperties(record, vo);
        vo.setEvidenceFileIds(parseIds(record.getEvidenceFileIds()));
        // 契约 Q16（2026-09-28）：附件清单 files[]——newCertFileId 从本地台账解析文件名/类别/上传时间；
        // 佐证材料（evidenceFileIds）是 FileApi 主键，infra 仅开放按 URL 查元数据，无按 id 查询，故 fileName 留空
        vo.setFiles(buildFiles(record));
        return vo;
    }

    /**
     * 附件清单组装（契约 Q16）：主件 newCertFileId → 本地台账（cert_file_versions）解析文件名/类别/上传时间；
     * 佐证材料 → 仅 fileId（infra 无按 id 查元数据接口，文件名由前端经上传回执/URL 通道补齐）。
     */
    private List<CertLifecycleFileRespVO> buildFiles(CertLifecycleRecordDO record) {
        List<CertLifecycleFileRespVO> files = new ArrayList<>();
        if (record.getNewCertFileId() != null) {
            CertFileVersionDO fv = certFileVersionMapper.selectById(record.getNewCertFileId());
            if (fv != null) {
                CertLifecycleFileRespVO item = new CertLifecycleFileRespVO();
                item.setFileId(fv.getFileId());
                item.setFileName(fv.getFileName());
                item.setFileCategory(fv.getFileCategory());
                item.setUploadTime(fv.getCreateTime());
                files.add(item);
            }
        }
        for (Long fileId : parseIds(record.getEvidenceFileIds())) {
            CertLifecycleFileRespVO item = new CertLifecycleFileRespVO();
            item.setFileId(fileId);
            files.add(item);
        }
        return files;
    }

    // ========== 内部 ==========

    private CertLifecycleRecordDO buildBaseRecord(CertLifecycleBizDomainEnum domain, CertLifecycleTypeEnum type,
                                                  CertLifecycleApplyReqVO reqVO, RefSnapshot snapshot,
                                                  String applyNo) {
        CertLifecycleRecordDO row = new CertLifecycleRecordDO();
        row.setBizDomain(domain.name());
        row.setBizId(reqVO.getBizId());
        row.setRefName(snapshot.name());
        row.setRefNo(snapshot.no());
        row.setEntId(snapshot.entId());
        row.setLifecycleType(type.name());
        row.setStatus(CertLifecycleStatusEnum.PENDING.name());
        row.setApplyNo(applyNo);
        row.setNewCertFileId(reqVO.getNewCertFileId());
        // 存储形态转换：VO 的 List<Long> → DB json 列文本（规范 §四：转换收敛在 Service）
        row.setEvidenceFileIds(toJsonIds(reqVO.getEvidenceFileIds()));
        row.setRemark(reqVO.getRemark());
        row.setAppliedBy(currentUserId());
        row.setAppliedAt(LocalDateTime.now());
        return row;
    }

    /** 幂等插入：幂等键 = domain:bizId:type:applyNo[:fieldName]，重试同一申请单不会产生重复流程。 */
    private void insertWithIdempotency(CertLifecycleRecordDO row, String applyNo, String fieldName) {
        String key = row.getBizDomain() + ":" + row.getBizId() + ":" + row.getLifecycleType() + ":" + applyNo
                + (fieldName == null ? "" : ":" + fieldName);
        if (certLifecycleRecordMapper.selectByIdempotentKey(key) != null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_APPLY_NO_DUPLICATE);
        }
        row.setIdempotentKey(key);
        certLifecycleRecordMapper.insert(row);
    }

    private void updateWithCas(CertLifecycleRecordDO record) {
        if (certLifecycleRecordMapper.updateById(record) == 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_DATA_MODIFIED);
        }
    }

    private CertLifecycleRecordDO validateExists(Long id) {
        CertLifecycleRecordDO record = id == null ? null : certLifecycleRecordMapper.selectById(id);
        if (record == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_NOT_EXISTS);
        }
        return record;
    }

    private CertLifecycleBizDomainEnum parseDomain(String code) {
        try {
            return CertLifecycleBizDomainEnum.valueOf(code);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_BIZ_DOMAIN_INVALID, code);
        }
    }

    private CertLifecycleTypeEnum parseType(String code) {
        try {
            return CertLifecycleTypeEnum.valueOf(code);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_TARGET_REQUIRED, "生命周期类型");
        }
    }

    private CertLifecycleStatusEnum parseStatus(String code) {
        try {
            return CertLifecycleStatusEnum.valueOf(code);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_STATUS_INVALID);
        }
    }

    private CertRevokeTypeEnum parseRevokeType(String code) {
        try {
            return CertRevokeTypeEnum.valueOf(code);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_TARGET_REQUIRED, "注销吊销类型");
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_TARGET_REQUIRED,
                    "日期格式（yyyy-MM-dd）：" + value);
        }
    }

    private CertificateInfoDO requireCertificate(Long id) {
        CertificateInfoDO cert = certificateInfoMapper.selectById(id);
        if (cert == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_BIZ_NOT_EXISTS);
        }
        return cert;
    }

    private PersonnelCertificateDO requirePersonnelCertificate(Long id) {
        PersonnelCertificateDO cert = personnelCertificateMapper.selectById(id);
        if (cert == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_BIZ_NOT_EXISTS);
        }
        return cert;
    }

    private EnterpriseQualificationDO requireQualification(Long id) {
        EnterpriseQualificationDO qual = enterpriseQualificationMapper.selectById(id);
        if (qual == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_BIZ_NOT_EXISTS);
        }
        return qual;
    }

    private QualificationSafetyPermitDO requireSafetyPermit(Long id) {
        QualificationSafetyPermitDO permit = qualificationSafetyPermitMapper.selectById(id);
        if (permit == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERT_LIFECYCLE_BIZ_NOT_EXISTS);
        }
        return permit;
    }

    /** 业务对象快照（名称/编号/企业/有效期），用于流程记录留痕与续期 old 值对照。 */
    private RefSnapshot loadRefSnapshot(CertLifecycleBizDomainEnum domain, Long bizId) {
        return switch (domain) {
            case CERT -> {
                CertificateInfoDO cert = requireCertificate(bizId);
                yield new RefSnapshot(cert.getCertName(), cert.getCertNo(), cert.getOrgId(),
                        cert.getValidFrom(), cert.getValidTo());
            }
            case PERSON_CERT -> {
                PersonnelCertificateDO cert = requirePersonnelCertificate(bizId);
                yield new RefSnapshot(cert.getCertName(), cert.getCertNo(), cert.getEntId(),
                        cert.getValidFrom(), cert.getValidTo());
            }
            case ENTERPRISE_QUAL -> {
                EnterpriseQualificationDO qual = requireQualification(bizId);
                yield new RefSnapshot(qual.getQualGrade() + qual.getQualCategory(), qual.getCertNo(), qual.getEntId(),
                        null, qual.getValidTo());
            }
            case SAFETY_PERMIT -> {
                QualificationSafetyPermitDO permit = requireSafetyPermit(bizId);
                yield new RefSnapshot("安全生产许可证", permit.getPermitNo(), permit.getEntId(),
                        permit.getIssueDate(), permit.getValidTo());
            }
        };
    }

    /** 当前操作人：优先取登录上下文，任务/测试环境无上下文时返回 null（不做伪造成"系统用户"）。 */
    private Long currentUserId() {
        try {
            return cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId();
        } catch (Exception e) {
            return null;
        }
    }

    private String appendRemark(String origin, String append) {
        if (append == null || append.isBlank()) {
            return origin;
        }
        return (origin == null || origin.isBlank()) ? append : origin + "；" + append;
    }

    private record RefSnapshot(String name, String no, Long entId, LocalDate validFrom, LocalDate validTo) {
    }
}
