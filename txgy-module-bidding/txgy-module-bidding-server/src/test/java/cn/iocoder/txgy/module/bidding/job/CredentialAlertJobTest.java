package cn.iocoder.txgy.module.bidding.job;

import cn.iocoder.txgy.module.bidding.service.certificate.CertificateAlertService;
import cn.iocoder.txgy.module.bidding.service.personnel.PersonnelAlertService;
import cn.iocoder.txgy.module.bidding.service.qualification.QualificationAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 到期预警定时任务单测。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§1.3（Job 设计）；ARCH 规范（阈值/开关集中受控）。
 * <p>为什么这些用例值得写：调度代码的失效方式是"静默不跑"——开关接反、异常逃逸导致后续调度被跳过，
 * 都不会有人报错。故显式钉住三件事：关时一次都不调、开时调到、异常不逃逸。
 */
class CredentialAlertJobTest {

    private CertificateAlertService certificateAlertService;
    private QualificationAlertService qualificationAlertService;
    private PersonnelAlertService personnelAlertService;
    private CredentialAlertJob job;

    @BeforeEach
    void setUp() {
        certificateAlertService = mock(CertificateAlertService.class);
        qualificationAlertService = mock(QualificationAlertService.class);
        personnelAlertService = mock(PersonnelAlertService.class);
        job = new CredentialAlertJob();
        ReflectionTestUtils.setField(job, "certificateAlertService", certificateAlertService);
        ReflectionTestUtils.setField(job, "qualificationAlertService", qualificationAlertService);
        ReflectionTestUtils.setField(job, "personnelAlertService", personnelAlertService);
        // 运营参数默认值（与 @Value 默认一致）：批量 100、轮数上限 10
        ReflectionTestUtils.setField(job, "notifyBatchSize", 100);
        ReflectionTestUtils.setField(job, "notifyMaxRounds", 10);
    }

    @Test
    void notifyDispatch_disabled_doesNotDispatch() {
        ReflectionTestUtils.setField(job, "schedulerEnabled", false);

        job.notifyDispatchJob();

        verify(certificateAlertService, never()).dispatchPending(any(Integer.class), any(LocalDateTime.class));
    }

    @Test
    void notifyDispatch_loopsUntilQueueDrained() {
        ReflectionTestUtils.setField(job, "schedulerEnabled", true);
        // 前两轮满批（还有积压），第三轮未满 → 排空后停止
        when(certificateAlertService.dispatchPending(any(Integer.class), any(LocalDateTime.class)))
                .thenReturn(100).thenReturn(100).thenReturn(3);

        job.notifyDispatchJob();

        verify(certificateAlertService, times(3)).dispatchPending(any(Integer.class), any(LocalDateTime.class));
    }

    @Test
    void notifyDispatch_stopsAtMaxRounds_whenBacklogNeverDrains() {
        ReflectionTestUtils.setField(job, "schedulerEnabled", true);
        ReflectionTestUtils.setField(job, "notifyMaxRounds", 4);
        when(certificateAlertService.dispatchPending(any(Integer.class), any(LocalDateTime.class))).thenReturn(100);

        job.notifyDispatchJob();

        // 上界生效：不会因为积压把调度线程占死
        verify(certificateAlertService, times(4)).dispatchPending(any(Integer.class), any(LocalDateTime.class));
    }

    @Test
    void notifyDispatch_usesConfiguredBatchSize() {
        ReflectionTestUtils.setField(job, "schedulerEnabled", true);
        ReflectionTestUtils.setField(job, "notifyBatchSize", 25);
        // 注意：返回数**等于**批量时循环会继续（无法判断是否排空），故再给一轮 0 表示排空
        when(certificateAlertService.dispatchPending(any(Integer.class), any(LocalDateTime.class)))
                .thenReturn(25).thenReturn(0);

        job.notifyDispatchJob();

        // 断言批量取自配置（25）而不是写死的 100
        verify(certificateAlertService, atLeastOnce()).dispatchPending(eq(25), any(LocalDateTime.class));
        verify(certificateAlertService, times(2)).dispatchPending(any(Integer.class), any(LocalDateTime.class));
    }

    @Test
    void notifyDispatch_failure_isSwallowed() {
        ReflectionTestUtils.setField(job, "schedulerEnabled", true);
        when(certificateAlertService.dispatchPending(any(Integer.class), any(LocalDateTime.class)))
                .thenThrow(new IllegalStateException("模拟派发失败"));

        assertDoesNotThrow(job::notifyDispatchJob);
    }

    @Test
    void schedulerDisabled_noneOfTheScansRun() {
        ReflectionTestUtils.setField(job, "schedulerEnabled", false);

        job.certificateExpiryAlertJob();
        job.qualificationExpiryAlertJob();
        job.personnelExpiryAlertJob();

        verify(certificateAlertService, never()).scanExpiryAlerts(any(LocalDate.class));
        verify(qualificationAlertService, never()).scanQualificationExpiries(any(LocalDate.class));
        verify(personnelAlertService, never()).scanPersonnelExpiries(any(LocalDate.class));
    }

    @Test
    void schedulerEnabled_eachBatchScansTodayOnce() {
        ReflectionTestUtils.setField(job, "schedulerEnabled", true);
        when(certificateAlertService.scanExpiryAlerts(any(LocalDate.class))).thenReturn(3);
        when(qualificationAlertService.scanQualificationExpiries(any(LocalDate.class))).thenReturn(2);
        when(personnelAlertService.scanPersonnelExpiries(any(LocalDate.class))).thenReturn(1);

        job.certificateExpiryAlertJob();
        job.qualificationExpiryAlertJob();
        job.personnelExpiryAlertJob();

        // 扫的是"当天"：跨天重跑必须以运行日为准，不能用启动日缓存
        verify(certificateAlertService).scanExpiryAlerts(LocalDate.now());
        verify(qualificationAlertService).scanQualificationExpiries(LocalDate.now());
        verify(personnelAlertService).scanPersonnelExpiries(LocalDate.now());
    }

    @Test
    void scanFailure_isSwallowed_soLaterRunsAreNotSkipped() {
        ReflectionTestUtils.setField(job, "schedulerEnabled", true);
        when(certificateAlertService.scanExpiryAlerts(any(LocalDate.class)))
                .thenThrow(new IllegalStateException("模拟扫描失败"));

        // 抛出会让调度框架按策略跳过后续执行 → 预警长期静默，故必须吞掉
        assertDoesNotThrow(job::certificateExpiryAlertJob);
        // 批次互相隔离：证照批次失败不影响资质/人员批次
        when(qualificationAlertService.scanQualificationExpiries(any(LocalDate.class))).thenReturn(0);
        assertDoesNotThrow(job::qualificationExpiryAlertJob);
        verify(qualificationAlertService).scanQualificationExpiries(LocalDate.now());
    }
}
