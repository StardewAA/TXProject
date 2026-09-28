package cn.iocoder.txgy.module.hrm.service.employee.onboard;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.employee.HrmEmployeeOnboardGuideRespVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.attendance.config.HrmAttendanceGroupDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.insurance.employee.HrmInsuranceEmployeeInfoDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.salary.config.HrmSalaryGroupDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.salary.employee.HrmSalaryEmployeeInfoDO;
import cn.iocoder.txgy.module.hrm.service.attendance.config.HrmAttendanceGroupService;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import cn.iocoder.txgy.module.hrm.service.insurance.employee.HrmInsuranceEmployeeInfoService;
import cn.iocoder.txgy.module.hrm.service.salary.config.HrmSalaryGroupService;
import cn.iocoder.txgy.module.hrm.service.salary.employee.HrmSalaryEmployeeInfoService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * HRM 员工「上岗向导」Service 实现（I-020）
 *
 * <p>口径：员工入职后要真正能上岗、能算薪，必须配齐四件事——</p>
 * <ol>
 *   <li><b>后台账号</b>：员工能登录员工端（员工档案自动开通/绑定）；</li>
 *   <li><b>薪资</b>：既要在薪资组覆盖范围内，也要有生效薪资档案；</li>
 *   <li><b>考勤组</b>：按部门或按人加入考勤组（有默认考勤组也算覆盖）；</li>
 *   <li><b>社保</b>：在社保表登记参保（登记后才会计入社保数据同步）。</li>
 * </ol>
 *
 * <p>本实现只做只读检查并给出跳转目标，不修改任何数据，避免误操作。</p>
 *
 * @author OPENLAB BS
 */
@Service
@Validated
@Slf4j
public class HrmEmployeeOnboardGuideServiceImpl implements HrmEmployeeOnboardGuideService {

    private static final int TOTAL_ITEMS = 4;

    @Resource
    private HrmEmployeeService employeeService;
    @Resource
    private HrmSalaryGroupService salaryGroupService;
    @Resource
    private HrmSalaryEmployeeInfoService salaryEmployeeInfoService;
    @Resource
    private HrmAttendanceGroupService attendanceGroupService;
    @Resource
    private HrmInsuranceEmployeeInfoService insuranceEmployeeInfoService;

    @Override
    public HrmEmployeeOnboardGuideRespVO getOnboardGuide(Long employeeId) {
        HrmEmployeeDO employee = employeeService.validateEmployeeExists(employeeId);
        List<HrmEmployeeOnboardGuideRespVO.Item> items = new ArrayList<>();
        items.add(buildAccountItem(employee));
        items.add(buildSalaryItem(employee));
        items.add(buildAttendanceItem(employee));
        items.add(buildInsuranceItem(employee));
        int readyCount = (int) items.stream()
                .filter(item -> Boolean.TRUE.equals(item.getDone())).count();
        return new HrmEmployeeOnboardGuideRespVO()
                .setEmployeeId(employee.getId()).setEmployeeName(employee.getName())
                .setJobNumber(employee.getJobNumber())
                .setReadyCount(readyCount).setTotalCount(TOTAL_ITEMS).setReady(readyCount == TOTAL_ITEMS)
                .setItems(items);
    }

    private HrmEmployeeOnboardGuideRespVO.Item buildAccountItem(HrmEmployeeDO employee) {
        boolean bound = employee.getUserId() != null;
        return new HrmEmployeeOnboardGuideRespVO.Item()
                .setKey("account").setTitle("后台账号").setDone(bound)
                .setStatusText(bound ? "已开通" : "未开通")
                .setDetail(bound ? "员工可用「工号 / 初始密码」登录员工端"
                        : "未绑定后台账号，员工无法登录员工端（员工档案页可自动开通）")
                .setActionPath("/hrm/employee/detail/" + employee.getId())
                .setActionText(bound ? "查看员工档案" : "去开通账号");
    }

