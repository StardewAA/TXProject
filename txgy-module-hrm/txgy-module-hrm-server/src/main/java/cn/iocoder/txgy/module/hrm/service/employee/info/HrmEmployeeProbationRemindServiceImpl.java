package cn.iocoder.txgy.module.hrm.service.employee.info;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.info.HrmEmployeeMapper;
import cn.iocoder.txgy.module.hrm.enums.MessageTemplateConstants;
import cn.iocoder.txgy.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.txgy.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.filterList;

/**
 * HRM 试用期到期提醒 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
@Slf4j
public class HrmEmployeeProbationRemindServiceImpl implements HrmEmployeeProbationRemindService {

    @Resource
    private HrmEmployeeMapper employeeMapper;
    @Resource
    private NotifyMessageSendApi notifyMessageSendApi;

    @Override
    public List<HrmEmployeeDO> getExpiringEmployees(Integer days) {
        int remindDays = days == null || days <= 0 ? DEFAULT_REMIND_DAYS : days;
        LocalDate today = LocalDate.now();
        LocalDate deadline = today.plusDays(remindDays);
        List<HrmEmployeeDO> probationEmployees = employeeMapper.selectProbationEmployees();
        List<HrmEmployeeDO> expiring = filterList(probationEmployees, employee -> {
            LocalDate end = resolveProbationEndDate(employee);
            return end != null && !end.isBefore(today) && !end.isAfter(deadline);
        });
        expiring.sort(Comparator.comparing(employee -> {
            LocalDate end = resolveProbationEndDate(employee);
            return end == null ? LocalDate.MAX : end;
        }));
        return expiring;
    }

    @Override
    public int sendProbationReminders(Integer days) {
        List<HrmEmployeeDO> employees = getExpiringEmployees(days);
        int sent = 0;
        for (HrmEmployeeDO employee : employees) {
            if (employee.getUserId() == null) {
                continue; // 未绑定后台账号：跳过（HR 侧仍可在待转正清单中看到）
            }
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("employeeName", StrUtil.blankToDefault(employee.getName(), "员工"));
                LocalDate end = resolveProbationEndDate(employee);
                params.put("probationEndDate", end == null ? "-" : end.toString());
                notifyMessageSendApi.sendSingleMessageToAdmin(new NotifySendSingleToUserReqDTO()
                        .setUserId(employee.getUserId())
                        .setTemplateCode(MessageTemplateConstants.PROBATION_EXPIRING)
                        .setTemplateParams(params));
                sent++;
            } catch (Throwable ex) {
                log.warn("[sendProbationReminders][员工({}) 试用期提醒发送失败]", employee.getId(), ex);
            }
        }
        return sent;
    }

    /**
     * 试用期结束日期（应转正日期）
     *
     * <p>优先取建档时自动写入的 {@code regularTime}；历史数据缺失时回退为「入职时间 + 试用期月数」。</p>
     */
    private LocalDate resolveProbationEndDate(HrmEmployeeDO employee) {
        if (employee.getRegularTime() != null) {
            return employee.getRegularTime().toLocalDate();
        }
        if (employee.getEntryTime() == null || employee.getProbation() == null || employee.getProbation() <= 0) {
            return null;
        }
        return employee.getEntryTime().toLocalDate().plusMonths(employee.getProbation());
    }

}
