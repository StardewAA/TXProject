package cn.iocoder.txgy.module.hrm.job.employee;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.tenant.core.job.TenantJob;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeContractDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.service.employee.employment.HrmEmployeeContractService;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import cn.iocoder.txgy.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.txgy.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HRM 劳动合同到期预警 Job
 *
 * <p>扫描"在职员工 + 合同执行中 + 已开启到期提醒 + 未来 N 天内到期"的劳动合同，
 * 给员工绑定的后台账号发送站内信提醒（未绑定账号的只记日志），并返回执行摘要。</p>
 *
 * <p>可直接复用仓库既有的 XXL-Job 调度（handler 名：hrmContractExpireJob），
 * 参数可传提前预警天数（默认 30）。</p>
 *
 * @author OPENLAB BS
 */
@Component
@Slf4j
public class HrmEmployeeContractExpireJob {

    private static final int DEFAULT_WARN_DAYS = 30;
    /**
     * 站内信模板编码（与 system_notify_template 对应）
     */
    private static final String TEMPLATE_CODE = "hrm_contract_expire_warning";

    @Resource
    private HrmEmployeeContractService employeeContractService;
    @Resource
    private HrmEmployeeService employeeService;
    @Resource
    private NotifyMessageSendApi notifyMessageSendApi;

    @XxlJob("hrmContractExpireJob")
    @TenantJob
    public String execute() {
        String param = XxlJobHelper.getJobParam();
        int warnDays = StrUtil.isBlank(param) ? DEFAULT_WARN_DAYS : Integer.parseInt(param.trim());
        List<HrmEmployeeContractDO> contracts = employeeContractService.getExpireWarningList(warnDays);
        LocalDateTime now = LocalDateTime.now();
        int notified = 0;
        int unbound = 0;
        for (HrmEmployeeContractDO contract : contracts) {
            try {
                HrmEmployeeDO employee = employeeService.getEmployee(contract.getEmployeeId());
                if (employee == null || employee.getUserId() == null) {
                    unbound++;
                    log.info("[execute][合同({}) 到期预警：员工({})未绑定后台账号，仅记录日志]",
                            contract.getId(), contract.getEmployeeId());
                    continue;
                }
                Map<String, Object> params = new HashMap<>();
                params.put("employeeName", StrUtil.blankToDefault(employee.getName(), "员工"));
                params.put("contractNo", StrUtil.blankToDefault(contract.getNo(), String.valueOf(contract.getId())));
                params.put("endTime", contract.getEndTime() == null ? "-" : contract.getEndTime().toLocalDate().toString());
                params.put("remainDays", contract.getEndTime() == null ? "-"
                        : String.valueOf(ChronoUnit.DAYS.between(now, contract.getEndTime())));
                notifyMessageSendApi.sendSingleMessageToAdmin(new NotifySendSingleToUserReqDTO()
                        .setUserId(employee.getUserId())
                        .setTemplateCode(TEMPLATE_CODE)
                        .setTemplateParams(params));
                notified++;
            } catch (Throwable ex) {
                log.warn("[execute][合同({}) 到期预警发送失败]", contract.getId(), ex);
            }
        }
        return String.format("HRM 合同到期预警：提前 %s 天，命中合同 %s 份，已通知 %s 人，未绑定账号 %s 人",
                warnDays, contracts.size(), notified, unbound);
    }

}
