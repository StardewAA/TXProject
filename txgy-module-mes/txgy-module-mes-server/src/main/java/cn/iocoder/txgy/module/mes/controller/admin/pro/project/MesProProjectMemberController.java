package cn.iocoder.txgy.module.mes.controller.admin.pro.project;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo.MesProProjectMemberRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo.MesProProjectMemberSaveReqVO;
import cn.iocoder.txgy.module.mes.service.pro.project.MesProProjectMemberService;
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

@Tag(name = "管理后台 - MES 生产管理-项目成员")
@RestController
@RequestMapping("/mes/pro-project-member")
@Validated
public class MesProProjectMemberController {

    @Resource
    private MesProProjectMemberService projectMemberService;

    @PostMapping("/create")
    @Operation(summary = "创建项目成员")
    @PreAuthorize("@ss.hasPermission('mes:pro-project-member:create')")
    public CommonResult<Long> createProjectMember(@Valid @RequestBody MesProProjectMemberSaveReqVO createReqVO) {
        return success(projectMemberService.createProjectMember(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新项目成员")
    @PreAuthorize("@ss.hasPermission('mes:pro-project-member:update')")
    public CommonResult<Boolean> updateProjectMember(@Valid @RequestBody MesProProjectMemberSaveReqVO updateReqVO) {
        projectMemberService.updateProjectMember(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除项目成员")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('mes:pro-project-member:delete')")
    public CommonResult<Boolean> deleteProjectMember(@RequestParam("id") Long id) {
        projectMemberService.deleteProjectMember(id);
        return success(true);
    }

    @GetMapping("/list")
    @Operation(summary = "获得项目的成员列表")
    @Parameter(name = "projectId", description = "项目编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('mes:pro-project-member:query')")
    public CommonResult<List<MesProProjectMemberRespVO>> getProjectMemberList(
            @RequestParam("projectId") Long projectId) {
        return success(BeanUtils.toBean(projectMemberService.getProjectMemberList(projectId),
                MesProProjectMemberRespVO.class));
    }

}
