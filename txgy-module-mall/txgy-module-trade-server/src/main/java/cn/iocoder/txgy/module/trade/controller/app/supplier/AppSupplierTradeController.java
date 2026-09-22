package cn.iocoder.txgy.module.trade.controller.app.supplier;

import cn.iocoder.txgy.framework.common.pojo.*;
import cn.iocoder.txgy.module.trade.service.supplier.SupplierTradeService;
import cn.iocoder.txgy.module.trade.framework.compatibility.LegacyMemberSupplierWriteGuard;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.module.trade.service.supplier.SupplierTradeService.memberId;

@RestController
@RequestMapping("/trade/supplier")
public class AppSupplierTradeController {
    @Resource private SupplierTradeService service;
    @Resource private LegacyMemberSupplierWriteGuard legacyWriteGuard;
    public record Delivery(@NotNull Long shopId, @NotNull Long id, @NotNull @Positive Long logisticsId,
                           @NotBlank @Pattern(regexp="[A-Za-z0-9-]{5,64}") String logisticsNo) {}
    @GetMapping("/order/page")
    public CommonResult<PageResult<SupplierTradeService.OrderRow>> page(@RequestParam Long shopId, @Valid PageParam page,
            @RequestParam(required=false) String no, @RequestParam(required=false) Integer status) {
        return success(service.page(memberId(), shopId, page, no, status));
    }
    @GetMapping("/order/get")
    public CommonResult<SupplierTradeService.OrderDetail> get(@RequestParam Long shopId, @RequestParam Long id) {
        return success(service.detail(memberId(), shopId, id));
    }
    @GetMapping("/express/list")
    public CommonResult<List<SupplierTradeService.Express>> express(@RequestParam Long shopId) {
        return success(service.express(memberId(), shopId));
    }
    @PutMapping("/order/deliver")
    public CommonResult<Boolean> deliver(@Valid @RequestBody Delivery req) {
        legacyWriteGuard.requireEnabled();
        service.deliver(memberId(), req.shopId(), req.id(), req.logisticsId(), req.logisticsNo()); return success(true);
    }
    @GetMapping("/after-sale/page")
    public CommonResult<PageResult<SupplierTradeService.AfterSaleRow>> afterSales(@RequestParam Long shopId, @Valid PageParam page,
            @RequestParam(required=false) String no, @RequestParam(required=false) Integer status) {
        return success(service.afterSales(memberId(), shopId, page, no, status));
    }
    public record AfterSaleProcess(@NotNull Long shopId, @NotNull Long id,
            @NotNull SupplierTradeService.AfterSaleAction action, @Size(max=200) String reason) {}
    @GetMapping("/after-sale/get")
    public CommonResult<SupplierTradeService.AfterSaleDetail> afterSaleGet(@RequestParam Long shopId, @RequestParam Long id) {
        return success(service.afterSaleDetail(memberId(), shopId, id));
    }
    @PutMapping("/after-sale/process")
    public CommonResult<Boolean> afterSaleProcess(@Valid @RequestBody AfterSaleProcess req) {
        legacyWriteGuard.requireEnabled();
        service.processAfterSale(memberId(), req.shopId(), req.id(), req.action(), req.reason());
        return success(true);
    }
}
