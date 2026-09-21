package cn.iocoder.txgy.module.erp.service.stock;

import cn.iocoder.txgy.module.erp.controller.admin.stock.vo.attribution.ErpSalesAttributionRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.stock.vo.attribution.ErpSalesPerformanceSummaryRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.stock.vo.attribution.ErpStockSourceBreakdownRespVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ERP 销售归因 Service
 *
 * <p>回答两个问题：</p>
 * <ol>
 *     <li>仓库里多少是 ERP 销售的（其余是商城发货 / MES 领料 / 手工调整）</li>
 *     <li>这些 ERP 销售分别是谁卖的（绩效归因）</li>
 * </ol>
 *
 * @author OPENLAB BS
 */
public interface ErpSalesAttributionService {

    /**
     * 出库来源构成：按来源系统 + 业务类型统计库存变动量。
     *
     * @param warehouseId 仓库编号，可空
     * @param startTime   开始时间，可空
     * @param endTime     结束时间，可空
     */
    List<ErpStockSourceBreakdownRespVO> getSourceBreakdown(Long warehouseId,
                                                           LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 销售绩效归因：按「销售员 × 产品」聚合销售出库与退货的数量、金额。
     *
     * @param userId      销售员编号，可空
     * @param warehouseId 仓库编号，可空
     * @param startTime   开始时间，可空
     * @param endTime     结束时间，可空
     */
    List<ErpSalesAttributionRespVO> getSalesAttribution(Long userId, Long warehouseId,
                                                        LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 销售绩效汇总：把「出入库归因」「名下已审批订单金额」「经订单归因的回款金额」
     * 三条口径汇总到销售员维度，并给出应收余额与回款率。
     *
     * <p>回款归因链路：{@code erp_finance_receipt_item(biz_type=销售订单) → erp_sale_order → sale_user_id}，
     * 即回款归属订单上的销售员，而不是回款单的填单人。</p>
     *
     * @param userId      销售员编号，可空
     * @param warehouseId 仓库编号，可空（只影响出入库口径）
     * @param startTime   开始时间，可空
     * @param endTime     结束时间，可空
     */
    List<ErpSalesPerformanceSummaryRespVO> getPerformanceSummary(Long userId, Long warehouseId,
                                                                 LocalDateTime startTime, LocalDateTime endTime);

}
