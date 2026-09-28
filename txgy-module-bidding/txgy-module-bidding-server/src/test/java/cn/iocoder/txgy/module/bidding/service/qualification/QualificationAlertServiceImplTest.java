package cn.iocoder.txgy.module.bidding.service.qualification;

import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertAlertConfigDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertAlertRecordDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertNotifyOutboxDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.EnterpriseQualificationDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationSafetyPermitDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationStaffConfigDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertAlertConfigMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertAlertRecordMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertNotifyOutboxMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.EnterpriseQualificationMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationSafetyPermitMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.qualification.QualificationStaffConfigMapper;
import cn.iocoder.txgy.module.bidding.enums.CertificateAlertTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.QualificationStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.SafetyPermitStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QUAL_EXPIRY 三源到期扫描规则测试（Mockito，TDD）：
 * 资质/安许/人员三类对象到期建 QUAL_EXPIRY 记录（ref 定位 + 幂等键）+ outbox 同事务；
 * ≤30 天主档/安许联动 EXPIRING_SOON；同日重跑幂等；配置缺省回落默认阈值。
 * 依据：《电子证件全流程管理_数据库设计.md》v3.3 §十五-到期预警复用。
 */
class QualificationAlertServiceImplTest {

    private CertAlertConfigMapper certAlertConfigMapper;
    private CertAlertRecordMapper certAlertRecordMapper;
    private CertNotifyOutboxMapper certNotifyOutboxMapper;
    private EnterpriseQualificationMapper enterpriseQualificationMapper;
    private QualificationSafetyPermitMapper qualificationSafetyPermitMapper;
    private QualificationStaffConfigMapper qualificationStaffConfigMapper;
    private QualificationAlertServiceImpl service;
    private final LocalDate today = LocalDate.of(2026, 9, 10);

    @BeforeEach
    void setUp() {
        certAlertConfigMapper = mock(CertAlertConfigMapper.class);
        certAlertRecordMapper = mock(CertAlertRecordMapper.class);
        certNotifyOutboxMapper = mock(CertNotifyOutboxMapper.class);
        enterpriseQualificationMapper = mock(EnterpriseQualificationMapper.class);
        qualificationSafetyPermitMapper = mock(QualificationSafetyPermitMapper.class);
        qualificationStaffConfigMapper = mock(QualificationStaffConfigMapper.class);
        service = new QualificationAlertServiceImpl();
        ReflectionTestUtils.setField(service, "certAlertConfigMapper", certAlertConfigMapper);
        ReflectionTestUtils.setField(service, "certAlertRecordMapper", certAlertRecordMapper);
        ReflectionTestUtils.setField(service, "certNotifyOutboxMapper", certNotifyOutboxMapper);
        ReflectionTestUtils.setField(service, "enterpriseQualificationMapper", enterpriseQualificationMapper);
        ReflectionTestUtils.setField(service, "qualificationSafetyPermitMapper", qualificationSafetyPermitMapper);
        ReflectionTestUtils.setField(service, "qualificationStaffConfigMapper", qualificationStaffConfigMapper);
    }

