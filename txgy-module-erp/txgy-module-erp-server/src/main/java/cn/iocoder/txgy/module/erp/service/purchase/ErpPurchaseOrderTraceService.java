package cn.iocoder.txgy.module.erp.service.purchase;

import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.order.ErpPurchaseOrderTraceRespVO;

/**
 * ERP 采购订单溯源 Service
 *
 * <p>把「询价 → 报价 → 定标 → 采购订单 → 采购入库」整条链一次返回。</p>
 *
 * @author OPENLAB BS
 */
public interface ErpPurchaseOrderTraceService {

    /**
     * 获得采购订单的溯源信息。
     *
     * @param orderId 采购订单编号
     * @return 溯源信息；订单不存在时返回 null
     */
    ErpPurchaseOrderTraceRespVO getTrace(Long orderId);

}
