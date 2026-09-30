package cn.iocoder.txgy.module.bidding.service.qualification;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationSafetyPermitDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertificateInfoMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationSafetyPermitMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.SafetyPermitStatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 安全生产许可证服务实现（建档 UK 判重 + 状态机显式迁移）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-8（permit_status 状态机 + version CAS、
 * binded_cert_id 影像边界）；《招投标模块开发规范》§4.5（乐观锁）。
 * <p>设计遵循/针对：状态流转仅接受 SafetyPermitStatusEnum 合法迁移；到期 EXPIRING_SOON 由 QUAL_EXPIRY
 * 扫描经本接口联动（与人工/申报路径共用迁移表，防非法直达）。
 */
@Slf4j
@Service
public class QualificationSafetyPermitServiceImpl implements QualificationSafetyPermitService {

    @Resource
    private QualificationSafetyPermitMapper qualificationSafetyPermitMapper;
    @Resource
    private CertificateInfoMapper certificateInfoMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPermit(Long entId, String permitNo, String issuingAuthority, LocalDate issueDate,
                             LocalDate validTo, String legalPerson, Long bindedCertId, String remark) {
        if (qualificationSafetyPermitMapper.selectByPermitNo(permitNo) != null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SAFETY_PERMIT_NO_DUPLICATE);
        }
        if (bindedCertId != null && certificateInfoMapper.selectById(bindedCertId) == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CERTIFICATE_NOT_EXISTS);
        }
        QualificationSafetyPermitDO permit = new QualificationSafetyPermitDO();
        permit.setEntId(entId);
        permit.setPermitNo(permitNo);
        permit.setIssuingAuthority(issuingAuthority);
        permit.setIssueDate(issueDate);
        permit.setValidTo(validTo);
        permit.setPermitStatus(SafetyPermitStatusEnum.VALID.name());
        permit.setLegalPerson(legalPerson);
        permit.setBindedCertId(bindedCertId);
        permit.setRemark(remark);
        qualificationSafetyPermitMapper.insert(permit);
        log.info("[safety-permit][create] permit={} id={}", permitNo, permit.getId());
        return permit.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long permitId, String targetStatus, Integer version, String reason) {
        QualificationSafetyPermitDO permit = qualificationSafetyPermitMapper.selectById(permitId);
        if (permit == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SAFETY_PERMIT_NOT_EXISTS);
        }
        if (version == null || !Objects.equals(version, permit.getVersion())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SAFETY_PERMIT_STATUS_INVALID);
        }
        SafetyPermitStatusEnum from;
        SafetyPermitStatusEnum to;
        try {
            from = SafetyPermitStatusEnum.valueOf(permit.getPermitStatus());
            to = SafetyPermitStatusEnum.valueOf(targetStatus);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SAFETY_PERMIT_STATUS_INVALID);
        }
        if (!SafetyPermitStatusEnum.isTransitionAllowed(from, to)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SAFETY_PERMIT_STATUS_INVALID);
        }
        permit.setPermitStatus(to.name());
        if (permit.getRemark() == null || permit.getRemark().isBlank()) {
            permit.setRemark(reason);
        } else if (reason != null && !reason.isBlank()) {
            permit.setRemark(permit.getRemark() + "；" + reason);
        }
        if (qualificationSafetyPermitMapper.updateById(permit) == 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SAFETY_PERMIT_STATUS_INVALID);
        }
    }
}
