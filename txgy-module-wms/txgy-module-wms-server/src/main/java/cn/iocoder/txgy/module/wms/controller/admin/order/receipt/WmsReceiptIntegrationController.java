package cn.iocoder.txgy.module.wms.controller.admin.order.receipt;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.wms.controller.admin.order.receipt.vo.order.WmsReceiptSimulationReqVO;
import cn.iocoder.txgy.module.wms.service.integration.WmsReceiptExecutionService;
import cn.iocoder.txgy.module.wms.service.integration.WmsReceiptSimulationService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.INTEGRATION_FEATURE_NOT_ENABLED;

@RestController
@RequestMapping("/wms/receipt-order")
public class WmsReceiptIntegrationController {
    @Resource private WmsReceiptSimulationService simulationService;
    @Resource private WmsReceiptExecutionService executionService;
    @Value("${txgy.wms.simulation-enabled:false}") private boolean simulationEnabled;

    @PostMapping("/simulate-erp")
    @PreAuthorize("@ss.hasPermission('wms:receipt-order:create')")
    public CommonResult<Map<String,Object>> simulate(@Valid @RequestBody WmsReceiptSimulationReqVO request) {
        if(!simulationEnabled) throw exception(INTEGRATION_FEATURE_NOT_ENABLED);
        request.setOrderType("PURCHASE_RECEIPT");
        return success(simulationService.simulate(request));
    }
    @PostMapping("/simulate-source")
    @PreAuthorize("#request.orderType.endsWith('RECEIPT') ? @ss.hasPermission('wms:receipt-order:create') : @ss.hasPermission('wms:shipment-order:create')")
    public CommonResult<Map<String,Object>> simulateSource(@Valid @RequestBody WmsReceiptSimulationReqVO request) {
        if (!simulationEnabled) throw exception(INTEGRATION_FEATURE_NOT_ENABLED);
        return success(simulationService.simulate(request));
    }
    @GetMapping("/execution")
    @PreAuthorize("@ss.hasPermission('wms:receipt-order:query')")
    public CommonResult<Map<String,Object>> execution(@RequestParam Long id) {
        return success(executionService.getExecution(id));
    }
    @PostMapping("/retry-notification")
    @PreAuthorize("@ss.hasPermission('wms:receipt-order:submit-approval')")
    public CommonResult<Boolean> retry(@RequestParam Long id) {
        return success(executionService.retryNotification(id));
    }
}
