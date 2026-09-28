package cn.iocoder.txgy.module.ai.tool.function.mes;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MES 生产领料单创建工具（Function Calling 写操作，**仅创建草稿**）
 *
 * 场景：用户说「给工单 WO202609210004 领 岩棉板 5 件 从 卷板原料库」这类话时，落成 MES 的
 * 生产领料单（{@code mes_wm_product_issue} 主单 + 行 + 明细三张表），状态置为「草稿」。
 *
 * 边界与 {@link MesWmItemReceiptCreateToolFunction} 相同：
 * 租户隔离、主数据只在本租户解析、**不提交审批/不拣货/不写库存**（领出必须走 MES 既有
 * {@code MesWmProductIssueService#submitProductIssue → finishProductIssue} 链路），
 * 创建后回读真实落库值供模型如实转述。
 *
 * @author 天信管业
 */
@Slf4j
@Component("mes_wm_product_issue_create")
public class MesWmProductIssueCreateToolFunction
        extends MesMdCreateSupport<MesWmProductIssueCreateToolFunction.Request> {

    public MesWmProductIssueCreateToolFunction(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @lombok.Data
    @com.fasterxml.jackson.annotation.JsonClassDescription(
            "MES 生产领料单创建工具：把用户描述的生产领料（生产工单、领用物料、数量、仓库）创建为 MES 真实生产领料单草稿，"
                    + "只建草稿、不提交审批、不执行领出")
    public static class Request extends BaseRequest {

        @com.fasterxml.jackson.annotation.JsonProperty(value = "workOrderKeyword")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription(
                "[必填] 生产工单编号或名称，例如：WO202609210004；必须是工单（不是任务/项目/销售订单）")
        private String workOrderKeyword;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "itemKeyword")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription(
                "[必填] 领用物料名称或编码，必须使用用户给出的原文，例如：岩棉板、RM-001")
        private String itemKeyword;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "quantity")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[必填] 领用数量，正数，例如：5")
        private Double quantity;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "warehouseKeyword")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription(
                "[可选] 领料仓库名称或编码，例如：卷板原料库；用户未提及时留空，可稍后在页面补")
        private String warehouseKeyword;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "workstationKeyword")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription(
                "[可选] 领料工作站/工位名称，用户给出时填写原文")
        private String workstationKeyword;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "requiredTime")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription(
                "[可选] 需求时间，格式 yyyy-MM-dd（或 yyyy-MM-dd HH:mm）；用户未给出时留空，默认按当天")
        private String requiredTime;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "batchCode")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription(
                "[可选] 批次号，用户给出时填写原文")
        private String batchCode;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription(
                "[可选] 单据名称，用户未给出时留空（系统按「生产领料-工单号」生成）")
        private String name;
    }

    @Override
    public Map<String, Object> apply(Request request) {
        Long tenantId = currentTenant();
        if (tenantId == null) {
            return error("无法识别当前用户所属租户，拒绝创建");
        }
        final String workOrderKeyword = trimToNull(request.getWorkOrderKeyword());
        final String itemKeyword = trimToNull(request.getItemKeyword());
        final Double quantity = request.getQuantity();
        if (workOrderKeyword == null) {
            return error("生产领料单创建失败：缺少生产工单编号，请补充后重试");
        }
        if (itemKeyword == null) {
            return error("生产领料单创建失败：缺少领用物料名称或编码，请补充后重试");
        }
        if (quantity == null || quantity <= 0) {
            return error("生产领料单创建失败：缺少有效的领用数量（须为正数），请补充后重试");
        }
        // 需求时间：支持 yyyy-MM-dd 与 yyyy-MM-dd HH:mm，解析失败直接报错让用户按格式补充
        LocalDateTime requiredTime;
        String requiredRaw = trimToNull(request.getRequiredTime());
        if (requiredRaw == null) {
            requiredTime = LocalDateTime.now().withSecond(0).withNano(0);
        } else {
            try {
                requiredTime = requiredRaw.length() > 10
                        ? LocalDateTime.parse(requiredRaw, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        : LocalDate.parse(requiredRaw, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay();
            } catch (Exception e) {
                return error("生产领料单创建失败：需求时间格式不正确（应为 yyyy-MM-dd），收到的是「" + requiredRaw + "」");
            }
        }
        final String code = generateCode("PI", trimToNull(request.getCode()));
        final String name = trimToNull(request.getName());
        final String warehouseKeyword = trimToNull(request.getWarehouseKeyword());
        final String workstationKeyword = trimToNull(request.getWorkstationKeyword());
        final String batchCode = trimToNull(request.getBatchCode());
        final String remark = trimToNull(request.getRemark());
        final LocalDateTime requiredTimeF = requiredTime;
        log.info("[apply][MES 生产领料单创建 收到请求 tenantId({}) code({}) workOrder({}) item({}) quantity({}) warehouse({})]",
                tenantId, code, workOrderKeyword, itemKeyword, quantity, warehouseKeyword);
        return executeInTenant(tenantId, "生产领料单创建",
                () -> doCreate(tenantId, code, name, workOrderKeyword, itemKeyword, quantity,
                        warehouseKeyword, workstationKeyword, requiredTimeF, batchCode, remark));
    }

    private Map<String, Object> doCreate(Long tenantId, String code, String name, String workOrderKeyword,
                                        String itemKeyword, Double quantity, String warehouseKeyword,
                                        String workstationKeyword, LocalDateTime requiredTime, String batchCode,
                                        String remark) {
        // 1. 单据编号唯一性
        List<Map<String, Object>> dup = jdbcTemplate.queryForList(
                "SELECT code FROM mes_wm_product_issue WHERE code = ? AND tenant_id = ? AND deleted = 0 LIMIT 1",
                code, tenantId);
        if (!dup.isEmpty()) {
            throw new IllegalArgumentException("生产领料单编号「" + code
                    + "」在系统中已存在，请勿重复添加；如需查看可直接说\"查询生产领料单 " + code + "\"");
        }
        // 2. 主数据解析（只在本租户档案内）
        Long workOrderId = resolveId(tenantId, "mes_pro_work_order", workOrderKeyword, "生产工单");
        Long itemId = resolveId(tenantId, "mes_md_item", itemKeyword, "物料");
        Long warehouseId = warehouseKeyword != null
                ? resolveId(tenantId, "mes_wm_warehouse", warehouseKeyword, "仓库") : null;
        Long workstationId = workstationKeyword != null
                ? resolveId(tenantId, "mes_md_workstation", workstationKeyword, "工作站") : null;
        // 3. 落库：主单（草稿）→ 行 → 明细（与 MES 页面「新增」三步等价）
        String workOrderCode = String.valueOf(jdbcTemplate.queryForMap(
                "SELECT code FROM mes_pro_work_order WHERE id = ?", workOrderId).get("code"));
        String itemName = String.valueOf(jdbcTemplate.queryForMap(
                "SELECT name FROM mes_md_item WHERE id = ?", itemId).get("name"));
        String finalName = name != null ? name : "生产领料-" + workOrderCode;
        String operator = currentOperator();
        Date today = Date.valueOf(LocalDate.now());
        jdbcTemplate.update("INSERT INTO mes_wm_product_issue (code, name, workstation_id, work_order_id,"
                        + " issue_date, required_time, status, remark, creator, updater, deleted, tenant_id)"
                        + " VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, ?, b'0', ?)",
                code, finalName, workstationId, workOrderId, today, requiredTime, remark,
                operator, operator, tenantId);
        Long issueId = jdbcTemplate.queryForObject(
                "SELECT id FROM mes_wm_product_issue WHERE code = ? AND tenant_id = ? AND deleted = 0",
                Long.class, code, tenantId);
        jdbcTemplate.update("INSERT INTO mes_wm_product_issue_line (issue_id, item_id, quantity, remark,"
                        + " creator, updater, deleted, tenant_id)"
                        + " VALUES (?, ?, ?, ?, ?, ?, b'0', ?)",
                issueId, itemId, quantity, remark, operator, operator, tenantId);
        Long lineId = jdbcTemplate.queryForObject(
                "SELECT id FROM mes_wm_product_issue_line WHERE issue_id = ? AND tenant_id = ? AND deleted = 0"
                        + " ORDER BY id DESC LIMIT 1", Long.class, issueId, tenantId);
        jdbcTemplate.update("INSERT INTO mes_wm_product_issue_detail (issue_id, line_id, item_id, quantity,"
                        + " batch_code, warehouse_id, remark, creator, updater, deleted, tenant_id)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, b'0', ?)",
                issueId, lineId, itemId, quantity, batchCode, warehouseId, remark, operator, operator, tenantId);
        // 4. 回读真实落库值
        Map<String, Object> record = new LinkedHashMap<>(jdbcTemplate.queryForMap(
                "SELECT r.code AS 单据编号, r.name AS 单据名称, IFNULL(wo.code,'-') AS 生产工单,"
                        + " DATE(r.issue_date) AS 领料日期, DATE_FORMAT(r.required_time,'%Y-%m-%d %H:%i') AS 需求时间,"
                        + " CASE r.status WHEN 0 THEN '草稿' WHEN 1 THEN '待拣货' WHEN 2 THEN '待执行领出'"
                        + "   WHEN 3 THEN '已完成' WHEN 5 THEN '已取消' ELSE CONCAT('状态码', r.status) END AS 状态"
                        + " FROM mes_wm_product_issue r"
                        + " LEFT JOIN mes_pro_work_order wo ON wo.id = r.work_order_id"
                        + " WHERE r.id = ? AND r.tenant_id = ?", issueId, tenantId));
        record.put("领用物料", itemName);
        record.put("领用数量", quantity);
        record.put("批次号", batchCode != null ? batchCode : "-");
        record.put("领料仓库", warehouseId != null
                ? jdbcTemplate.queryForMap("SELECT name FROM mes_wm_warehouse WHERE id = ?", warehouseId).get("name")
                : "未指定（可在页面补）");
        record.put("备注", StrUtil.blankToDefault(remark, "-"));
        return ok("生产领料单已创建为草稿（未提交审批、未领出）。请在「MES 系统 → 仓库管理 → 出库作业 → 生产领料」"
                + "中核对工单、物料、数量、仓库并提交审批；审批通过后再执行拣货与领出", record);
    }

}
