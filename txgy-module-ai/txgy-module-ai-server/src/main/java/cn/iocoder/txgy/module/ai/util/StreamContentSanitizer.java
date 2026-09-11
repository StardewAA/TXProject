package cn.iocoder.txgy.module.ai.util;

import java.util.regex.Pattern;

public final class StreamContentSanitizer {

    private static final Pattern DOUBAO_PROTOCOL = Pattern.compile("<\\|[^|]*\\|(?:FunctionCall[A-Za-z]*|function_call|tool_call)\\|>[\\s\\S]*?<\\|[^|]*\\|FunctionCallEnd\\|>|<\\|FunctionCallEnd\\|>");

    private static final Pattern JSON_TOOL_CALL = Pattern.compile("\\{\\s*\"name_size\"\\s*:\\s*\"[^\"]+\"\\s*,\\s*\"parameters\"\\s*:[\\s\\S]*?\\}\\s*");

    private StreamContentSanitizer() {}

    public static String sanitize(String raw) {
        if (raw == null || raw.isEmpty()) return raw;
        // 注意：不要加入「按 key 前缀猜工具参数」之类的规则（如 "\"query_xxx\":\"...\","），
        // 那会把模型正常输出的 JSON 片段一起删掉
        String step1 = DOUBAO_PROTOCOL.matcher(raw).replaceAll("");
        String step2 = JSON_TOOL_CALL.matcher(step1).replaceAll("");
        return step2.trim();
    }
}
