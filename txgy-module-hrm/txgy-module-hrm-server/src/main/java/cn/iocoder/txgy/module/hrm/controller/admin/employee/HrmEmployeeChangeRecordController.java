package cn.iocoder.txgy.module.hrm.controller.admin.employee;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.collection.MapUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.changerecord.HrmEmployeeChangeRecordRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.changerecord.HrmEmployeeChangeRecordCancelReqVO;
import cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeChangeCancelStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeChangeSourceEnum;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeChangeRecordDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.service.employee.employment.HrmEmployeeChangeRecordService;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import cn.iocoder.txgy.module.system.api.dept.DeptApi;
import cn.iocoder.txgy.module.system.api.dept.dto.DeptRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSetByFlatMap;

@Tag(name = "管理后台 - HRM 员工异动记录")
@RestController
@RequestMapping("/hrm/employee/change-record")
@Validated
public class HrmEmployeeChangeRecordController {

    @Resource
    private HrmEmployeeChangeRecordService changeRecordService;
    @Resource
    private HrmEmployeeService employeeService;
    @Resource
    private DeptApi deptApi;

    @GetMapping("/list")
    @Operation(summary = "获得员工异动记录列表")
    @Parameter(name = "employeeId", description = "员工编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('hrm:employee:query')")
    public CommonResult<List<HrmEmployeeChangeRecordRespVO>> getEmployeeChangeRecordList(
            @RequestParam("employeeId") Long employeeId) {
        return success(buildEmployeeChangeRecordRespVOList(
                changeRecordService.getEmployeeChangeRecordListByEmployeeId(employeeId)));
    }

    @PutMapping("/cancel")
    @Operation(summary = "撤销尚未生效的员工异动", description = "仅未生效的异动可撤销；已生效的需用反向异动冲正，保证台账不可篡改")
    @PreAuthorize("@ss.hasPermission('hrm:employee:update')")
    public CommonResult<Boolean> cancelEmployeeChangeRecord(
            @Valid @RequestBody HrmEmployeeChangeRecordCancelReqVO reqVO) {
        changeRecordService.cancelEmployeeChangeRecord(reqVO);
        return success(true);
    }

    // ==================== 拼接 VO ====================

    private List<HrmEmployeeChangeRecordRespVO> buildEmployeeChangeRecordRespVOList(
            List<HrmEmployeeChangeRecordDO> changeRecords) {
        Map<Long, DeptRespDTO> deptMap = deptApi.getDeptMap(
                convertSetByFlatMap(changeRecords, changeRecord -> Stream.of(changeRecord.getOldDeptId(), changeRecord.getNewDeptId())));
        Map<Long, HrmEmployeeDO> leaderEmployeeMap = employeeService.getEmployeeMap(
                convertSetByFlatMap(changeRecords, changeRecord -> Stream.of(changeRecord.getOldLeaderEmployeeId(), changeRecord.getNewLeaderEmployeeId())));
        return BeanUtils.toBean(changeRecords, HrmEmployeeChangeRecordRespVO.class, changeRecord -> {
            // 异动来源名称与撤销状态，供前端直接展示（异动审计 + 撤销）
            HrmEmployeeChangeSourceEnum sourceEnum = HrmEmployeeChangeSourceEnum.valueOf(changeRecord.getSource());
            changeRecord.setSourceName(sourceEnum == null ? null : sourceEnum.getName());
            changeRecord.setApplied(changeRecord.getAppliedTime() != null);
            changeRecord.setCanceled(HrmEmployeeChangeCancelStatusEnum.CANCELED.getStatus()
                    .equals(changeRecord.getCancelStatus()));
            MapUtils.findAndThen(deptMap, changeRecord.getOldDeptId(),
                    dept -> changeRecord.setOldDeptName(dept.getName()));
            MapUtils.findAndThen(deptMap, changeRecord.getNewDeptId(),
                    dept -> changeRecord.setNewDeptName(dept.getName()));
            MapUtils.findAndThen(leaderEmployeeMap, changeRecord.getOldLeaderEmployeeId(),
                    leader -> changeRecord.setOldLeaderEmployeeName(leader.getName()));
            MapUtils.findAndThen(leaderEmployeeMap, changeRecord.getNewLeaderEmployeeId(),
                    leader -> changeRecord.setNewLeaderEmployeeName(leader.getName()));
        });
    }

}
