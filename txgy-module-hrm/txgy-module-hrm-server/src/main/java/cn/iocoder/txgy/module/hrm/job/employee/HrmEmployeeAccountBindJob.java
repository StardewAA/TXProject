package cn.iocoder.txgy.module.hrm.job.employee;

import cn.iocoder.txgy.framework.tenant.core.job.TenantJob;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.employee.HrmEmployeeAutoBindRespVO;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * HRM 员工档案 ↔ 后台账号 自动绑定 Job
 *
 * <p>口径A（档案为准）：员工档案是主体、后台账号是附属。历史数据里常见「导入了档案但没绑账号」
 * 与「已有账号但没建档案」两类错配；本任务每天跑一次，把有手机号能唯一匹配的档案自动绑上，
 * 并补「员工」角色，保证这些人登录后员工端可用。跑完仍匹配不上的会统计出来，由 HR 人工处理。</p>
 *
 * <p>handler 名：hrmEmployeeAccountBindJob；无需参数，建议每天凌晨执行一次。</p>
 *
 * @author OPENLAB BS
 */
@Component
@Slf4j
public class HrmEmployeeAccountBindJob {

    @Resource
    private HrmEmployeeService employeeService;

    @XxlJob("hrmEmployeeAccountBindJob")
    @TenantJob
    public String execute() {
        HrmEmployeeAutoBindRespVO result = employeeService.autoBindUserAccount();
        log.info("[execute][员工档案与后台账号自动绑定完成：扫描 {}，绑定 {}，未匹配 {}]",
                result.getScannedCount(), result.getBoundCount(), result.getUnmatchedCount());
        return String.format("HRM 员工档案与账号自动绑定：扫描未绑定档案 %s 份，本次绑定 %s 份，仍无法匹配 %s 份",
                result.getScannedCount(), result.getBoundCount(), result.getUnmatchedCount());
    }

}
