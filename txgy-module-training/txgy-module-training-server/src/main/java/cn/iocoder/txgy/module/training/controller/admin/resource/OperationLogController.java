package cn.iocoder.txgy.module.training.controller.admin.resource;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.OperationLogPageReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.OperationLogDO;
import cn.iocoder.txgy.module.training.service.resource.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 资源中心操作日志")
@RestController
@RequestMapping("/training/resource-log")
@Validated
public class OperationLogController {

    @Resource
    private OperationLogService operationLogService;

    @GetMapping("/page")
    @Operation(summary = "操作日志分页")
    @PreAuthorize("@ss.hasPermission('training:resource-log:query')")
    public CommonResult<PageResult<OperationLogDO>> getLogPage(@Validated OperationLogPageReqVO pageReqVO) {
        return success(operationLogService.getLogPage(pageReqVO));
    }

}
