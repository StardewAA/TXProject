package cn.iocoder.txgy.module.hrm.controller.admin.portal.employee;

import cn.iocoder.txgy.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.workexperience.HrmEmployeeWorkExperienceRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.employee.vo.HrmPortalEmployeeWorkExperienceSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.experience.HrmEmployeeWorkExperienceDO;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import cn.iocoder.txgy.module.hrm.service.employee.experience.HrmEmployeeWorkExperienceService;
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
@Tag(name = "管理后台 - HRM 员工端工作经历")
@RestController
@RequestMapping("/hrm/portal/employee/work-experience")
@Validated
public class HrmPortalEmployeeWorkExperienceController {

    @Resource
    private HrmEmployeeWorkExperienceService workExperienceService;
    @Resource
    private HrmEmployeeService employeeService;
    @Resource
    private HrmPortalEmployeeSelfArchiveService selfArchiveService;

    @GetMapping("/list")
    @Operation(summary = "获得我的工作经历列表")
    @PreAuthorize("@ss.hasPermission('hrm:portal:query')")
    public CommonResult<List<HrmEmployeeWorkExperienceRespVO>> getWorkExperienceList() {
        Long employeeId = employeeService.validateEmployeeBySelf(getLoginUserId()).getId();
        List<HrmEmployeeWorkExperienceDO> workExperiences =
                workExperienceService.getWorkExperienceListByEmployeeId(employeeId);
        return success(BeanUtils.toBean(
                workExperiences, HrmEmployeeWorkExperienceRespVO.class));
    }

    @PostMapping("/create")
    @Operation(summary = "新增我的工作经历（员工自助完善档案，保存后 HR 后台立即可见）")
    @PreAuthorize("@ss.hasPermission('hrm:portal:employee:update')")
    public CommonResult<Long> createWorkExperience(
            @Valid @RequestBody HrmPortalEmployeeWorkExperienceSaveReqVO reqVO) {
        return success(selfArchiveService.createWork(getLoginUserId(), reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改我的工作经历（仅限本人记录）")
    @PreAuthorize("@ss.hasPermission('hrm:portal:employee:update')")
    public CommonResult<Boolean> updateWorkExperience(
            @Valid @RequestBody HrmPortalEmployeeWorkExperienceSaveReqVO reqVO) {
        selfArchiveService.updateWork(getLoginUserId(), reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除我的工作经历（仅限本人记录）")
    @Parameter(name = "id", description = "工作经历编号", required = true)
    @PreAuthorize("@ss.hasPermission('hrm:portal:employee:update')")
    public CommonResult<Boolean> deleteWorkExperience(@RequestParam("id") Long id) {
        selfArchiveService.deleteWork(getLoginUserId(), id);
        return success(true);
    }

}
