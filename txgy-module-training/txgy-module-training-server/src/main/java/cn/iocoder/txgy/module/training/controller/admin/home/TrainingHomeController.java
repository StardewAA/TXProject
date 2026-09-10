package cn.iocoder.txgy.module.training.controller.admin.home;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.training.controller.admin.home.vo.TrainingHomeOverviewRespVO;
import cn.iocoder.txgy.module.training.service.home.TrainingHomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 云培训学院工作台")
@RestController
@RequestMapping("/training/home")
public class TrainingHomeController {

    @Resource
    private TrainingHomeService trainingHomeService;

    @GetMapping("/overview")
    @Operation(summary = "获得云培训学院工作台概览")
    @PreAuthorize("@ss.hasPermission('training:workbench:query')")
    public CommonResult<TrainingHomeOverviewRespVO> getOverview() {
        return success(trainingHomeService.getOverview());
    }
}
