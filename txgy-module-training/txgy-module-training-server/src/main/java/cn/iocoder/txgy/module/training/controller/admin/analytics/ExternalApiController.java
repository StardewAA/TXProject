package cn.iocoder.txgy.module.training.controller.admin.analytics;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.training.service.analytics.ExternalApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 96 外部系统对接")
@RestController
@RequestMapping("/training/analytics/external-api")
@Validated
public class ExternalApiController {

    @Resource
    private ExternalApiService externalApiService;

    @GetMapping("/config/page")
    @Operation(summary = "外部API配置分页（鉴权/限流/降级/黑名单）")
    @PreAuthorize("@ss.hasPermission('training:analytics:external-config:update')")
    public CommonResult<PageResult<?>> getConfigPage() {
        // TODO[模块九-外部对接] 基于外部API配置表实现分页与维护
        return success(PageResult.empty());
    }

    @PostMapping("/task/dispatch")
    @Operation(summary = "外部培训任务下发（岗前/复工）")
    @PreAuthorize("@ss.hasPermission('training:analytics:external-api:dispatch')")
    public CommonResult<Map<String, Object>> dispatchTrainingTask(@RequestBody Map<String, Object> request) {
        // TODO[模块九-任务下发] 参数有效性校验 + 超时重试 + 鉴权限流
        return success(externalApiService.dispatchTrainingTask(request));
    }

    @GetMapping("/archive/batch")
    @Operation(summary = "批量获取脱敏人员培训档案（单次最多 200 条）")
    @PreAuthorize("@ss.hasPermission('training:analytics:external-api:archive')")
    public CommonResult<Map<String, Object>> getArchivesBatch() {
        // TODO[模块九-档案获取] 单次条数限制 + 敏感字段脱敏输出
        return success(externalApiService.getArchivesBatch(null));
    }

}
