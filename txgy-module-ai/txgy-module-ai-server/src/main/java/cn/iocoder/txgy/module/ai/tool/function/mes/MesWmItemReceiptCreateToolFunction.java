package cn.iocoder.txgy.module.ai.tool.function.mes;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MES 采购入库单创建工具（Function Calling 写操作，**仅创建草稿**）
 *
 * 场景：用户说「采购入库 岩棉板 20 件 到 卷板原料库」这类话时，把描述落成 MES 的
 * 采购入库单（{@code mes_wm_item_receipt} 主单 + 行 + 明细三张表），状态置为「草稿」。
 *
 * 安全与边界（与 MES 既有写工具同一套约定）：
 * 1. 租户从登录上下文强制获取，SQL 全部带 tenant_id，模型无法跨租户写入；
 * 2. 物料/供应商/仓库只在本租户档案内解析（精确 → 忽略空格 → 模糊，多义即报错让用户澄清）；
 * 3. **不提交审批、不上架、不写库存** —— 库存变动必须走 MES 既有
 *    {@code MesWmItemReceiptService#submitItemReceipt → finishItemReceipt} 链路（含 BPM 审批与库存事务），
 *    AI 只负责把单据草稿准备好，由人工在页面核对后提交；
 * 4. 创建后立即回读本租户内的真实落库值返回给模型，模型只能按「字段：值」如实转述。
 *
 * 注意：必须实现 Function 接口（Spring AI 的 ToolCallbackResolver 只解析 Function Bean，
 * BiFunction Bean 会被静默跳过）。
 *
 * @author 天信管业
 */
@Slf4j
@Component("mes_wm_item_receipt_create")
public class MesWmItemReceiptCreateToolFunction
        extends MesMdCreateSupport<MesWmItemReceiptCreateToolFunction.Request> {

    public MesWmItemReceiptCreateToolFunction(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    /**
     * 工具入参（由大模型从用户描述中提取）
     */
    @lombok.Data
    @com.fasterxml.jackson.annotation.JsonClassDescription(
            "MES 采购入库单创建工具：把用户描述的采购入库（物料、数量、供应商、仓库）创建为 MES 真实采购入库单草稿，"
                    + "只建草稿、不提交审批、不执行入库")
    public static class Request extends BaseRequest {

        @com.fasterxml.jackson.annotation.JsonProperty(value = "itemKeyword")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription(
                "[必填] 入库物料名称或编码，必须使用用户给出的原文，例如：岩棉板、RM-001")
        private String itemKeyword;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "quantity")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[必填] 入库数量，正数，例如：20")
        private Double quantity;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "vendorKeyword")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription(
                "[可选] 供应商名称或编码，例如：华瀚建材；用户未提及时留空")
        private String vendorKeyword;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "warehouseKeyword")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription(
                "[可选] 上架仓库名称或编码，例如：卷板原料库；用户未提及时留空，可稍后在页面补")
        private String warehouseKeyword;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "lotNumber")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription(
                "[可选] 批次号/炉号，用户给出时填写原文")
        private String lotNumber;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "purchaseOrderCode")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription(
                "[可选] 采购订单号（来源单据编号），用户明确给出时填写原文")
        private String purchaseOrderCode;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription(
                "[可选] 单据名称，用户未给出时留空（系统按「采购入库-物料名」生成）")
        private String name;
    }

    @Override
    public Map<String, Object> apply(Request request) {
        Long tenantId = currentTenant();
        if (tenantId == null) {
            return error("无法识别当前用户所属租户，拒绝创建");
        }
        final String itemKeyword = trimToNull(request.getItemKeyword());
        final Double quantity = request.getQuantity();
        if (itemKeyword == null) {
            return error("采购入库单创建失败：缺少物料名称或编码，请补充后重试");
        }
        if (quantity == null || quantity <= 0) {
            return error("采购入库单创建失败：缺少有效的入库数量（须为正数），请补充后重试");
        }
        final String code = generateCode("IR", trimToNull(request.getCode()));
        final String name = trimToNull(request.getName());
        final String vendorKeyword = trimToNull(request.getVendorKeyword());
        final String warehouseKeyword = trimToNull(request.getWarehouseKeyword());
        final String lotNumber = trimToNull(request.getLotNumber());
        final String purchaseOrderCode = trimToNull(request.getPurchaseOrderCode());
        final String remark = trimToNull(request.getRemark());
        log.info("[apply][MES 采购入库单创建 收到请求 tenantId({}) code({}) item({}) quantity({}) vendor({}) warehouse({})]",
                tenantId, code, itemKeyword, quantity, vendorKeyword, warehouseKeyword);
        return executeInTenant(tenantId, "采购入库单创建",
                () -> doCreate(tenantId, code, name, itemKeyword, quantity, vendorKeyword,
                        warehouseKeyword, lotNumber, purchaseOrderCode, remark));
    }

    private Map<String, Object> doCreate(Long tenantId, String code, String name, String itemKeyword, Double quantity,
                                        String vendorKeyword, String warehouseKeyword, String lotNumber,
                                        String purchaseOrderCode, String remark) {
        // 1. 单据编号唯一性（用户指定编号时最前置，避免后续解析出无关错误）
        List<Map<String, Object>> dup = jdbcTemplate.queryForList(
                "SELECT code FROM mes_wm_item_receipt WHERE code = ? AND tenant_id = ? AND deleted = 0 LIMIT 1",
                code, tenantId);
        if (!dup.isEmpty()) {
            throw new IllegalArgumentException("采购入库单编号「" + code
                    + "」在系统中已存在，请勿重复添加；如需查看可直接说\"查询采购入库单 " + code + "\"");
        }
        // 2. 主数据解析（只在本租户档案内）
        Long itemId = resolveId(tenantId, "mes_md_item", itemKeyword, "物料");
        Long vendorId = vendorKeyword != null ? resolveId(tenantId, "mes_md_vendor", vendorKeyword, "供应商") : null;
        Long warehouseId = warehouseKeyword != null
                ? resolveId(tenantId, "mes_wm_warehouse", warehouseKeyword, "仓库") : null;
        // 3. 落库：主单（草稿）→ 行 → 明细（与 MES 页面「新增」三步等价）
        String itemName = String.valueOf(jdbcTemplate.queryForMap(
                "SELECT name FROM mes_md_item WHERE id = ?", itemId).get("name"));
        String finalName = name != null ? name : "采购入库-" + itemName;
        String operator = currentOperator();
        Date today = Date.valueOf(java.time.LocalDate.now());
        jdbcTemplate.update("INSERT INTO mes_wm_item_receipt (code, name, vendor_id, purchase_order_code,"
                        + " receipt_date, status, remark, creator, updater, deleted, tenant_id)"
                        + " VALUES (?, ?, ?, ?, ?, 0, ?, ?, ?, b'0', ?)",
                code, finalName, vendorId, purchaseOrderCode, today, remark, operator, operator, tenantId);
        Long receiptId = jdbcTemplate.queryForObject(
                "SELECT id FROM mes_wm_item_receipt WHERE code = ? AND tenant_id = ? AND deleted = 0",
                Long.class, code, tenantId);
        jdbcTemplate.update("INSERT INTO mes_wm_item_receipt_line (receipt_id, item_id, received_quantity,"
                        + " lot_number, remark, creator, updater, deleted, tenant_id)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, b'0', ?)",
                receiptId, itemId, quantity, lotNumber, remark, operator, operator, tenantId);
        Long lineId = jdbcTemplate.queryForObject(
                "SELECT id FROM mes_wm_item_receipt_line WHERE receipt_id = ? AND tenant_id = ? AND deleted = 0"
                        + " ORDER BY id DESC LIMIT 1", Long.class, receiptId, tenantId);
        jdbcTemplate.update("INSERT INTO mes_wm_item_receipt_detail (line_id, receipt_id, item_id, quantity,"
                        + " warehouse_id, creator, updater, deleted, tenant_id)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, b'0', ?)",
                lineId, receiptId, itemId, quantity, warehouseId, operator, operator, tenantId);
        // 4. 回读真实落库值（含系统生成的编号与「草稿」状态）
        Map<String, Object> record = new LinkedHashMap<>(jdbcTemplate.queryForMap(
                "SELECT r.code AS 单据编号, r.name AS 单据名称, IFNULL(v.name,'-') AS 供应商,"
                        + " IFNULL(r.purchase_order_code,'-') AS 采购订单号, DATE(r.receipt_date) AS 收货日期,"
                        + " CASE r.status WHEN 0 THEN '草稿' WHEN 1 THEN 'BPM审批中' WHEN 2 THEN '待上架'"
                        + "   WHEN 3 THEN '待执行入库' WHEN 4 THEN '已完成' WHEN 5 THEN '已取消' ELSE '未知' END AS 状态"
                        + " FROM mes_wm_item_receipt r"
                        + " LEFT JOIN mes_md_vendor v ON v.id = r.vendor_id"
                        + " WHERE r.id = ? AND r.tenant_id = ?", receiptId, tenantId));
        record.put("物料", itemName);
        record.put("入库数量", quantity);
        record.put("批次号", lotNumber != null ? lotNumber : "-");
        record.put("上架仓库", warehouseId != null
                ? jdbcTemplate.queryForMap("SELECT name FROM mes_wm_warehouse WHERE id = ?", warehouseId).get("name")
                : "未指定（可在页面补）");
        record.put("备注", remark != null ? remark : "-");
        return ok("采购入库单已创建为草稿（未提交审批、未入库）。请在「MES 系统 → 仓库管理 → 入库作业 → 采购入库」"
                + "中核对物料、数量、仓库并提交审批；审批通过后再执行入库", record);
    }

}
