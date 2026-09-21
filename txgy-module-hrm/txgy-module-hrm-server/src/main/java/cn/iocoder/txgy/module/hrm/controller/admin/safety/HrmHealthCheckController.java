package cn.iocoder.txgy.module.hrm.controller.admin.safety;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.safety.vo.HrmHealthCheckPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.safety.vo.HrmHealthCheckRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.safety.vo.HrmHealthCheckSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.safety.HrmHealthCheckDO;
import cn.iocoder.txgy.module.hrm.enums.safety.HrmHealthCheckResultEnum;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import cn.iocoder.txgy.module.hrm.service.safety.HrmHealthCheckService;
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

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * HRM 体检与职业健康 Controller
 *
 * @author OPENLAB BS
 */
@Tag(name = "管理后台 - HRM 体检与职业健康")
@RestController
@RequestMapping("/hrm/health-check")
@Validated
public class HrmHealthCheckController {

    private static final Map<Integer, String> CHECK_TYPE_NAMES =
            Map.of(1, "入职", 2, "在岗", 3, "离职", 4, "职业健康");

    @Resource
    private HrmHealthCheckService healthCheckService;
    @Resource
    private HrmEmployeeService employeeService;

    @PostMapping("/create")
    @Operation(summary = "新增体检记录")
    @PreAuthorize("@ss.hasPermission('hrm:safety:health:update')")
    public CommonResult<Long> createHealthCheck(@Valid @RequestBody HrmHealthCheckSaveReqVO reqVO) {
        return success(healthCheckService.createHealthCheck(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改体检记录")
    @PreAuthorize("@ss.hasPermission('hrm:safety:health:update')")
    public CommonResult<Boolean> updateHealthCheck(@Valid @RequestBody HrmHealthCheckSaveReqVO reqVO) {
        healthCheckService.updateHealthCheck(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除体检记录")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('hrm:safety:health:update')")
    public CommonResult<Boolean> deleteHealthCheck(@RequestParam("id") Long id) {
        healthCheckService.deleteHealthCheck(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得体检记录分页")
    @PreAuthorize("@ss.hasPermission('hrm:safety:query')")
    public CommonResult<PageResult<HrmHealthCheckRespVO>> getHealthCheckPage(
            @Valid HrmHealthCheckPageReqVO pageReqVO) {
        PageResult<HrmHealthCheckDO> page = healthCheckService.getHealthCheckPage(pageReqVO);
        return success(new PageResult<>(buildRespVOList(page.getList()), page.getTotal()));
    }

    @GetMapping("/list")
    @Operation(summary = "获得员工体检记录列表")
    @Parameter(name = "employeeId", description = "员工编号", required = true, example = "124")
    @PreAuthorize("@ss.hasPermission('hrm:safety:query')")
    public CommonResult<List<HrmHealthCheckRespVO>> getHealthCheckList(@RequestParam("employeeId") Long employeeId) {
        return success(buildRespVOList(healthCheckService.getHealthCheckListByEmployeeId(employeeId)));
    }

    private List<HrmHealthCheckRespVO> buildRespVOList(List<HrmHealthCheckDO> list) {
        if (CollUtil.isEmpty(list)) {
            return Collections.emptyList();
        }
        Map<Long, HrmEmployeeDO> employeeMap = employeeService.getEmployeeMap(
                list.stream().map(HrmHealthCheckDO::getEmployeeId).collect(Collectors.toSet()));
        LocalDate today = LocalDate.now();
        return BeanUtils.toBean(list, HrmHealthCheckRespVO.class, vo -> {
            HrmEmployeeDO employee = employeeMap.get(vo.getEmployeeId());
            if (employee != null) {
                vo.setEmployeeName(employee.getName());
                vo.setJobNumber(employee.getJobNumber());
            }
            vo.setCheckTypeName(vo.getCheckType() == null ? null : CHECK_TYPE_NAMES.get(vo.getCheckType()));
            HrmHealthCheckResultEnum result = HrmHealthCheckResultEnum.valueOf(vo.getResult());
            vo.setResultName(result != null ? result.getName() : null);
            vo.setValid(vo.getValidUntil() == null || !vo.getValidUntil().isBefore(today));
        });
    }

}
