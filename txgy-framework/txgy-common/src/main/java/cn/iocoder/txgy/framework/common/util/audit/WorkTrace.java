package cn.iocoder.txgy.framework.common.util.audit;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import java.util.*;

/** Request-local, bounded business change snapshots. Never retains objects across requests. */
public final class WorkTrace {
    private static final ThreadLocal<State> LOCAL = new ThreadLocal<>();
    private static final int MAX_CHARS = 2_000_000;
    private WorkTrace() { }
    private static class State {
        final List<Object> entries = new ArrayList<>();
        final Set<String> warnings = new LinkedHashSet<>();
        final List<Runnable> deferred = new ArrayList<>();
        int chars;
    }
    public static boolean sensitive(String key) {
        String k = key.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
        return k.contains("password") || k.contains("passwd") || k.contains("token")
                || k.contains("secret") || k.contains("credential") || k.contains("privatekey")
                || k.equals("authorization") || k.equals("cookie") || k.equals("setcookie")
                || k.equals("apikey") || k.equals("pwd") || k.equals("config") || k.equals("configvalue");
    }
    public static String sanitizeJson(String value) {
        if (value == null || value.isBlank()) return value;
        try { return JsonUtils.toJsonString(sanitize(JsonUtils.getObjectMapper().readValue(value, Object.class), 0)); }
        catch (RuntimeException e) { return "\"[无法解析的内容已隐藏]\""; }
    }
    public static Object sanitize(Object value, int depth) {
        if (depth > 24) return "[嵌套内容过深，已省略]";
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, v) -> result.put(String.valueOf(key), sensitive(String.valueOf(key))
                    ? "[已脱敏]" : sanitize(v, depth + 1)));
            return result;
        }
        if (value instanceof Collection<?> items) {
            List<Object> result = new ArrayList<>();
            for (Object item : items) result.add(sanitize(item, depth + 1));
            return result;
        }
        if (value instanceof String s && (s.stripLeading().startsWith("{") || s.stripLeading().startsWith("["))) {
            try { return sanitize(JsonUtils.getObjectMapper().readValue(s, Object.class), depth + 1); }
            catch (RuntimeException e) { return "[无法解析的嵌套内容已隐藏]"; }
        }
        return value;
    }
    public static void begin() { LOCAL.set(new State()); }
    public static void clear() { LOCAL.remove(); }
    public static boolean active() { return LOCAL.get() != null; }
    public static void warning(String warning) {
        State state = LOCAL.get();
        if (state != null && state.warnings.size() < 20) state.warnings.add(warning);
    }
    @SuppressWarnings("unchecked")
    public static Map<String, Object> add(String table, String operation, List<?> before, List<?> after) {
        State state = LOCAL.get();
        if (state == null) return null;
        Map<String, Object> entry = (Map<String, Object>) sanitize(Map.of("table", table, "operation", operation, "before", before, "after", after), 0);
        int length = JsonUtils.toJsonString(entry).length();
        if (state.entries.size() >= 200 || state.chars + length > MAX_CHARS) {
            warning("本次变更明细超过采集上限，部分明细未收录；操作及请求仍保留");
            return null;
        }
        state.chars += length;
        state.entries.add(entry);
        return entry;
    }
    public static void defer(Runnable task) {
        State state = LOCAL.get();
        if (state == null) return;
        if (state.deferred.size() >= 200) { warning("批量变更明细超过采集上限，部分内容未收录"); return; }
        state.deferred.add(task);
    }
    public static void flushDeferred() {
        State state = LOCAL.get();
        if (state == null) return;
        List<Runnable> tasks = new ArrayList<>(state.deferred);
        state.deferred.clear();
        tasks.forEach(Runnable::run);
    }
    public static String snapshot() {
        State state = LOCAL.get();
        if (state != null && !state.deferred.isEmpty()) warning("部分批量变更未完成采集，请结合请求结果和原单据核查");
        return JsonUtils.toJsonString(Map.of("entries", state == null ? List.of() : state.entries,
                "warnings", state == null ? Set.of() : state.warnings));
    }
}
