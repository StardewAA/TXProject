package cn.iocoder.txgy.module.bidding.service.personnel;

import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertAlertConfigDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertAlertRecordDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertNotifyOutboxDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelCertificateDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelProfileDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertAlertConfigMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertAlertRecordMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertNotifyOutboxMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelCertificateMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelProfileMapper;
import cn.iocoder.txgy.module.bidding.enums.CertificateAlertLevelEnum;
import cn.iocoder.txgy.module.bidding.enums.PersonnelCertStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 员工证件到期预警扫描测试（Mockito，TDD）。
 *
 * <p>依据：用户口径"人员资质证书、安全生产证、身份证有效期到期前自动提醒"；
 * 《电子证件全流程管理_数据库设计.md》v3.3 §九（幂等键 + 档位 + outbox 可靠送达）；
 * 06 文档 §二-4（复审独立时间维度）、§二-5（法定预警下限不得被调低而错过法定窗口）；
 * 开发规范 §4.5（≤30 天状态联动走 CAS，失败即抛错不吞并发）。
 * <p>覆盖：三源同一基准日各自产出一条预警；同日重跑幂等（已有记录则跳过）；
 * ≤30 天把 VALID 联动为 EXPIRING_SOON 且已不是 VALID 时不重复联动；
 * 法定下限（建造师 90 天）把 INFO 提升为 WARNING；配置关闭时不扫描。
 */
class PersonnelAlertServiceImplTest {

