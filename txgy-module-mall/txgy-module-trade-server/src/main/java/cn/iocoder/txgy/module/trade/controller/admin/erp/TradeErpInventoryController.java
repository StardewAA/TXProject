package cn.iocoder.txgy.module.trade.controller.admin.erp;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.trade.service.erp.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.module.trade.service.erp.ErpInventoryModels.*;

@RestController
@RequestMapping("/trade/erp-inventory")
public class TradeErpInventoryController {
    private final TradeErpInventoryService inventory;
    public TradeErpInventoryController(TradeErpInventoryService inventory) {
        this.inventory=inventory;
    }
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('trade:order:query')")
    public CommonResult<List<Entry>> list(@RequestParam Long orderId) { return success(inventory.list(orderId)); }
}
