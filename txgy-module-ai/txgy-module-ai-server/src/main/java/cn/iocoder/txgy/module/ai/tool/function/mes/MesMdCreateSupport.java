package cn.iocoder.txgy.module.ai.tool.function.mes;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * MES 基础档案创建工具公共基类（Function Calling 写操作）
 *
 * 与 {@link MesWorkOrderCreateToolFunction} 同一套安全约定：
 * 1. 租户编号从登录上下文强制获取，模型无法指定 → 租户数据隔离
 * 2. 编码唯一性校验 + 同名重复拦截，避免重复建档
 * 3. 创建后立即回读本租户内的新记录，把真实落库值返回给模型如实转述
 * 4. 失败时返回失败原因，由模型原样告知用户
 *
 * @author 天信管业
 */
@Slf4j
public abstract class MesMdCreateSupport<Req> implements Function<Req, Map<String, Object>> {

    protected final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    protected MesMdCreateSupport(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 工具入参基类：公共字段 */
    @lombok.Data
    public abstract static class BaseRequest {
        @com.fasterxml.jackson.annotation.JsonProperty(value = "code")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[可选] 编码：用户明确给出编码时填写；系统校验唯一，已存在会返回失败原因；不填则自动生成")
        private String code;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "remark")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[可选] 备注，用户给出的补充说明原文")
        private String remark;
    }

    protected Map<String, Object> ok(String message, Map<String, Object> record) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("message", message);
        resp.put("record", record);
        return resp;
    }

    protected Map<String, Object> error(String message) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", false);
        resp.put("message", message);
        resp.put("record", null);
        return resp;
    }

    protected String trimToNull(String s) {
        return StrUtil.trimToNull(s);
    }

    /** 编码：用户未给时按前缀 + 时间戳自动生成 */
    protected String generateCode(String prefix, String userCode) {
        return userCode != null ? userCode
                : prefix + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now());
    }

    /**
     * 在租户上下文内执行创建，统一处理业务异常解包
     *
     * @param tenantId 租户编号
     * @param action   动作名（用于日志与失败文案，如「物料创建」）
     * @param body     创建逻辑（抛 IllegalArgumentException 表示业务性失败）
     */
    protected Map<String, Object> executeInTenant(Long tenantId, String action, java.util.function.Supplier<Map<String, Object>> body) {
        try {
            Map<String, Object> result = TenantUtils.execute(tenantId, body::get);
            log.info("[executeInTenant][{} 成功 tenantId({})]", action, tenantId);
            return result;
        } catch (IllegalArgumentException e) {
            log.warn("[executeInTenant][{} 失败 tenantId({}) reason({})]", action, tenantId, e.getMessage());
            return error(action + "失败：" + e.getMessage());
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            if (root instanceof IllegalArgumentException) {
                log.warn("[executeInTenant][{} 失败 tenantId({}) reason({})]", action, tenantId, root.getMessage());
                return error(action + "失败：" + root.getMessage());
            }
            log.error("[executeInTenant][{} 异常 tenantId({})]", action, tenantId, e);
            return error(action + "失败：系统异常，请稍后重试");
        }
    }

    /**
     * 编码/名称重复拦截：编码精确重复必拦；名称完全相同也拦截（基础档案不允许同名）
     *
     * @return null 表示无重复；否则返回拦截原因
     */
    protected String checkDuplicate(Long tenantId, String table, String code, String name) {
        List<Map<String, Object>> dup = jdbcTemplate.queryForList(
                "SELECT id, code, name FROM " + table
                        + " WHERE tenant_id = ? AND deleted = 0 AND (code = ? OR name = ?) LIMIT 1",
                tenantId, code, name);
        if (!dup.isEmpty()) {
            Map<String, Object> hit = dup.get(0);
            boolean sameCode = code.equals(hit.get("code"));
            return (sameCode ? "编码「" + code + "」" : "名称「" + name + "」")
                    + "在系统中已存在（现有记录：" + hit.get("code") + " " + hit.get("name")
                    + "），请勿重复添加；如需修改请在对应档案页面编辑";
        }
        return null;
    }

    /** 获取当前租户编号，未登录上下文返回 null */
    protected Long currentTenant() {
        return TenantContextHolder.getTenantId();
    }

    /** 操作人（写入 creator/updater 字段）：取登录用户，取不到时退回 '1'（与 MES 既有工具一致） */
    protected String currentOperator() {
        Long userId = cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId();
        return userId != null ? String.valueOf(userId) : "1";
    }

    /**
     * 在本租户档案表内把关键词解析为唯一记录编号（单据录入工具复用）
     *
     * 匹配顺序：编码/名称精确 → 忽略空格比较 → 名称模糊；无匹配或多匹配都抛
     * {@link IllegalArgumentException}（带候选提示），由调用方转成给模型/用户的失败原因。
     *
     * @param tenantId 租户编号
     * @param table    档案表名（如 mes_md_item），必须含 id/code/name 三列
     * @param keyword  用户给出的名称或编码原文
     * @param label    业务名词（用于失败文案，如「物料」）
     */
    protected Long resolveId(Long tenantId, String table, String keyword, String label) {
        String kwNorm = keyword.replaceAll("[\\s　]+", "");
        List<Map<String, Object>> exact = jdbcTemplate.queryForList(
                "SELECT id, code, name FROM " + table
                        + " WHERE tenant_id = ? AND deleted = 0 AND (code = ? OR name = ?"
                        + " OR REPLACE(name, ' ', '') = ?) LIMIT 2",
                tenantId, keyword, keyword, kwNorm);
        if (exact.size() == 1) {
            return ((Number) exact.get(0).get("id")).longValue();
        }
        if (exact.size() > 1) {
            throw new IllegalArgumentException(label + "「" + keyword + "」在本租户存在多条同名记录，请提供唯一的编码");
        }
        List<Map<String, Object>> fuzzy = jdbcTemplate.queryForList(
                "SELECT id, code, name FROM " + table
                        + " WHERE tenant_id = ? AND deleted = 0"
                        + " AND REPLACE(name, ' ', '') LIKE CONCAT('%', ?, '%') LIMIT 6",
                tenantId, kwNorm);
        if (fuzzy.isEmpty()) {
            throw new IllegalArgumentException("在本租户档案中未找到" + label + "「" + keyword + "」，请核对名称或编码");
        }
        if (fuzzy.size() > 1) {
            String candidates = fuzzy.stream()
                    .map(row -> String.valueOf(row.get("code")) + " " + row.get("name"))
                    .collect(java.util.stream.Collectors.joining("；"));
            throw new IllegalArgumentException(label + "「" + keyword + "」匹配到多条记录，请提供更精确的名称或编码。候选："
                    + candidates);
        }
        return ((Number) fuzzy.get(0).get("id")).longValue();
    }

}
