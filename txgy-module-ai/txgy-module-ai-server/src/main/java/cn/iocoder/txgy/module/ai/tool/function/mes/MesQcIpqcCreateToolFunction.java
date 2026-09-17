package cn.iocoder.txgy.module.ai.tool.function.mes;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * MES 过程检验（IPQC）录入工具（Function Calling 写操作）
 *
 * 用户给出任务编号、检验类型与检验数量等字段后，直接写入 MES 真实数据库（mes_qc_ipqc 表）。
 * 必填字段缺失时返回缺失清单（由模型转述给用户补充），不做任何猜测补全。
 *
 * 安全约定与工单创建工具一致：租户取自登录上下文；任务/检验方案仅在本租户内解析；
 * 创建后回读真实落库值；失败返回原因。
 *
 * @author 天信管业
 */
@Slf4j
@Component("mes_qc_ipqc_create")
public class MesQcIpqcCreateToolFunction
        implements Function<MesQcIpqcCreateToolFunction.Request, MesQcIpqcCreateToolFunction.Response> {

    private final JdbcTemplate jdbcTemplate;

    public MesQcIpqcCreateToolFunction(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Data
    @JsonClassDescription("MES 过程检验单（IPQC）录入工具：把用户提供的检验字段直接录入 MES 系统真实检验单（写入数据库）")
    public static class Request {

        @JsonProperty(value = "taskCode")
        @JsonPropertyDescription("[必填] 生产任务编号，用户给出的原文。系统在本租户内解析任务并关联工单/产品/工位/工序")
        private String taskCode;

        @JsonProperty(value = "typeLabel")
        @JsonPropertyDescription("[必填] 检验类型（中文）：自制工序检验、首检、巡检、自检、成品检验 之一")
        private String typeLabel;

        @JsonProperty(value = "checkQuantity")
        @JsonPropertyDescription("[必填] 检验数量，正数")
        private Double checkQuantity;

        @JsonProperty(value = "qualifiedQuantity")
        @JsonPropertyDescription("[必填] 合格数量，不大于检验数量")
        private Double qualifiedQuantity;

        @JsonProperty(value = "unqualifiedQuantity")
        @JsonPropertyDescription("[可选] 不合格数量，不填时自动按 检验数量-合格数量 计算")
        private Double unqualifiedQuantity;

        @JsonProperty(value = "inspectDate")
        @JsonPropertyDescription("[可选] 检验日期，格式 yyyy-MM-dd HH:mm:ss，不填默认当前时间")
        private String inspectDate;

        @JsonProperty(value = "templateKeyword")
        @JsonPropertyDescription("[可选] 检验方案名称关键词，不填时自动匹配产品名称对应的方案")
        private String templateKeyword;

        @JsonProperty(value = "remark")
        @JsonPropertyDescription("[可选] 备注")
        private String remark;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonClassDescription("MES 过程检验单录入结果：ipqc 为录入成功后回读的真实字段")
    public static class Response {

        private Boolean success;
        private String message;
        private Map<String, Object> ipqc;

        public static Response ok(Map<String, Object> ipqc) {
            return new Response(true, "检验单录入成功（状态：待检验）", ipqc);
        }

        public static Response error(String message) {
            return new Response(false, message, null);
        }
    }

    /** 检验类型：中文 → 数据库值（与字典 mes_ipqc_type 一致） */
    private static final Map<String, Integer> TYPE_MAP = Map.of(
            "自制工序检验", 1, "首检", 2, "巡检", 3, "自检", 4, "成品检验", 5);

    @Override
    public Response apply(Request request) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            return Response.error("无法识别当前用户所属租户，拒绝录入");
        }
        String taskCode = StrUtil.trimToNull(request.getTaskCode());
        String typeLabel = StrUtil.trimToNull(request.getTypeLabel());
        Double checkQuantity = request.getCheckQuantity();
        Double qualifiedQuantity = request.getQualifiedQuantity();
        // 必填字段校验：缺失时逐条列出，等待用户补充，不猜测
        java.util.List<String> missing = new java.util.ArrayList<>();
        if (taskCode == null) {
            missing.add("生产任务编号");
        }
        if (typeLabel == null) {
            missing.add("检验类型（自制工序检验/首检/巡检/自检/成品检验）");
        }
        if (checkQuantity == null || checkQuantity <= 0) {
            missing.add("检验数量（正数）");
        }
        if (qualifiedQuantity == null || qualifiedQuantity < 0) {
            missing.add("合格数量");
        }
        if (!missing.isEmpty()) {
            return Response.error("缺少必填字段：" + String.join("、", missing) + "，请补充后再录入");
        }
        Integer type = TYPE_MAP.get(typeLabel);
        if (type == null) {
            return Response.error("检验类型「" + typeLabel + "」无效，可选：自制工序检验、首检、巡检、自检、成品检验");
        }
        if (qualifiedQuantity > checkQuantity) {
            return Response.error("合格数量（" + qualifiedQuantity + "）不能大于检验数量（" + checkQuantity + "）");
        }
        Double unqualified = request.getUnqualifiedQuantity() != null
                ? request.getUnqualifiedQuantity()
                : Math.max(checkQuantity - qualifiedQuantity, 0);
        LocalDateTime inspectDate = LocalDateTime.now();
        if (StrUtil.trimToNull(request.getInspectDate()) != null) {
            try {
                inspectDate = LocalDateTime.parse(StrUtil.trimToNull(request.getInspectDate()),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (Exception e) {
                return Response.error("检验日期格式不正确（应为 yyyy-MM-dd HH:mm:ss），收到的是「"
                        + request.getInspectDate() + "」");
            }
        }
        final String taskCodeFinal = taskCode;
        final String typeLabelFinal = typeLabel;
        final Integer typeFinal = type;
        final Double checkQuantityFinal = checkQuantity;
        final Double qualifiedQuantityFinal = qualifiedQuantity;
        final Double unqualifiedFinal = unqualified;
        final LocalDateTime inspectDateFinal = inspectDate;
        log.info("[apply][MES IPQC录入 收到请求 tenantId({}) task({}) type({}) check({}) qualified({})]",
                tenantId, taskCode, typeLabel, checkQuantity, qualifiedQuantity);
        try {
            Map<String, Object> created = TenantUtils.execute(tenantId, () -> doCreate(tenantId, taskCodeFinal,
                    typeFinal, typeLabelFinal, checkQuantityFinal, qualifiedQuantityFinal, unqualifiedFinal,
                    inspectDateFinal, StrUtil.trimToNull(request.getTemplateKeyword()),
                    StrUtil.trimToNull(request.getRemark())));
            log.info("[apply][MES IPQC录入成功 tenantId({}) 编号({})]", tenantId, created.get("检验单编号"));
            return Response.ok(created);
        } catch (IllegalArgumentException e) {
            log.warn("[apply][MES IPQC录入失败 tenantId({}) reason({})]", tenantId, e.getMessage());
            return Response.error("检验单录入失败：" + e.getMessage());
        } catch (Exception e) {
            // TenantUtils.execute 会把业务异常包成 RuntimeException：解开包装取业务原因
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            if (root instanceof IllegalArgumentException) {
                log.warn("[apply][MES IPQC录入失败 tenantId({}) reason({})]", tenantId, root.getMessage());
                return Response.error("检验单录入失败：" + root.getMessage());
            }
            log.error("[apply][MES IPQC录入异常 tenantId({})]", tenantId, e);
            return Response.error("检验单录入失败：系统异常，请稍后重试");
        }
    }

    private Map<String, Object> doCreate(Long tenantId, String taskCode, Integer type, String typeLabel,
            Double checkQuantity, Double qualifiedQuantity, Double unqualifiedQuantity,
            LocalDateTime inspectDate, String templateKeyword, String remark) {
        // 1. 解析任务（本租户内，编号精确）
        List<Map<String, Object>> tasks = jdbcTemplate.queryForList(
                "SELECT id, work_order_id, workstation_id, process_id, item_id"
                        + " FROM mes_pro_task WHERE tenant_id = ? AND deleted = 0 AND code = ? LIMIT 1",
                tenantId, taskCode);
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("在本租户内未找到生产任务「" + taskCode + "」，请核对任务编号");
        }
        Map<String, Object> task = tasks.get(0);
        Long taskId = ((Number) task.get("id")).longValue();
        Long itemId = ((Number) task.get("item_id")).longValue();
        // 2. 解析检验方案（NOT NULL）：关键词 → 产品名匹配 → 本租户第一条
        Long templateId = resolveTemplate(tenantId, itemId, templateKeyword);
        // 3. 插入检验单（status=0 待检验，与 MES 既有检验流程一致）
        String code = "IPQC" + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now());
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        String name = "AI助手-" + typeLabel + "检验单-" + taskCode;
        jdbcTemplate.update("INSERT INTO mes_qc_ipqc (code, name, type, template_id,"
                        + " work_order_id, task_id, workstation_id, process_id, item_id,"
                        + " check_quantity, qualified_quantity, unqualified_quantity,"
                        + " labor_scrap_quantity, material_scrap_quantity, other_scrap_quantity,"
                        + " inspect_date, inspector_user_id, status, remark, creator, updater, deleted, tenant_id)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0, ?, ?, 0, ?, '1', '1', b'0', ?)",
                code, name, type, templateId,
                ((Number) task.get("work_order_id")).longValue(),
                taskId,
                ((Number) task.get("workstation_id")).longValue(),
                ((Number) task.get("process_id")).longValue(),
                itemId,
                checkQuantity, qualifiedQuantity, unqualifiedQuantity,
                inspectDate, userId, remark, tenantId);
        // 4. 回读真实落库值
        return jdbcTemplate.queryForMap("SELECT q.code AS 检验单编号, q.name AS 检验单名称,"
                + " CASE q.type WHEN 1 THEN '自制工序检验' WHEN 2 THEN '首检' WHEN 3 THEN '巡检' WHEN 4 THEN '自检' WHEN 5 THEN '成品检验' ELSE '未知' END AS 检验类型,"
                + " t.code AS 任务编号, wo.code AS 工单编号, IFNULL(it.name,'') AS 产品,"
                + " q.check_quantity + 0 AS 检验数量, q.qualified_quantity + 0 AS 合格数量, q.unqualified_quantity + 0 AS 不合格数量,"
                + " CASE q.status WHEN 0 THEN '待检验' WHEN 1 THEN '检验中' WHEN 2 THEN '已检验' WHEN 4 THEN '已关闭' ELSE '未知' END AS 状态,"
                + " DATE(q.inspect_date) AS 检验日期, IFNULL(q.remark,'-') AS 备注"
                + " FROM mes_qc_ipqc q"
                + " LEFT JOIN mes_pro_task t ON t.id = q.task_id"
                + " LEFT JOIN mes_pro_work_order wo ON wo.id = q.work_order_id"
                + " LEFT JOIN mes_md_item it ON it.id = q.item_id"
                + " WHERE q.code = ? AND q.tenant_id = ? AND q.deleted = 0", code, tenantId);
    }

    /** 解析检验方案：关键词精确/模糊 → 产品名匹配 → 本租户第一条；均无则报错 */
    private Long resolveTemplate(Long tenantId, Long itemId, String templateKeyword) {
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
                "SELECT name FROM mes_md_item WHERE id = ? AND tenant_id = ? AND deleted = 0", itemId, tenantId);
        String itemName = items.isEmpty() ? "" : String.valueOf(items.get(0).get("name"));
        List<Map<String, Object>> hit;
        if (templateKeyword != null) {
            hit = jdbcTemplate.queryForList("SELECT id FROM mes_qc_template"
                            + " WHERE tenant_id = ? AND deleted = 0 AND (name = ? OR name LIKE ?) ORDER BY id LIMIT 1",
                    tenantId, templateKeyword, "%" + templateKeyword + "%");
            if (!hit.isEmpty()) {
                return ((Number) hit.get(0).get("id")).longValue();
            }
            throw new IllegalArgumentException("在本租户内未找到检验方案「" + templateKeyword + "」，请核对方案名称");
        }
        if (!itemName.isEmpty()) {
            hit = jdbcTemplate.queryForList("SELECT id FROM mes_qc_template"
                            + " WHERE tenant_id = ? AND deleted = 0 AND name LIKE ? ORDER BY id LIMIT 1",
                    tenantId, "%" + itemName + "%");
            if (!hit.isEmpty()) {
                return ((Number) hit.get(0).get("id")).longValue();
            }
        }
        hit = jdbcTemplate.queryForList("SELECT id FROM mes_qc_template"
                + " WHERE tenant_id = ? AND deleted = 0 ORDER BY id LIMIT 1", tenantId);
        if (hit.isEmpty()) {
            throw new IllegalArgumentException("本租户尚未维护检验方案，请先在 MES 质量管理-检验方案中新增");
        }
        return ((Number) hit.get(0).get("id")).longValue();
    }

}
