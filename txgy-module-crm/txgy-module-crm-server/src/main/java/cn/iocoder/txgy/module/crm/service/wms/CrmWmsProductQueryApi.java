package cn.iocoder.txgy.module.crm.service.wms;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * CRM 读取 WMS 商品的只读接口契约。
 *
 * <p>对应 WMS 交付的 {@code GET /admin-api/wms/crm/products/page}。
 * tenant-id 由 {@code TenantRequestInterceptor} 按当前租户上下文自动透传，Authorization 由配置注入。
 * 该接口只读：不新增、修改、冻结、扣减或调整 WMS 库存。</p>
 */
@FeignClient(name = "wms-server")
public interface CrmWmsProductQueryApi {

    @GetMapping("/admin-api/wms/crm/products/page")
    CommonResult<PageResult<CrmWmsProductSkuDTO>> getProductPage(@RequestHeader("Authorization") String authorization,
                                                                 @RequestParam("pageNo") Integer pageNo,
                                                                 @RequestParam("pageSize") Integer pageSize,
                                                                 @RequestParam(value = "materialCode", required = false) String materialCode,
                                                                 @RequestParam(value = "materialName", required = false) String materialName,
                                                                 @RequestParam(value = "skuCode", required = false) String skuCode);

}
