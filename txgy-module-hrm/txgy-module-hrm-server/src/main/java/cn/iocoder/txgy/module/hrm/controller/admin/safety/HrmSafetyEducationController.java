package cn.iocoder.txgy.module.hrm.controller.admin.safety;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.safety.vo.HrmSafetyEducationPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.safety.vo.HrmSafetyEducationRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.safety.vo.HrmSafetyEducationSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.safety.HrmSafetyEducationDO;
import cn.iocoder.txgy.module.hrm.enums.safety.HrmSafetyEducationLevelEnum;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import cn.iocoder.txgy.module.hrm.service.safety.HrmSafetyEducationService;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * HRM 三级安全教育 Controller
 *
 * @author OPENLAB BS
 */
@Tag(name = "管理后台 - HRM 三级安全教育")
@RestController
@RequestMapping("/hrm/safety-education")
@Validated
public class HrmSafetyEducationController {

    @Resource
    private HrmSafetyEducationService educationService;
    @Resource
    private HrmEmployeeService employeeService;
    @PostMapping("/create")
    @Operation(summary = "新增安全教育记录")
    @PreAuthorize("@ss.hasPermission('hrm:safety:education:update')")
    public CommonResult<Long> createEducation(@Valid @RequestBody HrmSafetyEducationSaveReqVO reqVO) {
        return success(educationService.createEducation(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改安全教育记录")
    @PreAuthorize("@ss.hasPermission('hrm:safety:education:update')")
    public CommonResult<Boolean> updateEducation(@Valid @RequestBody HrmSafetyEducationSaveReqVO reqVO) {
        educationService.updateEducation(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除安全教育记录")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('hrm:safety:education:update')")
    public CommonResult<Boolean> deleteEducation(@RequestParam("id") Long id) {
        educationService.deleteEducation(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得安全教育记录分页")
    @PreAuthorize("@ss.hasPermission('hrm:safety:query')")
    public CommonResult<PageResult<HrmSafetyEducationRespVO>> getEducationPage(
            @Valid HrmSafetyEducationPageReqVO pageReqVO) {
        PageResult<HrmSafetyEducationDO> page = educationService.getEducationPage(pageReqVO);
        return success(new PageResult<>(buildRespVOList(page.getList()), page.getTotal()));
    }

    @GetMapping("/list")
    @Operation(summary = "获得员工安全教育记录列表")
    @Parameter(name = "employeeId", description = "员工编号", required = true, example = "124")
    @PreAuthorize("@ss.hasPermission('hrm:safety:query')")
    public CommonResult<List<HrmSafetyEducationRespVO>> getEducationList(@RequestParam("employeeId") Long employeeId) {
        return success(buildRespVOList(educationService.getEducationListByEmployeeId(employeeId)));
    }

    private List<HrmSafetyEducationRespVO> buildRespVOList(List<HrmSafetyEducationDO> list) {
        if (CollUtil.isEmpty(list)) {
            return Collections.emptyList();
        }
        Map<Long, HrmEmployeeDO> employeeMap = employeeService.getEmployeeMap(
                list.stream().map(HrmSafetyEducationDO::getEmployeeId).collect(Collectors.toSet()));
        return BeanUtils.toBean(list, HrmSafetyEducationRespVO.class, vo -> {
            HrmEmployeeDO employee = employeeMap.get(vo.getEmployeeId());
            if (employee != null) {
                vo.setEmployeeName(employee.getName());
                vo.setJobNumber(employee.getJobNumber());
            }
            HrmSafetyEducationLevelEnum level = HrmSafetyEducationLevelEnum.valueOf(vo.getLevel());
            vo.setLevelName(level != null ? level.getName() : null);
        });
    }

}
