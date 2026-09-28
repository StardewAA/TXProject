package cn.iocoder.txgy.module.crm.service.wms;

import cn.iocoder.txgy.framework.common.pojo.PageResult;

import java.util.Collection;
import java.util.List;

/**
 * CRM 侧 WMS 商品（仓库库存）只读查询适配器。
 *
 * <p>CRM 产品管理不落库，所有产品数据实时取自 WMS：WMS 存在则展示，WMS 删除则不展示。</p>
 */
public interface CrmWmsProductReadAdapter {

    /**
     * 分页查询当前租户下 WMS 启用的商品规格（一个 SKU 一行）。
     *
     * @param pageNo   页码，从 1 开始
     * @param pageSize 每页条数
     * @param name     商品名称，包含匹配；为空表示不过滤
     * @return 规格分页结果
     */
    PageResult<CrmWmsProductSkuDTO> getProductPage(Integer pageNo, Integer pageSize, String name);

    /**
     * 按 WMS 规格编号查询单个规格；WMS 不存在时返回 null
     *
     * @param skuId WMS 规格编号
     * @return 规格数据
     */
    CrmWmsProductSkuDTO getBySkuId(Long skuId);

    /**
     * 按 WMS 规格编号批量查询；返回的顺序与数量由 WMS 决定，不存在的规格不会返回
     *
     * @param skuIds WMS 规格编号
     * @return 规格数据列表
     */
    List<CrmWmsProductSkuDTO> getBySkuIds(Collection<Long> skuIds);

}
