package cn.iocoder.txgy.framework.mybatis.core.interceptor;

import cn.iocoder.txgy.framework.common.util.audit.WorkTrace;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Captures row changes on the existing transaction connection after tenant SQL rewriting.
 * Unsupported SQL is reported, never guessed or rewritten on the business path.
 */
@Intercepts({
        @Signature(type = StatementHandler.class, method = "update", args = Statement.class),
        @Signature(type = StatementHandler.class, method = "batch", args = Statement.class),
        @Signature(type = Executor.class, method = "flushStatements", args = {})
})
public class WorkTraceInterceptor implements Interceptor {
    private static final Pattern UPDATE = Pattern.compile("(?is)^\\s*UPDATE\\s+(`?[a-zA-Z0-9_]+`?)\\s+SET\\s+.+?\\s+WHERE\\s+(.+)$");
    private static final Pattern DELETE = Pattern.compile("(?is)^\\s*DELETE\\s+FROM\\s+(`?[a-zA-Z0-9_]+`?)\\s+WHERE\\s+(.+)$");
    private static final Pattern INSERT = Pattern.compile("(?is)^\\s*INSERT\\s+INTO\\s+(`?[a-zA-Z0-9_]+`?)\\s*\\(");
    private static final Pattern BUSINESS = Pattern.compile("(?i)(wms|erp|crm|bpm|mes|hrm|fms|product|trade|training|system)_.*");
    private static final int ROW_LIMIT = 100;

