package cn.iocoder.txgy.module.cms.controller.admin.knowledge;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.cms.service.validity.CmsLawValidityCheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - CMS 法律效力核验")
@RestController
@RequestMapping("/cms/knowledge/validity-task")
@Validated
public class CmsLawValidityTaskController {

    private final CmsLawValidityCheckService service;

    public CmsLawValidityTaskController(CmsLawValidityCheckService service) {
        this.service = service;
    }

    @GetMapping("/list")
    @Operation(summary = "查询法律效力核验待办")
    @PreAuthorize("@ss.hasPermission('cms:knowledge:query')")
    public CommonResult<List<CmsLawValidityCheckService.ValidityTask>> list(
            @RequestParam(defaultValue = "PENDING") String status) {
        return success(service.list(status));
    }

    @PostMapping("/check")
    @Operation(summary = "立即核验已发布法律的远端效力与来源可用性")
    @PreAuthorize("@ss.hasPermission('cms:knowledge:reindex')")
    public CommonResult<CmsLawValidityCheckService.CheckSummary> check() {
        return success(service.checkCurrentTenant());
    }

    @PostMapping("/archive")
    @Operation(summary = "人工确认风险并归档法律")
    @PreAuthorize("@ss.hasPermission('cms:knowledge:delete')")
    public CommonResult<Boolean> archive(@RequestParam long taskId,
                                         @Valid @RequestBody(required = false) ResolveRequest request) {
        service.archive(taskId, request == null ? null : request.note());
        return success(true);
    }

    @PutMapping("/dismiss")
    @Operation(summary = "人工核验后忽略本次风险")
    @PreAuthorize("@ss.hasPermission('cms:knowledge:publish')")
    public CommonResult<Boolean> dismiss(@RequestParam long taskId,
                                         @Valid @RequestBody(required = false) ResolveRequest request) {
        service.dismiss(taskId, request == null ? null : request.note());
        return success(true);
    }

    public record ResolveRequest(@Size(max = 500) String note) { }
}
