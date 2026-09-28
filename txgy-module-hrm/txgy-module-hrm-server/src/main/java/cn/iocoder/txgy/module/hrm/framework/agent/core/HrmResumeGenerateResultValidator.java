package cn.iocoder.txgy.module.hrm.framework.agent.core;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.iocoder.txgy.module.hrm.util.HrmResumeAgentPayloadUtils;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_MODEL_OUTPUT_INVALID;

/**
 * AI 简历草稿结果校验。
 *
 * <p>生成结果仍是模型输出，不能直接当成事实写入用户简历。本校验器同时检查
 * 结构、长度、敏感信息和事实来源：公司、时间、学历、证书、技能及成果中的
 * 关键内容必须能在用户回答中找到，疑似新增事实的结果直接拒绝。</p>
 */
public final class HrmResumeGenerateResultValidator {

    private static final int MAX_HEADLINE_LENGTH = 200;
    private static final int MAX_SUMMARY_LENGTH = 1200;
    private static final int MAX_FIELD_LENGTH = 500;
    private static final int MAX_SKILLS = 12;
    private static final int MAX_EXPERIENCES = 8;
    private static final int MAX_PROJECTS = 8;
    private static final int MAX_EDUCATION = 5;
    private static final Pattern NUMBER = Pattern.compile("(?<![\\d.])\\d+(?:\\.\\d+)?%?(?![\\d.])");

    private static final Set<String> ROOT_FIELDS = Set.of(
            "headline", "summary", "skills", "experiences", "projects", "educationExperiences");
    private static final Set<String> EXPERIENCE_FIELDS = Set.of(
            "company", "position", "start", "end", "description", "achievements");
    private static final Set<String> PROJECT_FIELDS = Set.of(
            "name", "role", "start", "end", "description", "achievements", "highlight");
    private static final Set<String> EDUCATION_FIELDS = Set.of(
            "school", "degree", "major", "start", "end", "experience");

    private HrmResumeGenerateResultValidator() {
    }

    /** 校验并返回可以交给前端编辑的草稿结果。 */
    public static JSONObject validate(String rawContent, String answersContent) {
        JSONObject root = HrmRecruitAgentResultValidationSupport.parseResultObject(rawContent);
        requireExactFields(root, ROOT_FIELDS);
        if (HrmRecruitAgentResultValidationSupport.containsForbiddenKey(root)
                || HrmResumeAgentPayloadUtils.containsSensitive(root)) {
            throw exception(AGENT_MODEL_OUTPUT_INVALID);
        }

        String source = StrUtil.nullToEmpty(answersContent);
        String headline = requireText(root, "headline", MAX_HEADLINE_LENGTH, true);
        String summary = requireText(root, "summary", MAX_SUMMARY_LENGTH, true);
        validateSkills(root.getJSONArray("skills"), source);
        validateExperiences(root.getJSONArray("experiences"), source);
        validateProjects(root.getJSONArray("projects"), source);
        validateEducation(root.getJSONArray("educationExperiences"), source);

        // 防止模型在摘要、亮点等自由文本中额外写入新的年份、机构、证书或数字成果。
        if (HrmResumeFactGuard.introducesNewFact(source, root.toString())
                || introducesNewNumber(source, headline + "\n" + summary + "\n" + root.toString())) {
            throw exception(AGENT_MODEL_OUTPUT_INVALID);
        }
        return root;
    }

    private static void validateSkills(JSONArray skills, String source) {
        if (skills == null || skills.size() > MAX_SKILLS) {
            throw exception(AGENT_MODEL_OUTPUT_INVALID);
        }
        for (Object item : skills) {
            if (!(item instanceof String skill) || !isText(skill, MAX_FIELD_LENGTH)
                    || !isContainedInSource(skill, source)) {
                throw exception(AGENT_MODEL_OUTPUT_INVALID);
            }
        }
    }

    private static void validateExperiences(JSONArray experiences, String source) {
        validateArray(experiences, MAX_EXPERIENCES, EXPERIENCE_FIELDS);
        for (Object item : experiences) {
            JSONObject experience = (JSONObject) item;
            validateFactField(experience, "company", source);
            validateFactField(experience, "position", source);
            validateFactField(experience, "start", source);
            validateFactField(experience, "end", source);
            validateTextField(experience, "description");
            validateTextField(experience, "achievements");
        }
    }

    private static void validateProjects(JSONArray projects, String source) {
        validateArray(projects, MAX_PROJECTS, PROJECT_FIELDS);
        for (Object item : projects) {
            JSONObject project = (JSONObject) item;
            validateFactField(project, "name", source);
            validateFactField(project, "role", source);
            validateFactField(project, "start", source);
            validateFactField(project, "end", source);
            validateTextField(project, "description");
            validateTextField(project, "achievements");
            validateTextField(project, "highlight");
        }
    }

    private static void validateEducation(JSONArray education, String source) {
        validateArray(education, MAX_EDUCATION, EDUCATION_FIELDS);
        for (Object item : education) {
            JSONObject record = (JSONObject) item;
            validateFactField(record, "school", source);
            validateFactField(record, "degree", source);
            validateFactField(record, "major", source);
            validateFactField(record, "start", source);
            validateFactField(record, "end", source);
            validateTextField(record, "experience");
        }
    }

    private static void validateArray(JSONArray array, int maxSize, Set<String> fields) {
        if (array == null || array.size() > maxSize) {
            throw exception(AGENT_MODEL_OUTPUT_INVALID);
        }
        for (Object item : array) {
            if (!(item instanceof JSONObject object)) {
                throw exception(AGENT_MODEL_OUTPUT_INVALID);
            }
            requireExactFields(object, fields);
        }
    }

    private static void validateFactField(JSONObject object, String field, String source) {
        String value = requireText(object, field, MAX_FIELD_LENGTH, false);
        if (StrUtil.isNotBlank(value) && !isContainedInSource(value, source)) {
            throw exception(AGENT_MODEL_OUTPUT_INVALID);
        }
    }

    private static void validateTextField(JSONObject object, String field) {
        requireText(object, field, MAX_FIELD_LENGTH, false);
    }

    private static String requireText(JSONObject object, String field, int maxLength, boolean nonBlank) {
        Object value = object.get(field);
        if (!(value instanceof String text) || text.length() > maxLength
                || (nonBlank && StrUtil.isBlank(text))) {
            throw exception(AGENT_MODEL_OUTPUT_INVALID);
        }
        return text;
    }

    private static boolean isText(String value, int maxLength) {
        return StrUtil.isNotBlank(value) && value.length() <= maxLength;
    }

    private static boolean isContainedInSource(String value, String source) {
        return normalize(value).length() > 0 && normalize(source).contains(normalize(value));
    }

    private static String normalize(String value) {
        return StrUtil.nullToEmpty(value).replaceAll("\\s+", "").toLowerCase();
    }

    private static boolean introducesNewNumber(String source, String result) {
        Set<String> sourceNumbers = extractNumbers(source);
        Matcher matcher = NUMBER.matcher(result);
        while (matcher.find()) {
            if (!sourceNumbers.contains(matcher.group())) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> extractNumbers(String text) {
        Set<String> numbers = new LinkedHashSet<>();
        Matcher matcher = NUMBER.matcher(StrUtil.nullToEmpty(text));
        while (matcher.find()) {
            numbers.add(matcher.group());
        }
        return numbers;
    }

    private static void requireExactFields(JSONObject object, Set<String> fields) {
        if (!object.keySet().equals(fields)) {
            throw exception(AGENT_MODEL_OUTPUT_INVALID);
        }
    }
}
