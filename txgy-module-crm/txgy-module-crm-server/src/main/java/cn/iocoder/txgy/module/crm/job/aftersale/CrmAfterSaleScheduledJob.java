package cn.iocoder.txgy.module.crm.job.aftersale;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.crm.service.aftersale.CrmAfterSaleTicketService;
import cn.iocoder.txgy.module.crm.service.aftersale.CrmAfterSaleVisitPlanService;
import cn.iocoder.txgy.module.crm.service.aftersale.CrmAfterSaleVisitTaskService;
import cn.iocoder.txgy.module.crm.service.aftersale.CrmAfterSaleWarrantyService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * 售后（质保/回访）定时任务 —— Spring 内置调度
 *
 * <p>与 XXL-Job 版本（{@link CrmAfterSaleJobHandler}）逻辑等价：当部署环境未接入
 * XXL-Job 调度中心（{@code xxl.job.enabled=false}）时，由本类基于 Spring @Scheduled
 * 兜底执行，保证「回访任务逾期标记」等能力开箱可用。两者重复执行是幂等的。
 *
 * <p>执行方式与框架 {@code @TenantJob} 一致：按租户逐个执行，保证多租户数据隔离正确。
 *
 * @author txgycloud
 */
@Component
@Slf4j
public class CrmAfterSaleScheduledJob {

    @Resource
    private CrmAfterSaleWarrantyService warrantyService;
    @Resource
    private CrmAfterSaleVisitPlanService visitPlanService;
    @Resource
    private CrmAfterSaleVisitTaskService visitTaskService;
    @Resource
    private CrmAfterSaleTicketService ticketService;
    @Resource
    private TenantFrameworkService tenantFrameworkService;

    /**
     * 回访任务逾期标记：每日 02:20 扫描一次
     *
     * <p>状态为「待处理」且当前日期已超过「窗口期截止日期」的任务，自动标记为「已逾期」。
     * 窗口期截止日期 = 计划回访日期（task_date）+ 回访任务逾期判定天数；窗口期内
     * （计划回访日期 ≤ 今天 ≤ 窗口期截止日期）不标记逾期。
     * 逾期状态于每日凌晨统一流转，最长延迟约 24 小时。
     */
    @Scheduled(cron = "0 20 2 * * ?")
    public void markOverdueVisitTasks() {
        executeForEachTenant("markOverdueVisitTasks", tenantId -> {
            int count = visitTaskService.autoMarkOverdueVisitTasks();
            log.info("[markOverdueVisitTasks][租户({}) 自动标记逾期回访任务 {} 个]", tenantId, count);
        });
    }

    /**
     * 工单超时自动标记：每日 02:30 扫描一次
     *
     * <p>扫描所有未闭环（待处理 / 处理中）的工单，若「开始处理时间（未开始则以创建时间兜底）
     * + 售后设置『工单超时预警天数』」已早于当前时间，则自动把状态变更为「已超时」。
     * 被标记的工单会出现在 CRM 待办事项的「工单超时预警」中，直到完成或关闭。
     * 读取侧另有懒同步兜底（30 秒限流），最长延迟约 24 小时。
     */
    @Scheduled(cron = "0 30 2 * * ?")
    public void markOverdueTickets() {
        executeForEachTenant("markOverdueTickets", tenantId -> {
            int count = ticketService.autoMarkOverdueTickets();
            log.info("[markOverdueTickets][租户({}) 自动标记超时工单 {} 个]", tenantId, count);
        });
    }

    /**
     * 质保状态流转 + 到期站内信提醒：每日 02:00
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void warrantyRemindJob() {
        executeForEachTenant("warrantyRemindJob", tenantId -> {
            int statusCount = warrantyService.autoUpdateWarrantyStatus();
            int remindCount = warrantyService.sendWarrantyExpireRemind();
            log.info("[warrantyRemindJob][租户({}) 质保状态流转 {} 条，发送到期提醒 {} 条]", tenantId, statusCount, remindCount);
        });
    }

    /**
     * 回访任务自动生成（断点续排）+ 临期/到期提醒：每日 02:10
     */
    @Scheduled(cron = "0 10 2 * * ?")
    public void visitTaskGenerateAndRemindJob() {
        executeForEachTenant("visitTaskGenerateAndRemindJob", tenantId -> {
            int genCount = visitPlanService.autoGenerateVisitTasks();
            int remindCount = visitTaskService.remindDueVisitTasks();
            log.info("[visitTaskGenerateAndRemindJob][租户({}) 生成回访任务 {} 个，发送临期提醒 {} 个]", tenantId, genCount, remindCount);
        });
    }

    /**
     * 按租户逐个执行任务逻辑（与框架 @TenantJob 的做法保持一致）
     *
     * @param jobName 任务名称，用于日志
     * @param action  单个租户内的执行逻辑
     */
    private void executeForEachTenant(String jobName, Consumer<Long> action) {
        List<Long> tenantIds;
        try {
            tenantIds = tenantFrameworkService.getTenantIds();
        } catch (Exception ex) {
            log.error("[{}][获取租户列表失败]", jobName, ex);
            return;
        }
        if (CollUtil.isEmpty(tenantIds)) {
            log.info("[{}][无有效租户，跳过执行]", jobName);
            return;
        }
        tenantIds.forEach(tenantId -> {
            try {
                TenantUtils.execute(tenantId, () -> action.accept(tenantId));
            } catch (Exception ex) {
                log.error("[{}][租户({}) 执行失败]", jobName, tenantId, ex);
            }
        });
    }

}
