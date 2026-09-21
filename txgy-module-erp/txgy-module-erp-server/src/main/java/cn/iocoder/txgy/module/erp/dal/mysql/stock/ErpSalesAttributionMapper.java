package cn.iocoder.txgy.module.erp.dal.mysql.stock;

import cn.iocoder.txgy.module.erp.dal.dataobject.stock.ErpSalesAttributionRowDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.stock.ErpSalesUserAmountRowDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.stock.ErpStockSourceBreakdownDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ERP 销售归因查询 Mapper
 *
 * <p>两个问题、两张查询：</p>
 * <ol>
 *     <li><b>仓库里多少是 ERP 销售的</b> —— {@link #selectSourceBreakdown}：
 *         按来源系统 + 业务类型分组。只有 source_system='ERP' 且 biz_type=50 才算 ERP 销售出库</li>
 *     <li><b>哪些是 ERP 的什么人销售的</b> —— {@link #selectSalesAttribution}：
 *         以 source_user_id（责任人快照）归因，按「销售员 × 产品」聚合数量与金额</li>
 * </ol>
 *
 * <p><b>重要</b>：所有统计都强制过滤 {@code source_system IS NOT NULL}，
 * 这样历史种子数据（source_system 为 NULL、biz_id 全为 0）会被自然排除，不污染统计。</p>
 *
 * @author OPENLAB BS
 */
@Mapper
public interface ErpSalesAttributionMapper {

    /**
     * 出库来源构成：按来源系统 + 业务类型统计库存变动量。
     *
     * @param tenantId    租户编号
     * @param warehouseId 仓库编号，可空
     * @param startTime   开始时间，可空（含）
     * @param endTime     结束时间，可空（不含）
     */
    @Select("""
            SELECT r.source_system AS sourceSystem,
                   r.biz_type      AS bizType,
                   SUM(r.count)    AS totalCount,
                   COUNT(*)        AS recordCount
              FROM erp_stock_record r
             WHERE r.tenant_id = #{tenantId}
               AND r.deleted = b'0'
               AND r.source_system IS NOT NULL
               AND (#{warehouseId} IS NULL OR r.warehouse_id = #{warehouseId})
               AND (#{startTime}   IS NULL OR r.create_time >= #{startTime})
               AND (#{endTime}     IS NULL OR r.create_time <  #{endTime})
             GROUP BY r.source_system, r.biz_type
             ORDER BY r.source_system, r.biz_type
            """)
    List<ErpStockSourceBreakdownDO> selectSourceBreakdown(@Param("tenantId") Long tenantId,
                                                          @Param("warehouseId") Long warehouseId,
                                                          @Param("startTime") LocalDateTime startTime,
                                                          @Param("endTime") LocalDateTime endTime);

    /**
     * 销售绩效归因：按「销售员 × 产品」聚合销售出库(50) 与销售退货(60)。
     *
     * <p>数量取自库存流水；金额联销售出库/退货明细行（biz_item_id）。</p>
     */
    @Select("""
            SELECT r.source_user_id AS userId,
                   p.material_code  AS productCode,
                   p.name           AS productName,
                   SUM(CASE WHEN r.biz_type = 50 THEN -r.count ELSE 0 END)                        AS outCount,
                   SUM(CASE WHEN r.biz_type = 60 THEN  r.count ELSE 0 END)                        AS returnCount,
                   SUM(CASE WHEN r.biz_type = 50 THEN -r.count ELSE 0 END)
                 - SUM(CASE WHEN r.biz_type = 60 THEN  r.count ELSE 0 END)                        AS netCount,
                   SUM(CASE WHEN r.biz_type = 50 THEN  COALESCE(soi.total_price, 0) ELSE 0 END)   AS outAmount,
                   SUM(CASE WHEN r.biz_type = 60 THEN  COALESCE(sri.total_price, 0) ELSE 0 END)   AS returnAmount,
                   SUM(CASE WHEN r.biz_type = 50 THEN  COALESCE(soi.total_price, 0) ELSE 0 END)
                 - SUM(CASE WHEN r.biz_type = 60 THEN  COALESCE(sri.total_price, 0) ELSE 0 END)   AS netAmount
              FROM erp_stock_record r
              JOIN erp_product p
                ON p.id = r.product_id
               AND p.deleted = b'0'
              LEFT JOIN erp_sale_out_items soi
                ON r.biz_type = 50 AND soi.id = r.biz_item_id AND soi.deleted = b'0'
              LEFT JOIN erp_sale_return_items sri
                ON r.biz_type = 60 AND sri.id = r.biz_item_id AND sri.deleted = b'0'
             WHERE r.tenant_id = #{tenantId}
               AND r.deleted = b'0'
               AND r.source_system = 'ERP'
               AND r.biz_type IN (50, 60)
               AND (#{userId}      IS NULL OR r.source_user_id = #{userId})
               AND (#{warehouseId} IS NULL OR r.warehouse_id = #{warehouseId})
               AND (#{startTime}   IS NULL OR r.create_time >= #{startTime})
               AND (#{endTime}     IS NULL OR r.create_time <  #{endTime})
             GROUP BY r.source_user_id, p.material_code, p.name
             ORDER BY netAmount DESC
            """)
    List<ErpSalesAttributionRowDO> selectSalesAttribution(@Param("tenantId") Long tenantId,
                                                          @Param("userId") Long userId,
                                                          @Param("warehouseId") Long warehouseId,
                                                          @Param("startTime") LocalDateTime startTime,
                                                          @Param("endTime") LocalDateTime endTime);

    /**
     * 销售订单金额：按销售员聚合已审批销售订单的金额与单数。
     *
     * <p>作为「应收」的分母口径 —— 回款是针对订单发生的，与出库进度无关。</p>
     */
    @Select("""
            SELECT o.sale_user_id   AS userId,
                   SUM(o.total_price) AS amount,
                   COUNT(*)         AS docCount
              FROM erp_sale_order o
             WHERE o.tenant_id = #{tenantId}
               AND o.deleted = b'0'
               AND o.status = #{approveStatus}
               AND o.sale_user_id IS NOT NULL
               AND (#{userId}    IS NULL OR o.sale_user_id = #{userId})
               AND (#{startTime} IS NULL OR o.order_time >= #{startTime})
               AND (#{endTime}   IS NULL OR o.order_time <  #{endTime})
             GROUP BY o.sale_user_id
            """)
    List<ErpSalesUserAmountRowDO> selectSalesOrderAmount(@Param("tenantId") Long tenantId,
                                                         @Param("userId") Long userId,
                                                         @Param("approveStatus") Integer approveStatus,
                                                         @Param("startTime") LocalDateTime startTime,
                                                         @Param("endTime") LocalDateTime endTime);

    /**
     * 回款金额归因：回款单明细（biz_type = 销售订单）经订单归因到销售员。
     *
     * <p>归因链路：{@code erp_finance_receipt_item.biz_id → erp_sale_order.id → sale_user_id}。
     * 因此回款归属的是<b>订单上的销售员</b>，而不是填单人（finance_user_id）。
     * 只统计已审批（status=20）的回款单，红字回款按负数自然冲减。</p>
     */
    @Select("""
            SELECT o.sale_user_id      AS userId,
                   SUM(ri.receipt_price) AS amount,
                   COUNT(DISTINCT r.id)  AS docCount
              FROM erp_finance_receipt_item ri
              JOIN erp_finance_receipt r
                ON r.id = ri.receipt_id
               AND r.deleted = b'0'
               AND r.status = #{approveStatus}
              JOIN erp_sale_order o
                ON o.id = ri.biz_id
               AND o.deleted = b'0'
             WHERE ri.tenant_id = #{tenantId}
               AND ri.deleted = b'0'
               AND ri.biz_type = #{saleOrderBizType}
               AND o.sale_user_id IS NOT NULL
               AND (#{userId}    IS NULL OR o.sale_user_id = #{userId})
               AND (#{startTime} IS NULL OR r.receipt_time >= #{startTime})
               AND (#{endTime}   IS NULL OR r.receipt_time <  #{endTime})
             GROUP BY o.sale_user_id
            """)
    List<ErpSalesUserAmountRowDO> selectSalesReceiptAmount(@Param("tenantId") Long tenantId,
                                                           @Param("userId") Long userId,
                                                           @Param("approveStatus") Integer approveStatus,
                                                           @Param("saleOrderBizType") Integer saleOrderBizType,
                                                           @Param("startTime") LocalDateTime startTime,
                                                           @Param("endTime") LocalDateTime endTime);

}
