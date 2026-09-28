package cn.iocoder.txgy.module.training.controller.admin.analytics;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.training.service.analytics.PersonalArchiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 92 个人学习档案")
@RestController
@RequestMapping("/training/analytics/personal-archive")
@Validated
public class PersonalArchiveController {

    @Resource
    private PersonalArchiveService personalArchiveService;

    @GetMapping("/overview")
    @Operation(summary = "个人档案总览（学时明细/考试记录/证书/VR记录/成长进度）")
    @PreAuthorize("@ss.hasPermission('training:analytics:personal-archive:query')")
    public CommonResult<Map<String, Object>> getOverview() {
        // TODO[模块九-个人档案] 框架期默认查当前登录学员；后续支持管理员代查
        return success(personalArchiveService.getOverview(getLoginUserId()));
    }

    @PostMapping("/cert-expire-remind")
    @Operation(summary = "证书到期提醒")
    @PreAuthorize("@ss.hasPermission('training:analytics:personal-archive:query')")
    public CommonResult<Boolean> remindCertExpire() {
        return success(personalArchiveService.remindCertExpire(getLoginUserId()));
    }

}
