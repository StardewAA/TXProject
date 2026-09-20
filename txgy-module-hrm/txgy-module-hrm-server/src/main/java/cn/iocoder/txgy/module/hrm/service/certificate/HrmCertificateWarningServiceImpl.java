package cn.iocoder.txgy.module.hrm.service.certificate;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.warning.HrmCertificateWarningHandleReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.warning.HrmCertificateWarningPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.warning.HrmCertificateWarningRespVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.certificate.HrmCertificateTypeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.certificate.HrmCertificateWarningDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.experience.HrmEmployeeCertificateDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.certificate.HrmCertificateWarningMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.experience.HrmEmployeeCertificateMapper;
import cn.iocoder.txgy.module.hrm.enums.MessageTemplateConstants;
import cn.iocoder.txgy.module.hrm.enums.certificate.HrmCertificateWarningNotifyStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.certificate.HrmCertificateWarningStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.certificate.HrmCertificateWarningTypeEnum;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import cn.iocoder.txgy.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.txgy.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertMap;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.filterList;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_WARNING_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_WARNING_STATUS_INVALID;

/**
 * HRM 证件预警 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class HrmCertificateWarningServiceImpl implements HrmCertificateWarningService {

    @Resource
    private HrmCertificateWarningMapper certificateWarningMapper;
    @Resource
    private HrmEmployeeCertificateMapper certificateMapper;
    @Resource
    private HrmCertificateTypeService certificateTypeService;
    @Resource
    private HrmCertificateStatusCalculator certificateStatusCalculator;
    @Resource
    private HrmEmployeeService employeeService;
    @Resource
    private NotifyMessageSendApi notifyMessageSendApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int scanAndGenerateWarnings(LocalDate today) {
        // 1. 扫描已纳入资格证件域且未注销的证件
        List<HrmEmployeeCertificateDO> certificates = certificateMapper.selectListForWarningScan();
        if (CollUtil.isEmpty(certificates)) {
            return 0;
        }
        Map<Long, HrmCertificateTypeDO> certificateTypeMap = certificateTypeService.getCertificateTypeMap(
                convertSet(certificates, HrmEmployeeCertificateDO::getTypeId));
        // 2. 逐证件生成到期与复审预警，按「证件 + 预警类型 + 预警日期」去重
        int createdCount = 0;
        for (HrmEmployeeCertificateDO certificate : certificates) {
            HrmCertificateTypeDO certificateType = certificate.getTypeId() == null
                    ? null : certificateTypeMap.get(certificate.getTypeId());
            int advanceDays = certificateStatusCalculator.getAdvanceDays(certificateType);
            // 2.1 到期预警
            Integer remainDays = certificateStatusCalculator.calculateRemainDays(certificate, today);
            if (remainDays != null && remainDays <= advanceDays) {
                createdCount += createWarningIfAbsent(certificate, HrmCertificateWarningTypeEnum.EXPIRING, today,
                        certificate.getEndTime(), remainDays);
            }
            // 2.2 复审预警，仅证件类型开启复审且已维护复审日期时生成
            if (certificateType != null && Boolean.TRUE.equals(certificateType.getNeedReview())
                    && certificate.getReviewTime() != null) {
                int reviewRemainDays = (int) ChronoUnit.DAYS.between(today,
                        certificate.getReviewTime().toLocalDate());
                if (reviewRemainDays <= advanceDays) {
                    createdCount += createWarningIfAbsent(certificate, HrmCertificateWarningTypeEnum.REVIEW, today,
                            certificate.getReviewTime(), reviewRemainDays);
                }
            }
        }
        return createdCount;
    }

    @Override
    public PageResult<HrmCertificateWarningRespVO> getCertificateWarningPage(HrmCertificateWarningPageReqVO reqVO) {
        PageResult<HrmCertificateWarningDO> pageResult = certificateWarningMapper.selectPage(reqVO);
        return new PageResult<>(buildWarningRespVOList(pageResult.getList()), pageResult.getTotal());
    }

    @Override
    public void handleCertificateWarning(HrmCertificateWarningHandleReqVO reqVO) {
        // 1. 校验预警存在
        HrmCertificateWarningDO warning = certificateWarningMapper.selectById(reqVO.getId());
        if (warning == null) {
            throw exception(CERTIFICATE_WARNING_NOT_EXISTS);
        }
        // 2. 校验状态流转：待处理可流转为已处理/已忽略，已处理/已忽略只能重新启用为待处理
        Integer currentStatus = warning.getStatus();
        Integer targetStatus = reqVO.getStatus();
        if (HrmCertificateWarningStatusEnum.valueOf(targetStatus) == null
                || Objects.equals(currentStatus, targetStatus)) {
            throw exception(CERTIFICATE_WARNING_STATUS_INVALID);
        }
        boolean reEnable = Objects.equals(targetStatus, HrmCertificateWarningStatusEnum.PENDING.getStatus());
        if (!reEnable && !Objects.equals(currentStatus, HrmCertificateWarningStatusEnum.PENDING.getStatus())) {
            throw exception(CERTIFICATE_WARNING_STATUS_INVALID);
        }
        // 3. 更新处理信息，重新启用时清空历史处理痕迹
        certificateWarningMapper.updateHandleInfo(warning.getId(), targetStatus,
                reEnable ? null : getLoginUserId(), reEnable ? null : LocalDateTime.now(),
                reEnable ? null : reqVO.getHandleRemark());
    }

    @Override
    public Long getPendingWarningCount() {
        return certificateWarningMapper.selectCountByStatus(HrmCertificateWarningStatusEnum.PENDING.getStatus());
    }

    @Override
    public Map<Long, Long> getPendingWarningCountMap(Collection<Long> certificateIds) {
        if (CollUtil.isEmpty(certificateIds)) {
            return Collections.emptyMap();
        }
        List<HrmCertificateWarningDO> warnings = certificateWarningMapper.selectListByCertificateIdsAndStatus(
                certificateIds, HrmCertificateWarningStatusEnum.PENDING.getStatus());
        Map<Long, Long> countMap = new HashMap<>();
        for (HrmCertificateWarningDO warning : warnings) {
            countMap.merge(warning.getCertificateId(), 1L, Long::sum);
        }
        return countMap;
    }

    @Override
    public int notifyPendingWarnings(LocalDate today) {
        // 1. 查询待通知与通知失败但未超过重试上限的预警
        List<HrmCertificateWarningDO> warnings = certificateWarningMapper.selectListByNotifyStatus(
                HrmCertificateWarningNotifyStatusEnum.PENDING.getStatus());
        warnings.addAll(filterList(certificateWarningMapper.selectListByNotifyStatus(
                        HrmCertificateWarningNotifyStatusEnum.FAILED.getStatus()),
                warning -> warning.getNotifyRetryCount() != null
                        && warning.getNotifyRetryCount() < MAX_NOTIFY_RETRY_COUNT));
        if (CollUtil.isEmpty(warnings)) {
            return 0;
        }
        // 2. 批量获得员工，用于解析站内信接收人
        Map<Long, HrmEmployeeDO> employeeMap = convertMap(
                employeeService.getEmployeeListByIds(convertSet(warnings, HrmCertificateWarningDO::getEmployeeId)),
                HrmEmployeeDO::getId);
        // 3. 逐条通知，失败只记录原因并累计重试次数，不影响业务事实
        int successCount = 0;
        for (HrmCertificateWarningDO warning : warnings) {
            int retryCount = warning.getNotifyRetryCount() == null ? 0 : warning.getNotifyRetryCount();
            HrmEmployeeDO employee = employeeMap.get(warning.getEmployeeId());
            if (employee == null || employee.getUserId() == null) {
                certificateWarningMapper.updateNotifyInfo(warning.getId(),
                        HrmCertificateWarningNotifyStatusEnum.FAILED.getStatus(), LocalDateTime.now(),
                        "员工未绑定后台账号，无法发送站内信", retryCount + 1);
                continue;
            }
            try {
                notifyMessageSendApi.sendSingleMessageToAdmin(new NotifySendSingleToUserReqDTO()
                        .setUserId(employee.getUserId())
                        .setTemplateCode(MessageTemplateConstants.CERTIFICATE_WARNING)
                        .setTemplateParams(buildTemplateParams(warning, employee)));
                certificateWarningMapper.updateNotifyInfo(warning.getId(),
                        HrmCertificateWarningNotifyStatusEnum.SUCCESS.getStatus(), LocalDateTime.now(), null,
                        retryCount);
                successCount++;
            } catch (Exception ex) {
                certificateWarningMapper.updateNotifyInfo(warning.getId(),
                        HrmCertificateWarningNotifyStatusEnum.FAILED.getStatus(), LocalDateTime.now(),
                        ex.getMessage(), retryCount + 1);
            }
        }
        return successCount;
    }

    /**
     * 按「证件 + 预警类型 + 预警日期」幂等生成预警记录，已存在时跳过
     */
    private int createWarningIfAbsent(HrmEmployeeCertificateDO certificate, HrmCertificateWarningTypeEnum type,
                                      LocalDate today, LocalDateTime expireDate, int remainDays) {
        if (certificateWarningMapper.selectByUniqueKey(certificate.getId(), type.getType(), today) != null) {
            return 0;
        }
        certificateWarningMapper.insert(HrmCertificateWarningDO.builder()
                .certificateId(certificate.getId())
                .employeeId(certificate.getEmployeeId())
                .type(type.getType())
                .warningDate(today)
                .expireDate(expireDate)
                .remainDays(remainDays)
                .status(HrmCertificateWarningStatusEnum.PENDING.getStatus())
                .notifyStatus(HrmCertificateWarningNotifyStatusEnum.PENDING.getStatus())
                .notifyRetryCount(0)
                .build());
        return 1;
    }

    private Map<String, Object> buildTemplateParams(HrmCertificateWarningDO warning, HrmEmployeeDO employee) {
        Map<String, Object> templateParams = new HashMap<>();
        templateParams.put("employeeName", employee.getName());
        templateParams.put("warningType", HrmCertificateWarningTypeEnum.valueOf(warning.getType()) == null
                ? "证件预警" : HrmCertificateWarningTypeEnum.valueOf(warning.getType()).getName());
        templateParams.put("expireDate", warning.getExpireDate() == null
                ? "" : warning.getExpireDate().toLocalDate().toString());
        templateParams.put("remainDays", warning.getRemainDays() == null ? "" : warning.getRemainDays());
        return templateParams;
    }

    /**
     * 填充证件名称、编号、类型与员工信息，便于前端直接展示
     */
    private List<HrmCertificateWarningRespVO> buildWarningRespVOList(List<HrmCertificateWarningDO> warnings) {
        if (CollUtil.isEmpty(warnings)) {
            return Collections.emptyList();
        }
        Map<Long, HrmEmployeeCertificateDO> certificateMap = convertMap(
                certificateMapper.selectListByIds(convertSet(warnings, HrmCertificateWarningDO::getCertificateId)),
                HrmEmployeeCertificateDO::getId);
        Map<Long, HrmCertificateTypeDO> certificateTypeMap = certificateTypeService.getCertificateTypeMap(
                convertSet(certificateMap.values(), HrmEmployeeCertificateDO::getTypeId));
        Map<Long, HrmEmployeeDO> employeeMap = convertMap(
                employeeService.getEmployeeListByIds(convertSet(warnings, HrmCertificateWarningDO::getEmployeeId)),
                HrmEmployeeDO::getId);
        return convertList(warnings, warning -> {
            HrmCertificateWarningRespVO respVO = BeanUtils.toBean(warning, HrmCertificateWarningRespVO.class);
            HrmEmployeeCertificateDO certificate = certificateMap.get(warning.getCertificateId());
            if (certificate != null) {
                respVO.setCertificateName(certificate.getName()).setCertificateNo(certificate.getNo());
                HrmCertificateTypeDO certificateType = certificate.getTypeId() == null
                        ? null : certificateTypeMap.get(certificate.getTypeId());
                respVO.setCertificateTypeName(certificateType == null ? null : certificateType.getName());
            }
            HrmEmployeeDO employee = employeeMap.get(warning.getEmployeeId());
            if (employee != null) {
                respVO.setEmployeeName(employee.getName()).setEmployeeJobNumber(employee.getJobNumber())
                        .setDeptId(employee.getDeptId());
            }
            return respVO;
        });
    }

}
