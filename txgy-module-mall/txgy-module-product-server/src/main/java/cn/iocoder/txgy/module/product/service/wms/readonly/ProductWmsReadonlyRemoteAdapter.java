package cn.iocoder.txgy.module.product.service.wms.readonly;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.module.product.config.wms.ProductWmsReadonlyProperties;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 远程 WMS 只读查询实现。
 */
@Service
@ConditionalOnProperty(prefix = "txgy.integration.wms.readonly", name = "mode", havingValue = "remote")
public class ProductWmsReadonlyRemoteAdapter implements ProductWmsReadonlyQueryAdapter {

    @Resource
    private ProductWmsReadonlyQueryApi productWmsReadonlyQueryApi;
    @Resource
    private ProductWmsReadonlyProperties productWmsReadonlyProperties;

    @Override
    public List<ProductWmsSkuSummaryDTO> getSkuSummary(Long itemId) {
        String authorization = productWmsReadonlyProperties.getAuthorization();
        if (StrUtil.isBlank(authorization)) {
            throw new IllegalStateException("txgy.integration.wms.readonly.authorization 未配置");
        }
        return productWmsReadonlyQueryApi.getSkuSummary(authorization, itemId).getCheckedData();
    }

}
