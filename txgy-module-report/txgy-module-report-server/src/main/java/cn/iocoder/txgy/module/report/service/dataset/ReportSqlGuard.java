package cn.iocoder.txgy.module.report.service.dataset;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.module.report.controller.admin.dataset.vo.ReportDatasetParamVO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.report.enums.ErrorCodeConstants.*;

/**
 * 报表 SQL 安全守卫
 *
 * 只有两件事：**只读校验**与**参数化渲染 + 强制约束包装**。
 * 全部为纯函数，不依赖 Spring 上下文，便于单元测试。
 *
 * 约束：
 * 1. 只允许 SELECT / WITH 开头的单条语句；
 * 2. 禁止写操作、文件读写、系统库、延时函数等关键字；
 * 3. 参数一律转为 PreparedStatement 占位符（不做字符串拼接，杜绝注入）；
 * 4. 租户过滤条件由服务端强制追加（前端无法覆盖）；
 * 5. 强制行数上限（多取一行用于识别"超过上限"）。
 *
 * @author OPENLAB BS
 */
public final class ReportSqlGuard {

    /**
     * 外层包装别名，用于追加租户条件与行数上限
     */
    public static final String WRAPPER_ALIAS = "__report_ds";

    /**
     * 参数占位符：${name}
     */
    private static final Pattern PARAM_PATTERN = Pattern.compile("\\$\\{\\s*([A-Za-z0-9_]+)\\s*}");

    /**
     * 合法标识符：列名或 表.列
     */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?$");

    /**
     * 禁止关键字（小写匹配）
     */
    private static final List<String> FORBIDDEN_KEYWORDS = List.of(
            "insert ", "update ", "delete ", "drop ", "alter ", "truncate ", "create ", "grant ", "revoke ",
            "replace ", "rename ", "call ", "execute ", "handler ", "lock tables", "unlock tables",
            "into outfile", "into dumpfile", "load_file", "load data", "sleep(", "benchmark(",
            "information_schema", "performance_schema", "mysql.", "sys.");

    private ReportSqlGuard() {
    }

    /**
     * 准备待执行的 SQL：校验 + 参数化 + 租户与行数包装
     *
     * @param rawSql      数据集 SQL（含 ${name} 占位）
     * @param tenantColumn 租户过滤列名，可为空
     * @param definitions 参数定义，可为空
     * @param inputParams 前端传入参数，可为空
     * @param maxRows     行数上限
     * @param tenantId    当前租户编号
     * @return 可执行 SQL 与有序参数
     */
    public static PreparedSql prepare(String rawSql, String tenantColumn,
                                      List<ReportDatasetParamVO> definitions, Map<String, Object> inputParams,
                                      int maxRows, Long tenantId) {
        validateSql(rawSql);
        validateTenantColumn(tenantColumn);
        // 数据集查询走原生 JdbcTemplate，不经过 MyBatis 租户插件，必须显式声明租户过滤列
        if (StrUtil.isBlank(tenantColumn)) {
            throw exception(DATASET_TENANT_COLUMN_REQUIRED);
        }
        Map<String, Object> params = inputParams == null ? Collections.emptyMap() : inputParams;
        // 1. 参数化渲染
        List<Object> args = new ArrayList<>();
        String sql = render(rawSql, definitions, params, args);
        // 2. 行数上限放在内层（多取一行用于识别超限）：
        //    内层带 LIMIT 会强制物化，从而保证内层 ORDER BY 在外层包装后仍然有效
        String limited = sql + " LIMIT " + (maxRows + 1);
        // 3. 包装：租户条件由服务端强制追加
        StringBuilder wrapped = new StringBuilder("SELECT * FROM (").append(limited).append(") ").append(WRAPPER_ALIAS);
        if (StrUtil.isNotBlank(tenantColumn)) {
            wrapped.append(" WHERE ").append(WRAPPER_ALIAS).append('.').append(tenantColumn).append(" = ?");
            args.add(tenantId);
        }
        return new PreparedSql(wrapped.toString(), args);
    }

    /**
     * 准备原始 SQL（大屏逃生通道）：仅做只读校验与行数限制，不做参数化
     */
    public static PreparedSql prepareRaw(String rawSql, int maxRows) {
        validateSql(rawSql);
        // 逃生通道同样不允许跨租户：SQL 必须显式引用 tenant_id（无法自动注入，只能强制声明）
        if (!rawSql.toLowerCase().contains("tenant_id")) {
            throw exception(DATASET_RAW_SQL_TENANT_REQUIRED);
        }
        return new PreparedSql("SELECT * FROM (" + rawSql + " LIMIT " + (maxRows + 1) + ") " + WRAPPER_ALIAS,
                new ArrayList<>());
    }