    private CertAlertConfigMapper certAlertConfigMapper;
    private CertAlertRecordMapper certAlertRecordMapper;
    private CertNotifyOutboxMapper certNotifyOutboxMapper;
    private PersonnelCertificateMapper personnelCertificateMapper;
    private PersonnelProfileMapper personnelProfileMapper;
    private PersonnelAlertServiceImpl service;

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 10);

    @BeforeEach
    void setUp() {
        certAlertConfigMapper = mock(CertAlertConfigMapper.class);
        certAlertRecordMapper = mock(CertAlertRecordMapper.class);
        certNotifyOutboxMapper = mock(CertNotifyOutboxMapper.class);
        personnelCertificateMapper = mock(PersonnelCertificateMapper.class);
        personnelProfileMapper = mock(PersonnelProfileMapper.class);
        service = new PersonnelAlertServiceImpl();
        ReflectionTestUtils.setField(service, "certAlertConfigMapper", certAlertConfigMapper);
        ReflectionTestUtils.setField(service, "certAlertRecordMapper", certAlertRecordMapper);
        ReflectionTestUtils.setField(service, "certNotifyOutboxMapper", certNotifyOutboxMapper);
        ReflectionTestUtils.setField(service, "personnelCertificateMapper", personnelCertificateMapper);
        ReflectionTestUtils.setField(service, "personnelProfileMapper", personnelProfileMapper);
    }

    private void stubDefaultConfig() {
        // 平台默认档位 [7,30,60,90]，渠道 EMAIL/IN_APP（与既有 EXPIRY/QUAL_EXPIRY 扫描同范式）
        CertAlertConfigDO row = new CertAlertConfigDO();
        row.setAlertType("EXPIRY");
        row.setEnabled(true);
        row.setThresholds("[7,30,60,90]");
        row.setNotifyChannels("[\"EMAIL\",\"IN_APP\"]");
        when(certAlertConfigMapper.selectGlobal(anyString())).thenReturn(row);
    }

    private PersonnelCertificateDO cert(Long id, String type, String status, LocalDate validTo, LocalDate reviewDate) {
        PersonnelCertificateDO c = new PersonnelCertificateDO();
        c.setId(id);
        c.setEntId(1L);
        c.setPersonId(100L);
        c.setCertType(type);
        c.setCertName("证书" + id);
        c.setCertNo("NO" + id);
        c.setCertStatus(status);
        c.setValidTo(validTo);
        c.setNextReviewDate(reviewDate);
        c.setVersion(0);
        return c;
    }

    @Test
    void scan_createsAlertAndOutbox_forCertExpiry_andMarksExpiringSoon() {
        stubDefaultConfig();
        PersonnelCertificateDO cert = cert(1L, "REG_PRACTICE_CERT", "VALID", TODAY.plusDays(20), null);
        when(personnelCertificateMapper.selectExpiringCandidates(TODAY, 90)).thenReturn(List.of(cert));
        when(personnelCertificateMapper.selectReviewDueCandidates(eq(TODAY), any(Integer.class))).thenReturn(List.of());
        when(personnelProfileMapper.selectExpiringIdCards(eq(TODAY), any(Integer.class))).thenReturn(List.of());
        when(certAlertRecordMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(personnelCertificateMapper.updateById(any(PersonnelCertificateDO.class))).thenReturn(1);

        int created = service.scanPersonnelExpiries(TODAY);

        assertEquals(1, created);
        ArgumentCaptor<CertAlertRecordDO> cap = ArgumentCaptor.forClass(CertAlertRecordDO.class);
        verify(certAlertRecordMapper).insert(cap.capture());
        assertEquals("PERSON_CERT", cap.getValue().getRefType());
        assertEquals("EXPIRY", cap.getValue().getAlertType());
        // 幂等键必须含触发日期，否则同一张证明天不会再提醒
        assertTrue(cap.getValue().getIdempotentKey().endsWith("20260910"));
        verify(certNotifyOutboxMapper, times(2)).insert(any(CertNotifyOutboxDO.class));
        // ≤30 天联动：状态必须被推进，否则页面仍显示"有效"
        ArgumentCaptor<PersonnelCertificateDO> certCap = ArgumentCaptor.forClass(PersonnelCertificateDO.class);
        verify(personnelCertificateMapper).updateById(certCap.capture());
        assertEquals(PersonnelCertStatusEnum.EXPIRING_SOON.name(), certCap.getValue().getCertStatus());
    }

    @Test
    void scan_skips_whenIdempotentRecordExists() {
        stubDefaultConfig();
        when(personnelCertificateMapper.selectExpiringCandidates(TODAY, 90))
                .thenReturn(List.of(cert(1L, "REG_PRACTICE_CERT", "VALID", TODAY.plusDays(20), null)));
        when(personnelCertificateMapper.selectReviewDueCandidates(eq(TODAY), any(Integer.class))).thenReturn(List.of());
        when(personnelProfileMapper.selectExpiringIdCards(eq(TODAY), any(Integer.class))).thenReturn(List.of());
        when(certAlertRecordMapper.selectByIdempotentKey(anyString())).thenReturn(new CertAlertRecordDO());

        assertEquals(0, service.scanPersonnelExpiries(TODAY));
        verify(certAlertRecordMapper, never()).insert(any(CertAlertRecordDO.class));
        verify(certNotifyOutboxMapper, never()).insert(any(CertNotifyOutboxDO.class));
    }

    @Test
    void scan_doesNotDowngrade_whenAlreadyExpiringSoon() {
        stubDefaultConfig();
        when(personnelCertificateMapper.selectExpiringCandidates(TODAY, 90))
                .thenReturn(List.of(cert(1L, "REG_PRACTICE_CERT", "EXPIRING_SOON", TODAY.plusDays(20), null)));
        when(personnelCertificateMapper.selectReviewDueCandidates(eq(TODAY), any(Integer.class))).thenReturn(List.of());
        when(personnelProfileMapper.selectExpiringIdCards(eq(TODAY), any(Integer.class))).thenReturn(List.of());
        when(certAlertRecordMapper.selectByIdempotentKey(anyString())).thenReturn(null);

        service.scanPersonnelExpiries(TODAY);

        // 无谓的 CAS 更新既浪费也会引入并发失败，已处于目标状态就不写
        verify(personnelCertificateMapper, never()).updateById(any(PersonnelCertificateDO.class));
    }

    @Test
    void scan_coversReviewAndIdCardSources() {
        stubDefaultConfig();
        when(personnelCertificateMapper.selectExpiringCandidates(TODAY, 90)).thenReturn(List.of());
        when(personnelCertificateMapper.selectReviewDueCandidates(eq(TODAY), any(Integer.class)))
                .thenReturn(List.of(cert(2L, "SKILL_CERT", "VALID", TODAY.plusYears(2), TODAY.plusDays(45))));
        PersonnelProfileDO profile = new PersonnelProfileDO();
        profile.setId(100L);
        profile.setPersonName("张三");
        profile.setIdCardValidTo(TODAY.plusDays(80));
        when(personnelProfileMapper.selectExpiringIdCards(eq(TODAY), any(Integer.class))).thenReturn(List.of(profile));
        when(certAlertRecordMapper.selectByIdempotentKey(anyString())).thenReturn(null);

        int created = service.scanPersonnelExpiries(TODAY);

        // 复审一条 + 身份证一条：复审是"到期之前就已失效"的独立维度，必须单独扫
        assertEquals(2, created);
        ArgumentCaptor<CertAlertRecordDO> cap = ArgumentCaptor.forClass(CertAlertRecordDO.class);
        verify(certAlertRecordMapper, times(2)).insert(cap.capture());
        assertTrue(cap.getAllValues().stream().anyMatch(r -> "PERSON_CERT_REVIEW".equals(r.getRefType())
                && "REVIEW".equals(r.getAlertType())));
        assertTrue(cap.getAllValues().stream().anyMatch(r -> "PERSON_ID_CARD".equals(r.getRefType())));
    }

    @Test
    void scan_promotesLevelToWarning_insideLegalFloorWindow() {
        stubDefaultConfig();
        // 建造师法定下限 90 天：剩余 80 天时平台档位只给 INFO，但已进入法定办理窗口，应升为 WARNING
        when(personnelCertificateMapper.selectExpiringCandidates(TODAY, 90))
                .thenReturn(List.of(cert(1L, "REG_PRACTICE_CERT", "VALID", TODAY.plusDays(80), null)));
        when(personnelCertificateMapper.selectReviewDueCandidates(eq(TODAY), any(Integer.class))).thenReturn(List.of());
        when(personnelProfileMapper.selectExpiringIdCards(eq(TODAY), any(Integer.class))).thenReturn(List.of());
        when(certAlertRecordMapper.selectByIdempotentKey(anyString())).thenReturn(null);

        service.scanPersonnelExpiries(TODAY);

        ArgumentCaptor<CertAlertRecordDO> cap = ArgumentCaptor.forClass(CertAlertRecordDO.class);
        verify(certAlertRecordMapper).insert(cap.capture());
        assertEquals(CertificateAlertLevelEnum.WARNING.name(), cap.getValue().getAlertLevel());
    }

    @Test
    void scan_returnsZero_whenConfigDisabled() {
        CertAlertConfigDO row = new CertAlertConfigDO();
        row.setEnabled(false);
        row.setThresholds("[7,30,60,90]");
        when(certAlertConfigMapper.selectGlobal(anyString())).thenReturn(row);

        assertEquals(0, service.scanPersonnelExpiries(TODAY));
        verify(personnelCertificateMapper, never()).selectExpiringCandidates(any(), any(Integer.class));
    }

    @Test
    void legalFloorDays_coversKeyCertificateTypes() {
        Map<String, Integer> floor = service.legalFloorDays();
        assertEquals(90, floor.get("REG_PRACTICE_CERT"));
        assertEquals(90, floor.get("SAFETY_ASSESS_CERT"));
        assertEquals(180, floor.get("SPECIAL_EQUIPMENT"));
        assertFalse(floor.isEmpty());
    }
}
