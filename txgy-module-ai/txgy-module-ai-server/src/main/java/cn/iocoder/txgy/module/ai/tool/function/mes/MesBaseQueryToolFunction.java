package cn.iocoder.txgy.module.ai.tool.function.mes;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.ai.util.AiUtils;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * MES 业务数据查询工具基类（Function Calling）
 *
 * 核心约定（对应需求：AI 严禁编造业务数据）：
 * 1. 租户编号从 ToolContext 登录上下文强制获取，模型无法指定 → 天然租户权限隔离
 * 2. 查询为只读 SQL，且必须带 tenant_id = ? AND deleted = 0
 * 3. 查询结果为空时返回空 list，由模型按系统提示词约束如实回复「未查询到相关数据」
 * 4. 查询异常返回固定话术「数据查询接口异常，请稍后重试」，不暴露内部细节
 *
 * @author 天信管业
 */
@Slf4j
public abstract class MesBaseQueryToolFunction
        implements BiFunction<MesBaseQueryToolFunction.Request, ToolContext, MesBaseQueryToolFunction.Response> {

    protected final JdbcTemplate jdbcTemplate;
    /**
     * 业务域中文名（例如：生产工单）
     */
    protected final String bizName;

    protected MesBaseQueryToolFunction(JdbcTemplate jdbcTemplate, String bizName) {
        this.jdbcTemplate = jdbcTemplate;
        this.bizName = bizName;
    }

    /**
     * 工具入参（由大模型按需填写，全部可选）
     */
    @Data
    @JsonClassDescription("MES 业务数据实时查询工具：从 MES 后端数据库读取真实业务数据")
    public static class Request {

        @JsonProperty(value = "keyword")
        @JsonPropertyDescription("[筛选条件] 业务对象关键词，用户提问点名了具体对象时必须原样填写，例如：物料名称\"PVC颗粒\"、物料编码\"IF2022082432\"、工单编号\"WO2026001\"、设备名称、仓库名称、客户名称等。系统按该词在数据库过滤，只返回匹配记录；禁止用户点名了对象却留空导致查询全表。仅当用户没有给出任何具体对象时才可以留空（表示查看该业务最新若干条）。")
        private String keyword;

        @JsonProperty(value = "status")
        @JsonPropertyDescription("状态过滤（中文），例如：草稿、已确认、已完成、已取消、停机、生产中、维护中，可为空")
        private String status;

        @JsonProperty(value = "limit")
        @JsonPropertyDescription("返回条数上限，默认 10，最大 20")
        private Integer limit;

    }

    /**
     * 工具出参：真实数据库记录列表（字段名为中文，模型只能整理转述，不得修改数值）
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonClassDescription("MES 业务数据查询结果：list 为数据库真实记录，total 为条数")
    public static class Response {

        private Boolean success;
        private String message;
        private String bizName;
        private Integer total;
        private List<Map<String, Object>> list;

        public static Response ok(String bizName, List<Map<String, Object>> list) {
            // 空数据话术与系统提示词保持一致：模型须如实回复「未查询到相关数据，请核对查询条件」
            return new Response(true, CollUtil.isEmpty(list) ? "未查询到相关数据，请核对查询条件" : "查询成功",
                    bizName, list == null ? 0 : list.size(), list == null ? Collections.emptyList() : list);
        }

        public static Response error(String message) {
            return new Response(false, message, null, 0, Collections.emptyList());
        }

    }

    /**
     * 无意义关键词表：模型常把业务域名称（如"生产工单"）误传为 keyword，导致 LIKE 匹配不到而误报空数据，
     * 这些词作为关键词没有过滤价值，直接忽略
     */
    private static final java.util.Set<String> MEANINGLESS_KEYWORDS = java.util.Set.of(
            "生产工单", "工单", "生产任务", "任务", "报工", "生产报工", "库存", "在库", "仓储", "设备", "设备台账",
            "质检", "检验", "质量", "物料", "产品", "物料产品", "车间", "工位", "车间工位", "班组", "排班",
            "客户", "供应商", "客户供应商", "工序", "工艺", "全部", "所有", "查询", "信息", "数据", "记录",
            "余量", "现存量", "可用量",
            "安灯", "安灯记录", "异常呼叫", "待检", "待检任务", "待生产", "待办", "未处置", "已处置");

    @Override
    public Response apply(Request request, ToolContext toolContext) {
        // 1. 租户从登录上下文强制获取（权限保障核心：模型无法指定租户，只能查本租户数据）
        Long tenantId = (Long) toolContext.getContext().get(AiUtils.TOOL_CONTEXT_TENANT_ID);
        if (tenantId == null) {
            return Response.error("无法识别当前用户所属租户，拒绝查询");
        }
        // 2. 参数规整（【修复】默认 50 / 上限 100：默认 10 会导致数据多于 10 条时
        // 查询结果被截断，用户误以为数据缺失，如设备台账 11 台只显示 10 台）
        int limit = request.getLimit() == null ? 50 : Math.min(Math.max(request.getLimit(), 1), 100);
        String keyword = normalizeKeyword(request.getKeyword());
        String status = blankToNull(request.getStatus());
        // 3. 请求参数日志（便于排查工具调用问题）
        log.info("[apply][MES 工具({}) 收到请求 tenantId({}) keyword({}) status({}) limit({})]",
                bizName, tenantId, keyword, status, limit);
        // 3. 在租户上下文中执行只读查询
        try {
            List<Map<String, Object>> list = TenantUtils.execute(tenantId, () -> doQuery(tenantId, keyword, status, limit));
            if (CollUtil.isEmpty(list)) {
                // 空数据：如实返回，由模型按约束回复「未查询到相关数据，请确认查询条件是否正确」
                log.info("[apply][MES 工具({}) 查询完成 tenantId({}) 结果: 空]", bizName, tenantId);
                return Response.ok(bizName, Collections.emptyList());
            }
            log.info("[apply][MES 工具({}) 查询成功 tenantId({}) 条数({})]", bizName, tenantId, list.size());
            return Response.ok(bizName, list);
        } catch (Exception e) {
            log.error("[apply][MES 业务域({}) 查询异常 tenantId({})]", bizName, tenantId, e);
            return Response.error("数据查询接口异常，请稍后重试");
        }
    }

    /**
     * 子类实现具体查询；SQL 必须带 tenant_id = ? AND deleted = 0
     *
     * @param tenantId 租户编号
     * @param keyword  关键词过滤（可为 null）
     * @param status   状态过滤（中文，可为 null）
     * @param limit    条数上限
     */
    protected abstract List<Map<String, Object>> doQuery(Long tenantId, String keyword, String status, int limit);

    protected static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return (t.isEmpty() || "null".equalsIgnoreCase(t) || "无".equals(t)) ? null : t;
    }

    /**
     * 关键词归一化：去掉无意义业务域词（防止模型把"生产工单"当关键词导致误报空数据）
     */
    protected static String normalizeKeyword(String s) {
        String t = blankToNull(s);
        if (t == null) {
            return null;
        }
        return MEANINGLESS_KEYWORDS.contains(t) ? null : t;
    }

    protected static String like(String keyword) {
        return "%" + keyword + "%";
    }

}
