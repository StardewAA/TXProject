package cn.iocoder.txgy.module.bidding.service.qualification;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.EnterpriseQualificationSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertificateInfoDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.EnterpriseQualificationDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationApplyRecordDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertificateInfoMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.EnterpriseQualificationMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationApplyRecordMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.QualificationApplyStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.QualificationApplyTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.QualificationSerialEnum;
import cn.iocoder.txgy.module.bidding.enums.QualificationStatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 企业资质主档/申报流服务实现（T 轨；延续申报与审批办结同事务编排）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-1/15-7（状态机、办结回写同事务+version 乐观锁、
 * binded_cert_id 边界）；《招投标模块开发规范》§4.5（状态流转接口化/乐观锁 CAS）。
 * <p>设计遵循/针对：① UK 判重防重复建档；② binded_cert_id 校验通用台账存在性（边界 §十五：影像/验真归
 * certificate_info）；③ 申报流只追加状态变更（状态机 + version CAS），延续办结回写主档 VALID+valid_to；
 * ④ 审批退回还原 VALID，临近到期由预警扫描（M-later QUAL_EXPIRY 并入）重新置 EXPIRING_SOON；
 * ⑤ 本模块当前版本申报仅支持延续 RENEW（其余类型走核查编排后再扩展，报 QUAL_APPLY_PARAM_INVALID）。
 */
@Slf4j
@Service
public class EnterpriseQualificationServiceImpl implements EnterpriseQualificationService {

