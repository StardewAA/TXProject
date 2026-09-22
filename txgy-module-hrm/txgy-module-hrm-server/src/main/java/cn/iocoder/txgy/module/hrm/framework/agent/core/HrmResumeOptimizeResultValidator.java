package cn.iocoder.txgy.module.hrm.framework.agent.core;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_MODEL_OUTPUT_INVALID;

/**
 * 简历优化结果的结构化校验（A02）。
 *
 * <p>除结构校验外，这里还有两道针对「不得虚构」的护栏：
 * <ol>
 *   <li>originalValue 必须真实出现在简历原文中——对不上说明模型编造了原文；</li>
 *   <li>suggestedValue 不得引入原文没有的年份、机构或证书——疑似编造事实。</li>
 * </ol>
 * 不合格的建议条会被**丢弃**而不是让整个任务失败：
 * 一条建议有问题不应让用户拿不到其它有效建议，但会被剔除且不进入确认环节。</p>
 */
public final class HrmResumeOptimizeResultValidator {

    /** 允许建议的模块 */
    private static final Set<String> ALLOWED_MODULES = Set.of(
            "BASIC_INFO", "JOB_INTENTION", "EDUCATION",
            "WORK_EXPERIENCE", "PROJECT_EXPERIENCE", "SKILLS", "SELF_INTRO");

    /**
     * 模块别名归一化。
     *
     * <p>实测模型会用 STRENGTHS 表示「个人优势」。与其把这类建议直接丢弃，
     * 不如归一到标准模块名，既保住有效建议，前端也能正确分组。</p>
     */
    private static final Map<String, String> MODULE_ALIASES = Map.ofEntries(
            Map.entry("STRENGTHS", "SELF_INTRO"),
            Map.entry("STRENGTH", "SELF_INTRO"),
            Map.entry("SELF_EVALUATION", "SELF_INTRO"),
            Map.entry("SUMMARY", "SELF_INTRO"),
            Map.entry("EXPERIENCE", "WORK_EXPERIENCE"),
            Map.entry("WORK", "WORK_EXPERIENCE"),
            Map.entry("PROJECT", "PROJECT_EXPERIENCE"),
            Map.entry("PROJECTS", "PROJECT_EXPERIENCE"),
            Map.entry("SKILL", "SKILLS"),
            Map.entry("EDUCATIONS", "EDUCATION"),
            Map.entry("BASIC", "BASIC_INFO"),
            Map.entry("BASIC_INFORMATION", "BASIC_INFO"),
            Map.entry("INTENTION", "JOB_INTENTION"),
            Map.entry("JOB_INTENT", "JOB_INTENTION"));

    /**
     * 允许润色的字段。
     *
     * <p>公司、学校、学历、时间、职位等事实字段不在此列：这类内容只能由求职者本人修改，
     * 模型不得改动，从源头杜绝「改着改着把事实改了」。</p>
     */
    private static final Set<String> OPTIMIZABLE_FIELDS = Set.of(
            "description", "achievements", "experience", "strengths", "skills");

    private static final int MAX_ITEMS = 8;
    private static final int MAX_TEXT_LENGTH = 2000;

    private HrmResumeOptimizeResultValidator() {
    }

    /**
     * 校验并返回优化结果。被剔除的建议不会出现在返回结果里。
     *
     * @param rawContent    模型返回的原始文本
     * @param resumeContent 简历原文，用于校验 originalValue 是否真实存在
     * @return 通过校验的结果，items 只包含合格建议
     */
    public static JSONObject validate(String rawContent, String resumeContent) {
        JSONObject root = HrmRecruitAgentResultValidationSupport.parseResultObject(rawContent);
        JSONArray items = root.getJSONArray("items");
        if (items == null) {
            throw exception(AGENT_MODEL_OUTPUT_INVALID);
        }
        JSONArray accepted = new JSONArray();
        for (Object item : items) {
            if (!(item instanceof JSONObject suggestion)) {
                continue;
            }
            if (isAcceptable(suggestion, resumeContent)) {
                accepted.add(suggestion);
            }
        }
        // 超出上限时只保留前 MAX_ITEMS 条
        JSONArray limited = new JSONArray();
        for (int i = 0; i < Math.min(accepted.size(), MAX_ITEMS); i++) {
            limited.add(accepted.get(i));
        }
        root.set("items", limited);
        return root;
    }

    private static boolean isAcceptable(JSONObject suggestion, String resumeContent) {
        String module = normalizeModule(suggestion.getStr("module"));
        if (module == null) {
            return false;
        }
        // 归一化后写回，保证前端拿到的是标准模块名
        suggestion.set("module", module);
        String fieldPath = suggestion.getStr("fieldPath");
        if (fieldPath == null || !OPTIMIZABLE_FIELDS.contains(fieldPath)) {
            return false;
        }
        // 一期只支持改写既有内容：缺省或非 REPLACE 之外的操作都不接受
        String operation = suggestion.getStr("operation");
        if (operation != null && !"REPLACE".equals(operation)) {
            return false;
        }
        suggestion.set("operation", "REPLACE");
        String originalValue = suggestion.getStr("originalValue");
        String suggestedValue = suggestion.getStr("suggestedValue");
        String reason = suggestion.getStr("reason");
        if (!isValidText(originalValue) || !isValidText(suggestedValue) || !isValidText(reason)) {
            return false;
        }
        // 护栏一：原文片段必须真实存在
        if (StrUtil.isBlank(resumeContent) || !resumeContent.contains(originalValue)) {
            return false;
        }
        // 护栏二：不得引入原文没有的事实
        return !HrmResumeFactGuard.introducesNewFact(originalValue, suggestedValue);
    }

    /** 模块名归一化；无法识别时返回 null */
    private static String normalizeModule(String module) {
        if (StrUtil.isBlank(module)) {
            return null;
        }
        String upper = module.trim().toUpperCase();
        if (ALLOWED_MODULES.contains(upper)) {
            return upper;
        }
        return MODULE_ALIASES.get(upper);
    }

    private static boolean isValidText(String text) {
        return StrUtil.isNotBlank(text) && text.length() <= MAX_TEXT_LENGTH;
    }

    /** 供测试与排查使用：可润色字段清单 */
    public static List<String> optimizableFields() {
        return List.copyOf(OPTIMIZABLE_FIELDS);
    }
}
