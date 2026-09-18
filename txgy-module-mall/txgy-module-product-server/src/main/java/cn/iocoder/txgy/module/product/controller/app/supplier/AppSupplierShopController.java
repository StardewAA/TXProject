package cn.iocoder.txgy.module.product.controller.app.supplier;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.product.service.supplier.SupplierShopService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService.memberId;
@RestController
@RequestMapping("/product/supplier-shop")
public class AppSupplierShopController {
    @Resource private SupplierShopService service;
    @GetMapping("/get") public CommonResult<SupplierShopService.Detail> get(@RequestParam Long shopId) { return success(service.get(memberId(),shopId)); }
    @PutMapping("/save") public CommonResult<Boolean> save(@Valid @RequestBody SupplierShopService.Save req) { service.save(memberId(),req);return success(true); }
}
