package cn.iocoder.txgy.module.wms.controller.admin.order.shipment;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.wms.service.integration.WmsShipmentExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/wms/shipment-order")
@RequiredArgsConstructor
public class WmsShipmentIntegrationController {
    private final WmsShipmentExecutionService execution;
    @GetMapping("/execution")
    @PreAuthorize("@ss.hasPermission('wms:shipment-order:query')")
    public CommonResult<Map<String,Object>> getExecution(@RequestParam Long id) {
        return success(execution.getExecution(id));
    }
    @PostMapping("/retry-notification")
    @PreAuthorize("@ss.hasPermission('wms:shipment-order:submit-approval')")
    public CommonResult<Boolean> retryNotification(@RequestParam Long id) {
        return success(execution.retryNotification(id));
    }
}
