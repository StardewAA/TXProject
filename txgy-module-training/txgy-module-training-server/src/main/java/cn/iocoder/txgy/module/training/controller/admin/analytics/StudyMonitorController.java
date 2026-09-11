package cn.iocoder.txgy.module.training.controller.admin.analytics;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.training.service.analytics.StudyMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 91 学时管控与学习监管")
@RestController
@RequestMapping("/training/analytics/study-monitor")
@Validated
public class StudyMonitorController {

    @Resource
    private StudyMonitorService studyMonitorService;

    @GetMapping("/page")
    @Operation(summary = "分页查询全员学习行为数据（含未完成人员清单）")
    @PreAuthorize("@ss.hasPermission('training:analytics:study-monitor:query')")
    public CommonResult<PageResult<?>> getStudyBehaviorPage() {
        // TODO[模块九-学习行为] 接收分页/筛选参数并透传 Service
        return success(studyMonitorService.getStudyBehaviorPage(null));
    }

    @PostMapping("/remind")
    @Operation(summary = "批量推送站内督促消息")
    @PreAuthorize("@ss.hasPermission('training:analytics:study-monitor:remind')")
    public CommonResult<Boolean> remind(@RequestParam("userIds") List<Long> userIds) {
        return success(studyMonitorService.remind(userIds));
    }

}
