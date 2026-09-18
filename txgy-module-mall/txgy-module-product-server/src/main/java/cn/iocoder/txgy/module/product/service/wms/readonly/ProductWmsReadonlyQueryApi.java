package cn.iocoder.txgy.module.product.service.wms.readonly;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 商城侧预留的 WMS 只读查询契约。
 *
 * <p>WMS 已提供此接口。只授予调用账号查询权限，不通过本接口执行任何库存变更。</p>
 */
@FeignClient(name = "wms-server")
public interface ProductWmsReadonlyQueryApi {

    @GetMapping("/admin-api/wms/inventory/sku-summary")
    CommonResult<List<ProductWmsSkuSummaryDTO>> getSkuSummary(@RequestHeader("Authorization") String authorization,
                                                              @RequestParam("itemId") Long itemId);

}
