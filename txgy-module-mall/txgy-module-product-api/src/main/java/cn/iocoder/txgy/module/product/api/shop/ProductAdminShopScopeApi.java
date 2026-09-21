package cn.iocoder.txgy.module.product.api.shop;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.product.api.shop.dto.ProductAdminAuthorizedShopRespDTO;
import cn.iocoder.txgy.module.product.api.shop.dto.ProductAdminShopScopeRespDTO;
import cn.iocoder.txgy.module.product.enums.ApiConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** 商城后台企业、店铺经营数据范围 API，供交易等商城模块复用。 */
@FeignClient(name = ApiConstants.NAME, contextId = "productAdminShopScope")
public interface ProductAdminShopScopeApi {

    String PREFIX = ApiConstants.PREFIX + "/admin-shop-scope";

    record ReturnAddress(String receiverName, String receiverMobile, String address) {}

    @GetMapping(PREFIX + "/get")
    CommonResult<ProductAdminShopScopeRespDTO> getScope(@RequestParam("adminUserId") Long adminUserId);

    @GetMapping(PREFIX + "/validate-shop")
    CommonResult<Boolean> validateAuthorizedShop(@RequestParam("adminUserId") Long adminUserId,
                                                  @RequestParam("shopId") Long shopId);

    @GetMapping(PREFIX + "/authorized-shops")
    CommonResult<List<ProductAdminAuthorizedShopRespDTO>> authorizedShops(
            @RequestParam("adminUserId") Long adminUserId);

    @GetMapping(PREFIX + "/return-address")
    CommonResult<ReturnAddress> returnAddress(@RequestParam("adminUserId") Long adminUserId,
                                               @RequestParam("shopId") Long shopId);

}
