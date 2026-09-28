package cn.iocoder.txgy.module.hrm.framework.agent.core;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;

import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_MODEL_OUTPUT_INVALID;

/**
 * 「招聘沟通筛选条件」的结构化校验。
 *
 * <p>模型在这个场景里只输出条件、不输出任何候选人信息，因此校验目标是
 * 「条件是不是可执行」：字段白名单、取值类型、数值范围。渲染安全与敏感字段那套
 * 仍复用 {@link HrmRecruitAgentResultValidationSupport#parseResultObject}。</p>
 */
public final class HrmConversationFilterResultValidator {

    /** 关键词数组字段；缺省或 null 一律视为「不限」 */
    private static final List<String> KEYWORD_FIELDS = List.of(
            "domicileKeywords", "schoolTierKeywords", "schoolKeywords", "educationKeywords",
            "genderKeywords", "postNameKeywords", "expectedCityKeywords", "resumeKeywords");

    private static final int MAX_KEYWORDS = 10;
    private static final int MAX_KEYWORD_LENGTH = 50;
    private static final int MAX_UNSUPPORTED_LENGTH = 200;

    private HrmConversationFilterResultValidator() {
    }

    public static JSONObject validate(String content) {
        JSONObject root = HrmRecruitAgentResultValidationSupport.parseResultObject(content);
        HrmRecruitAgentResultValidationSupport.requireText(root.getStr("criteria"));
        JSONObject filters = root.getJSONObject("filters");
        if (filters == null) {
            throw exception(AGENT_MODEL_OUTPUT_INVALID);
        }
        KEYWORD_FIELDS.forEach(field -> validateKeywords(filters, field));
        // 区间取人体合理范围，模型给出离谱数值时整任务失败而不是筛出乱七八糟的结果
        validateRange(filters, "ageMin", 16, 80);
        validateRange(filters, "ageMax", 16, 80);
        validateRange(filters, "workTimeMin", 0, 60);
        validateRange(filters, "workTimeMax", 0, 60);
        validateUnsupported(filters);
        return root;
    }

    /** 数组字段允许缺省，但出现时必须是长度受限的字符串数组 */
    private static void validateKeywords(JSONObject filters, String key) {
        if (filters.isNull(key)) {
            return;
        }
        Object value = filters.get(key);
        if (!(value instanceof JSONArray array) || array.size() > MAX_KEYWORDS) {
            throw exception(AGENT_MODEL_OUTPUT_INVALID);
        }
        for (Object item : array) {
            if (!(item instanceof String text) || StrUtil.isBlank(text)
                    || text.length() > MAX_KEYWORD_LENGTH) {
                throw exception(AGENT_MODEL_OUTPUT_INVALID);
            }
        }
    }

    private static void validateRange(JSONObject filters, String key, int min, int max) {
        if (filters.isNull(key)) {
            return;
        }
        Object value = filters.get(key);
        if (!(value instanceof Number number)) {
            throw exception(AGENT_MODEL_OUTPUT_INVALID);
        }
        int intValue = number.intValue();
        if (intValue < min || intValue > max) {
            throw exception(AGENT_MODEL_OUTPUT_INVALID);
        }
    }

    /** 无法翻译的条件是自由文本，只限长度与条数 */
    private static void validateUnsupported(JSONObject filters) {
        if (filters.isNull("unsupported")) {
            return;
        }
        Object value = filters.get("unsupported");
        if (!(value instanceof JSONArray array) || array.size() > MAX_KEYWORDS) {
            throw exception(AGENT_MODEL_OUTPUT_INVALID);
        }
        for (Object item : array) {
            if (!(item instanceof String text) || StrUtil.isBlank(text)
                    || text.length() > MAX_UNSUPPORTED_LENGTH) {
                throw exception(AGENT_MODEL_OUTPUT_INVALID);
            }
        }
    }
}
