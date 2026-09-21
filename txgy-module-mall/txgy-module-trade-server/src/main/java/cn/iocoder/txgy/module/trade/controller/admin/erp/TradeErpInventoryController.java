package cn.iocoder.txgy.module.trade.controller.admin.erp;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.trade.service.erp.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.module.trade.service.erp.ErpInventoryModels.*;

@RestController
@RequestMapping("/trade/erp-inventory")
public class TradeErpInventoryController {
    private final TradeErpInventoryService inventory;
    private final TradeErpResultService results;
    public TradeErpInventoryController(TradeErpInventoryService inventory, TradeErpResultService results) {
        this.inventory=inventory; this.results=results;
    }
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('trade:order:query')")
    public CommonResult<List<Entry>> list(@RequestParam Long orderId) { return success(inventory.list(orderId)); }

    @PostMapping("/retry")
    @PreAuthorize("@ss.hasPermission('trade:order:update')")
    public CommonResult<Entry> retry(@RequestParam String requestId) { return success(inventory.retry(requestId)); }

    @PostMapping("/mock/seed")
    @PreAuthorize("@ss.hasPermission('trade:order:update')")
    public CommonResult<Boolean> seed(@Valid @RequestBody Seed seed) { inventory.seed(seed); return success(true); }

    /** 临时对接契约：后台服务账号权限+独立密钥双重校验，默认关闭，不能匿名回传。 */
    @PostMapping("/callback")
    @PreAuthorize("@ss.hasPermission('trade:erp-inventory:callback')")
    public CommonResult<Entry> callback(@RequestHeader("X-ERP-Callback-Secret") String secret,
                                       @Valid @RequestBody Callback callback) {
        inventory.authenticateCallback(secret);
        try {
            return success(results.receive(secret, callback));
        } catch (RuntimeException failure) {
            try {
                inventory.recordCallbackFailure(callback.requestId(),
                        "ERP结果未能应用到商城，订单状态未推进，请核实后沿用原请求号重试");
            } catch (RuntimeException ignored) {
                // 无记录或数据库不可用时保留原异常，由统一异常处理记录。
            }
            throw failure;
        }
    }
}