    private HrmEmployeeOnboardGuideRespVO.Item buildSalaryItem(HrmEmployeeDO employee) {
        Map<Long, HrmSalaryGroupDO> salaryGroupMap = salaryGroupService.getEmployeeSalaryGroupMap(
                Collections.singletonList(employee));
        HrmSalaryGroupDO salaryGroup = salaryGroupMap.get(employee.getId());
        HrmSalaryEmployeeInfoDO salaryInfo =
                salaryEmployeeInfoService.getSalaryEmployeeInfoByEmployeeId(employee.getId());
        boolean inGroup = salaryGroup != null;
        boolean hasArchive = salaryInfo != null;
        String detail;
        if (!inGroup && !hasArchive) {
            detail = "既未加入薪资组，也没有薪资档案，核算时不会出现在工资表里";
        } else if (!inGroup) {
            detail = "已有薪资档案，但未加入薪资组，核算时不会出现在工资表里";
        } else if (!hasArchive) {
            detail = "已加入薪资组「" + salaryGroup.getName() + "」，但还没有薪资档案（将继承上月或按 0 核算）";
        } else {
            detail = "已加入薪资组「" + salaryGroup.getName() + "」，且已建薪资档案";
        }
        return new HrmEmployeeOnboardGuideRespVO.Item()
                .setKey("salary").setTitle("薪资配置").setDone(inGroup && hasArchive)
                .setStatusText(inGroup && hasArchive ? "已就绪"
                        : inGroup || hasArchive ? "部分就绪" : "未配置")
                .setDetail(detail)
                .setActionPath("/hrm/salary/employee-info")
                .setActionText("去配置薪资");
    }

    private HrmEmployeeOnboardGuideRespVO.Item buildAttendanceItem(HrmEmployeeDO employee) {
        HrmAttendanceGroupDO matched = findAttendanceGroup(employee);
        boolean done = matched != null;
        return new HrmEmployeeOnboardGuideRespVO.Item()
                .setKey("attendance").setTitle("考勤组").setDone(done)
                .setStatusText(done ? "已覆盖" : "未覆盖")
                .setDetail(done ? "已纳入考勤组「" + matched.getName() + "」"
                        : "未匹配任何考勤组（按部门或按人均未命中、也没有默认考勤组），打卡与考勤统计会缺失")
                .setActionPath("/hrm/attendance/config/group")
                .setActionText("去配置考勤组");
    }

    private HrmAttendanceGroupDO findAttendanceGroup(HrmEmployeeDO employee) {
        List<HrmAttendanceGroupDO> groups = attendanceGroupService.getAttendanceGroupList();
        if (CollUtil.isEmpty(groups)) {
            return null;
        }
        // 1. 按人指定优先
        HrmAttendanceGroupDO byEmployee = groups.stream()
                .filter(group -> CollUtil.contains(group.getEmployeeIds(), employee.getId()))
                .findFirst().orElse(null);
        if (byEmployee != null) {
            return byEmployee;
        }
        // 2. 按部门覆盖
        HrmAttendanceGroupDO byDept = employee.getDeptId() == null ? null : groups.stream()
                .filter(group -> CollUtil.contains(group.getDeptIds(), employee.getDeptId()))
                .findFirst().orElse(null);
        if (byDept != null) {
            return byDept;
        }
        // 3. 默认考勤组兜底
        return groups.stream().filter(group -> Boolean.TRUE.equals(group.getDefaultStatus()))
                .findFirst().orElse(null);
    }

    private HrmEmployeeOnboardGuideRespVO.Item buildInsuranceItem(HrmEmployeeDO employee) {
        HrmInsuranceEmployeeInfoDO insuranceInfo =
                insuranceEmployeeInfoService.getInsuranceEmployeeInfoByEmployeeId(employee.getId());
        boolean done = insuranceInfo != null;
        return new HrmEmployeeOnboardGuideRespVO.Item()
                .setKey("insurance").setTitle("社保参保").setDone(done)
                .setStatusText(done ? "已登记" : "未登记")
                .setDetail(done ? "已在社保表登记参保（" + StrUtil.blankToDefault(
                                insuranceInfo.getSocialSecurityNumber(), "社保号未填") + "）"
                        : "未在社保表登记参保，核算时个人社保/公积金会按 0 计算（可能多发工资）")
                .setActionPath("/hrm/insurance/month-record")
                .setActionText("去登记参保");
    }

}