    @Test
    void scan_allThreeSources_createsQualAlertsAndOutboxes() {
        configDefaults();
        when(certAlertRecordMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(certAlertRecordMapper.insert(any(CertAlertRecordDO.class))).thenReturn(1);
        when(certNotifyOutboxMapper.insert(any(CertNotifyOutboxDO.class))).thenReturn(1);
        when(enterpriseQualificationMapper.updateById(any(EnterpriseQualificationDO.class))).thenReturn(1);
        when(qualificationSafetyPermitMapper.updateById(any(QualificationSafetyPermitDO.class))).thenReturn(1);

        EnterpriseQualificationDO qual = qualOf(QualificationStatusEnum.VALID);
        qual.setValidTo(today.plusDays(25));
        qual.setVersion(2);
        QualificationSafetyPermitDO permit = permitOf(SafetyPermitStatusEnum.VALID);
        permit.setValidTo(today.plusDays(6));
        permit.setVersion(1);
        QualificationStaffConfigDO staff = staffOf();
        staff.setRegValidTo(today.plusDays(40));
        staff.setPersonName("李工");

        when(enterpriseQualificationMapper.selectExpiringCandidates(today, 90)).thenReturn(List.of(qual));
        when(qualificationSafetyPermitMapper.selectExpiringCandidates(today, 90)).thenReturn(List.of(permit));
        when(qualificationStaffConfigMapper.selectExpiringStaff(today, 90)).thenReturn(List.of(staff));

        int created = service.scanQualificationExpiries(today);

        assertEquals(3, created, "三源各 1 条");
        verify(certAlertRecordMapper, times(3)).insert(any(CertAlertRecordDO.class));
        verify(certNotifyOutboxMapper, times(6)).insert(any(CertNotifyOutboxDO.class));
        assertEquals(QualificationStatusEnum.EXPIRING_SOON.name(), qual.getQualStatus(), "≤30 天资质联动");
        assertEquals(SafetyPermitStatusEnum.EXPIRING_SOON.name(), permit.getPermitStatus(), "≤30 天安许联动");
        verify(enterpriseQualificationMapper).updateById(qual);
        verify(qualificationSafetyPermitMapper).updateById(permit);
        ArgumentCaptor<CertAlertRecordDO> cap = ArgumentCaptor.forClass(CertAlertRecordDO.class);
        verify(certAlertRecordMapper, times(3)).insert(cap.capture());
        for (CertAlertRecordDO rec : cap.getAllValues()) {
            String key = rec.getIdempotentKey();
            assertTrue(key.startsWith("QUAL:") || key.startsWith("SAFETY_PERMIT:") || key.startsWith("STAFF:"),
                    "资质类幂等键应含 ref 前缀: " + key);
            assertTrue(key.contains(CertificateAlertTypeEnum.QUAL_EXPIRY.name()), key);
            assertTrue(key.endsWith("20260910"), key);
        }
    }

    @Test
    void scan_sameDayRerun_skipsExisting() {
        configDefaults();
        when(certAlertRecordMapper.selectByIdempotentKey(anyString())).thenReturn(new CertAlertRecordDO());
        EnterpriseQualificationDO qual = qualOf(QualificationStatusEnum.VALID);
        qual.setValidTo(today.plusDays(25));
        when(enterpriseQualificationMapper.selectExpiringCandidates(today, 90)).thenReturn(List.of(qual));
        when(qualificationSafetyPermitMapper.selectExpiringCandidates(today, 90)).thenReturn(List.of());
        when(qualificationStaffConfigMapper.selectExpiringStaff(today, 90)).thenReturn(List.of());

        int created = service.scanQualificationExpiries(today);

        assertEquals(0, created, "同日已触发应幂等跳过");
        verify(certAlertRecordMapper, never()).insert(any(CertAlertRecordDO.class));
    }

    @Test
    void scan_disabledConfig_returnsZero() {
        CertAlertConfigDO row = configRow();
        row.setEnabled(Boolean.FALSE);
        when(certAlertConfigMapper.selectGlobal(CertificateAlertTypeEnum.QUAL_EXPIRY.name())).thenReturn(row);
        assertEquals(0, service.scanQualificationExpiries(today));
        verify(enterpriseQualificationMapper, never()).selectExpiringCandidates(any(LocalDate.class), anyInt());
    }

    @Test
    void scan_staffOnly_expiringWithinWindow_alert() {
        configDefaults();
        when(certAlertRecordMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(certAlertRecordMapper.insert(any(CertAlertRecordDO.class))).thenReturn(1);
        when(certNotifyOutboxMapper.insert(any(CertNotifyOutboxDO.class))).thenReturn(1);
        when(enterpriseQualificationMapper.selectExpiringCandidates(today, 90)).thenReturn(List.of());
        when(qualificationSafetyPermitMapper.selectExpiringCandidates(today, 90)).thenReturn(List.of());
        QualificationStaffConfigDO staff = staffOf();
        staff.setRegValidTo(today.plusDays(5));
        when(qualificationStaffConfigMapper.selectExpiringStaff(today, 90)).thenReturn(List.of(staff));

        int created = service.scanQualificationExpiries(today);

        assertEquals(1, created);
        ArgumentCaptor<CertAlertRecordDO> cap = ArgumentCaptor.forClass(CertAlertRecordDO.class);
        verify(certAlertRecordMapper).insert(cap.capture());
        assertEquals("STAFF", cap.getValue().getRefType());
        assertEquals("CRITICAL", cap.getValue().getAlertLevel(), "≤7 天应 CRITICAL");
    }

    private void configDefaults() {
        when(certAlertConfigMapper.selectGlobal(CertificateAlertTypeEnum.QUAL_EXPIRY.name())).thenReturn(null);
    }

    private CertAlertConfigDO configRow() {
        CertAlertConfigDO row = new CertAlertConfigDO();
        row.setAlertType(CertificateAlertTypeEnum.QUAL_EXPIRY.name());
        row.setEnabled(Boolean.TRUE);
        row.setThresholds("[7,30,60,90]");
        row.setNotifyChannels("[\"EMAIL\",\"IN_APP\"]");
        return row;
    }

    private EnterpriseQualificationDO qualOf(QualificationStatusEnum status) {
        EnterpriseQualificationDO q = new EnterpriseQualificationDO();
        q.setId(1L);
        q.setEntId(1L);
        q.setQualSerial("CONSTRUCTION");
        q.setQualCategory("市政公用工程施工总承包");
        q.setQualGrade("一级");
        q.setCertNo("D123456789");
        q.setQualStatus(status.name());
        return q;
    }

    private QualificationSafetyPermitDO permitOf(SafetyPermitStatusEnum status) {
        QualificationSafetyPermitDO p = new QualificationSafetyPermitDO();
        p.setId(2L);
        p.setEntId(1L);
        p.setPermitNo("(陕)JZ安许证字[2023]000001");
        p.setPermitStatus(status.name());
        return p;
    }

    private QualificationStaffConfigDO staffOf() {
        QualificationStaffConfigDO s = new QualificationStaffConfigDO();
        s.setId(3L);
        s.setEntId(1L);
        s.setQualId(1L);
        s.setRegNo("陕123456");
        return s;
    }
}
