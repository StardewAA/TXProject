package cn.iocoder.txgy.module.crm.service.wms;

import cn.iocoder.txgy.framework.common.pojo.PageResult;

/**
 * CRM 侧 WMS 商品只读查询适配器。
 */
public interface CrmWmsProductReadAdapter {

    /**
     * 分页查询当前租户下 WMS 启用的商品规格（一个 SKU 一行）。
     *
     * @param pageNo   页码，从 1 开始
     * @param pageSize 每页条数
     * @return 规格分页结果
     */
    PageResult<CrmWmsProductSkuDTO> getProductPage(Integer pageNo, Integer pageSize);

}
