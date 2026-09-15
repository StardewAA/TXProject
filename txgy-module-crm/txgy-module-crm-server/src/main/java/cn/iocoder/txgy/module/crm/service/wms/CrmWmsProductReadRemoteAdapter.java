package cn.iocoder.txgy.module.crm.service.wms;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.crm.config.wms.CrmWmsProductReadProperties;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 远程 WMS 商品只读查询实现：调用 WMS 的 {@code /admin-api/wms/crm/products/page}。
 */
@Service
@ConditionalOnProperty(prefix = "txgy.integration.wms.crm-product", name = "mode", havingValue = "remote")
public class CrmWmsProductReadRemoteAdapter implements CrmWmsProductReadAdapter {

    @Resource
    private CrmWmsProductQueryApi crmWmsProductQueryApi;
    @Resource
    private CrmWmsProductReadProperties properties;

    @Override
    public PageResult<CrmWmsProductSkuDTO> getProductPage(Integer pageNo, Integer pageSize) {
        String authorization = properties.getAuthorization();
        if (StrUtil.isBlank(authorization)) {
            throw new IllegalStateException("txgy.integration.wms.crm-product.authorization 未配置");
        }
        // tenant-id 由 TenantRequestInterceptor 按当前租户上下文透传
        return crmWmsProductQueryApi.getProductPage(authorization, pageNo, pageSize, null, null, null)
                .getCheckedData();
    }

}
