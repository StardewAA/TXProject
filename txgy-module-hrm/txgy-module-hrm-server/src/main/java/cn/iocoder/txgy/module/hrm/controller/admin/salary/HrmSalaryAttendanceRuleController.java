package cn.iocoder.txgy.module.hrm.controller.admin.salary;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.attendancerule.HrmSalaryAttendanceCalcReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.attendancerule.HrmSalaryAttendanceCalcRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.attendancerule.HrmSalaryAttendanceRulePageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.attendancerule.HrmSalaryAttendanceRuleSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.salary.attendancerule.HrmSalaryAttendanceRuleDO;
import cn.iocoder.txgy.module.hrm.service.salary.attendancerule.HrmSalaryAttendanceRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 考勤计薪规则：把"出勤/缺勤怎么算钱"做成 HR 可配置的模板。
 *
 * @author OPENLAB BS
 */
@Tag(name = "管理后台 - HRM 考勤计薪规则")
@RestController
@RequestMapping("/hrm/salary/attendance-rule")
@Validated
public class HrmSalaryAttendanceRuleController {

    @Resource
    private HrmSalaryAttendanceRuleService attendanceRuleService;

    @PostMapping("/create")
    @Operation(summary = "创建考勤计薪规则")
    @PreAuthorize("@ss.hasPermission('hrm:salary:attendance-rule:create')")
    public CommonResult<Long> createRule(@Valid @RequestBody HrmSalaryAttendanceRuleSaveReqVO createReqVO) {
        return success(attendanceRuleService.createRule(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新考勤计薪规则")
    @PreAuthorize("@ss.hasPermission('hrm:salary:attendance-rule:update')")
    public CommonResult<Boolean> updateRule(@Valid @RequestBody HrmSalaryAttendanceRuleSaveReqVO updateReqVO) {
        attendanceRuleService.updateRule(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除考勤计薪规则")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('hrm:salary:attendance-rule:delete')")
    public CommonResult<Boolean> deleteRule(@RequestParam("id") Long id) {
        attendanceRuleService.deleteRule(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得考勤计薪规则")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('hrm:salary:attendance-rule:query')")
    public CommonResult<HrmSalaryAttendanceRuleDO> getRule(@RequestParam("id") Long id) {
        return success(attendanceRuleService.getRule(id));
    }

    @GetMapping("/page")
    @Operation(summary = "获得考勤计薪规则分页")
    @PreAuthorize("@ss.hasPermission('hrm:salary:attendance-rule:query')")
    public CommonResult<PageResult<HrmSalaryAttendanceRuleDO>> getRulePage(
            @Valid HrmSalaryAttendanceRulePageReqVO pageReqVO) {
        return success(attendanceRuleService.getRulePage(pageReqVO));
    }

    @GetMapping("/default")
    @Operation(summary = "获得默认考勤计薪规则")
    @PreAuthorize("@ss.hasPermission('hrm:salary:attendance-rule:query')")
    public CommonResult<HrmSalaryAttendanceRuleDO> getDefaultRule() {
        return success(attendanceRuleService.getDefaultRule());
    }

    @PostMapping("/calc")
    @Operation(summary = "考勤计薪试算", description = "按规则把出勤/缺勤/请假折算成扣款与满勤奖，返回试算应发；可直接用于月度工资表")
    @PreAuthorize("@ss.hasPermission('hrm:salary:attendance-rule:query')")
    public CommonResult<HrmSalaryAttendanceCalcRespVO> calc(
            @Valid @RequestBody HrmSalaryAttendanceCalcReqVO calcReqVO) {
        return success(attendanceRuleService.calc(calcReqVO));
    }

}
