package cn.iocoder.txgy.module.ai.tool.function.mes;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * MES 生产工单创建工具（Function Calling 写操作）
 *
 * 对应系统提示词「问答逻辑」：用户提供工单字段并要求生成生产工单时，
 * 调用本工具把字段内容直接写入 MES 真实数据库（mes_pro_work_order 表），不生成任何模板/文档。
 *
 * 注意：必须实现 {@link Function} 接口（Spring AI 的 ToolCallbackResolver 只自动解析 Function Bean，
 * BiFunction Bean 会被静默跳过导致工具未绑定，模型从而口头编造创建结果）。
 *
 * 安全约定：
 * 1. 租户编号从 TenantContextHolder 登录上下文强制获取（工具在同请求线程内执行），模型无法指定 → 租户数据隔离
 * 2. 产品/客户只允许在本租户内解析（SQL 强制 tenant_id = ? AND deleted = 0）
 * 3. 创建后立即回读本租户内的新记录，把真实落库值（含系统生成的编号与「草稿」状态）如实返回给模型
 * 4. 失败时返回失败原因（如产品不存在、字段缺失），由模型原样告知用户
 *
 * @author 天信管业
 */
@Slf4j
@Component("mes_work_order_create")
public class MesWorkOrderCreateToolFunction
        implements Function<MesWorkOrderCreateToolFunction.Request, MesWorkOrderCreateToolFunction.Response> {

    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public MesWorkOrderCreateToolFunction(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 工具入参（由大模型从用户给出的工单字段中提取）
     */
    @Data
    @JsonClassDescription("MES 生产工单创建工具：把用户提供的工单字段直接创建为 MES 系统内的真实生产工单（写入数据库）")
    public static class Request {

        @JsonProperty(value = "name")
        @JsonPropertyDescription("[必填] 工单名称，取用户给出的工单名称/标题原文")
        private String name;

        @JsonProperty(value = "orderCode")
        @JsonPropertyDescription("[可选] 工单编号：用户明确指定工单编号时填写；系统校验唯一，已存在会返回失败原因；不填则自动生成")
        private String orderCode;

        @JsonProperty(value = "productKeyword")
        @JsonPropertyDescription("[必填] 产品名称或产品编码，必须使用用户给出的原文，例如：螺丝刀【蓝色，一字型】、IF2022082439。系统在本租户物料档案中精确解析为产品编号")
        private String productKeyword;

        @JsonProperty(value = "quantity")
        @JsonPropertyDescription("[必填] 计划生产数量，正数，例如：500")
        private Double quantity;

        @JsonProperty(value = "requestDate")
        @JsonPropertyDescription("[必填] 要求交期，格式 yyyy-MM-dd，例如：2026-09-25")
        private String requestDate;

        @JsonProperty(value = "clientKeyword")
        @JsonPropertyDescription("[可选] 客户名称（如：博世、比亚迪股份有限公司）。注意：来源单据编号/订单编号（如 SO-TEST-20260914）不是客户名称，禁止填入本字段；用户未明确给出客户名称时必须留空")
        private String clientKeyword;

        @JsonProperty(value = "batchCode")
        @JsonPropertyDescription("[可选] 批次号，用户给出时填写原文")
        private String batchCode;

        @JsonProperty(value = "remark")
        @JsonPropertyDescription("[可选] 备注，用户给出的补充说明原文")
        private String remark;
    }

    /**
     * 工具出参：success + 创建成功后的工单真实字段（字段名为中文，模型按 字段:值 原样转述）
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonClassDescription("MES 生产工单创建结果：workOrder 为创建成功后回读的真实工单字段")
    public static class Response {

        private Boolean success;
        private String message;
        private Map<String, Object> workOrder;

        public static Response ok(Map<String, Object> workOrder) {
            return new Response(true, "工单创建成功（状态：草稿，可在 MES 生产工单页面确认）", workOrder);
        }

        public static Response error(String message) {
            return new Response(false, message, null);
        }
    }

    @Override
    public Response apply(Request request) {
        // 1. 租户从登录上下文强制获取（模型无法指定租户，只能写本租户数据）
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            return Response.error("无法识别当前用户所属租户，拒绝创建");
        }
        // 2. 必填字段校验（缺失时直接告知原因，不猜测补全）
        String name = StrUtil.trimToNull(request.getName());
        String productKeyword = StrUtil.trimToNull(request.getProductKeyword());
        String requestDate = StrUtil.trimToNull(request.getRequestDate());
        Double quantity = request.getQuantity();
        if (name == null) {
            return Response.error("工单创建失败：缺少工单名称，请补充后重试");
        }
        if (productKeyword == null) {
            return Response.error("工单创建失败：缺少产品名称或编码，请补充后重试");
        }
        if (quantity == null || quantity <= 0) {
            return Response.error("工单创建失败：缺少有效的计划生产数量（须为正数）");
        }
        if (requestDate == null) {
            return Response.error("工单创建失败：缺少要求交期（格式 yyyy-MM-dd），请补充后重试");
        }
        LocalDateTime requestDateTime;
        try {
            requestDateTime = requestDate.length() > 10
                    ? LocalDateTime.parse(requestDate, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    : LocalDate.parse(requestDate, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay();
        } catch (Exception e) {
            return Response.error("工单创建失败：要求交期格式不正确（应为 yyyy-MM-dd），收到的是「" + requestDate + "」");
        }
        log.info("[apply][MES 工单创建 收到请求 tenantId({}) name({}) product({}) quantity({}) requestDate({})]",
                tenantId, name, productKeyword, quantity, requestDate);
        // 3. 在租户上下文中执行创建（解析产品/客户 → 插入工单 → 回读真实落库值）
        try {
            final String nameF = name;
            final String productF = productKeyword;
            final Double quantityF = quantity;
            final LocalDateTime requestDateF = requestDateTime;
            final String clientF = StrUtil.trimToNull(request.getClientKeyword());
            final String batchF = StrUtil.trimToNull(request.getBatchCode());
            final String remarkF = StrUtil.trimToNull(request.getRemark());
            final String orderCodeF = StrUtil.trimToNull(request.getOrderCode());
            Map<String, Object> created = TenantUtils.execute(tenantId,
                    () -> doCreate(tenantId, nameF, orderCodeF, productF, quantityF, requestDateF,
                            clientF, batchF, remarkF));
            log.info("[apply][MES 工单创建成功 tenantId({}) 编号({})]", tenantId, created.get("工单编号"));
            return Response.ok(created);
        } catch (IllegalArgumentException e) {
            // 业务性失败（产品/客户解析失败等）：原因原样返回给模型转述
            log.warn("[apply][MES 工单创建失败 tenantId({}) reason({})]", tenantId, e.getMessage());
            return Response.error("工单创建失败：" + e.getMessage());
        } catch (Exception e) {
            // TenantUtils.execute 会把业务异常包成 RuntimeException：解开包装取业务原因
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            if (root instanceof IllegalArgumentException) {
                log.warn("[apply][MES 工单创建失败 tenantId({}) reason({})]", tenantId, root.getMessage());
                return Response.error("工单创建失败：" + root.getMessage());
            }
            log.error("[apply][MES 工单创建异常 tenantId({})]", tenantId, e);
            return Response.error("工单创建失败：系统异常，请稍后重试");
        }
    }

    /**
     * 租户上下文内执行：解析产品/客户 → 插入 → 回读
     */
    private Map<String, Object> doCreate(Long tenantId, String name, String orderCodeRaw, String productKeyword,
            Double quantity, LocalDateTime requestDateTime, String clientKeyword, String batchCode, String remark) {
        // 0. 重复校验最前置：用户拿已存在工单的字段来"添加"时，直接告知已存在，
        //    避免在产品/客户解析上纠缠出无关错误
        String orderCode = StrUtil.trimToNull(orderCodeRaw);
        if (orderCode != null) {
            List<Map<String, Object>> dup = jdbcTemplate.queryForList(
                    "SELECT id, code, name FROM mes_pro_work_order WHERE code = ? AND tenant_id = ? AND deleted = 0 LIMIT 1",
                    orderCode, tenantId);
            if (!dup.isEmpty()) {
                throw new IllegalArgumentException("工单编号「" + orderCode + "」在系统中已存在（名称："
                        + dup.get(0).get("name") + "），请勿重复添加；如需查询可直接说\"查询工单 " + orderCode + "\"");
            }
        }
        // 1. 解析产品（本租户物料档案内：编码精确 → 名称精确 → 名称模糊，避免解析到错误产品）
        Long productId = resolveSingleton(tenantId, "mes_md_item", productKeyword,
                "产品", List.of("code", "name"));
        // 2. 解析客户（可选）
        Long clientId = null;
        if (clientKeyword != null) {
            clientId = resolveSingleton(tenantId, "mes_md_client", clientKeyword,
                    "客户", List.of("name"));
        }
        // 3. 组装并插入（状态=草稿 与 MES 既有新增接口一致）
        // 工单编号：用户指定则沿用（重复校验已在步骤 0 完成）；未指定则按 MO+时间戳 自动生成
        String code = orderCode != null ? orderCode
                : "MO" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now());
        Integer orderSourceType = clientId != null ? 1 : 2; // 1=客户订单 2=库存备货
        jdbcTemplate.update("INSERT INTO mes_pro_work_order (code, name, type, order_source_type,"
                        + " product_id, quantity, quantity_produced, quantity_changed, quantity_scheduled,"
                        + " client_id, batch_code, request_date, parent_id, status, remark,"
                        + " creator, updater, deleted, tenant_id)"
                        + " VALUES (?, ?, 1, ?, ?, ?, 0, 0, 0, ?, ?, ?, 0, 0, ?, '1', '1', b'0', ?)",
                code, name, orderSourceType, productId, quantity, clientId, batchCode,
                requestDateTime, remark, tenantId);
        // 4. 回读真实落库值（含系统生成编号、状态），供模型按 字段:值 如实转述
        return jdbcTemplate.queryForMap("SELECT wo.id AS 工单ID, wo.code AS 工单编号, wo.name AS 工单名称,"
                + " IFNULL(it.name,'') AS 产品, IFNULL(it.code,'') AS 产品编码,"
                + " IFNULL(cl.name,'-') AS 客户, wo.quantity + 0 AS 计划数量,"
                + " DATE(wo.request_date) AS 要求交期, IFNULL(wo.batch_code,'-') AS 批次号,"
                + " CASE wo.order_source_type WHEN 1 THEN '客户订单' WHEN 2 THEN '库存备货' ELSE '未知' END AS 来源类型,"
                + " CASE wo.status WHEN 0 THEN '草稿' WHEN 1 THEN '已确认' WHEN 2 THEN '已完成' WHEN 3 THEN '已取消' ELSE '未知' END AS 状态,"
                + " IFNULL(wo.remark,'-') AS 备注"
                + " FROM mes_pro_work_order wo"
                + " LEFT JOIN mes_md_item it ON it.id = wo.product_id"
                + " LEFT JOIN mes_md_client cl ON cl.id = wo.client_id"
                + " WHERE wo.code = ? AND wo.tenant_id = ? AND wo.deleted = 0", code, tenantId);
    }

    /**
     * 在本租户档案表内把关键词解析为唯一记录编号：
     * 编码精确 → 名称精确 → 名称模糊；无匹配或多匹配都抛出带候选信息的原因，由模型告知用户
     */
    private Long resolveSingleton(Long tenantId, String table, String keyword, String label, List<String> nameCols) {
        // 1) 编码/名称精确匹配
        List<Map<String, Object>> exact = jdbcTemplate.queryForList(
                "SELECT id, code, name FROM " + table
                        + " WHERE tenant_id = ? AND deleted = 0 AND (code = ? OR name = ?) LIMIT 2",
                tenantId, keyword, keyword);
        if (exact.size() == 1) {
            return ((Number) exact.get(0).get("id")).longValue();
        }
        if (exact.size() > 1) {
            throw new IllegalArgumentException(label + "「" + keyword + "」在本租户存在多条同名记录，请提供唯一的编码");
        }
        // 2) 名称模糊匹配兜底
        List<Map<String, Object>> fuzzy = jdbcTemplate.queryForList(
                "SELECT id, code, name FROM " + table
                        + " WHERE tenant_id = ? AND deleted = 0 AND name LIKE ? LIMIT 6",
                tenantId, "%" + keyword + "%");
        if (fuzzy.isEmpty()) {
            throw new IllegalArgumentException("在本租户档案中未找到" + label + "「" + keyword + "」，请核对名称或编码");
        }
        if (fuzzy.size() > 1) {
            String candidates = fuzzy.stream()
                    .map(r -> r.get("code") + " " + r.get("name"))
                    .reduce((a, b) -> a + "；" + b).orElse("");
            throw new IllegalArgumentException(label + "「" + keyword + "」匹配到多条记录：" + candidates + "，请指明其中之一");
        }
        return ((Number) fuzzy.get(0).get("id")).longValue();
    }

}
