package cn.iocoder.txgy.module.bidding.job;

import cn.iocoder.txgy.module.bidding.service.certificate.CertificateAlertService;
import cn.iocoder.txgy.module.bidding.service.personnel.PersonnelAlertService;
import cn.iocoder.txgy.module.bidding.service.qualification.QualificationAlertService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 证照/资质/人员到期预警定时任务（"到期预警"闭环的调度入口）。
 *
 * <p>规范出处：v3.3 §十五-到期预警复用（企业资质/安许/人员注册 valid 到期 → cert_alert 域）；
 * 《电子资质七大模块技术方案.md》§1.3 Job/接口设计；既有先例 {@link SafetyRiskJob}
 * （同模块 @Scheduled + @Value 开关 + cron 可配 + 异常吞掉的范式）。
 *
 * <p>为什么必须由本模块补这个 Job：到期预警此前只有三个"运行接口"
 * （{@code /qualification-alert-run/scan} 等，面向运维补跑/联调），
 * 且其 javadoc 原写"定时化由调度中心 cron 调用"——但全仓既无 infra_job 也无 JobHandler/XXL-Job 实现，
 * 即**线上没有任何调度会调它们**：预警不会自己产生，中级交付的"到期预警"是死功能。
 * 本条按既有 Job 范式补齐，而不是继续依赖一个并不存在的调度中心。
 *
 * <p>为什么用 @Scheduled 而不是引调度框架：与 {@link SafetyRiskJob} 同一理由——本模块无 XXL-Job 依赖，
 * 引入新调度框架会让部署多一个组件。
 *
 * <p>为什么开关**默认关**（{@code bidding.credential-alert.scheduler.enabled:false}）：
 * ① 本模块的 @Scheduled 是**进程内**调度，多实例部署会每个实例各扫一遍（扫描按"同日幂等"设计，
 *    重跑只浪费资源、不会产生重复待办，但仍应由运维确认单实例执行或接受重复扫描）；
 * ② 首次开启会对存量证照批量落待办，属于可见的业务动作。
 * 因此上线动作是"运维改一行配置"，不是代码发版。启用与核对步骤见技术方案 §1.3。
 *
 * <p>业务目标：到期预警每日自动产出待办，不依赖人工点击；三个批次互相隔离，
 * 任一批次失败不影响其余批次，异常不逃逸以免被调度框架按策略跳过后续执行（预警长期静默）。
 */
@Slf4j
@Component
public class CredentialAlertJob {

    @Resource
    private CertificateAlertService certificateAlertService;

    @Resource
    private QualificationAlertService qualificationAlertService;

    @Resource
    private PersonnelAlertService personnelAlertService;

    /** 每轮派发的通知条数上限（运营参数，走部署配置而非规则配置中心——它不影响对外结论，只影响吞吐）。 */
    @Value("${bidding.credential-alert.notify.batch-size:100}")
    private int notifyBatchSize;

    /** 单次执行的派发轮数上限：积压时避免单次跑太久占住调度线程（100×10=单次最多 1000 条）。 */
    @Value("${bidding.credential-alert.notify.max-rounds:10}")
    private int notifyMaxRounds;

    /** 调度总开关（默认关：上线需运维确认后开启，理由见类注释）。 */
    @Value("${bidding.credential-alert.scheduler.enabled:false}")
    private boolean schedulerEnabled;

    /**
     * 证照到期预警：每日 02:00（cron 可配）。
     *
     * <p>为什么与其它批次错开 20 分钟：三个批次都会扫全量台账并写 cert_alert_records，
     * 同一分钟并发会互相争锁；错开可让单个批次慢时也不互相拖累。
     */
    @Scheduled(cron = "${bidding.credential-alert.cron.certificate:0 0 2 * * ?}")
    public void certificateExpiryAlertJob() {
        if (!schedulerEnabled) {
            return;
        }
        try {
            int created = certificateAlertService.scanExpiryAlerts(LocalDate.now());
            log.info("[credential-alert-job][certificate][done] 新增证照到期预警 {} 条", created);
        } catch (Exception ex) {
            // 定时任务必须吞掉异常：抛出会让后续调度被框架按策略跳过（预警长期停更）
            log.error("[credential-alert-job][certificate][fail] 证照到期预警批次异常：{}", ex.getMessage(), ex);
        }
    }

    /** 企业资质 + 安许到期预警：每日 02:20（cron 可配；两者共用 QUAL_EXPIRY 扫描入口）。 */
    @Scheduled(cron = "${bidding.credential-alert.cron.qualification:0 20 2 * * ?}")
    public void qualificationExpiryAlertJob() {
        if (!schedulerEnabled) {
            return;
        }
        try {
            int created = qualificationAlertService.scanQualificationExpiries(LocalDate.now());
            log.info("[credential-alert-job][qualification][done] 新增资质/安许到期预警 {} 条", created);
        } catch (Exception ex) {
            log.error("[credential-alert-job][qualification][fail] 资质到期预警批次异常：{}", ex.getMessage(), ex);
        }
    }

    /**
     * 通知派发：每 5 分钟（cron 可配）——把 outbox 里待发的预警通知真正发出去。
     *
     * <p>为什么必须有这一批、且频率远高于扫描：扫描只是"生成预警 + 入 outbox"，
     * 真正发送在 {@code CertificateAlertService#dispatchPending}（outbox 模式：失败退避重试、超次转死信）。
     * 此前派发只有手工端点 {@code POST /bidding/certificate-alert-run/dispatch}，
     * 即**通知会永远躺在 outbox 里发不出去**——扫描定时化只解决了一半，这是同一处死功能的第二层。
     * <p>为什么循环多轮：单轮最多 100 条，积压（如首次上线存量预警）需要连续排空；
     * 循环上界 {@code max-rounds} 防止积压时单次执行时间过长。
     * <p>为什么用"本轮未满即停"而不是"查到空为止"：查询按 limit 取件，返回数 < limit 即证明已排空，
     * 可少一次空查询（队列长度恰为 batch 整数倍时多跑一轮空查询，代价可忽略）。
     */
    @Scheduled(cron = "${bidding.credential-alert.cron.notify-dispatch:0 */5 * * * ?}")
    public void notifyDispatchJob() {
        if (!schedulerEnabled) {
            return;
        }
        try {
            int total = 0;
            for (int round = 0; round < notifyMaxRounds; round++) {
                int dispatched = certificateAlertService.dispatchPending(notifyBatchSize, LocalDateTime.now());
                total += dispatched;
                if (dispatched < notifyBatchSize) {
                    break;
                }
            }
            if (total > 0) {
                log.info("[credential-alert-job][notify][done] 派发预警通知 {} 条", total);
            }
        } catch (Exception ex) {
            log.error("[credential-alert-job][notify][fail] 通知派发批次异常：{}", ex.getMessage(), ex);
        }
    }

    /** 人员注册到期预警：每日 02:40（cron 可配）。 */
    @Scheduled(cron = "${bidding.credential-alert.cron.personnel:0 40 2 * * ?}")
    public void personnelExpiryAlertJob() {
        if (!schedulerEnabled) {
            return;
        }
        try {
            int created = personnelAlertService.scanPersonnelExpiries(LocalDate.now());
            log.info("[credential-alert-job][personnel][done] 新增人员注册到期预警 {} 条", created);
        } catch (Exception ex) {
            log.error("[credential-alert-job][personnel][fail] 人员到期预警批次异常：{}", ex.getMessage(), ex);
        }
    }
}
