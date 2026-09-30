package cn.iocoder.txgy.module.erp.dal.mysql.assistant;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * ERP 智能助手（首页 AI 对话）的只读查询 Mapper
 *
 * <p>为了让「AI 找订单」回答的是<b>真实业务数据</b>而不是写死的演示语料，
 * 这里提供几段只读 SQL：单据按单号/客户名模糊搜索、产品与客商搜索、库存查询、待审批统计。</p>
 *
 * <p><b>只读</b>：全部为 {@code SELECT}，且只访问 ERP 自己的表，不跨模块。</p>
 *
 * @author OPENLAB BS
 */
@Mapper
public interface ErpAssistantMapper {

    /**
     * 按关键词（单号 / 客户名）搜索销售订单
     */
    @Select("""
            SELECT o.id          AS id,
                   o.no          AS no,
                   o.status      AS status,
                   o.total_price AS totalPrice,
                   o.order_time  AS orderTime,
                   c.name        AS customerName
              FROM erp_sale_order o
              LEFT JOIN erp_customer c ON c.id = o.customer_id AND c.deleted = b'0'
             WHERE o.tenant_id = #{tenantId}
               AND o.deleted = b'0'
               AND (o.no = #{keyword}
                    OR o.no LIKE CONCAT(#{keyword}, '%')
                    OR c.name LIKE CONCAT(#{keyword}, '%'))
             ORDER BY o.id DESC
             LIMIT #{limit}
            """)
    List<Map<String, Object>> selectSaleOrders(@Param("tenantId") Long tenantId,
                                               @Param("keyword") String keyword,
                                               @Param("limit") Integer limit);

    /**
     * 按关键词（单号 / 供应商名）搜索采购订单
     */
    @Select("""
            SELECT o.id          AS id,
                   o.no          AS no,
                   o.status      AS status,
                   o.total_price AS totalPrice,
                   o.order_time  AS orderTime,
                   s.name        AS supplierName
              FROM erp_purchase_order o
              LEFT JOIN erp_supplier s ON s.id = o.supplier_id AND s.deleted = b'0'
             WHERE o.tenant_id = #{tenantId}
               AND o.deleted = b'0'
               AND (o.no = #{keyword}
                    OR o.no LIKE CONCAT(#{keyword}, '%')
                    OR s.name LIKE CONCAT(#{keyword}, '%'))
             ORDER BY o.id DESC
             LIMIT #{limit}
            """)
    List<Map<String, Object>> selectPurchaseOrders(@Param("tenantId") Long tenantId,
                                                   @Param("keyword") String keyword,
                                                   @Param("limit") Integer limit);

    /**
     * 按关键词（产品名 / 物料编码 / 条形码）搜索产品
     */
    @Select("""
            SELECT p.id            AS id,
                   p.name          AS name,
                   p.material_code AS materialCode,
                   p.sale_price    AS salePrice,
                   p.status        AS status
              FROM erp_product p
             WHERE p.tenant_id = #{tenantId}
               AND p.deleted = b'0'
               AND (p.name LIKE CONCAT(#{keyword}, '%')
                    OR p.material_code = #{keyword}
                    OR p.material_code LIKE CONCAT(#{keyword}, '%')
                    OR p.bar_code = #{keyword}
                    OR p.bar_code LIKE CONCAT(#{keyword}, '%'))
             ORDER BY p.id DESC
             LIMIT #{limit}
            """)
    List<Map<String, Object>> selectProducts(@Param("tenantId") Long tenantId,
                                             @Param("keyword") String keyword,
                                             @Param("limit") Integer limit);

    /**
     * 按关键词搜索客商（客户 + 供应商）
     */
    @Select("""
            SELECT '客户' AS partnerType, c.id AS id, c.name AS name
              FROM erp_customer c
             WHERE c.tenant_id = #{tenantId} AND c.deleted = b'0'
               AND c.name LIKE CONCAT(#{keyword}, '%')
             UNION ALL
            SELECT '供应商' AS partnerType, s.id AS id, s.name AS name
              FROM erp_supplier s
             WHERE s.tenant_id = #{tenantId} AND s.deleted = b'0'
               AND s.name LIKE CONCAT(#{keyword}, '%')
             LIMIT #{limit}
            """)
    List<Map<String, Object>> selectPartners(@Param("tenantId") Long tenantId,
                                             @Param("keyword") String keyword,
                                             @Param("limit") Integer limit);

    /**
     * 查询产品库存（keyword 为空表示取库存量最大的几条）
     */
    @Select("""
            SELECT p.name          AS productName,
                   p.material_code AS materialCode,
                   w.name          AS warehouseName,
                   s.`count`       AS count
              FROM erp_stock s
              JOIN erp_product p ON p.id = s.product_id AND p.deleted = b'0'
              JOIN erp_warehouse w ON w.id = s.warehouse_id AND w.deleted = b'0'
             WHERE s.tenant_id = #{tenantId}
               AND s.deleted = b'0'
               AND (#{keyword} = ''
                    OR p.name LIKE CONCAT(#{keyword}, '%')
                    OR p.material_code = #{keyword}
                    OR p.material_code LIKE CONCAT(#{keyword}, '%'))
             ORDER BY s.`count` DESC
             LIMIT #{limit}
            """)
    List<Map<String, Object>> selectStocks(@Param("tenantId") Long tenantId,
                                           @Param("keyword") String keyword,
                                           @Param("limit") Integer limit);

    /**
     * 待审批（审批中，状态 10）单据数量：销售订单 + 采购订单 + 采购申请 + 询价单
     */
    @Select("""
            SELECT
              (SELECT COUNT(*) FROM erp_sale_order       WHERE tenant_id = #{tenantId} AND deleted = b'0' AND status = 10)
            + (SELECT COUNT(*) FROM erp_purchase_order   WHERE tenant_id = #{tenantId} AND deleted = b'0' AND status = 10)
            + (SELECT COUNT(*) FROM erp_purchase_request WHERE tenant_id = #{tenantId} AND deleted = b'0' AND status = 10)
            + (SELECT COUNT(*) FROM erp_inquiry          WHERE tenant_id = #{tenantId} AND deleted = b'0' AND status = 10)
            """)
    Long countPendingApprovals(@Param("tenantId") Long tenantId);

}
