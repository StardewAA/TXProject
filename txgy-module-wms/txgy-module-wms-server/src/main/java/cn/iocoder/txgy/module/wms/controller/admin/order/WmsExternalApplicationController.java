package cn.iocoder.txgy.module.wms.controller.admin.order;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.wms.api.integration.WmsIntegrationApiImpl;
import cn.iocoder.txgy.module.wms.api.integration.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Application creation is separate from read-only master/stock endpoints. */
@RestController
@RequestMapping("/wms/external-application")
@RequiredArgsConstructor
public class WmsExternalApplicationController {
    private final WmsIntegrationApiImpl api;
    @PostMapping("/erp/receipt")
    @PreAuthorize("@ss.hasPermission('wms:receipt-order:create')")
    public CommonResult<WmsIntegrationAcceptedRespDTO> erpReceipt(@Valid @RequestBody WmsOrderRequestDTO q) {
        return api.createPurchaseReceipt(q);
    }
    @PostMapping("/erp/shipment")
    @PreAuthorize("@ss.hasPermission('wms:shipment-order:create')")
    public CommonResult<WmsIntegrationAcceptedRespDTO> erpShipment(@Valid @RequestBody WmsOrderRequestDTO q) {
        return api.createSaleShipment(q);
    }
    @PostMapping("/mall/shipment")
    @PreAuthorize("@ss.hasPermission('wms:shipment-order:create')")
    public CommonResult<WmsIntegrationAcceptedRespDTO> mallShipment(@Valid @RequestBody WmsOrderRequestDTO q) {
        return api.createMallShipment(q);
    }
    @PostMapping("/mes/receipt")
    @PreAuthorize("@ss.hasPermission('wms:receipt-order:create')")
    public CommonResult<WmsIntegrationAcceptedRespDTO> mesReceipt(@Valid @RequestBody WmsOrderRequestDTO q) {
        return api.createProductionReceipt(q);
    }
    @PostMapping("/mes/shipment")
    @PreAuthorize("@ss.hasPermission('wms:shipment-order:create')")
    public CommonResult<WmsIntegrationAcceptedRespDTO> mesShipment(@Valid @RequestBody WmsOrderRequestDTO q) {
        return api.createProductionShipment(q);
    }
    @GetMapping("/execution")
    @PreAuthorize("#orderType.endsWith('RECEIPT') ? @ss.hasPermission('wms:receipt-order:query') : @ss.hasPermission('wms:shipment-order:query')")
    public CommonResult<WmsIntegrationRequestRespDTO> execution(@RequestParam String sourceOrderNo,
                                                              @RequestParam String orderType) {
        return api.getOrderExecution(TenantContextHolder.getRequiredTenantId(), sourceOrderNo, orderType);
    }
    @GetMapping("/mall/execution")
    @PreAuthorize("@ss.hasPermission('wms:shipment-order:query')")
    public CommonResult<WmsIntegrationRequestRespDTO> mallExecution(@RequestParam String sourceOrderNo) {
        return api.getMallOrderExecution(TenantContextHolder.getRequiredTenantId(), sourceOrderNo);
    }
}
