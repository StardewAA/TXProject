package cn.iocoder.txgy.module.product.api.shop;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.product.api.shop.dto.ProductAdminAuthorizedShopRespDTO;
import cn.iocoder.txgy.module.product.api.shop.dto.ProductAdminShopScopeRespDTO;
import cn.iocoder.txgy.module.product.dal.dataobject.shop.ShopReturnAddressDO;
import cn.iocoder.txgy.module.product.dal.mysql.shop.ShopReturnAddressMapper;
import cn.iocoder.txgy.module.product.service.enterprise.ProductAdminBusinessScopeService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
public class ProductAdminShopScopeApiImpl implements ProductAdminShopScopeApi {

    @Resource private ProductAdminBusinessScopeService scopeService;
    @Resource private ShopReturnAddressMapper returnAddressMapper;

    @Override
    public CommonResult<ProductAdminShopScopeRespDTO> getScope(Long adminUserId) {
        return CommonResult.success(scopeService.toDto(scopeService.scope(adminUserId)));
    }

    @Override
    public CommonResult<Boolean> validateAuthorizedShop(Long adminUserId, Long shopId) {
        scopeService.requireMerchantShop(adminUserId, shopId);
        return CommonResult.success(true);
    }

    @Override
    public CommonResult<List<ProductAdminAuthorizedShopRespDTO>> authorizedShops(Long adminUserId) {
        return CommonResult.success(scopeService.authorizedShopOptions(adminUserId));
    }

    @Override
    public CommonResult<ReturnAddress> returnAddress(Long adminUserId, Long shopId) {
        scopeService.requireMerchantShop(adminUserId, shopId);
        ShopReturnAddressDO row = returnAddressMapper.selectById(shopId);
        return CommonResult.success(row == null ? null
                : new ReturnAddress(row.getReceiverName(), row.getReceiverMobile(), row.getAddress()));
    }
}
