package cn.iocoder.txgy.module.erp.service.stock;

import cn.iocoder.txgy.module.erp.controller.admin.stock.vo.check.ErpStockCheckComparisonRespVO;

/**
 * ERP 盘点单三方比对 Service 接口
 *
 * <p>盘点单由 ERP 出单（第 1 方：ERP 账面），ERP 录实盘（第 2 方：ERP 实盘），
 * WMS 实物账由旁路同步镜像过来（第 3 方：{@code erp_stock_mirror}）。
 * 本服务把三者摆在一起并算出「WMS 需调整量」，作为 WMS 执行调整的输入。</p>
 *
 * @author OPENLAB BS
 */
public interface ErpStockCheckComparisonService {

    /**
     * 获得盘点单三方比对结果
     *
     * @param checkId 盘点单编号
     * @return 三方比对结果
     */
    ErpStockCheckComparisonRespVO getComparison(Long checkId);

}
