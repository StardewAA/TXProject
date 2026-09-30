package cn.iocoder.txgy.module.product.api.enterprise;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.product.enums.ApiConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Internal read-only, minimal enterprise facts. Does not create membership or authorize staff. */
@FeignClient(name = ApiConstants.NAME, contextId = "productEnterpriseCustomerApi")
public interface ProductEnterpriseCustomerApi {
    @GetMapping(ApiConstants.PREFIX + "/enterprise/customer-owner")
    CommonResult<Owner> getCustomerOwner(@RequestParam("id") Long id);

    record Owner(Long id, Long tenantId, String name, boolean approved) { }
}
