package cn.iocoder.txgy.module.product.controller.app.supplier;

import cn.iocoder.txgy.framework.common.pojo.*;
import cn.iocoder.txgy.module.product.service.supplier.SupplierStockService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService.memberId;

@RestController
@RequestMapping("/product/supplier-stock")
public class AppSupplierStockController {
    @Resource private SupplierStockService service;
    @PostMapping("/adjust")
    public CommonResult<SupplierStockService.Entry> adjust(@Valid @RequestBody SupplierStockService.Adjust req) {
        return success(service.adjust(memberId(),req));
    }
    @GetMapping("/page")
    public CommonResult<PageResult<SupplierStockService.Entry>> page(@RequestParam Long shopId,
            @RequestParam Long spuId, @Valid PageParam page) {
        return success(service.page(memberId(),shopId,spuId,page));
    }
}
