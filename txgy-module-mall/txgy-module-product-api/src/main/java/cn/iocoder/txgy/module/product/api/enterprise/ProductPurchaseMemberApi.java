package cn.iocoder.txgy.module.product.api.enterprise;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.product.enums.ApiConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 企业采购成员的内部数据范围，供交易等商城模块复用。 */
@FeignClient(name = ApiConstants.NAME, contextId = "productPurchaseMemberApi")
public interface ProductPurchaseMemberApi {

    String PREFIX = ApiConstants.PREFIX + "/purchase-member";

    record Scope(Long tenantId, Long memberId, Long enterpriseId) {}

    /** 返回当前租户内有效、已认证且身份一致的采购成员范围；无效时直接拒绝。 */
    @GetMapping(PREFIX + "/scope")
    CommonResult<Scope> getScope(@RequestParam("memberId") Long memberId);

    /** 校验采购成员是否仍属于指定企业，防止客户端伪造 buyerEnterpriseId。 */
    @GetMapping(PREFIX + "/validate-enterprise")
    CommonResult<Boolean> validateEnterprise(@RequestParam("memberId") Long memberId,
                                               @RequestParam("enterpriseId") Long enterpriseId);
}
