package cn.iocoder.txgy.module.hrm.service.certificate;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.renewal.HrmCertificateRenewalCreateReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.renewal.HrmCertificateRenewalRespVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.certificate.HrmCertificateRenewalDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.certificate.HrmCertificateTypeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.experience.HrmEmployeeCertificateDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.certificate.HrmCertificateRenewalMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.experience.HrmEmployeeCertificateMapper;
import cn.iocoder.txgy.module.hrm.enums.certificate.HrmCertificateRenewalTypeEnum;
import cn.iocoder.txgy.module.hrm.enums.certificate.HrmCertificateStatusEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.txgy.framework.common.util.date.LocalDateTimeUtils.getDayBeginTime;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_LEDGER_ATTACHMENT_REQUIRED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_LEDGER_CANCELLED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_LEDGER_END_TIME_INVALID;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_RENEWAL_REVIEW_TIME_NOT_ALLOWED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_RENEWAL_REVIEW_TIME_REQUIRED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_RENEWAL_TIME_INVALID;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_RENEWAL_TYPE_INVALID;

/**
 * HRM 证件续证/复审 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class HrmCertificateRenewalServiceImpl implements HrmCertificateRenewalService {

    @Resource
    private HrmCertificateRenewalMapper certificateRenewalMapper;
    @Resource
    private HrmEmployeeCertificateMapper certificateMapper;
    @Resource
    private HrmCertificateLedgerService certificateLedgerService;
    @Resource
    private HrmCertificateTypeService certificateTypeService;
    @Resource
    private HrmCertificateStatusCalculator certificateStatusCalculator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCertificateRenewal(HrmCertificateRenewalCreateReqVO reqVO) {
        // 1. 校验证件存在且未注销
        HrmEmployeeCertificateDO certificate = certificateLedgerService.validateCertificateLedgerExists(
                reqVO.getCertificateId());
        if (Objects.equals(certificate.getStatus(), HrmCertificateStatusEnum.CANCELLED.getStatus())) {
            throw exception(CERTIFICATE_LEDGER_CANCELLED);
        }
        // 2. 校验续证/复审类型与必填项
        HrmCertificateRenewalTypeEnum renewalType = HrmCertificateRenewalTypeEnum.valueOf(reqVO.getType());
        if (renewalType == null) {
            throw exception(CERTIFICATE_RENEWAL_TYPE_INVALID);
        }
        validateReviewTime(renewalType, reqVO.getReviewTime());
        validateNewValidity(certificate, reqVO);
        HrmCertificateTypeDO certificateType = certificate.getTypeId() == null
                ? null : certificateTypeService.validateCertificateTypeEnabled(certificate.getTypeId());
        validateAttachmentRequired(certificateType, reqVO.getAttachmentUrl());
        // 3. 追加历史记录，原证件信息以快照保存
        HrmCertificateRenewalDO renewal = BeanUtils.toBean(reqVO, HrmCertificateRenewalDO.class)
                .setEmployeeId(certificate.getEmployeeId())
                .setOldNo(certificate.getNo())
                .setOldStartTime(certificate.getStartTime())
                .setOldEndTime(certificate.getEndTime())
                .setNewStartTime(getDayBeginTime(reqVO.getNewStartTime()))
                .setNewEndTime(getDayBeginTime(reqVO.getNewEndTime()))
                .setReviewTime(getDayBeginTime(reqVO.getReviewTime()))
                .setIssuingTime(getDayBeginTime(reqVO.getIssuingTime()))
                .setOperatorUserId(getLoginUserId())
                .setRenewTime(LocalDateTime.now());
        certificateRenewalMapper.insert(renewal);
        // 4. 台账主表更新为办理后的新值，并重算状态
        HrmEmployeeCertificateDO updateObj = HrmEmployeeCertificateDO.builder()
                .id(certificate.getId())
                .no(reqVO.getNewNo())
                .startTime(getDayBeginTime(reqVO.getNewStartTime()))
                .endTime(getDayBeginTime(reqVO.getNewEndTime()))
                .reviewTime(getDayBeginTime(reqVO.getReviewTime()))
                .issuingAuthority(reqVO.getIssuingAuthority())
                .issuingTime(getDayBeginTime(reqVO.getIssuingTime()))
                .attachmentUrl(reqVO.getAttachmentUrl())
                .build();
        updateObj.setStatus(certificateStatusCalculator.calculateStatus(
                HrmEmployeeCertificateDO.builder()
                        .id(certificate.getId())
                        .status(certificate.getStatus())
                        .endTime(updateObj.getEndTime() == null ? certificate.getEndTime() : updateObj.getEndTime())
                        .build(),
                certificateType, LocalDate.now()));
        certificateMapper.updateById(updateObj);
        return renewal.getId();
    }

    @Override
    public List<HrmCertificateRenewalRespVO> getCertificateRenewalList(Long certificateId) {
        return BeanUtils.toBean(certificateRenewalMapper.selectListByCertificateId(certificateId),
                HrmCertificateRenewalRespVO.class);
    }

    @Override
    public List<HrmCertificateRenewalRespVO> getCertificateRenewalListByEmployeeId(Long employeeId) {
        if (employeeId == null) {
            return Collections.emptyList();
        }
        return convertList(certificateRenewalMapper.selectListByEmployeeId(employeeId),
                renewal -> BeanUtils.toBean(renewal, HrmCertificateRenewalRespVO.class));
    }

    /**
     * 复审必须填写复审日期，续证不允许填写复审日期
     */
    private void validateReviewTime(HrmCertificateRenewalTypeEnum renewalType, LocalDateTime reviewTime) {
        if (renewalType == HrmCertificateRenewalTypeEnum.REVIEW && reviewTime == null) {
            throw exception(CERTIFICATE_RENEWAL_REVIEW_TIME_REQUIRED);
        }
        if (renewalType == HrmCertificateRenewalTypeEnum.RENEW && reviewTime != null) {
            throw exception(CERTIFICATE_RENEWAL_REVIEW_TIME_NOT_ALLOWED);
        }
    }

    /**
     * 办理后的有效期不得早于原有效期，且新有效期区间自身必须合法
     */
    private void validateNewValidity(HrmEmployeeCertificateDO certificate, HrmCertificateRenewalCreateReqVO reqVO) {
        if (reqVO.getNewStartTime() != null && reqVO.getNewEndTime() != null
                && reqVO.getNewEndTime().isBefore(reqVO.getNewStartTime())) {
            throw exception(CERTIFICATE_LEDGER_END_TIME_INVALID);
        }
        if (reqVO.getNewEndTime() != null && certificate.getEndTime() != null
                && reqVO.getNewEndTime().isBefore(certificate.getEndTime())) {
            throw exception(CERTIFICATE_RENEWAL_TIME_INVALID);
        }
    }

    private void validateAttachmentRequired(HrmCertificateTypeDO certificateType, String attachmentUrl) {
        if (certificateType != null && Boolean.TRUE.equals(certificateType.getRequireAttachment())
                && StrUtil.isEmpty(attachmentUrl)) {
            throw exception(CERTIFICATE_LEDGER_ATTACHMENT_REQUIRED, certificateType.getName());
        }
    }

}
