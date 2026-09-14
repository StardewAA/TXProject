package cn.iocoder.txgy.module.bidding.service.certificate;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertAlertRecordDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertNotifyOutboxDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertificateInfoDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertAlertConfigMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertAlertRecordMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertNotifyOutboxMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.certificate.CertificateInfoMapper;
import cn.iocoder.txgy.module.bidding.enums.CertificateChangeSourceEnum;
import cn.iocoder.txgy.module.bidding.enums.CertificateStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 到期预警服务规则测试（Mockito，TDD）：
 * 档位映射（7→CRITICAL/20→WARNING/70→INFO）、幂等键防重跑、≤30 天置 EXPIRING_SOON、配置关闭跳过、
 * 发件箱派发成功/失败退避/死信、预警处理 CAS。
 * 依据：《电子证件全流程管理_数据库设计.md》v3.3 §九/§十一。
 */
class CertificateAlertServiceImplTest {

    private CertAlertConfigMapper certAlertConfigMapper;
    private CertAlertRecordMapper certAlertRecordMapper;
    private CertNotifyOutboxMapper certNotifyOutboxMapper;
    private CertificateInfoMapper certificateInfoMapper;
    private CertificateService certificateService;
    private CertNotifyChannelSender certNotifyChannelSender;
    private CertificateAlertServiceImpl service;

    @BeforeEach
    void setUp() {
        certAlertConfigMapper = mock(CertAlertConfigMapper.class);
        certAlertRecordMapper = mock(CertAlertRecordMapper.class);
        certNotifyOutboxMapper = mock(CertNotifyOutboxMapper.class);
        certificateInfoMapper = mock(CertificateInfoMapper.class);
        certificateService = mock(CertificateService.class);
        certNotifyChannelSender = mock(CertNotifyChannelSender.class);
        service = new CertificateAlertServiceImpl();
        ReflectionTestUtils.setField(service, "certAlertConfigMapper", certAlertConfigMapper);
        ReflectionTestUtils.setField(service, "certAlertRecordMapper", certAlertRecordMapper);
        ReflectionTestUtils.setField(service, "certNotifyOutboxMapper", certNotifyOutboxMapper);
        ReflectionTestUtils.setField(service, "certificateInfoMapper", certificateInfoMapper);
        ReflectionTestUtils.setField(service, "certificateService", certificateService);
        ReflectionTestUtils.setField(service, "certNotifyChannelSender", certNotifyChannelSender);
    }

