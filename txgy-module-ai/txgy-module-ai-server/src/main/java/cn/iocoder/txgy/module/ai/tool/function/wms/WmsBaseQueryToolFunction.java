package cn.iocoder.txgy.module.ai.tool.function.wms;

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
import java.util.Set;
import java.util.function.BiFunction;

/**
 * WMS 仓储业务数据查询工具基类（Function Calling）
 *
 * <p>与 {@code MesBaseQueryToolFunction} 保持同一套约定（对应需求：AI 严禁编造业务数据）：
 * 1. 租户编号从 ToolContext 登录上下文强制获取，模型无法指定，天然租户隔离
 * 2. 只读 SQL，必须带 tenant_id = ? AND deleted = 0
 * 3. 空结果返回空 list，由模型按系统提示词如实回复「未查询到相关数据」
 * 4. 查询异常返回固定话术，不暴露内部细节
 *
 * @author 天信管业
 */
@Slf4j
public abstract class WmsBaseQueryToolFunction
        implements BiFunction<WmsBaseQueryToolFunction.Request, ToolContext, WmsBaseQueryToolFunction.Response> {

    protected final JdbcTemplate jdbcTemplate;
    /**
     * 业务域中文名（例如：库存余额）
     */
    protected final String bizName;

    protected WmsBaseQueryToolFunction(JdbcTemplate jdbcTemplate, String bizName) {
        this.jdbcTemplate = jdbcTemplate;
        this.bizName = bizName;
    }

    /**
     * 工具入参（由大模型按需填写，全部可选）
     */
    @Data
    @JsonClassDescription("WMS 仓储业务数据实时查询工具：从 WMS 后端数据库读取真实的仓库、库存、商品、往来企业与单据数据")
    public static class Request {

        @JsonProperty(value = "keyword")
        @JsonPropertyDescription("[筛选条件] 业务对象关键词。用户点名了具体对象时必须原样填写，例如：仓库名称\"金岩原辅材料库\"、商品名称\"岩棉板\"、商品编号\"CP-1001\"、往来企业名称\"江苏鑫盛防腐涂料有限公司\"、单据编号\"RK2026001\"等。系统按该词过滤，只返回匹配记录；禁止用户点名了对象却留空导致全表返回。仅当用户没有给出任何具体对象时才留空（表示查看最新若干条）。")
        private String keyword;

        @JsonProperty(value = "status")
        @JsonPropertyDescription("状态过滤（中文），例如：草稿、待审批、已通过、已驳回、已完成、待入库、已入库，可为空")
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
    @JsonClassDescription("WMS 业务数据查询结果：list 为数据库真实记录，total 为条数")
    public static class Response {

        private Boolean success;
        private String message;
        private String bizName;
        private Integer total;
        private List<Map<String, Object>> list;

        public static Response ok(String bizName, List<Map<String, Object>> list) {
            return new Response(true, CollUtil.isEmpty(list) ? "未查询到相关数据，请核对查询条件" : "查询成功",
                    bizName, list == null ? 0 : list.size(), list == null ? Collections.emptyList() : list);
        }

        public static Response error(String message) {
            return new Response(false, message, null, 0, Collections.emptyList());
        }

    }

    /**
     * 无意义关键词表：模型常把业务域名称（如"库存余额"）误传为 keyword，
     * LIKE 匹配不到会误报空数据，这些词作为关键词没有过滤价值，直接忽略
     */
    private static final Set<String> MEANINGLESS_KEYWORDS = Set.of(
            "库存", "库存余额", "在库", "现存量", "可用量", "余量", "仓储", "仓库", "库房", "仓库列表",
            "商品", "商品档案", "物料", "产品", "物品", "商品列表",
            "往来企业", "企业", "供应商", "客户", "厂商", "单位", "往来单位",
            "单据", "入库单", "出库单", "移库单", "盘库单", "单据列表",
            "全部", "所有", "查询", "信息", "数据", "记录", "列表", "汇总");

    @Override
    public Response apply(Request request, ToolContext toolContext) {
        // 1. 租户从登录上下文强制获取（模型无法指定租户，只能查本租户数据）
        Long tenantId = (Long) toolContext.getContext().get(AiUtils.TOOL_CONTEXT_TENANT_ID);
        if (tenantId == null) {
            return Response.error("无法识别当前用户所属租户，拒绝查询");
        }
        // 2. 参数规整
        int limit = request.getLimit() == null ? 10 : Math.min(Math.max(request.getLimit(), 1), 20);
        String keyword = normalizeKeyword(request.getKeyword());
        String status = blankToNull(request.getStatus());
        log.info("[apply][WMS 工具({}) 收到请求 tenantId({}) keyword({}) status({}) limit({})]",
                bizName, tenantId, keyword, status, limit);
        // 3. 在租户上下文中执行只读查询
        try {
            List<Map<String, Object>> list = TenantUtils.execute(tenantId, () -> doQuery(tenantId, keyword, status, limit));
            if (CollUtil.isEmpty(list)) {
                log.info("[apply][WMS 工具({}) 查询完成 tenantId({}) 结果: 空]", bizName, tenantId);
                return Response.ok(bizName, Collections.emptyList());
            }
            log.info("[apply][WMS 工具({}) 查询成功 tenantId({}) 条数({})]", bizName, tenantId, list.size());
            return Response.ok(bizName, list);
        } catch (Exception e) {
            log.error("[apply][WMS 业务域({}) 查询异常 tenantId({})]", bizName, tenantId, e);
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
     * 关键词归一化：去掉无意义业务域词，防止模型把业务域名称当关键词导致误报空数据
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
