package cn.iocoder.txgy.module.product.controller.app.supplier;

import cn.iocoder.txgy.framework.common.pojo.*;
import cn.iocoder.txgy.module.product.service.supplier.SupplierProductService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService.memberId;

@RestController
@RequestMapping("/product/supplier-product")
public class AppSupplierProductController {
    @Resource private SupplierProductService service;
    @GetMapping("/options")
    public CommonResult<Map<String, Object>> options(@RequestParam Long shopId) { return success(service.options(memberId(), shopId)); }
    @GetMapping("/page")
    public CommonResult<PageResult<SupplierProductService.Summary>> page(@RequestParam Long shopId, @Valid PageParam page,
            @RequestParam(required=false) String name, @RequestParam(required=false) Integer status) {
        return success(service.page(memberId(), shopId, page, name, status));
    }
    @GetMapping("/get")
    public CommonResult<SupplierProductService.Detail> get(@RequestParam Long shopId, @RequestParam Long id) {
        return success(service.detail(memberId(), shopId, id));
    }
    @PostMapping("/save")
    public CommonResult<Long> save(@Valid @RequestBody SupplierProductSaveReqVO req) { return success(service.save(memberId(), req)); }
    @PutMapping("/status")
    public CommonResult<Boolean> status(@RequestParam Long shopId, @RequestParam Long id, @RequestParam Integer status) {
        service.status(memberId(), shopId, id, status); return success(true);
    }
}
