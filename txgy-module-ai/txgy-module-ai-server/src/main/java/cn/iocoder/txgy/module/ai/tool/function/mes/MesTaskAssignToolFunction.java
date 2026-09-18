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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * MES 生产任务分配工具（Function Calling 写操作）
 *
 * 用途：用户确认目标班组后，把任务分配到指定班组（更新 mes_pro_task.team_id）。
 * 智能分配流程：先用 mes_team_load_query 查看负载推荐 → 用户确认班组 → 本工具执行。
 *
 * 安全约定与其他写工具一致：租户取自登录上下文；任务/班组仅在本租户内解析；
 * 已完成/已取消的任务拒绝分配；执行后回读真实落库值；失败返回原因。
 *
 * @author 天信管业
 */
@Slf4j
@Component("mes_task_assign")
public class MesTaskAssignToolFunction
        implements Function<MesTaskAssignToolFunction.Request, MesTaskAssignToolFunction.Response> {

    private final JdbcTemplate jdbcTemplate;

    public MesTaskAssignToolFunction(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Data
    @JsonClassDescription("MES 生产任务分配工具：把生产任务分配到指定班组（写入数据库）")
    public static class Request {

        @JsonProperty(value = "taskCode")
        @JsonPropertyDescription("[必填] 生产任务编号，用户给出的原文")
        private String taskCode;

        @JsonProperty(value = "teamKeyword")
        @JsonPropertyDescription("[必填] 目标班组名称或编码，必须来自 mes_team_load_query 返回的班组列表或用户明确指定的班组，例如：注塑A组")
        private String teamKeyword;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonClassDescription("MES 任务分配结果：task 为分配成功后回读的真实任务字段")
    public static class Response {

        private Boolean success;
        private String message;
        private Map<String, Object> task;

        public static Response ok(Map<String, Object> task) {
            return new Response(true, "任务分配成功", task);
        }

        public static Response error(String message) {
            return new Response(false, message, null);
        }
    }

    @Override
    public Response apply(Request request) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            return Response.error("无法识别当前用户所属租户，拒绝分配");
        }
        String taskCode = StrUtil.trimToNull(request.getTaskCode());
        String teamKeyword = StrUtil.trimToNull(request.getTeamKeyword());
        // 必填校验：缺失时逐条列出
        List<String> missing = new java.util.ArrayList<>();
        if (taskCode == null) {
            missing.add("生产任务编号");
        }
        if (teamKeyword == null) {
            missing.add("目标班组");
        }
        if (!missing.isEmpty()) {
            return Response.error("缺少必填字段：" + String.join("、", missing)
                    + "。可先用 mes_team_load_query 查看各班组负载推荐");
        }
        log.info("[apply][MES 任务分配 收到请求 tenantId({}) task({}) team({})]", tenantId, taskCode, teamKeyword);
        try {
            Map<String, Object> created = TenantUtils.execute(tenantId, () -> doAssign(tenantId, taskCode, teamKeyword));
            log.info("[apply][MES 任务分配成功 tenantId({}) task({}) team({})]", tenantId, taskCode, teamKeyword);
            return Response.ok(created);
        } catch (IllegalArgumentException e) {
            log.warn("[apply][MES 任务分配失败 tenantId({}) reason({})]", tenantId, e.getMessage());
            return Response.error("任务分配失败：" + e.getMessage());
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            if (root instanceof IllegalArgumentException) {
                log.warn("[apply][MES 任务分配失败 tenantId({}) reason({})]", tenantId, root.getMessage());
                return Response.error("任务分配失败：" + root.getMessage());
            }
            log.error("[apply][MES 任务分配异常 tenantId({})]", tenantId, e);
            return Response.error("任务分配失败：系统异常，请稍后重试");
        }
    }

    private Map<String, Object> doAssign(Long tenantId, String taskCode, String teamKeyword) {
        // 1. 解析任务（本租户，编号精确）
        List<Map<String, Object>> tasks = jdbcTemplate.queryForList(
                "SELECT t.id, t.code, t.status, IFNULL(t.name,'') AS name, IFNULL(team.name,'未分配') AS current_team"
                        + " FROM mes_pro_task t LEFT JOIN mes_cal_team team ON team.id = t.team_id"
                        + " WHERE t.tenant_id = ? AND t.deleted = 0 AND t.code = ? LIMIT 1",
                tenantId, taskCode);
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("在本租户内未找到生产任务「" + taskCode + "」，请核对任务编号");
        }
        Map<String, Object> task = tasks.get(0);
        Integer status = ((Number) task.get("status")).intValue();
        // 2. 状态校验：已完成/已取消不可分配
        if (status == 4 || status == 5) {
            throw new IllegalArgumentException("任务「" + taskCode + "」当前状态为"
                    + (status == 4 ? "已完成" : "已取消") + "，不可分配");
        }
        // 3. 解析目标班组（名称精确 → 模糊）
        Long teamId = resolveTeam(tenantId, teamKeyword);
        if (teamKeyword.equals(task.get("current_team"))) {
            throw new IllegalArgumentException("任务「" + taskCode + "」当前已分配给「" + teamKeyword + "」，无需重复分配");
        }
        // 4. 执行分配（仅更新 team_id）
        jdbcTemplate.update("UPDATE mes_pro_task SET team_id = ?, updater = '1' WHERE id = ? AND tenant_id = ?",
                teamId, task.get("id"), tenantId);
        // 5. 回读真实落库值
        return jdbcTemplate.queryForMap("SELECT t.code AS 任务编号, t.name AS 任务名称,"
                + " IFNULL(it.name,'') AS 产品, t.quantity + 0 AS 计划数量,"
                + " (t.quantity - t.produced_quantity) + 0 AS 未完工数量,"
                + " team.name AS 分配班组, team.code AS 班组编码,"
                + " CASE t.status WHEN 0 THEN '草稿' WHEN 1 THEN '已确认' WHEN 2 THEN '审批中' WHEN 3 THEN '生产中' WHEN 4 THEN '已完成' WHEN 5 THEN '已取消' ELSE '未知' END AS 状态"
                + " FROM mes_pro_task t"
                + " LEFT JOIN mes_md_item it ON it.id = t.item_id"
                + " LEFT JOIN mes_cal_team team ON team.id = t.team_id"
                + " WHERE t.id = ? AND t.tenant_id = ? AND t.deleted = 0", task.get("id"), tenantId);
    }

    /** 解析班组：名称/编码精确 → 模糊；无匹配或多匹配给出候选 */
    private Long resolveTeam(Long tenantId, String keyword) {
        List<Map<String, Object>> exact = jdbcTemplate.queryForList(
                "SELECT id, name FROM mes_cal_team WHERE tenant_id = ? AND deleted = 0 AND (name = ? OR code = ?) LIMIT 2",
                tenantId, keyword, keyword);
        if (exact.size() == 1) {
            return ((Number) exact.get(0).get("id")).longValue();
        }
        if (exact.size() > 1) {
            throw new IllegalArgumentException("班组「" + keyword + "」存在多条同名记录，请提供班组编码");
        }
        List<Map<String, Object>> fuzzy = jdbcTemplate.queryForList(
                "SELECT id, name FROM mes_cal_team WHERE tenant_id = ? AND deleted = 0 AND name LIKE ? LIMIT 6",
                tenantId, "%" + keyword + "%");
        if (fuzzy.isEmpty()) {
            throw new IllegalArgumentException("在本租户内未找到班组「" + keyword + "」，可用班组见 mes_team_load_query 查询结果");
        }
        if (fuzzy.size() > 1) {
            String candidates = fuzzy.stream().map(r -> String.valueOf(r.get("name")))
                    .reduce((a, b) -> a + "；" + b).orElse("");
            throw new IllegalArgumentException("班组「" + keyword + "」匹配到多条：" + candidates + "，请指明其中之一");
        }
        return ((Number) fuzzy.get(0).get("id")).longValue();
    }

}
