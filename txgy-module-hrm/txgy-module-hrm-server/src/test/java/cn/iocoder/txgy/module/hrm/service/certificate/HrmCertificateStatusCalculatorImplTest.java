package cn.iocoder.txgy.module.hrm.service.certificate;

import cn.iocoder.txgy.module.hrm.dal.dataobject.certificate.HrmCertificateTypeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.experience.HrmEmployeeCertificateDO;
import cn.iocoder.txgy.module.hrm.enums.certificate.HrmCertificateStatusEnum;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link HrmCertificateStatusCalculatorImpl} 的单元测试类
 *
 * 状态计算不依赖数据库，直接实例化被测对象。
 *
 * @author OPENLAB BS
 */
public class HrmCertificateStatusCalculatorImplTest {

    private final HrmCertificateStatusCalculatorImpl calculator = new HrmCertificateStatusCalculatorImpl();

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 18);

    @Test
    public void testCalculateStatus_noEndTime() {
        // 无有效结束日期视为长期有效
        HrmEmployeeCertificateDO certificate = buildCertificate(null, HrmCertificateStatusEnum.VALID.getStatus());

        assertEquals(HrmCertificateStatusEnum.VALID.getStatus(),
                calculator.calculateStatus(certificate, buildType(30), TODAY));
        assertNull(calculator.calculateRemainDays(certificate, TODAY));
    }

    @Test
    public void testCalculateStatus_valid() {
        // 剩余 120 天，超过提前期 30 天
        HrmEmployeeCertificateDO certificate = buildCertificate(
                LocalDateTime.of(2027, 1, 16, 0, 0), HrmCertificateStatusEnum.EXPIRING.getStatus());

        assertEquals(HrmCertificateStatusEnum.VALID.getStatus(),
                calculator.calculateStatus(certificate, buildType(30), TODAY));
        assertEquals(120, calculator.calculateRemainDays(certificate, TODAY));
    }

    @Test
    public void testCalculateStatus_expiringAtBoundary() {
        // 剩余恰好等于提前期，应判定为即将到期
        HrmEmployeeCertificateDO certificate = buildCertificate(
                LocalDateTime.of(2026, 10, 18, 0, 0), HrmCertificateStatusEnum.VALID.getStatus());

        assertEquals(HrmCertificateStatusEnum.EXPIRING.getStatus(),
                calculator.calculateStatus(certificate, buildType(30), TODAY));
        assertEquals(30, calculator.calculateRemainDays(certificate, TODAY));
    }

    @Test
    public void testCalculateStatus_expired() {
        // 已过结束日期，剩余天数为负数
        HrmEmployeeCertificateDO certificate = buildCertificate(
                LocalDateTime.of(2026, 9, 8, 0, 0), HrmCertificateStatusEnum.EXPIRING.getStatus());

        assertEquals(HrmCertificateStatusEnum.EXPIRED.getStatus(),
                calculator.calculateStatus(certificate, buildType(30), TODAY));
        assertEquals(-10, calculator.calculateRemainDays(certificate, TODAY));
    }

    @Test
    public void testCalculateStatus_cancelledKeepsManualState() {
        // 注销是人工终态，即使已过期也不被自动改写
        HrmEmployeeCertificateDO certificate = buildCertificate(
                LocalDateTime.of(2020, 1, 1, 0, 0), HrmCertificateStatusEnum.CANCELLED.getStatus());

        assertEquals(HrmCertificateStatusEnum.CANCELLED.getStatus(),
                calculator.calculateStatus(certificate, buildType(30), TODAY));
    }

    @Test
    public void testCalculateStatus_typeNotConfiguredFallsBackToDefault() {
        // 证件类型为空或未配置提前期时，按默认 30 天兜底
        HrmEmployeeCertificateDO certificate = buildCertificate(
                LocalDateTime.of(2026, 10, 17, 0, 0), HrmCertificateStatusEnum.VALID.getStatus());

        assertEquals(29, calculator.calculateRemainDays(certificate, TODAY));
        assertEquals(HrmCertificateStatusEnum.EXPIRING.getStatus(),
                calculator.calculateStatus(certificate, null, TODAY));
        assertEquals(HrmCertificateStatusEnum.EXPIRING.getStatus(),
                calculator.calculateStatus(certificate, buildType(null), TODAY));
        assertEquals(HrmCertificateStatusCalculator.DEFAULT_ADVANCE_DAYS,
                calculator.getAdvanceDays(buildType(null)));
    }

    @Test
    public void testCalculateStatus_customAdvanceDays() {
        // 提前期 90 天时，剩余 60 天即进入即将到期
        HrmEmployeeCertificateDO certificate = buildCertificate(
                LocalDateTime.of(2026, 11, 17, 0, 0), HrmCertificateStatusEnum.VALID.getStatus());

        assertEquals(60, calculator.calculateRemainDays(certificate, TODAY));
        assertEquals(HrmCertificateStatusEnum.EXPIRING.getStatus(),
                calculator.calculateStatus(certificate, buildType(90), TODAY));
    }

    @Test
    public void testGetAdvanceDays_negativeFallsBackToDefault() {
        // 非法配置（负数）同样兜底为默认值，避免提前期失效导致漏预警
        assertEquals(HrmCertificateStatusCalculator.DEFAULT_ADVANCE_DAYS,
                calculator.getAdvanceDays(buildType(-1)));
    }

    private HrmEmployeeCertificateDO buildCertificate(LocalDateTime endTime, Integer status) {
        return HrmEmployeeCertificateDO.builder()
                .id(1L)
                .employeeId(2L)
                .endTime(endTime)
                .status(status)
                .build();
    }

    private HrmCertificateTypeDO buildType(Integer advanceDays) {
        return HrmCertificateTypeDO.builder()
                .id(1L)
                .name("特种作业操作证")
                .advanceDays(advanceDays)
                .build();
    }

}