    @Test
    void scan_createsRecordsWithLevelBandsAndOutboxPerChannel() {
        LocalDate today = LocalDate.of(2026, 9, 5);
        when(certAlertConfigMapper.selectGlobal(anyString())).thenReturn(null); // 平台默认开启
        CertificateInfoDO c7 = cert(1L, today.plusDays(5), CertificateStatusEnum.ACTIVE);
        CertificateInfoDO c20 = cert(2L, today.plusDays(20), CertificateStatusEnum.ACTIVE);
        CertificateInfoDO c70 = cert(3L, today.plusDays(70), CertificateStatusEnum.ACTIVE);
        when(certificateInfoMapper.selectExpiringCandidates(today, 90))
                .thenReturn(List.of(c7, c20, c70));
        when(certAlertRecordMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(certAlertRecordMapper.insert(any(CertAlertRecordDO.class))).thenAnswer(inv -> {
            CertAlertRecordDO d = inv.getArgument(0);
            d.setId(d.getCertId());
            return 1;
        });
        when(certNotifyOutboxMapper.insert(any(CertNotifyOutboxDO.class))).thenReturn(1);

        int created = service.scanExpiryAlerts(today);

        assertEquals(3, created);
        ArgumentCaptor<CertAlertRecordDO> recs = ArgumentCaptor.forClass(CertAlertRecordDO.class);
        verify(certAlertRecordMapper, times(3)).insert(recs.capture());
        List<CertAlertRecordDO> rows = recs.getAllValues();
        assertEquals("CRITICAL", rows.get(0).getAlertLevel(), "≤7 天应 CRITICAL");
        assertEquals("WARNING", rows.get(1).getAlertLevel(), "≤30 天应 WARNING");
        assertEquals("INFO", rows.get(2).getAlertLevel(), "70 天（≤90/60）应 INFO");
        assertTrue(rows.get(0).getAlertMessage().contains("5 天后到期"));
        // 每证照默认 2 渠道 → 6 条发件箱
        verify(certNotifyOutboxMapper, times(6)).insert(any(CertNotifyOutboxDO.class));
        ArgumentCaptor<CertNotifyOutboxDO> outbox = ArgumentCaptor.forClass(CertNotifyOutboxDO.class);
        verify(certNotifyOutboxMapper, times(6)).insert(outbox.capture());
        assertEquals(1L, outbox.getAllValues().get(0).getBizId());
        assertTrue(outbox.getAllValues().get(0).getPayload().contains("alertMessage"));
    }

    @Test
    void scan_activeWith30DaysOrLess_marksExpiringSoon() {
        LocalDate today = LocalDate.of(2026, 9, 5);
        when(certAlertConfigMapper.selectGlobal(anyString())).thenReturn(null);
        CertificateInfoDO c20 = cert(2L, today.plusDays(20), CertificateStatusEnum.ACTIVE);
        c20.setVersion(3);
        when(certificateInfoMapper.selectExpiringCandidates(today, 90)).thenReturn(List.of(c20));
        when(certAlertRecordMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(certAlertRecordMapper.insert(any(CertAlertRecordDO.class))).thenAnswer(inv -> {
            CertAlertRecordDO d = inv.getArgument(0);
            d.setId(9L);
            return 1;
        });
        when(certNotifyOutboxMapper.insert(any(CertNotifyOutboxDO.class))).thenReturn(1);

        service.scanExpiryAlerts(today);

        verify(certificateService).changeStatus(eq(2L), eq(CertificateStatusEnum.EXPIRING_SOON.name()),
                eq(3), anyString(), eq(CertificateChangeSourceEnum.SYSTEM), isNull());
    }

    @Test
    void scan_sameDayRerunSkippedByIdempotentKey() {
        LocalDate today = LocalDate.of(2026, 9, 5);
        when(certAlertConfigMapper.selectGlobal(anyString())).thenReturn(null);
        when(certificateInfoMapper.selectExpiringCandidates(today, 90))
                .thenReturn(List.of(cert(1L, today.plusDays(5), CertificateStatusEnum.ACTIVE)));
        CertAlertRecordDO existing = new CertAlertRecordDO();
        existing.setId(1L);
        when(certAlertRecordMapper.selectByIdempotentKey(anyString())).thenReturn(existing);

        int created = service.scanExpiryAlerts(today);

        assertEquals(0, created, "幂等键命中当日记录，重跑应跳过");
        verify(certAlertRecordMapper, never()).insert(any(CertAlertRecordDO.class));
        verify(certNotifyOutboxMapper, never()).insert(any(CertNotifyOutboxDO.class));
        verify(certificateService, never()).changeStatus(any(), any(), anyInt(), anyString(), any(), any());
    }

    @Test
    void scan_configDisabledSkipsAll() {
        // 配置行存在但 enabled=false
        cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertAlertConfigDO row =
                new cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertAlertConfigDO();
        row.setAlertType("EXPIRY");
        row.setEnabled(Boolean.FALSE);
        when(certAlertConfigMapper.selectGlobal(anyString())).thenReturn(row);

        int created = service.scanExpiryAlerts(LocalDate.of(2026, 9, 5));

        assertEquals(0, created);
        verify(certificateInfoMapper, never()).selectExpiringCandidates(any(), anyInt());
    }

    @Test
    void dispatch_successMarksSuccessAndMirrorsRecordStatus() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 9, 5, 9, 0);
        CertNotifyOutboxDO row = outbox(10L, "ALERT", 1L, "EMAIL", "PENDING");
        when(certNotifyOutboxMapper.selectDispatchable(now, 50)).thenReturn(List.of(row));
        when(certNotifyOutboxMapper.updateById(any(CertNotifyOutboxDO.class))).thenReturn(1);

        int handled = service.dispatchPending(50, now);

        assertEquals(1, handled);
        assertEquals("SUCCESS", row.getSendStatus());
        assertEquals(now, row.getSentAt());
        verify(certAlertRecordMapper).updateNotifyStatus(1L, "SUCCESS");
    }

