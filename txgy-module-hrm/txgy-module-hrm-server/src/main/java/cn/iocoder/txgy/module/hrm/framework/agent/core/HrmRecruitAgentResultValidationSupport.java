package cn.iocoder.txgy.module.hrm.framework.agent.core;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.txgy.module.hrm.util.HrmResumeAgentPayloadUtils;

import java.util.List;
import java.util.Set;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_MODEL_OUTPUT_INVALID;

/**
 * 模型结构化输出的公共校验逻辑。
 *
 * <p>诊断与匹配共用同一套「不可信数据」处理规则，避免两处各写一份、日后只改一处而留下缺口。</p>
 */
final class HrmRecruitAgentResultValidationSupport {

    /**
     * 允许作为依据来源的字段。
     *
     * <p>直接复用负载构建时的白名单，保证「提示词列给模型的字段」与「校验器接受的字段」完全一致。</p>
     */
    static final Set<String> ALLOWED_EVIDENCE_FIELDS =
            HrmResumeAgentPayloadUtils.agentAllowedFieldNames();

    static final int MAX_ITEMS = 10;
    static final int MAX_TEXT_LENGTH = 500;

    private HrmRecruitAgentResultValidationSupport() {
    }

    /** 解析模型返回的文本；模型有时会包一层 Markdown 代码块，这里只剥壳 */
    static JSONObject parseResultObject(String rawContent) {
        if (StrUtil.isBlank(rawContent)) {
            throw exception(AGENT_MODEL_OUTPUT_INVALID);
        }
        JSONObject root;
        try {
            root = JSONUtil.parseObj(stripCodeFence(rawContent));
        } catch (RuntimeException ex) {
            throw exception(AGENT_MODEL_OUTPUT_INVALID);
        }
        // 防御：结论里出现敏感属性字段，说明模型用无关属性做了判断，直接判定不合格
        if (containsForbiddenKey(root)) {
            throw exception(AGENT_MODEL_OUTPUT_INVALID);
        }
        return root;
    }

    static String stripCodeFence(String content) {
        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        if (firstNewline < 0) {
            return trimmed;
        }
        String body = trimmed.substring(firstNewline + 1);
        int lastFence = body.lastIndexOf("```");
        return lastFence >= 0 ? body.substring(0, lastFence).trim() : body.trim();
    }

    static void validateOverallScore(JSONObject root) {
        Object value = root.get("overallScore");
        if (!(value instanceof Number number)) {
            throw exception(AGENT_MODEL_OUTPUT_INVALID);
        }
        int score = number.intValue();
        if (score < 0 || score > 100) {
            throw exception(AGENT_MODEL_OUTPUT_INVALID);
        }
    }

    /**
     * 校验分项维度：必须完整覆盖 requiredDimensions，每项含 score、comment 与至少一条依据。
     */
    static void validateDimensions(JSONObject root, List<String> requiredDimensions) {
        JSONArray dimensions = root.getJSONArray("dimensions");
        if (dimensions == null || dimensions.isEmpty()) {
            throw exception(AGENT_MODEL_OUTPUT_INVALID);
        }
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (Object item : dimensions) {
            if (!(item instanceof JSONObject dimension)) {
                throw exception(AGENT_MODEL_OUTPUT_INVALID);
            }
            String key = dimension.getStr("key");
            if (!requiredDimensions.contains(key)) {
                throw exception(AGENT_MODEL_OUTPUT_INVALID);
            }
            seen.add(key);
            Object scoreValue = dimension.get("score");
            if (!(scoreValue instanceof Number scoreNumber)
                    || scoreNumber.intValue() < 0 || scoreNumber.intValue() > 100) {
                throw exception(AGENT_MODEL_OUTPUT_INVALID);
            }
            requireText(dimension.getStr("comment"));
            validateEvidence(dimension.getJSONArray("evidence"));
        }
        if (!seen.containsAll(requiredDimensions)) {
            throw exception(AGENT_MODEL_OUTPUT_INVALID);
        }
    }

    static void validateEvidence(JSONArray evidence) {
        if (evidence == null || evidence.isEmpty()) {
            throw exception(AGENT_MODEL_OUTPUT_INVALID);
        }
        for (Object item : evidence) {
            if (!(item instanceof JSONObject entry)) {
                throw exception(AGENT_MODEL_OUTPUT_INVALID);
            }
            String field = requireString(entry, "field");
            if (!ALLOWED_EVIDENCE_FIELDS.contains(field)) {
                throw exception(AGENT_MODEL_OUTPUT_INVALID);
            }
            requireString(entry, "note");
        }
    }

    /** 校验字符串数组：允许为空，但有条数上限且每条必须有内容 */
    static void validateTextList(JSONObject root, String key) {
        JSONArray array = root.getJSONArray(key);
        if (array == null) {
            throw exception(AGENT_MODEL_OUTPUT_INVALID);
        }
        if (array.size() > MAX_ITEMS) {
            throw exception(AGENT_MODEL_OUTPUT_INVALID);
        }
        for (Object item : array) {
            if (!(item instanceof String text)) {
                throw exception(AGENT_MODEL_OUTPUT_INVALID);
            }
            requireText(text);
        }
    }

    /**
     * 校验对象数组：允许为空，元素必须包含 requiredFields 且均为非空**字符串**。
     */
    static void validateObjectList(JSONObject root, String key, List<String> requiredFields) {
        JSONArray array = root.getJSONArray(key);
        if (array == null) {
            throw exception(AGENT_MODEL_OUTPUT_INVALID);
        }
        if (array.size() > MAX_ITEMS) {
            throw exception(AGENT_MODEL_OUTPUT_INVALID);
        }
        for (Object item : array) {
            if (!(item instanceof JSONObject entry)) {
                throw exception(AGENT_MODEL_OUTPUT_INVALID);
            }
            for (String field : requiredFields) {
                requireString(entry, field);
            }
        }
    }

    /**
     * 取字段并要求它是非空字符串。
     *
     * <p>刻意不接受数组或对象：模型偶尔会把 note 写成数组（例如期望城市），
     * 前端会把它当文本渲染出乱码，因此在这里直接判为不合格。</p>
     */
    static String requireString(JSONObject entry, String field) {
        Object value = entry.get(field);
        if (!(value instanceof String text)) {
            throw exception(AGENT_MODEL_OUTPUT_INVALID);
        }
        requireText(text);
        return text;
    }

    static void requireText(String text) {
        if (StrUtil.isBlank(text) || text.length() > MAX_TEXT_LENGTH) {
            throw exception(AGENT_MODEL_OUTPUT_INVALID);
        }
    }

    /** 递归检查结果中是否出现敏感属性字段名 */
    static boolean containsForbiddenKey(Object node) {
        if (node instanceof JSONObject object) {
            for (var entry : object.entrySet()) {
                if (HrmResumeAgentPayloadUtils.FORBIDDEN_ATTRIBUTE_FIELDS.contains(entry.getKey())) {
                    return true;
                }
                if (containsForbiddenKey(entry.getValue())) {
                    return true;
                }
            }
            return false;
        }
        if (node instanceof JSONArray array) {
            for (Object item : array) {
                if (containsForbiddenKey(item)) {
                    return true;
                }
            }
        }
        return false;
    }
}
