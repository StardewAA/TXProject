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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * MES 生产报工录入工具（Function Calling 写操作）
 *
 * 用户给出任务编号与报工数量等字段后，直接写入 MES 真实数据库（mes_pro_feedback 表）。
 * 必填字段缺失时返回缺失清单（由模型转述给用户补充），不做任何猜测补全。
 *
 * 安全约定与工单创建工具一致：租户取自登录上下文；任务仅在本租户内解析；
 * 创建后回读真实落库值；失败返回原因。
 *
 * @author 天信管业
 */
@Slf4j
@Component("mes_feedback_create")
public class MesFeedbackCreateToolFunction
        implements Function<MesFeedbackCreateToolFunction.Request, MesFeedbackCreateToolFunction.Response> {

    private final JdbcTemplate jdbcTemplate;

    public MesFeedbackCreateToolFunction(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Data
    @JsonClassDescription("MES 生产报工录入工具：把用户提供的报工字段直接录入 MES 系统真实报工记录（写入数据库）")
    public static class Request {

        @JsonProperty(value = "taskCode")
        @JsonPropertyDescription("[必填] 生产任务编号，用户给出的原文，例如：TASK20260315000001。系统在本租户内解析任务")
        private String taskCode;

        @JsonProperty(value = "feedbackQuantity")
        @JsonPropertyDescription("[必填] 报工数量（本次完成总数量），正数")
        private Double feedbackQuantity;

        @JsonProperty(value = "qualifiedQuantity")
        @JsonPropertyDescription("[必填] 合格数量，正数且不大于报工数量")
        private Double qualifiedQuantity;

        @JsonProperty(value = "unqualifiedQuantity")
        @JsonPropertyDescription("[可选] 不合格数量，不填时自动按 报工数量-合格数量 计算")
        private Double unqualifiedQuantity;

        @JsonProperty(value = "feedbackTime")
        @JsonPropertyDescription("[可选] 报工时间，格式 yyyy-MM-dd HH:mm:ss，不填默认当前时间")
        private String feedbackTime;

        @JsonProperty(value = "lotNumber")
        @JsonPropertyDescription("[可选] 批次号")
        private String lotNumber;

        @JsonProperty(value = "remark")
        @JsonPropertyDescription("[可选] 备注")
        private String remark;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonClassDescription("MES 生产报工录入结果：feedback 为录入成功后回读的真实字段")
    public static class Response {

        private Boolean success;
        private String message;
        private Map<String, Object> feedback;

        public static Response ok(Map<String, Object> feedback) {
            return new Response(true, "报工录入成功（状态：草稿，待审批）", feedback);
        }

        public static Response error(String message) {
            return new Response(false, message, null);
        }
    }

    @Override
    public Response apply(Request request) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            return Response.error("无法识别当前用户所属租户，拒绝录入");
        }
        String taskCode = StrUtil.trimToNull(request.getTaskCode());
        Double feedbackQuantity = request.getFeedbackQuantity();
        Double qualifiedQuantity = request.getQualifiedQuantity();
        // 必填字段校验：缺失时逐条列出，等待用户补充，不猜测
        java.util.List<String> missing = new java.util.ArrayList<>();
        if (taskCode == null) {
            missing.add("生产任务编号");
        }
        if (feedbackQuantity == null || feedbackQuantity <= 0) {
            missing.add("报工数量（正数）");
        }
        if (qualifiedQuantity == null || qualifiedQuantity < 0) {
            missing.add("合格数量");
        }
        if (!missing.isEmpty()) {
            return Response.error("缺少必填字段：" + String.join("、", missing) + "，请补充后再录入");
        }
        if (qualifiedQuantity > feedbackQuantity) {
            return Response.error("合格数量（" + qualifiedQuantity + "）不能大于报工数量（" + feedbackQuantity + "）");
        }
        Double unqualified = request.getUnqualifiedQuantity() != null
                ? request.getUnqualifiedQuantity()
                : Math.max(feedbackQuantity - qualifiedQuantity, 0);
        LocalDateTime feedbackTime = LocalDateTime.now();
        if (StrUtil.trimToNull(request.getFeedbackTime()) != null) {
            try {
                feedbackTime = LocalDateTime.parse(StrUtil.trimToNull(request.getFeedbackTime()),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (Exception e) {
                return Response.error("报工时间格式不正确（应为 yyyy-MM-dd HH:mm:ss），收到的是「"
                        + request.getFeedbackTime() + "」");
            }
        }
        final LocalDateTime feedbackTimeFinal = feedbackTime;
        final Double feedbackQuantityFinal = feedbackQuantity;
        final Double qualifiedQuantityFinal = qualifiedQuantity;
        final Double unqualifiedFinal = unqualified;
        final String taskCodeFinal = taskCode;
        log.info("[apply][MES 报工录入 收到请求 tenantId({}) task({}) feedback({}) qualified({})]",
                tenantId, taskCode, feedbackQuantity, qualifiedQuantity);
        try {
            Map<String, Object> created = TenantUtils.execute(tenantId, () -> doCreate(tenantId, taskCodeFinal,
                    feedbackQuantityFinal, qualifiedQuantityFinal, unqualifiedFinal, feedbackTimeFinal,
                    StrUtil.trimToNull(request.getLotNumber()), StrUtil.trimToNull(request.getRemark())));
            log.info("[apply][MES 报工录入成功 tenantId({}) 编号({})]", tenantId, created.get("报工编号"));
            return Response.ok(created);
        } catch (IllegalArgumentException e) {
            log.warn("[apply][MES 报工录入失败 tenantId({}) reason({})]", tenantId, e.getMessage());
            return Response.error("报工录入失败：" + e.getMessage());
        } catch (Exception e) {
            // TenantUtils.execute 会把业务异常包成 RuntimeException：解开包装取业务原因
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            if (root instanceof IllegalArgumentException) {
                log.warn("[apply][MES 报工录入失败 tenantId({}) reason({})]", tenantId, root.getMessage());
                return Response.error("报工录入失败：" + root.getMessage());
            }
            log.error("[apply][MES 报工录入异常 tenantId({})]", tenantId, e);
            return Response.error("报工录入失败：系统异常，请稍后重试");
        }
    }

    private Map<String, Object> doCreate(Long tenantId, String taskCode, Double feedbackQuantity,
            Double qualifiedQuantity, Double unqualifiedQuantity, LocalDateTime feedbackTime,
            String lotNumber, String remark) {
        // 1. 解析任务（本租户内，编号精确）
        List<Map<String, Object>> tasks = jdbcTemplate.queryForList(
                "SELECT id, code, work_order_id, workstation_id, process_id, item_id, route_id, quantity + 0 AS quantity"
                        + " FROM mes_pro_task WHERE tenant_id = ? AND deleted = 0 AND code = ? LIMIT 1",
                tenantId, taskCode);
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("在本租户内未找到生产任务「" + taskCode + "」，请核对任务编号");
        }
        Map<String, Object> task = tasks.get(0);
        Long taskId = ((Number) task.get("id")).longValue();
        // 2. 插入报工记录（type=1 自行报工，status=0 草稿/待审批，与 MES 既有报工流程一致）
        String code = "FB" + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now());
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        Object routeId = task.get("route_id");
        jdbcTemplate.update("INSERT INTO mes_pro_feedback (code, type, channel, feedback_time,"
                        + " workstation_id, process_id, route_id, work_order_id, task_id, item_id,"
                        + " scheduled_quantity, feedback_quantity, qualified_quantity, unqualified_quantity,"
                        + " uncheck_quantity, labor_scrap_quantity, material_scrap_quantity, other_scrap_quantity,"
                        + " feedback_user_id, status, lot_number, remark, creator, updater, deleted, tenant_id)"
                        + " VALUES (?, 1, 'AI助手', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0, 0, ?, 0, ?, ?, '1', '1', b'0', ?)",
                code, feedbackTime,
                ((Number) task.get("workstation_id")).longValue(),
                ((Number) task.get("process_id")).longValue(),
                routeId != null ? ((Number) routeId).longValue() : 0L,
                ((Number) task.get("work_order_id")).longValue(),
                taskId,
                ((Number) task.get("item_id")).longValue(),
                task.get("quantity"), feedbackQuantity, qualifiedQuantity, unqualifiedQuantity,
                userId, lotNumber, remark, tenantId);
        // 3. 回读真实落库值
        return jdbcTemplate.queryForMap("SELECT fb.code AS 报工编号, t.code AS 任务编号,"
                + " wo.code AS 工单编号, IFNULL(it.name,'') AS 产品,"
                + " IFNULL(ws.name,'-') AS 工位, IFNULL(p.name,'-') AS 工序,"
                + " fb.feedback_quantity + 0 AS 报工数量, fb.qualified_quantity + 0 AS 合格数量,"
                + " fb.unqualified_quantity + 0 AS 不合格数量,"
                + " CASE fb.status WHEN 0 THEN '草稿' WHEN 1 THEN '审批中' WHEN 2 THEN '待检验' WHEN 3 THEN '已完成' WHEN 4 THEN '已取消' ELSE '未知' END AS 状态,"
                + " IFNULL(fb.lot_number,'-') AS 批次号, IFNULL(fb.remark,'-') AS 备注"
                + " FROM mes_pro_feedback fb"
                + " LEFT JOIN mes_pro_task t ON t.id = fb.task_id"
                + " LEFT JOIN mes_pro_work_order wo ON wo.id = fb.work_order_id"
                + " LEFT JOIN mes_md_item it ON it.id = fb.item_id"
                + " LEFT JOIN mes_md_workstation ws ON ws.id = fb.workstation_id"
                + " LEFT JOIN mes_pro_process p ON p.id = fb.process_id"
                + " WHERE fb.code = ? AND fb.tenant_id = ? AND fb.deleted = 0", code, tenantId);
    }

}
