package cn.iocoder.txgy.module.hrm.service.certificate;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.ledger.HrmCertificateLedgerPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.ledger.HrmCertificateLedgerRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.ledger.HrmCertificateLedgerSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.certificate.HrmCertificateTypeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.experience.HrmEmployeeCertificateDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.experience.HrmEmployeeCertificateMapper;
import cn.iocoder.txgy.module.hrm.enums.certificate.HrmCertificateStatusEnum;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertMap;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.txgy.framework.common.util.date.LocalDateTimeUtils.getDayBeginTime;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_LEDGER_ATTACHMENT_REQUIRED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_LEDGER_END_TIME_INVALID;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_LEDGER_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_LEDGER_STATUS_INVALID;

/**
 * HRM 证件台账 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class HrmCertificateLedgerServiceImpl implements HrmCertificateLedgerService {

    @Resource
    private HrmEmployeeCertificateMapper certificateMapper;
    @Resource
    private HrmCertificateTypeService certificateTypeService;
    @Resource
    private HrmCertificateStatusCalculator certificateStatusCalculator;
    @Resource
    private HrmCertificateWarningService certificateWarningService;
    @Resource
    private HrmEmployeeService employeeService;

    @Override
    public Long createCertificateLedger(HrmCertificateLedgerSaveReqVO reqVO) {
        // 1. 校验员工、证件类型、有效期与附件要求
        employeeService.validateEmployeeExists(reqVO.getEmployeeId());
        HrmCertificateTypeDO certificateType = getCertificateTypeIfPresent(reqVO.getTypeId());
        validateTimeRange(reqVO.getStartTime(), reqVO.getEndTime());
        validateAttachmentRequired(certificateType, reqVO.getAttachmentUrl());
        // 2. 落库，状态为派生数据，创建时即按当前日期计算
        HrmEmployeeCertificateDO certificate = BeanUtils.toBean(reqVO, HrmEmployeeCertificateDO.class)
                .setStartTime(getDayBeginTime(reqVO.getStartTime()))
                .setEndTime(getDayBeginTime(reqVO.getEndTime()))
                .setReviewTime(getDayBeginTime(reqVO.getReviewTime()))
                .setIssuingTime(getDayBeginTime(reqVO.getIssuingTime()));
        certificate.setStatus(certificateStatusCalculator.calculateStatus(certificate, certificateType,
                LocalDate.now()));
        certificateMapper.insert(certificate);
        return certificate.getId();
    }

    @Override
    public void updateCertificateLedger(HrmCertificateLedgerSaveReqVO reqVO) {
        // 1. 校验记录存在、员工与证件类型合法
        HrmEmployeeCertificateDO current = validateCertificateLedgerExists(reqVO.getId());
        employeeService.validateEmployeeExists(reqVO.getEmployeeId());
        HrmCertificateTypeDO certificateType = getCertificateTypeIfPresent(reqVO.getTypeId());
        validateTimeRange(reqVO.getStartTime(), reqVO.getEndTime());
        validateAttachmentRequired(certificateType, reqVO.getAttachmentUrl());
        // 2. 更新，状态重新计算；员工归属不允许变更，避免台账跨员工漂移
        HrmEmployeeCertificateDO updateObj = BeanUtils.toBean(reqVO, HrmEmployeeCertificateDO.class)
                .setEmployeeId(null)
                .setStartTime(getDayBeginTime(reqVO.getStartTime()))
                .setEndTime(getDayBeginTime(reqVO.getEndTime()))
                .setReviewTime(getDayBeginTime(reqVO.getReviewTime()))
                .setIssuingTime(getDayBeginTime(reqVO.getIssuingTime()));
        updateObj.setStatus(certificateStatusCalculator.calculateStatus(
                HrmEmployeeCertificateDO.builder()
                        .id(current.getId())
                        .status(current.getStatus())
                        .endTime(updateObj.getEndTime() == null ? current.getEndTime() : updateObj.getEndTime())
                        .build(),
                certificateType, LocalDate.now()));
        certificateMapper.updateById(updateObj);
    }

    @Override
    public void deleteCertificateLedger(Long id) {
        validateCertificateLedgerExists(id);
        certificateMapper.deleteById(id);
    }

    @Override
    public void updateCertificateLedgerStatus(Long id, Integer status) {
        HrmEmployeeCertificateDO certificate = validateCertificateLedgerExists(id);
        // 1. 只允许手工注销，或把注销的证件恢复为自动计算状态
        if (Objects.equals(status, HrmCertificateStatusEnum.CANCELLED.getStatus())) {
            certificateMapper.updateById(HrmEmployeeCertificateDO.builder()
                    .id(id).status(HrmCertificateStatusEnum.CANCELLED.getStatus()).build());
            return;
        }
        if (!Objects.equals(status, HrmCertificateStatusEnum.VALID.getStatus())) {
            throw exception(CERTIFICATE_LEDGER_STATUS_INVALID);
        }
        // 2. 恢复为有效时按有效期与提前期重新计算，避免与事实不符
        HrmCertificateTypeDO certificateType = getCertificateTypeIfPresent(certificate.getTypeId());
        Integer calculatedStatus = certificateStatusCalculator.calculateStatus(
                HrmEmployeeCertificateDO.builder()
                        .id(certificate.getId())
                        .status(HrmCertificateStatusEnum.VALID.getStatus())
                        .endTime(certificate.getEndTime())
                        .build(),
                certificateType, LocalDate.now());
        certificateMapper.updateById(HrmEmployeeCertificateDO.builder()
                .id(id).status(calculatedStatus).build());
    }

    @Override
    public HrmEmployeeCertificateDO getCertificateLedger(Long id) {
        return certificateMapper.selectById(id);
    }

    @Override
    public PageResult<HrmCertificateLedgerRespVO> getCertificateLedgerPage(HrmCertificateLedgerPageReqVO reqVO) {
        PageResult<HrmEmployeeCertificateDO> pageResult = certificateMapper.selectPage(reqVO);
        return new PageResult<>(buildCertificateLedgerRespVOList(pageResult.getList()), pageResult.getTotal());
    }

    @Override
    public HrmCertificateLedgerRespVO getCertificateLedgerRespVO(Long id) {
        HrmEmployeeCertificateDO certificate = validateCertificateLedgerExists(id);
        List<HrmCertificateLedgerRespVO> list = buildCertificateLedgerRespVOList(Collections.singletonList(certificate));
        return CollUtil.isEmpty(list) ? null : list.get(0);
    }

    @Override
    public HrmEmployeeCertificateDO validateCertificateLedgerExists(Long id) {
        HrmEmployeeCertificateDO certificate = id == null ? null : certificateMapper.selectById(id);
        if (certificate == null) {
            throw exception(CERTIFICATE_LEDGER_NOT_EXISTS);
        }
        return certificate;
    }

    @Override
    public int refreshCertificateStatus(LocalDate today) {
        // 1. 全量扫描证件台账
        List<HrmEmployeeCertificateDO> certificates = certificateMapper.selectList(
                new LambdaQueryWrapperX<HrmEmployeeCertificateDO>()
                        .orderByAsc(HrmEmployeeCertificateDO::getId));
        if (CollUtil.isEmpty(certificates)) {
            return 0;
        }
        Map<Long, HrmCertificateTypeDO> certificateTypeMap = certificateTypeService.getCertificateTypeMap(
                convertSet(certificates, HrmEmployeeCertificateDO::getTypeId));
        // 2. 逐个重算，仅状态发生变化时写库，避免无意义的行更新
        int changedCount = 0;
        for (HrmEmployeeCertificateDO certificate : certificates) {
            HrmCertificateTypeDO certificateType = certificate.getTypeId() == null
                    ? null : certificateTypeMap.get(certificate.getTypeId());
            Integer calculatedStatus = certificateStatusCalculator.calculateStatus(certificate, certificateType, today);
            if (Objects.equals(calculatedStatus, certificate.getStatus())) {
                continue;
            }
            certificateMapper.updateById(HrmEmployeeCertificateDO.builder()
                    .id(certificate.getId()).status(calculatedStatus).build());
            changedCount++;
        }
        return changedCount;
    }

    /**
     * 填充员工、证件类型、剩余天数与待处理预警数量
     */
    private List<HrmCertificateLedgerRespVO> buildCertificateLedgerRespVOList(
            List<HrmEmployeeCertificateDO> certificates) {
        if (CollUtil.isEmpty(certificates)) {
            return Collections.emptyList();
        }
        Map<Long, HrmCertificateTypeDO> certificateTypeMap = certificateTypeService.getCertificateTypeMap(
                convertSet(certificates, HrmEmployeeCertificateDO::getTypeId));
        Map<Long, HrmEmployeeDO> employeeMap = convertMap(
                employeeService.getEmployeeListByIds(convertSet(certificates, HrmEmployeeCertificateDO::getEmployeeId)),
                HrmEmployeeDO::getId);
        Map<Long, Long> pendingWarningCountMap = certificateWarningService.getPendingWarningCountMap(
                convertSet(certificates, HrmEmployeeCertificateDO::getId));
        LocalDate today = LocalDate.now();
        return convertList(certificates, certificate -> {
            HrmCertificateLedgerRespVO respVO = BeanUtils.toBean(certificate, HrmCertificateLedgerRespVO.class);
            respVO.setRemainDays(certificateStatusCalculator.calculateRemainDays(certificate, today));
            respVO.setPendingWarningCount(pendingWarningCountMap.getOrDefault(certificate.getId(), 0L));
            HrmCertificateTypeDO certificateType = certificate.getTypeId() == null
                    ? null : certificateTypeMap.get(certificate.getTypeId());
            if (certificateType != null) {
                respVO.setTypeName(certificateType.getName()).setSpecialOperation(certificateType.getSpecialOperation());
            }
            HrmEmployeeDO employee = employeeMap.get(certificate.getEmployeeId());
            if (employee != null) {
                respVO.setEmployeeName(employee.getName()).setEmployeeJobNumber(employee.getJobNumber())
                        .setDeptId(employee.getDeptId());
            }
            return respVO;
        });
    }

    private HrmCertificateTypeDO getCertificateTypeIfPresent(Long typeId) {
        return typeId == null ? null : certificateTypeService.validateCertificateTypeEnabled(typeId);
    }

    private void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime != null && endTime != null && endTime.isBefore(startTime)) {
            throw exception(CERTIFICATE_LEDGER_END_TIME_INVALID);
        }
    }

    private void validateAttachmentRequired(HrmCertificateTypeDO certificateType, String attachmentUrl) {
        if (certificateType != null && Boolean.TRUE.equals(certificateType.getRequireAttachment())
                && StrUtil.isEmpty(attachmentUrl)) {
            throw exception(CERTIFICATE_LEDGER_ATTACHMENT_REQUIRED, certificateType.getName());
        }
    }

}