    @Test
    void dispatch_failureBacksOffThenDead() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 9, 5, 9, 0);
        // 第一次失败：重试 1 → FAILED + 1 分钟后
        CertNotifyOutboxDO r1 = outbox(10L, "ALERT", 1L, "EMAIL", "PENDING");
        when(certNotifyOutboxMapper.selectDispatchable(now, 50)).thenReturn(List.of(r1));
        org.mockito.Mockito.doThrow(new RuntimeException("gateway down"))
                .when(certNotifyChannelSender).send(anyString(), anyString(), anyString());
        when(certNotifyOutboxMapper.updateById(any(CertNotifyOutboxDO.class))).thenReturn(1);

        service.dispatchPending(50, now);
        assertEquals("FAILED", r1.getSendStatus());
        assertEquals(1, r1.getRetryCount());
        assertEquals(now.plusMinutes(1), r1.getNextRetryAt());
        verify(certAlertRecordMapper, never()).updateNotifyStatus(any(), anyString());

        // 已达最大重试（retry_count=5=max_retry）再失败 → DEAD 死信
        CertNotifyOutboxDO rDead = outbox(11L, "ALERT", 2L, "EMAIL", "FAILED");
        rDead.setRetryCount(5);
        rDead.setMaxRetry(5);
        rDead.setNextRetryAt(now.minusMinutes(1));
        when(certNotifyOutboxMapper.selectDispatchable(now, 50)).thenReturn(List.of(rDead));
        service.dispatchPending(50, now);
        assertEquals("DEAD", rDead.getSendStatus());
        assertEquals(6, rDead.getRetryCount());
    }

    @Test
    void markHandled_okAndVersionConflict() {
        CertAlertRecordDO rec = new CertAlertRecordDO();
        rec.setId(7L);
        rec.setVersion(0);
        when(certAlertRecordMapper.selectById(7L)).thenReturn(rec);
        when(certAlertRecordMapper.updateById(any(CertAlertRecordDO.class))).thenReturn(1);

        service.markAlertHandled(7L, 0, 42L, "已续期");
        assertTrue(rec.getHandled());
        assertEquals("已续期", rec.getHandleNote());

        CertAlertRecordDO rec2 = new CertAlertRecordDO();
        rec2.setId(8L);
        rec2.setVersion(2);
        when(certAlertRecordMapper.selectById(8L)).thenReturn(rec2);
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.markAlertHandled(8L, 1, 42L, "已续期"));
        assertEquals(Integer.valueOf(1_060_109_011), e.getCode(), "版本不符应报 CERT_ALERT_RECORD_MODIFIED");
    }

    // ========== 工具 ==========

    private CertificateInfoDO cert(Long id, LocalDate validTo, CertificateStatusEnum status) {
        CertificateInfoDO c = new CertificateInfoDO();
        c.setId(id);
        c.setCertName("建筑业企业资质证书");
        c.setCertNo("D" + id);
        c.setCertStatus(status.name());
        c.setValidTo(validTo);
        c.setVersion(0);
        return c;
    }

    private CertNotifyOutboxDO outbox(Long id, String bizType, Long bizId, String channel, String status) {
        CertNotifyOutboxDO o = new CertNotifyOutboxDO();
        o.setId(id);
        o.setBizType(bizType);
        o.setBizId(bizId);
        o.setChannel(channel);
        o.setReceiver("TENANT_ADMIN");
        o.setPayload("{\"alertMessage\":\"将于 30 天后到期\"}");
        o.setSendStatus(status);
        o.setRetryCount(0);
        o.setMaxRetry(5);
        return o;
    }
}
