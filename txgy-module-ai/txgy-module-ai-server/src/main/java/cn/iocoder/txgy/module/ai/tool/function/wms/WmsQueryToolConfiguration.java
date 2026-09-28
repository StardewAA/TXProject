package cn.iocoder.txgy.module.ai.tool.function.wms;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * WMS 仓储业务域查询工具装配（6 个业务域，与 AiWmsBizTypeEnum 的 toolName 一一对应）
 *
 * <p>权限链路：ai_chat_role.permission → AiPermissionGuard.filterAllowedTools（按授权业务域过滤）
 * → 工具注入大模型 → 模型 Function Calling 调用 → 本类只读 SQL 查询真实数据
 *
 * <p>数据原则：所有记录来自 WMS 数据库实时查询，状态码在 SQL 中翻译为中文，模型只做整理转述。
 *
 * @author 天信管业
 */
@Configuration
public class WmsQueryToolConfiguration {

    /**
     * 多列关键词过滤 SQL 片段（OR 语义）
     *
     * <p>占位符顺序为每列一组 [keyword, like(keyword)]，与各 doQuery 的 args 绑定顺序保持一致。
     */
    private static String keywordOrFilter(String... columns) {
        StringBuilder sb = new StringBuilder(" AND ((");
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) {
                sb.append(" OR ");
            }
            sb.append("(? IS NULL OR ").append(columns[i]).append(" LIKE ?)");
        }
        return sb.append("))").toString();
    }

    /**
     * 追加 keywordOrFilter 所需的占位符参数（每列两个：keyword、like(keyword)）
     */
    private static void addKeywordArgs(List<Object> args, int columnCount, String keyword) {
        for (int i = 0; i < columnCount; i++) {
            args.add(keyword);
            args.add(keyword != null ? "%" + keyword + "%" : null);
        }
    }

    // ==================== 库存域 ====================

    /**
     * 库存余额查询：按仓库 + SKU 维度统计在库数量
     */
    @Bean("wms_stock_query")
    public WmsBaseQueryToolFunction stockQueryTool(JdbcTemplate jdbcTemplate) {
        return new WmsBaseQueryToolFunction(jdbcTemplate, "库存余额") {
            @Override
            protected List<Map<String, Object>> doQuery(Long tenantId, String keyword, String status, int limit) {
                List<Object> args = new ArrayList<>();
                args.add(tenantId);
                addKeywordArgs(args, 4, keyword);
                args.add(limit);
                String sql = "SELECT wh.name AS 仓库, wh.code AS 仓库编号,"                        + " IFNULL(it.name,'') AS 商品, IFNULL(it.code,'') AS 商品编号,"
                        + " IFNULL(sk.name,'') AS 规格, IFNULL(sk.code,'') AS 规格编号,"
                        + " iv.quantity + 0 AS 在库数量, IFNULL(it.unit,'') AS 单位"
                        + " FROM wms_inventory iv"
                        + " LEFT JOIN wms_warehouse wh ON wh.id = iv.warehouse_id"
                        + " LEFT JOIN wms_item_sku sk ON sk.id = iv.sku_id"
                        + " LEFT JOIN wms_item it ON it.id = sk.item_id"
                        + " WHERE iv.tenant_id = ? AND iv.deleted = 0 AND iv.quantity > 0"
                        + keywordOrFilter("wh.name", "it.name", "it.code", "sk.name")
                        + " ORDER BY iv.quantity DESC LIMIT ?";
                return jdbcTemplate.queryForList(sql, args.toArray());
            }
        };
    }

    /**
     * 低库存预警查询：低于指定数量的在库记录，阈值从 status 参数传入（纯数字）
     */
    @Bean("wms_stock_alert_query")
    public WmsBaseQueryToolFunction stockAlertQueryTool(JdbcTemplate jdbcTemplate) {
        return new WmsBaseQueryToolFunction(jdbcTemplate, "库存预警") {
            @Override
            protected List<Map<String, Object>> doQuery(Long tenantId, String keyword, String status, int limit) {
                int threshold = 20;
                if (status != null) {
                    try {
                        threshold = Integer.parseInt(status.trim());
                    } catch (NumberFormatException ignored) {
                        // 非数字则用默认阈值
                    }
                }
                String sql = "SELECT wh.name AS 仓库, IFNULL(it.name,'') AS 商品, IFNULL(it.code,'') AS 商品编号,"
                        + " IFNULL(sk.name,'') AS 规格, iv.quantity + 0 AS 在库数量, IFNULL(it.unit,'') AS 单位,"
                        + " ? AS 预警阈值"
                        + " FROM wms_inventory iv"
                        + " LEFT JOIN wms_warehouse wh ON wh.id = iv.warehouse_id"
                        + " LEFT JOIN wms_item_sku sk ON sk.id = iv.sku_id"
                        + " LEFT JOIN wms_item it ON it.id = sk.item_id"
                        + " WHERE iv.tenant_id = ? AND iv.deleted = 0 AND iv.quantity <= ?"
                        + keywordOrFilter("wh.name", "it.name", "it.code")
                        + " ORDER BY iv.quantity ASC LIMIT ?";
                // 注意：SELECT 中的 ? 在 WHERE 之前，参数顺序需与 SQL 占位符一致
                List<Object> ordered = new ArrayList<>();
                ordered.add(threshold);   // SELECT 的 ? AS 预警阈值
                ordered.add(tenantId);    // WHERE iv.tenant_id = ?
                for (int i = 0; i < 3; i++) {
                    ordered.add(keyword);
                    ordered.add(keyword != null ? "%" + keyword + "%" : null);
                }
                ordered.add(threshold);   // AND iv.quantity <= ?
                ordered.add(limit);
                return jdbcTemplate.queryForList(sql, ordered.toArray());
            }
        };
    }

    // ==================== 主数据域 ====================

    /**
     * 商品档案查询
     */
    @Bean("wms_item_query")
    public WmsBaseQueryToolFunction itemQueryTool(JdbcTemplate jdbcTemplate) {
        return new WmsBaseQueryToolFunction(jdbcTemplate, "商品档案") {
            @Override
            protected List<Map<String, Object>> doQuery(Long tenantId, String keyword, String status, int limit) {
                List<Object> args = new ArrayList<>();
                args.add(tenantId);
                addKeywordArgs(args, 3, keyword);
                args.add(limit);
                String sql = "SELECT it.code AS 商品编号, it.name AS 商品名称, IFNULL(it.unit,'') AS 单位,"
                        + " IFNULL(c.name,'') AS 分类, IFNULL(b.name,'') AS 品牌,"
                        + " CASE WHEN it.batch_manage = b'1' THEN '是' ELSE '否' END AS 批次管理,"
                        + " CASE WHEN it.shelf_life_manage = b'1' THEN '是' ELSE '否' END AS 保质期管理,"
                        + " IFNULL(it.shelf_life_months,0) AS 保质期月数,"
                        + " CASE WHEN it.dangerous_goods = b'1' THEN '危险品' ELSE '普通商品' END AS 危险品标识,"
                        + " IFNULL(it.package_spec,'') AS 包装规格, IFNULL(it.storage_condition,'') AS 存储条件,"
                        + " CASE it.status WHEN 0 THEN '启用' WHEN 1 THEN '停用' ELSE '未知' END AS 状态"
                        + " FROM wms_item it"
                        + " LEFT JOIN wms_item_category c ON c.id = it.category_id"
                        + " LEFT JOIN wms_item_brand b ON b.id = it.brand_id"
                        + " WHERE it.tenant_id = ? AND it.deleted = 0"
                        + keywordOrFilter("it.code", "it.name", "c.name")
                        + " ORDER BY it.id DESC LIMIT ?";
                return jdbcTemplate.queryForList(sql, args.toArray());
            }
        };
    }

    /**
     * 仓库查询
     */
    @Bean("wms_warehouse_query")
    public WmsBaseQueryToolFunction warehouseQueryTool(JdbcTemplate jdbcTemplate) {
        return new WmsBaseQueryToolFunction(jdbcTemplate, "仓库信息") {
            @Override
            protected List<Map<String, Object>> doQuery(Long tenantId, String keyword, String status, int limit) {
                List<Object> args = new ArrayList<>();
                args.add(tenantId);
                addKeywordArgs(args, 4, keyword);
                args.add(limit);
                String sql = "SELECT wh.code AS 仓库编号, wh.name AS 仓库名称,"
                        + " IFNULL(wh.city,'') AS 所在城市, IFNULL(wh.organization_name,'') AS 所属组织,"
                        + " IFNULL(wh.principal,'') AS 负责人, IFNULL(wh.principal_phone,'') AS 负责人电话,"
                        + " IFNULL(wh.address,'') AS 地址,"
                        + " CASE wh.status WHEN 0 THEN '启用' WHEN 1 THEN '停用' ELSE '未知' END AS 状态,"
                        + " (SELECT IFNULL(SUM(iv.quantity),0) FROM wms_inventory iv WHERE iv.warehouse_id = wh.id AND iv.deleted = 0) AS 在库合计"
                        + " FROM wms_warehouse wh"
                        + " WHERE wh.tenant_id = ? AND wh.deleted = 0"
                        + keywordOrFilter("wh.code", "wh.name", "wh.city", "wh.organization_name")
                        + " ORDER BY wh.id ASC LIMIT ?";
                return jdbcTemplate.queryForList(sql, args.toArray());
            }
        };
    }

    /**
     * 往来企业查询（供应商 / 客户）
     */
    @Bean("wms_merchant_query")
    public WmsBaseQueryToolFunction merchantQueryTool(JdbcTemplate jdbcTemplate) {
        return new WmsBaseQueryToolFunction(jdbcTemplate, "往来企业") {
            @Override
            protected List<Map<String, Object>> doQuery(Long tenantId, String keyword, String status, int limit) {
                List<Object> args = new ArrayList<>();
                args.add(tenantId);
                addKeywordArgs(args, 3, keyword);
                args.add(limit);
                String sql = "SELECT m.code AS 企业编号, IFNULL(m.external_code,'') AS 对接编号, m.name AS 企业名称,"
                        + " CASE m.type WHEN 1 THEN '供应商' WHEN 2 THEN '客户' ELSE '未知' END AS 类型,"
                        + " IFNULL(m.level,'') AS 级别, IFNULL(m.contact,'') AS 联系人,"
                        + " IFNULL(m.mobile,'') AS 手机, IFNULL(m.telephone,'') AS 电话,"
                        + " IFNULL(m.address,'') AS 地址,"
                        + " CASE m.status WHEN 0 THEN '启用' WHEN 1 THEN '停用' ELSE '未知' END AS 状态"
                        + " FROM wms_merchant m"
                        + " WHERE m.tenant_id = ? AND m.deleted = 0"
                        + keywordOrFilter("m.code", "m.name", "m.external_code")
                        + " ORDER BY m.id ASC LIMIT ?";
                return jdbcTemplate.queryForList(sql, args.toArray());
            }
        };
    }

    /**
     * 出入库单据查询（入库单 / 出库单统一查询，含审批状态）
     */
    @Bean("wms_order_query")
    public WmsBaseQueryToolFunction orderQueryTool(JdbcTemplate jdbcTemplate) {
        return new WmsBaseQueryToolFunction(jdbcTemplate, "出入库单据") {
            @Override
            protected List<Map<String, Object>> doQuery(Long tenantId, String keyword, String status, int limit) {
                List<Object> args = new ArrayList<>();
                args.add(tenantId);
                addKeywordArgs(args, 3, keyword);
                args.add(limit);
                String sql = "SELECT '入库单' AS 单据类型, r.no AS 单据编号,"
                        + " DATE(r.order_time) AS 单据日期, IFNULL(wh.name,'') AS 仓库,"
                        + " IFNULL(m.name,'') AS 往来企业, r.total_quantity + 0 AS 总数量,"
                        + " CASE r.status WHEN 0 THEN '草稿' WHEN 1 THEN '待审批' WHEN 2 THEN '已审批'"
                        + "   WHEN 3 THEN '已完成' WHEN 4 THEN '已作废' ELSE '未知' END AS 状态,"
                        + " CASE r.approval_status WHEN 0 THEN '待审批' WHEN 1 THEN '已通过' WHEN 2 THEN '已驳回'"
                        + "   WHEN -1 THEN '未提交' ELSE '未知' END AS 审批状态"
                        + " FROM wms_receipt_order r"
                        + " LEFT JOIN wms_warehouse wh ON wh.id = r.warehouse_id"
                        + " LEFT JOIN wms_merchant m ON m.id = r.merchant_id"
                        + " WHERE r.tenant_id = ? AND r.deleted = 0"
                        + keywordOrFilter("r.no", "wh.name", "m.name")
                        + " UNION ALL "
                        + "SELECT '出库单' AS 单据类型, s.no AS 单据编号,"
                        + " DATE(s.order_time) AS 单据日期, IFNULL(wh2.name,'') AS 仓库,"
                        + " IFNULL(m2.name,'') AS 往来企业, s.total_quantity + 0 AS 总数量,"
                        + " CASE s.status WHEN 0 THEN '草稿' WHEN 1 THEN '待审批' WHEN 2 THEN '已审批'"
                        + "   WHEN 3 THEN '已完成' WHEN 4 THEN '已作废' ELSE '未知' END AS 状态,"
                        + " CASE s.approval_status WHEN 0 THEN '待审批' WHEN 1 THEN '已通过' WHEN 2 THEN '已驳回'"
                        + "   WHEN -1 THEN '未提交' ELSE '未知' END AS 审批状态"
                        + " FROM wms_shipment_order s"
                        + " LEFT JOIN wms_warehouse wh2 ON wh2.id = s.warehouse_id"
                        + " LEFT JOIN wms_merchant m2 ON m2.id = s.merchant_id"
                        + " WHERE s.tenant_id = ? AND s.deleted = 0"
                        + keywordOrFilter("s.no", "wh2.name", "m2.name")
                        + " ORDER BY 单据日期 DESC LIMIT ?";
                // UNION ALL 两段各自需要一份参数
                List<Object> ordered = new ArrayList<>();
                ordered.add(tenantId);
                for (int i = 0; i < 3; i++) {
                    ordered.add(keyword);
                    ordered.add(keyword != null ? "%" + keyword + "%" : null);
                }
                ordered.add(tenantId);
                for (int i = 0; i < 3; i++) {
                    ordered.add(keyword);
                    ordered.add(keyword != null ? "%" + keyword + "%" : null);
                }
                ordered.add(limit);
                return jdbcTemplate.queryForList(sql, ordered.toArray());
            }
        };
    }

}
