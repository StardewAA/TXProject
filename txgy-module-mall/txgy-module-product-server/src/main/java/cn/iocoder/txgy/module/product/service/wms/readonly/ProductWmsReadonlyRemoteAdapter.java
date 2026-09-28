package cn.iocoder.txgy.module.product.service.wms.readonly;

import cn.iocoder.txgy.module.wms.api.stock.WmsStockReadApi;
import cn.iocoder.txgy.module.wms.api.stock.dto.WmsStockPageReqDTO;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 远程 WMS 只读查询实现。
 */
@Service
@ConditionalOnProperty(prefix = "txgy.integration.wms.readonly", name = "mode", havingValue = "remote", matchIfMissing = true)
public class ProductWmsReadonlyRemoteAdapter implements ProductWmsReadonlyQueryAdapter {

    @Resource
    private WmsStockReadApi wmsStockReadApi;

    @Override
    public List<ProductWmsSkuSummaryDTO> getSkuSummary(Long itemId) {
        WmsStockPageReqDTO request = new WmsStockPageReqDTO();
        request.setItemId(itemId);
        request.setPageNo(1);
        request.setPageSize(200);
        var page = wmsStockReadApi.getStockPage(request).getCheckedData();
        if (page == null || page.getList() == null) return List.of();
        return page.getList().stream()
                // 兼容尚未识别 itemId 查询参数的旧 WMS：返回标识不匹配时不展示，避免串用其它物料。
                .filter(row -> itemId != null && itemId.equals(row.getItemId()))
                .map(row -> {
            ProductWmsSkuSummaryDTO result = new ProductWmsSkuSummaryDTO();
            result.setItemId(row.getItemId());
            result.setItemCode(row.getMaterialCode());
            result.setItemName(row.getMaterialName());
            result.setSkuId(row.getSkuId());
            result.setSkuCode(row.getSkuCode());
            result.setSkuName(row.getSkuName());
            result.setUnit(row.getUnitName());
            result.setTotalQuantity(row.getQuantity());
            return result;
                }).toList();
    }

}
