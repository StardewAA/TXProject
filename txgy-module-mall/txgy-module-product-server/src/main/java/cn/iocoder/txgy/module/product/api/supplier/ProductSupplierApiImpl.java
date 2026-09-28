package cn.iocoder.txgy.module.product.api.supplier;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductSupplierApiImpl implements ProductSupplierApi {
    @Resource private cn.iocoder.txgy.module.product.service.supplier.SupplierShopService shopService;
    @Override public CommonResult<ReturnAddress> returnAddress(Long memberId,Long shopId) {
        return CommonResult.success(shopService.requiredAddress(memberId,shopId));
    }
    @Resource private EnterpriseMembershipService membership;
    @Override public CommonResult<Boolean> validateShop(Long memberId, Long shopId) {
        membership.requireShop(memberId, shopId);
        return CommonResult.success(true);
    }
}
