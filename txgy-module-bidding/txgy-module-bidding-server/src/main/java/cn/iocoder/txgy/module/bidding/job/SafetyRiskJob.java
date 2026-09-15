package cn.iocoder.txgy.module.bidding.job;

import cn.iocoder.txgy.module.bidding.service.safety.SafetyPermitRiskService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 安许风险与人员风险定时任务（方案 §1.3 的三个 Job）。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§1.3（safetyPermitRiskJob / personnelRiskJob / projectKeyPostChangeJob）；
 * 既有先例：本模块 CrawlRunServiceImpl 的 @Scheduled + @Value 开关范式（§4.5 一致）。
 * <p>为什么用 @Scheduled 而不是 XXL-Job：本模块既有定时任务均为 Spring 调度（无 XXL-Job 依赖），
 * 引入新调度框架会让部署多一个组件；同时保留 dryRun 与"开关默认关"两个安全阀——风险判定会阻断投标，
 * 首次上线必须先 dryRun 核对分布再开启（方案 §1.5 迁移步骤）。
 * <p>业务目标：风险状态每日自动刷新，不依赖人工点击；异常可通过开关一键停用。
 */
@Slf4j
@Component
public class SafetyRiskJob {

    @Resource
    private SafetyPermitRiskService safetyPermitRiskService;

    /** 调度总开关（默认关：生产首次上线需先 dryRun 核对后再开，避免误判直接阻断投标）。 */
    @Value("${bidding.safety-risk.scheduler.enabled:false}")
    private boolean schedulerEnabled;

    /** 安许风险：每日 02:10（cron 可配）。 */
    @Scheduled(cron = "${bidding.safety-risk.cron.safety-permit:0 10 2 * * ?}")
    public void safetyPermitRiskJob() {
        if (!schedulerEnabled) {
            return;
        }
        try {
            int evaluated = safetyPermitRiskService.evaluateAll(LocalDate.now(), false);
            log.info("[safety-risk-job][done] 评估安许证 {} 张", evaluated);
        } catch (Exception ex) {
            // 定时任务必须吞掉异常：抛出会让后续调度被框架按策略跳过（风险状态长期停更）
            log.error("[safety-risk-job][fail] 安许风险评估批次异常：{}", ex.getMessage(), ex);
        }
    }

    /** 人员风险：每周一 03:00（cron 可配）。 */
    @Scheduled(cron = "${bidding.safety-risk.cron.personnel:0 0 3 ? * MON}")
    public void personnelRiskJob() {
        if (!schedulerEnabled) {
            return;
        }
        try {
            int abnormal = safetyPermitRiskService.evaluatePersonnelRisk(LocalDate.now());
            log.info("[person-risk-job][done] 打标异常人员 {} 名", abnormal);
        } catch (Exception ex) {
            log.error("[person-risk-job][fail] 人员风险批次异常：{}", ex.getMessage(), ex);
        }
    }

    /** 关键岗位变更：每日 03:30（cron 可配）。 */
    @Scheduled(cron = "${bidding.safety-risk.cron.key-post:0 30 3 * * ?}")
    public void projectKeyPostChangeJob() {
        if (!schedulerEnabled) {
            return;
        }
        // 说明：关键岗位变更统计当前按需查询（getKeyPostChanges），批次任务仅做日志巡检，
        //      待"项目-岗位-人员"登记入口上线后在此改为遍历在施项目并产出合规待办（plan §1.3 待接入项）。
        log.info("[key-post-job][tick] 关键岗位变更巡检（当前为按需查询模式）");
    }
}
