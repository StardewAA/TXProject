package cn.iocoder.txgy.module.trade.controller.admin.supplier;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.trade.service.supplier.SupplierTradeService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/** 供应企业后台销售履约入口；业务数据范围由 {@link SupplierTradeService} 再次强制校验。 */
@RestController
@RequestMapping("/trade/supplier")
public class AdminSupplierTradeController {

    @Resource
    private SupplierTradeService service;

    public record Delivery(@NotNull Long shopId, @NotNull Long id, @NotNull @Positive Long logisticsId,
                           @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{5,64}") String logisticsNo) {}

    @GetMapping("/order/page")
    @PreAuthorize("@ss.hasPermission('trade:supplier-order:query')")
    public CommonResult<PageResult<SupplierTradeService.OrderRow>> page(
            @RequestParam Long shopId, @Valid PageParam page,
            @RequestParam(required = false) String no,
            @RequestParam(required = false) Integer status) {
        return success(service.adminPage(shopId, page, no, status));
    }

    @GetMapping("/order/get")
    @PreAuthorize("@ss.hasPermission('trade:supplier-order:query')")
    public CommonResult<SupplierTradeService.OrderDetail> get(@RequestParam Long shopId,
                                                               @RequestParam Long id) {
        return success(service.adminDetail(shopId, id));
    }

    @GetMapping("/express/list")
    @PreAuthorize("@ss.hasPermission('trade:supplier-order:update')")
    public CommonResult<List<SupplierTradeService.Express>> express(@RequestParam Long shopId) {
        return success(service.adminExpress(shopId));
    }

    @PutMapping("/order/deliver")
    @PreAuthorize("@ss.hasPermission('trade:supplier-order:update')")
    public CommonResult<Boolean> deliver(@Valid @RequestBody Delivery request) {
        service.adminDeliver(request.shopId(), request.id(), request.logisticsId(), request.logisticsNo());
        return success(true);
    }

    @GetMapping("/after-sale/page")
    @PreAuthorize("@ss.hasPermission('trade:supplier-after-sale:query')")
    public CommonResult<PageResult<SupplierTradeService.AfterSaleRow>> afterSales(
            @RequestParam Long shopId, @Valid PageParam page,
            @RequestParam(required = false) String no,
            @RequestParam(required = false) Integer status) {
        return success(service.adminAfterSales(shopId, page, no, status));
    }

    public record AfterSaleProcess(@NotNull Long shopId, @NotNull Long id,
                                   @NotNull SupplierTradeService.AfterSaleAction action,
                                   @Size(max = 200) String reason) {}

    @GetMapping("/after-sale/get")
    @PreAuthorize("@ss.hasPermission('trade:supplier-after-sale:query')")
    public CommonResult<SupplierTradeService.AfterSaleDetail> afterSaleGet(@RequestParam Long shopId,
                                                                           @RequestParam Long id) {
        return success(service.adminAfterSaleDetail(shopId, id));
    }

    @PutMapping("/after-sale/process")
    @PreAuthorize("@ss.hasPermission('trade:supplier-after-sale:process')")
    public CommonResult<Boolean> afterSaleProcess(@Valid @RequestBody AfterSaleProcess request) {
        service.adminProcessAfterSale(request.shopId(), request.id(), request.action(), request.reason());
        return success(true);
    }
}
