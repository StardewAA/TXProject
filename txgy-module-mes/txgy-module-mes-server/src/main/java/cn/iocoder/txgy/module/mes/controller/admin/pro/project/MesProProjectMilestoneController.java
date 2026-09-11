package cn.iocoder.txgy.module.mes.controller.admin.pro.project;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo.MesProProjectMilestoneRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo.MesProProjectMilestoneSaveReqVO;
import cn.iocoder.txgy.module.mes.service.pro.project.MesProProjectMilestoneService;
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

@Tag(name = "管理后台 - MES 生产管理-项目里程碑")
@RestController
@RequestMapping("/mes/pro-project-milestone")
@Validated
public class MesProProjectMilestoneController {

    @Resource
    private MesProProjectMilestoneService milestoneService;

    @PostMapping("/create")
    @Operation(summary = "创建项目里程碑")
    @PreAuthorize("@ss.hasPermission('mes:pro-project-milestone:create')")
    public CommonResult<Long> createMilestone(@Valid @RequestBody MesProProjectMilestoneSaveReqVO createReqVO) {
        return success(milestoneService.createMilestone(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新项目里程碑")
    @PreAuthorize("@ss.hasPermission('mes:pro-project-milestone:update')")
    public CommonResult<Boolean> updateMilestone(@Valid @RequestBody MesProProjectMilestoneSaveReqVO updateReqVO) {
        milestoneService.updateMilestone(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除项目里程碑")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('mes:pro-project-milestone:delete')")
    public CommonResult<Boolean> deleteMilestone(@RequestParam("id") Long id) {
        milestoneService.deleteMilestone(id);
        return success(true);
    }

    @GetMapping("/list")
    @Operation(summary = "获得项目的里程碑列表")
    @Parameter(name = "projectId", description = "项目编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('mes:pro-project-milestone:query')")
    public CommonResult<List<MesProProjectMilestoneRespVO>> getMilestoneList(
            @RequestParam("projectId") Long projectId) {
        return success(BeanUtils.toBean(milestoneService.getMilestoneList(projectId),
                MesProProjectMilestoneRespVO.class));
    }

}
