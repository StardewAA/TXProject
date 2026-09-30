package cn.iocoder.txgy.module.wms.controller.admin.order.shipment;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.wms.service.integration.WmsCollaborationOrderGService;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/wms/collaboration-order-g")
public class WmsCollaborationOrderGController {

    @Resource
    private WmsCollaborationOrderGService collaborationOrderGService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('wms:shipment-order:query')")
    public CommonResult<Map<String, Object>> page(@RequestParam(defaultValue = "1") int pageNo,
                                                  @RequestParam(defaultValue = "20") int pageSize) {
        return success(collaborationOrderGService.page(TenantContextHolder.getRequiredTenantId(), pageNo, pageSize));
    }

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('wms:shipment-order:query')")
    public CommonResult<List<Map<String, Object>>> list(@RequestParam Long orderId) {
        return success(collaborationOrderGService.listByOrder(TenantContextHolder.getRequiredTenantId(), orderId));
    }

    @PostMapping("/approve")
    @PreAuthorize("@ss.hasPermission('wms:shipment-order:submit-approval')")
    public CommonResult<Boolean> approve(@RequestParam String gNo) {
        collaborationOrderGService.approve(TenantContextHolder.getRequiredTenantId(), gNo,
                SecurityFrameworkUtils.getLoginUser().getId());
        return success(true);
    }

    @PostMapping("/reject")
    @PreAuthorize("@ss.hasPermission('wms:shipment-order:submit-approval')")
    public CommonResult<Boolean> reject(@RequestParam String gNo, @RequestParam String reason) {
        collaborationOrderGService.reject(TenantContextHolder.getRequiredTenantId(), gNo,
                SecurityFrameworkUtils.getLoginUser().getId(), reason);
        return success(true);
    }
}
