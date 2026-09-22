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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * MES 班组负载查询工具（智能分配推荐，只读）
 *
 * 用途：用户要求"智能分配/派工任务"时，先调用本工具查看各班组的当前负载
 * （成员数、在产任务数、未完工总量），负载最低的班组标记为「推荐」。
 * 推荐结果经用户确认后，再调用 mes_task_assign 工具执行分配。
 *
 * 推荐规则：未完工总量升序（负载最低优先）；同量时在产任务数少者优先。
 *
 * @author 天信管业
 */
@Slf4j
@Component("mes_team_load_query")
public class MesTeamLoadQueryToolFunction
        implements Function<MesTeamLoadQueryToolFunction.Request, MesTeamLoadQueryToolFunction.Response> {

    private final JdbcTemplate jdbcTemplate;

    public MesTeamLoadQueryToolFunction(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Data
    @JsonClassDescription("MES 班组负载查询工具：查看各班组当前任务负载并给出智能分配推荐排序")
    public static class Request {

        @JsonProperty(value = "taskCode")
        @JsonPropertyDescription("[可选] 要分配的生产任务编号。给出时同时返回该任务的详情（产品/数量/当前班组），便于对比推荐")
        private String taskCode;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonClassDescription("MES 班组负载查询结果：list 为各班组负载（按未完工量升序，负载最低的班组 recommended=是）")
    public static class Response {

        private Boolean success;
        private String message;
        private Map<String, Object> task;
        private List<Map<String, Object>> list;

        public static Response ok(Map<String, Object> task, List<Map<String, Object>> list) {
            return new Response(true, "班组负载查询成功（按未完工量升序排列，recommended=是 为推荐班组）", task, list);
        }

        public static Response error(String message) {
            return new Response(false, message, null, null);
        }
    }

    @Override
    public Response apply(Request request) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            return Response.error("无法识别当前用户所属租户，拒绝查询");
        }
        String taskCode = StrUtil.trimToNull(request.getTaskCode());
        log.info("[apply][MES 班组负载查询 tenantId({}) task({})]", tenantId, taskCode);
        try {
            return TenantUtils.execute(tenantId, () -> doQuery(tenantId, taskCode));
        } catch (IllegalArgumentException e) {
            return Response.error("班组负载查询失败：" + e.getMessage());
        } catch (Exception e) {
            log.error("[apply][MES 班组负载查询异常 tenantId({})]", tenantId, e);
            return Response.error("班组负载查询失败：系统异常，请稍后重试");
        }
    }

    private Response doQuery(Long tenantId, String taskCode) {
        // 1. 可选：解析任务（编号精确）
        Map<String, Object> task = null;
        if (taskCode != null) {
            List<Map<String, Object>> tasks = jdbcTemplate.queryForList(
                    "SELECT t.id, t.code AS 任务编号, t.name AS 任务名称,"
                            + " IFNULL(it.name,'') AS 产品, t.quantity + 0 AS 计划数量,"
                            + " (t.quantity - t.produced_quantity) + 0 AS 未完工数量,"
                            + " IFNULL(team.name,'未分配') AS 当前班组"
                            + " FROM mes_pro_task t"
                            + " LEFT JOIN mes_md_item it ON it.id = t.item_id"
                            + " LEFT JOIN mes_cal_team team ON team.id = t.team_id"
                            + " WHERE t.tenant_id = ? AND t.deleted = 0 AND t.code = ? LIMIT 1",
                    tenantId, taskCode);
            if (tasks.isEmpty()) {
                throw new IllegalArgumentException("在本租户内未找到生产任务「" + taskCode + "」，请核对任务编号");
            }
            task = tasks.get(0);
        }
        // 2. 各班组负载：成员数 + 在产任务数(status 0-3) + 未完工总量
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT team.id, team.name AS 班组,"
                        + " (SELECT COUNT(*) FROM mes_cal_team_member m WHERE m.team_id = team.id AND m.deleted = 0) AS 成员数,"
                        + " (SELECT COUNT(*) FROM mes_pro_task t WHERE t.team_id = team.id AND t.tenant_id = team.tenant_id"
                        + "   AND t.deleted = 0 AND t.status IN (0,1,2,3)) AS 在产任务数,"
                        + " (SELECT IFNULL(SUM(t.quantity - t.produced_quantity),0) FROM mes_pro_task t"
                        + "   WHERE t.team_id = team.id AND t.tenant_id = team.tenant_id"
                        + "   AND t.deleted = 0 AND t.status IN (0,1,2,3)) AS 未完工数量"
                        + " FROM mes_cal_team team"
                        + " WHERE team.tenant_id = ? AND team.deleted = 0"
                        + " ORDER BY 未完工数量 ASC, 在产任务数 ASC",
                tenantId);
        // 3. 标记推荐（负载最低的第一条）
        List<Map<String, Object>> result = new ArrayList<>();
        boolean first = true;
        for (Map<String, Object> row : list) {
            Map<String, Object> r = new LinkedHashMap<>(row);
            r.remove("id");
            r.put("推荐", first ? "是" : "否");
            first = false;
            result.add(r);
        }
        if (result.isEmpty()) {
            return Response.error("本租户尚未维护班组，请先在 MES 排班管理中新增班组");
        }
        return Response.ok(task, result);
    }

}