    @Override public Object intercept(Invocation invocation) throws Throwable {
        if (!WorkTrace.active()) return invocation.proceed();
        if (invocation.getTarget() instanceof Executor) {
            Object result = invocation.proceed();
            WorkTrace.flushDeferred();
            return result;
        }
        boolean batch = "batch".equals(invocation.getMethod().getName());
        StatementHandler handler = (StatementHandler) invocation.getTarget();
        BoundSql bound = handler.getBoundSql();
        String sql = bound.getSql();
        var match = UPDATE.matcher(sql);
        String operation = "UPDATE";
        if (!match.matches()) { match = DELETE.matcher(sql); operation = "DELETE"; }
        if (!match.matches()) {
            var insert = INSERT.matcher(sql);
            Object result = invocation.proceed();
            if (insert.find() && allowed(insert.group(1))) {
                Connection conn = ((Statement) invocation.getArgs()[0]).getConnection();
                String name = insert.group(1);
                if (batch) WorkTrace.defer(() -> recordInsert(handler, bound, conn, name));
                else recordInsert(handler, bound, conn, name);
            } else {
                var unsupported = Pattern.compile("(?is)^\\s*(?:UPDATE|DELETE\\s+FROM|INSERT\\s+INTO)\\s+(`?[a-zA-Z0-9_]+`?)").matcher(sql);
                if (unsupported.find() && allowed(unsupported.group(1))) WorkTrace.warning(unsupported.group(1) + "：此类语句未提供行快照，请查看请求明细");
            }
            return result;
        }
        String table = match.group(1);
        if (!allowed(table)) return invocation.proceed();
        Connection connection = ((Statement) invocation.getArgs()[0]).getConnection();
        List<Map<String, Object>> before = List.of();
        boolean captured = false;
        try {
            int offset = placeholders(sql.substring(0, match.start(2)));
            MappedStatement mapped = mappedStatement(handler);
            // UPDATE performs a current read in MySQL; matching locking reads avoid stale REPEATABLE READ snapshots.
            String lock = connection.getAutoCommit() ? "" : " FOR UPDATE";
            if (connection.getAutoCommit()) WorkTrace.warning("非事务写入的前后值为观测快照，并发修改时请结合单据核查");
            BoundSql select = new BoundSql(mapped.getConfiguration(), "SELECT * FROM " + table + " WHERE " + match.group(2) + lock,
                    bound.getParameterMappings().subList(offset, bound.getParameterMappings().size()), bound.getParameterObject());
            bound.getAdditionalParameters().forEach(select::setAdditionalParameter);
            try (PreparedStatement query = connection.prepareStatement(select.getSql())) {
                query.setMaxRows(ROW_LIMIT + 1);
                new DefaultParameterHandler(mapped, select.getParameterObject(), select).setParameters(query);
                before = rows(query);
                captured = true;
            }
        } catch (Exception e) {
            WorkTrace.warning(table + "：未能读取变更前数据，操作请求仍已记录");
        }
        Object result = invocation.proceed(); // never catch/retry/commit the business statement
        if (batch && captured && !before.isEmpty()) {
            List<Map<String, Object>> batchBefore = before;
            String batchOperation = operation;
            WorkTrace.defer(() -> {
                WorkTrace.warning("批量变更展示该批次执行前后的整体快照");
                recordChange(connection, table, batchOperation, batchBefore);
            });
            return result;
        }
        if (captured && result instanceof Number count && count.intValue() > 0 && !before.isEmpty()) {
            recordChange(connection, table, operation, before);
        }
        return result;
    }
    private static void recordChange(Connection connection, String table, String operation, List<Map<String, Object>> before) {
        List<Map<String, Object>> after = List.of();
        if ("UPDATE".equals(operation)) {
            try { after = byIds(connection, table, before.stream().map(row -> row.get("id")).toList()); }
            catch (Exception e) { WorkTrace.warning(table + "：未能读取变更后数据"); }
        }
        trackOutcome(WorkTrace.add(table.replace("`", ""), operation, before, after), connection);
    }
    private static void trackOutcome(Map<String, Object> entry, Connection connection) {
        if (entry == null) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            entry.put("outcome", "PENDING");
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    entry.put("outcome", status == STATUS_COMMITTED ? "COMMITTED" : status == STATUS_ROLLED_BACK ? "ROLLED_BACK" : "EXECUTED");
                    if (status == STATUS_ROLLED_BACK) WorkTrace.warning("部分变更所属事务已回滚，未生效");
                }
            });
        } else {
            try { entry.put("outcome", connection.getAutoCommit() ? "COMMITTED" : "EXECUTED"); }
            catch (SQLException e) { entry.put("outcome", "EXECUTED"); }
            if ("EXECUTED".equals(entry.get("outcome"))) WorkTrace.warning("手动事务的最终提交状态未提供，字段值仅为执行时快照");
        }
    }
    private static boolean allowed(String table) {
        String name = table.replace("`", "").toLowerCase(Locale.ROOT);
        return BUSINESS.matcher(name).matches() && !name.contains("oauth") && !name.contains("log")
                && !name.contains("token") && !name.contains("sms") && !name.contains("mail")
                && !name.contains("social") && !name.contains("config");
    }
    private static MappedStatement mappedStatement(StatementHandler handler) {
        Object realHandler = com.baomidou.mybatisplus.core.toolkit.PluginUtils.realTarget(handler);
        return (MappedStatement) SystemMetaObject.forObject(realHandler).getValue("delegate.mappedStatement");
    }
    private static void recordInsert(StatementHandler handler, BoundSql bound, Connection connection, String table) {
        try {
            MappedStatement mapped = mappedStatement(handler);
            String[] keys = mapped.getKeyProperties();
            Object parameter = bound.getParameterObject();
            Object id = keys != null && keys.length == 1
                    ? mapped.getConfiguration().newMetaObject(parameter).getValue(keys[0]) : null;
            if (id == null && parameter != null && mapped.getConfiguration().newMetaObject(parameter).hasGetter("id")) {
                id = mapped.getConfiguration().newMetaObject(parameter).getValue("id");
            }
            if (id == null) {
                WorkTrace.warning(table + "：批量或无主键新增，字段内容请查看请求明细");
                return;
            }
            trackOutcome(WorkTrace.add(table.replace("`", ""), "INSERT", List.of(), byIds(connection, table, List.of(id))), connection);
        } catch (Exception e) { WorkTrace.warning(table + "：未能读取新增数据，操作请求仍已记录"); }
    }
    private static List<Map<String, Object>> byIds(Connection connection, String table, List<Object> ids) throws SQLException {
        if (ids.isEmpty()) return List.of();
        if (ids.stream().anyMatch(Objects::isNull)) throw new SQLException("unsupported key");
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        try (var query = connection.prepareStatement("SELECT * FROM " + table + " WHERE id IN (" + placeholders + ") ORDER BY id")) {
            for (int i=0; i<ids.size(); i++) query.setObject(i+1, ids.get(i));
            query.setMaxRows(ROW_LIMIT + 1);
            return rows(query);
        }
    }
    private static List<Map<String, Object>> rows(PreparedStatement query) throws SQLException {
        List<Map<String,Object>> rows = new ArrayList<>();
        try (ResultSet result = query.executeQuery()) {
            var meta = result.getMetaData();
            while (result.next()) {
                if (rows.size() == ROW_LIMIT) { WorkTrace.warning("单次数据库变更超过 100 行，已保留前 100 行明细"); break; }
                Map<String,Object> row = new LinkedHashMap<>();
                for (int i=1; i<=meta.getColumnCount(); i++) {
                    String key = meta.getColumnLabel(i).toLowerCase(Locale.ROOT);
                    Object value = result.getObject(i);
                    if (WorkTrace.sensitive(key)) value = "[已脱敏]";
                    else if (value instanceof byte[] || value instanceof Blob) value = "[二进制内容]";
                    else if (value != null && !(value instanceof Number) && !(value instanceof Boolean)) {
                        String text = String.valueOf(value);
                        if (text.length() > 4096) { text = text.substring(0,4096) + "[内容过长，已截断]"; WorkTrace.warning("部分长字段仅保留前 4096 字符"); }
                        value = text;
                    }
                    row.put(key, value);
                }
                rows.add(row);
            }
        }
        return rows;
    }
    /** Count bound parameters, ignoring question marks inside SQL literals/identifiers. */
    private static int placeholders(String text) {
        int count = 0; char quote = 0;
        for (int i=0; i<text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == '\\') { i++; continue; }
                if (c == quote) { if (i+1<text.length() && text.charAt(i+1)==quote) i++; else quote=0; }
            } else if (c=='\'' || c=='"' || c=='`') quote=c;
            else if (c=='?') count++;
        }
        return count;
    }
}
