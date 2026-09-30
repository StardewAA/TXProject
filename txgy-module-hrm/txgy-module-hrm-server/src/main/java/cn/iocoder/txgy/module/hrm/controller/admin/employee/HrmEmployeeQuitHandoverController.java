package cn.iocoder.txgy.module.hrm.controller.admin.employee;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.quithandover.HrmEmployeeQuitHandoverConfirmReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.quithandover.HrmEmployeeQuitHandoverItemSaveReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.quithandover.HrmEmployeeQuitHandoverProgressRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.quithandover.HrmEmployeeQuitHandoverWaiveReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.quithandover.HrmEmployeeQuitSettleReqVO;
import cn.iocoder.txgy.module.hrm.service.employee.employment.HrmEmployeeQuitHandoverService;
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
 * HRM 员工离职交接 Controller
 *
 * <p>把"人要走、东西还没还"这件事做成可确认的闭环：先登记交接明细（工器具、劳保用品、
 * 证件资料、档案文件、财务借支），再给交接结论（已确认 / 无需交接）。
 * 离职立即生效前会校验该结论，避免系统里人已离职、现场工具还在个人手里。</p>
 *
 * @author OPENLAB BS
 */
@Tag(name = "管理后台 - HRM 员工离职交接")
@RestController
@RequestMapping("/hrm/employee/quit-handover")
@Validated
public class HrmEmployeeQuitHandoverController {

    @Resource
    private HrmEmployeeQuitHandoverService quitHandoverService;

    @GetMapping("/progress")
    @Operation(summary = "获得员工离职交接进度（含明细）")
    @Parameter(name = "employeeId", description = "员工编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('hrm:employee:query')")
    public CommonResult<HrmEmployeeQuitHandoverProgressRespVO> getHandoverProgress(
            @RequestParam("employeeId") Long employeeId) {
        return success(quitHandoverService.getHandoverProgress(employeeId));
    }

    @PostMapping("/item-save")
    @Operation(summary = "保存离职交接明细")
    @PreAuthorize("@ss.hasPermission('hrm:employee:update')")
    public CommonResult<Long> saveHandoverItem(@Valid @RequestBody HrmEmployeeQuitHandoverItemSaveReqVO reqVO) {
        return success(quitHandoverService.saveHandoverItem(reqVO));
    }

    @DeleteMapping("/item-delete")
    @Operation(summary = "删除离职交接明细")
    @Parameter(name = "id", description = "交接明细编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('hrm:employee:update')")
    public CommonResult<Boolean> deleteHandoverItem(@RequestParam("id") Long id) {
        quitHandoverService.deleteHandoverItem(id);
        return success(true);
    }

    @PutMapping("/confirm")
    @Operation(summary = "确认离职交接完成", description = "存在待交回明细时会拦截，缺失项需先在明细中登记为「缺失未交回」")
    @PreAuthorize("@ss.hasPermission('hrm:employee:update')")
    public CommonResult<Boolean> confirmHandover(@Valid @RequestBody HrmEmployeeQuitHandoverConfirmReqVO reqVO) {
        quitHandoverService.confirmHandover(reqVO);
        return success(true);
    }

    @PutMapping("/waive")
    @Operation(summary = "标记为无需交接", description = "非现场岗位等确实无公司物品时使用，需填写原因")
    @PreAuthorize("@ss.hasPermission('hrm:employee:update')")
    public CommonResult<Boolean> waiveHandover(@Valid @RequestBody HrmEmployeeQuitHandoverWaiveReqVO reqVO) {
        quitHandoverService.waiveHandover(reqVO);
        return success(true);
    }

    @PutMapping("/settle")
    @Operation(summary = "确认离职工资已结清", description = "确认后计薪截止日生效，离职闭环完成；未结清的离职员工不允许再入职")
    @PreAuthorize("@ss.hasPermission('hrm:employee:update')")
    public CommonResult<Boolean> settleQuitSalary(@Valid @RequestBody HrmEmployeeQuitSettleReqVO reqVO) {
        quitHandoverService.settleQuitSalary(reqVO);
        return success(true);
    }

}
