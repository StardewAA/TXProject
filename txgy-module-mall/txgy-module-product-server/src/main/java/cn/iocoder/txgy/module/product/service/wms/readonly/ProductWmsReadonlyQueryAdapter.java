package cn.iocoder.txgy.module.product.service.wms.readonly;

import java.util.List;

/**
 * 商城侧 WMS 只读查询适配器。
 */
public interface ProductWmsReadonlyQueryAdapter {

    /**
     * 查询 WMS 商品下所有规格的售价和库存摘要。
     *
     * @param itemId WMS 商品编号
     * @return 规格摘要列表
     */
    List<ProductWmsSkuSummaryDTO> getSkuSummary(Long itemId);

}
