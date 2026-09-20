package cn.iocoder.txgy.module.infra.controller.admin.worklog;

import cn.iocoder.txgy.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.excel.core.util.ExcelUtils;
import cn.iocoder.txgy.module.infra.controller.admin.worklog.vo.*;
import cn.iocoder.txgy.module.infra.dal.dataobject.worklog.WorkLogDO;
import cn.iocoder.txgy.module.infra.service.worklog.WorkLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.*;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.txgy.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;

@RestController
@RequestMapping("/infra/work-log")
@Tag(name = "管理后台 - 工作留痕")
@Validated
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class WorkLogController {
    private final WorkLogService service;

    @GetMapping("/scope")
    @Operation(summary = "获取留痕可见范围")
    public CommonResult<Map<String, Boolean>> scope() { return success(Map.of("canViewAll", service.canViewAll())); }

    @GetMapping("/page")
    @Operation(summary = "查询工作留痕")
    public CommonResult<PageResult<WorkLogRespVO>> page(@Valid WorkLogPageReqVO req) {
        PageResult<WorkLogDO> result = service.page(req);
        return success(BeanUtils.toBean(result, WorkLogRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "查看留痕详情")
    public CommonResult<WorkLogRespVO> get(@RequestParam Long id, @RequestParam(defaultValue = "SELF") String scope) {
        return success(BeanUtils.toBean(service.get(id, scope), WorkLogRespVO.class));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出工作留痕")
    @ApiAccessLog(operateType = EXPORT)
    public void export(@Valid WorkLogPageReqVO req, HttpServletResponse response) throws IOException {
        // Bound the export, never silently truncate it. Each page re-checks scope and tenant.
        service.freezeExport(req);
        req.setPageNo(1);
        req.setPageSize(200);
        PageResult<WorkLogDO> first = service.page(req);
        if (first.getTotal() > 10000) throw cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.invalidParamException("单次最多导出 10000 条，请缩小时间范围");
        List<WorkLogDO> rows = new ArrayList<>(first.getList());
        for (int page = 2; rows.size() < first.getTotal(); page++) {
            req.setPageNo(page);
            List<WorkLogDO> next = service.page(req).getList();
            if (next.isEmpty()) break;
            rows.addAll(next);
        }
        ExcelUtils.write(response, "工作留痕.xlsx", "工作留痕", WorkLogRespVO.class,
                cn.iocoder.txgy.module.infra.service.worklog.WorkLogExport.rows(rows));
    }
}
