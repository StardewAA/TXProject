package cn.iocoder.txgy.module.erp.controller.admin.integration;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.erp.service.integration.ErpWmsIntegrationGateway;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationRequestRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - ERP/WMS 集成补偿查询")
@RestController
@RequestMapping("/erp/wms-integration")
public class ErpWmsIntegrationController {

    @Resource
    private ErpWmsIntegrationGateway integrationGateway;

    @GetMapping("/request-result")
    @Operation(summary = "按幂等请求号查询 WMS 处理结果，用于超时与回调丢失补偿")
    @Parameter(name = "requestId", required = true, description = "ERP 发往 WMS 的幂等请求号")
    @PreAuthorize("@ss.hasAnyPermissions('erp:sale-order:query', 'erp:purchase-order:query')")
    public CommonResult<WmsIntegrationRequestRespDTO> getRequestResult(@RequestParam("requestId") String requestId) {
        return success(integrationGateway.getRequestResult(requestId));
    }

    @GetMapping("/order-result")
    @Operation(summary = "按 ERP 来源单号查询 WMS 入库或出库结果")
    @PreAuthorize("@ss.hasAnyPermissions('erp:sale-order:query', 'erp:purchase-order:query')")
    public CommonResult<WmsIntegrationRequestRespDTO> getOrderResult(
            @RequestParam("sourceOrderNo") String sourceOrderNo,
            @RequestParam("orderType") String orderType) {
        return success(integrationGateway.getOrderExecution(TenantContextHolder.getRequiredTenantId(),
                sourceOrderNo, orderType));
    }
}
