package cn.iocoder.txgy.module.mes.controller.admin.pro.project;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo.MesProProjectFileRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo.MesProProjectFileSaveReqVO;
import cn.iocoder.txgy.module.mes.service.pro.project.MesProProjectFileService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - MES 生产管理-项目附件")
@RestController
@RequestMapping("/mes/pro-project-file")
@Validated
public class MesProProjectFileController {

    @Resource
    private MesProProjectFileService projectFileService;

    @PostMapping("/create")
    @Operation(summary = "新增项目附件（文件需先上传到 infra 文件服务）")
    @PreAuthorize("@ss.hasPermission('mes:pro-project-file:create')")
    public CommonResult<Long> createProjectFile(@Valid @RequestBody MesProProjectFileSaveReqVO createReqVO) {
        return success(projectFileService.createProjectFile(createReqVO));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除项目附件关联")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('mes:pro-project-file:delete')")
    public CommonResult<Boolean> deleteProjectFile(@RequestParam("id") Long id) {
        projectFileService.deleteProjectFile(id);
        return success(true);
    }

    @GetMapping("/list")
    @Operation(summary = "获得项目的附件列表")
    @Parameter(name = "projectId", description = "项目编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('mes:pro-project-file:query')")
    public CommonResult<List<MesProProjectFileRespVO>> getProjectFileList(
            @RequestParam("projectId") Long projectId) {
        return success(BeanUtils.toBean(projectFileService.getProjectFileList(projectId),
                MesProProjectFileRespVO.class));
    }

}