    @Resource
    private EnterpriseQualificationMapper enterpriseQualificationMapper;
    @Resource
    private QualificationApplyRecordMapper qualificationApplyRecordMapper;
    @Resource
    private CertificateInfoMapper certificateInfoMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createQualification(EnterpriseQualificationSaveReqVO reqVO) {
        // UK 判重（15-1：tenant+serial+category+grade+cert_no；建档幂等，规范 §4.5）
        if (enterpriseQualificationMapper.selectByCertNo(reqVO.getEntId(), reqVO.getQualSerial(),
                reqVO.getQualCategory(), reqVO.getQualGrade(), reqVO.getCertNo()) != null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_NO_DUPLICATE);
        }
        // 关联台账证书存在性校验（§十五 边界：影像/验真复用 certificate_info）
        if (reqVO.getBindedCertId() != null
                && certificateInfoMapper.selectById(reqVO.getBindedCertId()) == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERTIFICATE_NOT_EXISTS);
        }
        EnterpriseQualificationDO qual = new EnterpriseQualificationDO();
        qual.setEntId(reqVO.getEntId());
        qual.setQualSerial(validateSerial(reqVO.getQualSerial()));
        qual.setQualCategory(reqVO.getQualCategory());
        qual.setQualGrade(reqVO.getQualGrade());
        qual.setCertNo(reqVO.getCertNo());
        qual.setIssuingAuthority(reqVO.getIssuingAuthority());
        qual.setIssueDate(reqVO.getIssueDate());
        qual.setValidTo(reqVO.getValidTo());
        qual.setApprovalScope(reqVO.getApprovalScope());
        qual.setQualStatus(QualificationStatusEnum.VALID.name());
        qual.setBindedCertId(reqVO.getBindedCertId());
        qual.setSourceChannel("MANUAL");
        qual.setRemark(reqVO.getRemark());
        enterpriseQualificationMapper.insert(qual);
        log.info("[qual][create] ent={} qual={} 建档完成 id={}", reqVO.getEntId(), reqVO.getCertNo(), qual.getId());
        return qual.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long qualificationId, String targetStatus, Integer version, String reason,
                             Long operatorId) {
        EnterpriseQualificationDO qual = loadQualification(qualificationId);
        ensureVersion(version, qual.getVersion());
        QualificationStatusEnum from = parseQualStatus(qual.getQualStatus());
        QualificationStatusEnum to;
        try {
            to = QualificationStatusEnum.valueOf(targetStatus);
        } catch (IllegalArgumentException e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_STATUS_TRANSITION_INVALID);
        }
        if (!QualificationStatusEnum.isTransitionAllowed(from, to)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_STATUS_TRANSITION_INVALID);
        }
        qual.setQualStatus(to.name());
        if (StringUtils.hasText(reason)) {
            qual.setRemark(appendReason(qual.getRemark(), reason));
        }
        if (enterpriseQualificationMapper.updateById(qual) == 0) { // @Version CAS
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_DATA_MODIFIED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitRenewalApply(Long qualificationId, Integer version, Long ownerBy, LocalDate dueDate,
                                   String remark) {
        EnterpriseQualificationDO qual = loadQualification(qualificationId);
        ensureVersion(version, qual.getVersion());
        QualificationStatusEnum from = parseQualStatus(qual.getQualStatus());
        if (!QualificationStatusEnum.isTransitionAllowed(from, QualificationStatusEnum.PENDING_RENEW)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_STATUS_TRANSITION_INVALID);
        }
        // 申报流 SUBMITTED（同事务）
        QualificationApplyRecordDO apply = new QualificationApplyRecordDO();
        apply.setEntId(qual.getEntId());
        apply.setQualId(qualificationId);
        apply.setApplyType(QualificationApplyTypeEnum.RENEW.name());
        apply.setSubmitDate(LocalDate.now());
        apply.setDueDate(dueDate);
        apply.setStatus(QualificationApplyStatusEnum.SUBMITTED.name());
        apply.setOwnerBy(ownerBy);
        apply.setRemark(remark);
        qualificationApplyRecordMapper.insert(apply);
        // 主档 → PENDING_RENEW（待续期，15-7）
        qual.setQualStatus(QualificationStatusEnum.PENDING_RENEW.name());
        if (enterpriseQualificationMapper.updateById(qual) == 0) { // @Version CAS
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_DATA_MODIFIED);
        }
        return apply.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long approveRenewalApply(Long applyId, Integer version, String resultNo, String newCertNo,
                                    LocalDate newValidTo, Long reviewerId) {
        QualificationApplyRecordDO apply = loadApply(applyId);
        ensureVersion(version, apply.getVersion());
        if (!QualificationApplyTypeEnum.RENEW.name().equals(apply.getApplyType())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUAL_APPLY_PARAM_INVALID);
        }
        QualificationApplyStatusEnum from = parseApplyStatus(apply.getStatus());
        if (!QualificationApplyStatusEnum.isTransitionAllowed(from, QualificationApplyStatusEnum.APPROVED)
                && !QualificationApplyStatusEnum.isTransitionAllowed(from, QualificationApplyStatusEnum.ISSUED)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUAL_APPLY_STATUS_INVALID);
        }
        if (newValidTo == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUAL_APPLY_PARAM_INVALID);
        }
        // 申报 → APPROVED（发文）→ ISSUED（领证）
        apply.setStatus(QualificationApplyStatusEnum.APPROVED.name());
        apply.setResultNo(resultNo);
        apply.setNewCertNo(newCertNo);
        apply.setNewValidTo(newValidTo);
        apply.setUpdater(String.valueOf(reviewerId));
        qualificationApplyRecordMapper.updateById(apply);
        apply.setStatus(QualificationApplyStatusEnum.ISSUED.name());
        qualificationApplyRecordMapper.updateById(apply);

        // 办结回写主档（15-7：同事务 + version 乐观锁）
        EnterpriseQualificationDO qual = loadQualification(apply.getQualId());
        qual.setQualStatus(QualificationStatusEnum.VALID.name());
        qual.setValidTo(newValidTo);
        if (StringUtils.hasText(newCertNo)) {
            qual.setCertNo(newCertNo);
        }
        if (enterpriseQualificationMapper.updateById(qual) == 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_DATA_MODIFIED);
        }
        return apply.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long rejectRenewalApply(Long applyId, Integer version, Long reviewerId, String remark) {
        QualificationApplyRecordDO apply = loadApply(applyId);
        ensureVersion(version, apply.getVersion());
        QualificationApplyStatusEnum from = parseApplyStatus(apply.getStatus());
        if (!QualificationApplyStatusEnum.isTransitionAllowed(from, QualificationApplyStatusEnum.REJECTED)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUAL_APPLY_STATUS_INVALID);
        }
        apply.setStatus(QualificationApplyStatusEnum.REJECTED.name());
        apply.setUpdater(String.valueOf(reviewerId));
        if (StringUtils.hasText(remark)) {
            apply.setRemark(remark);
        }
        qualificationApplyRecordMapper.updateById(apply);
        // 主档 PENDING_RENEW → VALID 还原（15-1 联动；临近到期由预警扫描重新置 EXPIRING_SOON）
        EnterpriseQualificationDO qual = loadQualification(apply.getQualId());
        if (QualificationStatusEnum.PENDING_RENEW.name().equals(qual.getQualStatus())) {
            qual.setQualStatus(QualificationStatusEnum.VALID.name());
            if (enterpriseQualificationMapper.updateById(qual) == 0) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_DATA_MODIFIED);
            }
        }
        return apply.getId();
    }

    // ========== 内部工具 ==========

    private EnterpriseQualificationDO loadQualification(Long qualificationId) {
        EnterpriseQualificationDO qual = enterpriseQualificationMapper.selectById(qualificationId);
        if (qual == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_NOT_EXISTS);
        }
        return qual;
    }

    private QualificationApplyRecordDO loadApply(Long applyId) {
        QualificationApplyRecordDO apply = qualificationApplyRecordMapper.selectById(applyId);
        if (apply == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUAL_APPLY_NOT_EXISTS);
        }
        return apply;
    }

    private void ensureVersion(Integer clientVersion, Integer currentVersion) {
        if (clientVersion == null || !Objects.equals(clientVersion, currentVersion)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_DATA_MODIFIED);
        }
    }

    private String validateSerial(String qualSerial) {
        try {
            return QualificationSerialEnum.valueOf(qualSerial).name();
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_STATUS_TRANSITION_INVALID);
        }
    }

    private QualificationStatusEnum parseQualStatus(String status) {
        try {
            return QualificationStatusEnum.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_STATUS_TRANSITION_INVALID);
        }
    }

    private QualificationApplyStatusEnum parseApplyStatus(String status) {
        try {
            return QualificationApplyStatusEnum.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUAL_APPLY_STATUS_INVALID);
        }
    }

    private String appendReason(String current, String reason) {
        return current == null || current.isBlank() ? reason : current + "；" + reason;
    }
}
