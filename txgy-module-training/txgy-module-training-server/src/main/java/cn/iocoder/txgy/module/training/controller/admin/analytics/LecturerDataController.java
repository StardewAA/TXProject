package cn.iocoder.txgy.module.training.controller.admin.analytics;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.training.service.analytics.LecturerDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 93 讲师数据权限")
@RestController
@RequestMapping("/training/analytics/lecturer")
@Validated
public class LecturerDataController {

    @Resource
    private LecturerDataService lecturerDataService;

    @GetMapping("/course-students")
    @Operation(summary = "讲师查看自己课程下学员学习完成情况")
    @PreAuthorize("@ss.hasPermission('training:analytics:lecturer:query')")
    public CommonResult<PageResult<?>> getLecturerStudentPage() {
        // TODO[模块九-讲师权限] 数据权限边界：仅限讲师自己创建的课程
        return success(lecturerDataService.getLecturerStudentPage(getLoginUserId(), null));
    }

}