    /**
     * 只读校验：必须是单条 SELECT / WITH 语句，且不含危险关键字
     */
    public static void validateSql(String rawSql) {
        if (StrUtil.isBlank(rawSql)) {
            throw exception(DATASET_SQL_EMPTY);
        }
        String sql = rawSql.trim();
        // 去掉尾部单个分号后仍存在分号 => 多语句
        if (StrUtil.endWith(sql, ";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }
        if (sql.contains(";")) {
            throw exception(DATASET_SQL_MULTI_STATEMENT);
        }
        if (sql.contains("--") || sql.contains("/*") || sql.contains("#")) {
            throw exception(DATASET_SQL_FORBIDDEN_KEYWORD, "注释符");
        }
        String lower = sql.toLowerCase();
        if (!(lower.startsWith("select") || lower.startsWith("with"))) {
            throw exception(DATASET_SQL_NOT_SELECT);
        }
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (lower.contains(keyword)) {
                throw exception(DATASET_SQL_FORBIDDEN_KEYWORD, keyword.trim());
            }
        }
    }

    /**
     * 租户过滤列名校验：仅允许 列名 或 表.列
     */
    public static void validateTenantColumn(String tenantColumn) {
        if (StrUtil.isBlank(tenantColumn)) {
            return;
        }
        if (!IDENTIFIER_PATTERN.matcher(tenantColumn.trim()).matches()) {
            throw exception(DATASET_TENANT_COLUMN_INVALID, tenantColumn);
        }
    }

    /**
     * 参数名校验：仅允许字母、数字、下划线，且不以数字开头
     */
    public static void validateParamName(String name) {
        if (StrUtil.isBlank(name) || !IDENTIFIER_PATTERN.matcher(name.trim()).matches()) {
            throw exception(DATASET_PARAM_NAME_INVALID, name);
        }
    }

    /**
     * 参数化渲染：${name} -> ?
     *
     * 规则：
     * 1. 未声明的入参直接拒绝（避免前端塞入意外参数）；
     * 2. 必填参数缺失直接拒绝；
     * 3. SQL 中残留的 ${...} 说明引用了未声明参数，直接拒绝。
     */
    private static String render(String rawSql, List<ReportDatasetParamVO> definitions,
                                 Map<String, Object> inputParams, List<Object> args) {
        List<ReportDatasetParamVO> defs = definitions == null ? Collections.emptyList() : definitions;
        Set<String> declaredNames = new HashSet<>();
        for (ReportDatasetParamVO def : defs) {
            declaredNames.add(def.getName());
        }
        // 1. 拒绝未声明的入参
        for (String inputName : inputParams.keySet()) {
            if (!declaredNames.contains(inputName)) {
                throw exception(DATASET_PARAM_UNDECLARED, inputName);
            }
        }
        // 2. 逐参数替换：按声明顺序收集参数值，保证与占位符顺序一致
        Map<String, Object> orderedValues = new LinkedHashMap<>();
        for (ReportDatasetParamVO def : defs) {
            Object value = inputParams.get(def.getName());
            if (value == null || StrUtil.isBlank(String.valueOf(value))) {
                value = def.getDefaultValue();
            }
            if (Boolean.TRUE.equals(def.getRequired())
                    && (value == null || StrUtil.isBlank(String.valueOf(value)))) {
                throw exception(DATASET_PARAM_MISSING, StrUtil.blankToDefault(def.getLabel(), def.getName()));
            }
            orderedValues.put(def.getName(), value);
        }
        // 3. 占位符替换（同名参数多次出现时复用同一取值，并重复追加参数）
        Matcher matcher = PARAM_PATTERN.matcher(rawSql);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!declaredNames.contains(name)) {
                throw exception(DATASET_PARAM_UNDECLARED, name);
            }
            Object value = orderedValues.get(name);
            if (value == null || StrUtil.isBlank(String.valueOf(value))) {
                throw exception(DATASET_PARAM_MISSING, name);
            }
            matcher.appendReplacement(rendered, "?");
            args.add(value);
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    /**
     * 可执行 SQL 与有序参数
     */
    @Data
    @AllArgsConstructor
    public static class PreparedSql {

        /**
         * 待执行 SQL（含 ? 占位）
         */
        private String sql;

        /**
         * 有序参数
         */
        private List<Object> args;

    }

}
