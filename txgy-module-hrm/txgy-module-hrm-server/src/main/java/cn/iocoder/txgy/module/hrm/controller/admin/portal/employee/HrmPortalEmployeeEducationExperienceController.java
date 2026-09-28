package cn.iocoder.txgy.module.hrm.controller.admin.portal.employee;

import cn.iocoder.txgy.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.educationexperience.HrmEmployeeEducationExperienceRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.employee.vo.HrmPortalEmployeeEducationExperienceSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.experience.HrmEmployeeEducationExperienceDO;
import cn.iocoder.txgy.module.hrm.service.employee.experience.HrmEmployeeEducationExperienceService;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import cn.iocoder.txgy.module.hrm.service.employee.portal.HrmPortalEmployeeSelfArchiveService;
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

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@DataPermission(enable = false)
@Tag(name = "管理后台 - HRM 员工端教育经历")
@RestController
@RequestMapping("/hrm/portal/employee/education-experience")
@Validated
public class HrmPortalEmployeeEducationExperienceController {

    @Resource
    private HrmEmployeeEducationExperienceService educationExperienceService;
    @Resource
    private HrmEmployeeService employeeService;
    @Resource
    private HrmPortalEmployeeSelfArchiveService selfArchiveService;

    @GetMapping("/list")
    @Operation(summary = "获得我的教育经历列表")
    @PreAuthorize("@ss.hasPermission('hrm:portal:query')")
    public CommonResult<List<HrmEmployeeEducationExperienceRespVO>> getEducationExperienceList() {
        Long employeeId = employeeService.validateEmployeeBySelf(getLoginUserId()).getId();
        List<HrmEmployeeEducationExperienceDO> educationExperiences =
                educationExperienceService.getEducationExperienceListByEmployeeId(employeeId);
        return success(BeanUtils.toBean(
                educationExperiences, HrmEmployeeEducationExperienceRespVO.class));
    }

    @PostMapping("/create")
    @Operation(summary = "新增我的教育经历（员工自助完善档案，保存后 HR 后台立即可见）")
    @PreAuthorize("@ss.hasPermission('hrm:portal:employee:update')")
    public CommonResult<Long> createEducationExperience(
            @Valid @RequestBody HrmPortalEmployeeEducationExperienceSaveReqVO reqVO) {
        return success(selfArchiveService.createEducation(getLoginUserId(), reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改我的教育经历（仅限本人记录）")
    @PreAuthorize("@ss.hasPermission('hrm:portal:employee:update')")
    public CommonResult<Boolean> updateEducationExperience(
            @Valid @RequestBody HrmPortalEmployeeEducationExperienceSaveReqVO reqVO) {
        selfArchiveService.updateEducation(getLoginUserId(), reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除我的教育经历（仅限本人记录）")
    @Parameter(name = "id", description = "教育经历编号", required = true)
    @PreAuthorize("@ss.hasPermission('hrm:portal:employee:update')")
    public CommonResult<Boolean> deleteEducationExperience(@RequestParam("id") Long id) {
        selfArchiveService.deleteEducation(getLoginUserId(), id);
        return success(true);
    }

}
