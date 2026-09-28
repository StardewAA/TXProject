package cn.iocoder.txgy.module.hrm.job.employee;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.tenant.core.job.TenantJob;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeProbationRemindService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * HRM 试用期到期提醒 Job
 *
 * <p>每日扫描「在职 + 正式类型 + 未转正」且试用期结束日期在 N 天内的员工，给已绑定后台账号的员工发站内信，
 * 同时把待转正清单写进日志，便于 HR 在「员工档案 → 待转正」清单中处理。
 * 参数：提前提醒天数（默认 7），可通过 XXL-Job handler 名 {@code hrmEmployeeProbationExpireJob} 的 jobParam 覆盖。</p>
 *
 * @author OPENLAB BS
 */
@Component
@Slf4j
public class HrmEmployeeProbationExpireJob {

    @Resource
    private HrmEmployeeProbationRemindService probationRemindService;

    @XxlJob("hrmEmployeeProbationExpireJob")
    @TenantJob
    public String execute() {
        String param = XxlJobHelper.getJobParam();
        int days = StrUtil.isBlank(param) ? HrmEmployeeProbationRemindService.DEFAULT_REMIND_DAYS
                : Integer.parseInt(param.trim());
        List<HrmEmployeeDO> employees = probationRemindService.getExpiringEmployees(days);
        int sent = probationRemindService.sendProbationReminders(days);
        String names = employees.stream().map(HrmEmployeeDO::getName).limit(20).collect(Collectors.joining("、"));
        String result = StrUtil.format("试用期 {} 天内到期员工 {} 人，已发送提醒 {} 条{}",
                days, employees.size(), sent,
                employees.isEmpty() ? "" : "；名单：" + names + (employees.size() > 20 ? " 等" : ""));
        log.info("[hrmEmployeeProbationExpireJob][{}]", result);
        return result;
    }

}
