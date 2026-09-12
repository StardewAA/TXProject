package cn.iocoder.txgy.module.crm.job.aftersale;

import cn.iocoder.txgy.framework.tenant.core.job.TenantJob;
import cn.iocoder.txgy.module.crm.service.aftersale.CrmAfterSaleVisitPlanService;
import cn.iocoder.txgy.module.crm.service.aftersale.CrmAfterSaleVisitTaskService;
import cn.iocoder.txgy.module.crm.service.aftersale.CrmAfterSaleWarrantyService;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 售后（质保/回访）定时任务 Handler
 *
 * 建议在 XXL-JOB 后台按如下 cron 注册（示例为每日 02:00 执行）：
 * - 质保状态流转与到期提醒：crmAfterSaleWarrantyRemindJob，0 0 2 * * ?
 * - 回访任务自动生成：crmAfterSaleVisitTaskGenerateJob，0 10 2 * * ?
 * - 回访任务逾期标记与临期提醒：crmAfterSaleVisitTaskRemindJob，0 20 2 * * ?
 *
 * @author txgycloud
 */
@Component
public class CrmAfterSaleJobHandler {

    @Resource
    private CrmAfterSaleWarrantyService warrantyService;
    @Resource
    private CrmAfterSaleVisitPlanService visitPlanService;
    @Resource
    private CrmAfterSaleVisitTaskService visitTaskService;

    /**
     * 质保状态自动流转 + 到期站内信提醒
     */
    @XxlJob("crmAfterSaleWarrantyRemindJob")
    @TenantJob
    public String warrantyRemindJob() {
        int statusCount = warrantyService.autoUpdateWarrantyStatus();
        int remindCount = warrantyService.sendWarrantyExpireRemind();
        return String.format("质保状态流转 %s 条，发送到期提醒 %s 条", statusCount, remindCount);
    }

    /**
     * 启用中回访计划的到期回访任务自动生成（断点续排）
     */
    @XxlJob("crmAfterSaleVisitTaskGenerateJob")
    @TenantJob
    public String visitTaskGenerateJob() {
        int count = visitPlanService.autoGenerateVisitTasks();
        return String.format("本次生成回访任务 %s 个", count);
    }

    /**
     * 回访任务逾期标记 + 临期/到期站内信提醒
     */
    @XxlJob("crmAfterSaleVisitTaskRemindJob")
    @TenantJob
    public String visitTaskRemindJob() {
        int overdueCount = visitTaskService.autoMarkOverdueVisitTasks();
        int remindCount = visitTaskService.remindDueVisitTasks();
        return String.format("标记逾期 %s 个，发送临期提醒 %s 个", overdueCount, remindCount);
    }

}
