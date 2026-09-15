package cn.iocoder.txgy.module.crm.service.wms;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.crm.config.wms.CrmWmsProductReadProperties;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 远程 WMS 商品（仓库库存）只读查询实现：调用 WMS 的 {@code /admin-api/wms/crm/products/*}。
 */
@Service
@ConditionalOnProperty(prefix = "txgy.integration.wms.crm-product", name = "mode", havingValue = "remote")
public class CrmWmsProductReadRemoteAdapter implements CrmWmsProductReadAdapter {

    @Resource
    private CrmWmsProductQueryApi crmWmsProductQueryApi;
    @Resource
    private CrmWmsProductReadProperties properties;

    @Override
    public PageResult<CrmWmsProductSkuDTO> getProductPage(Integer pageNo, Integer pageSize, String name) {
        // tenant-id 由 TenantRequestInterceptor 按当前租户上下文透传
        return crmWmsProductQueryApi.getProductPage(authorization(), pageNo, pageSize,
                        null, name, null, null)
                .getCheckedData();
    }

    @Override
    public CrmWmsProductSkuDTO getBySkuId(Long skuId) {
        if (skuId == null) {
            return null;
        }
        return crmWmsProductQueryApi.getProduct(authorization(), skuId).getCheckedData();
    }

    @Override
    public List<CrmWmsProductSkuDTO> getBySkuIds(Collection<Long> skuIds) {
        if (CollUtil.isEmpty(skuIds)) {
            return Collections.emptyList();
        }
        return crmWmsProductQueryApi.getProductList(authorization(), new ArrayList<>(skuIds)).getCheckedData();
    }

    private String authorization() {
        String authorization = properties.getAuthorization();
        if (StrUtil.isBlank(authorization)) {
            throw new IllegalStateException("txgy.integration.wms.crm-product.authorization 未配置");
        }
        return authorization;
    }

}
