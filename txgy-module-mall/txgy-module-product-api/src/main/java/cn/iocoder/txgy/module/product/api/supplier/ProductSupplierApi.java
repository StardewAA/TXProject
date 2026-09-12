package cn.iocoder.txgy.module.product.api.supplier;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.product.enums.ApiConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 内部RPC：调用方必须从登录上下文取得会员编号，不能接受外部任意会员编号。 */
@FeignClient(name = ApiConstants.NAME, contextId = "productSupplierAuthorization")
public interface ProductSupplierApi {
    record ReturnAddress(String receiverName, String receiverMobile, String address) {}
    @GetMapping(ApiConstants.PREFIX + "/supplier/return-address")
    CommonResult<ReturnAddress> returnAddress(@RequestParam("memberId") Long memberId, @RequestParam("shopId") Long shopId);
    @GetMapping(ApiConstants.PREFIX + "/supplier/validate-shop")
    CommonResult<Boolean> validateShop(@RequestParam("memberId") Long memberId, @RequestParam("shopId") Long shopId);
}
