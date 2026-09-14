package cn.iocoder.txgy.module.wms.controller.admin.order.shipment;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.wms.service.integration.WmsShortageService;
import cn.iocoder.txgy.module.wms.service.integration.WmsShortageSender;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/wms/shipment-shortage")
public class WmsShortageController {
    @Resource private WmsShortageService shortages;
    @Resource private WmsShortageSender sender;
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('wms:shipment-order:query')")
    public CommonResult<List<Map<String,Object>>> list(@RequestParam Long orderId) { return success(shortages.list(orderId)); }
    @PostMapping("/recheck")
    @PreAuthorize("@ss.hasPermission('wms:shipment-order:submit-approval')")
    public CommonResult<List<Map<String,Object>>> recheck(@RequestParam Long orderId) { return success(shortages.recheck(orderId)); }
    @PostMapping("/retry")
    @PreAuthorize("@ss.hasPermission('wms:shipment-order:submit-approval')")
    public CommonResult<Boolean> retry(@RequestParam String eventId) { return success(sender.send(eventId,true)); }
}
