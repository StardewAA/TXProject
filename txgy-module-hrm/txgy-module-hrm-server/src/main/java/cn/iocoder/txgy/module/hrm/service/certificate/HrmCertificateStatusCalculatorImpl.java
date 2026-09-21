package cn.iocoder.txgy.module.hrm.service.certificate;

import cn.iocoder.txgy.module.hrm.dal.dataobject.certificate.HrmCertificateTypeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.experience.HrmEmployeeCertificateDO;
import cn.iocoder.txgy.module.hrm.enums.certificate.HrmCertificateStatusEnum;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * HRM 证件状态计算器实现类
 *
 * @author OPENLAB BS
 */
@Component
@Validated
public class HrmCertificateStatusCalculatorImpl implements HrmCertificateStatusCalculator {

    @Override
    public Integer calculateStatus(HrmEmployeeCertificateDO certificate, HrmCertificateTypeDO certificateType,
                                   LocalDate today) {
        // 1. 注销是人工终态，不参与自动计算
        if (Objects.equals(certificate.getStatus(), HrmCertificateStatusEnum.CANCELLED.getStatus())) {
            return HrmCertificateStatusEnum.CANCELLED.getStatus();
        }
        // 2. 无有效结束日期视为长期有效
        Integer remainDays = calculateRemainDays(certificate, today);
        if (remainDays == null) {
            return HrmCertificateStatusEnum.VALID.getStatus();
        }
        // 3. 已过期
        if (remainDays < 0) {
            return HrmCertificateStatusEnum.EXPIRED.getStatus();
        }
        // 4. 在提前预警期内为即将到期
        if (remainDays <= getAdvanceDays(certificateType)) {
            return HrmCertificateStatusEnum.EXPIRING.getStatus();
        }
        return HrmCertificateStatusEnum.VALID.getStatus();
    }

    @Override
    public Integer calculateRemainDays(HrmEmployeeCertificateDO certificate, LocalDate today) {
        if (certificate == null || certificate.getEndTime() == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(today, certificate.getEndTime().toLocalDate());
    }

    @Override
    public int getAdvanceDays(HrmCertificateTypeDO certificateType) {
        if (certificateType == null || certificateType.getAdvanceDays() == null
                || certificateType.getAdvanceDays() < 0) {
            return DEFAULT_ADVANCE_DAYS;
        }
        return certificateType.getAdvanceDays();
    }

}
