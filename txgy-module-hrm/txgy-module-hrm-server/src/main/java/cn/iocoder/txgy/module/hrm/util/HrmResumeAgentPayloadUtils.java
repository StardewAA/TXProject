package cn.iocoder.txgy.module.hrm.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 简历内容 → 可送入模型的负载。
 *
 * <p>采用白名单：只有与岗位匹配直接相关的字段会进入模型输入，其余一律丢弃。
 * 这样即使简历里存在性别、年龄、民族等字段，也不可能进入提示词、评分或排序。</p>
 *
 * <p>自由文本字段会再做一次脱敏，脱敏后仍能识别出敏感信息时直接抛异常（失败关闭）。</p>
 */
public final class HrmResumeAgentPayloadUtils {

    private static final int MIN_RESUME_TEXT_LENGTH = 50;
    private static final int MAX_RESUME_TEXT_LENGTH = 8000;

    /**
     * 明确禁止进入模型的属性字段。
     *
     * <p>既用于白名单兜底，也用于「输入里出现了这些字段」时的显式断言。</p>
     */
    public static final Set<String> FORBIDDEN_ATTRIBUTE_FIELDS = Set.of(
            "gender", "sex", "age", "birthMonth", "birthday", "birthDate",
            "nation", "ethnicity", "ethnic", "religion", "maritalStatus", "marriageStatus",
            "fertility", "childbearing", "disability", "disabled",
            "hukou", "domicile", "domicileAreaId", "address", "homeAddress",
            "idCard", "idNumber", "idNo", "identityCard",
            "mobile", "phone", "telephone", "email", "name", "realName",
            "photo", "avatar", "height", "weight", "politicalStatus");

    /** 与岗位匹配相关的标量字段 */
    private static final List<String> ALLOWED_SCALAR_FIELDS = List.of(
            "strengths", "skills", "identity", "employmentStatus", "jobType",
            "salary", "expectedPostName", "postTypeName", "expectedIndustry",
            "industry", "city", "expectedCities");

    /** 需要脱敏的自由文本字段 */
    private static final Set<String> FREE_TEXT_FIELDS = Set.of(
            "strengths", "skills", "description", "achievements", "experience", "name");

    private static final Map<String, List<String>> ALLOWED_ARRAY_FIELDS = new LinkedHashMap<>();

    static {
        ALLOWED_ARRAY_FIELDS.put("experiences", List.of(
                "company", "industry", "department", "position",
                "start", "end", "description", "achievements"));
        ALLOWED_ARRAY_FIELDS.put("projects", List.of(
                "name", "role", "start", "end", "description", "achievements", "highlight"));
        ALLOWED_ARRAY_FIELDS.put("educationExperiences", List.of(
                "degree", "school", "major", "schoolType", "start", "end", "experience"));
        // 下面三块是简历模板骨架里的小节（对齐参考站的简历结构）。
        // 不加进来的话，用户填了实习经历、社团经历、荣誉，模型却完全看不到——
        // 界面上能填能显示，诊断结论里却当它不存在，是最难被发现的那种漏。
        ALLOWED_ARRAY_FIELDS.put("internships", List.of(
                "company", "industry", "department", "position",
                "start", "end", "description", "achievements"));
        ALLOWED_ARRAY_FIELDS.put("clubs", List.of(
                "name", "role", "start", "end", "description"));
        ALLOWED_ARRAY_FIELDS.put("honors", List.of("name", "date"));
    }

    private HrmResumeAgentPayloadUtils() {
    }

    /**
     * 允许作为结论依据的字段名全集。
     *
     * <p>这是提示词与结构化校验的**唯一来源**：
     * 提示词把它列给模型，校验器用它做白名单。两者共用一个来源，
     * 避免「提示词让模型用某字段、校验器却把它判为非法」这类漂移。</p>
     */
    public static Set<String> agentAllowedFieldNames() {
        Set<String> names = new java.util.LinkedHashSet<>(ALLOWED_SCALAR_FIELDS);
        ALLOWED_ARRAY_FIELDS.forEach((arrayName, elementFields) -> {
            names.add(arrayName);
            names.addAll(elementFields);
        });
        // 职位负载字段
        names.addAll(List.of("postName", "description", "educationRequire", "workTime", "jobNature"));
        // 与数组元素同义的通用字段名，便于模型引用单条经历
        names.addAll(List.of("company", "position", "department", "start", "end",
                "name", "role", "degree", "school", "major", "schoolType", "experience"));
        names.add("resumeText");
        return java.util.Collections.unmodifiableSet(names);
    }

    /**
     * 可作为「简历事实」依据的字段名（A07 候选人摘要专用）。
     *
     * <p>与 {@link #agentAllowedFieldNames()} 的区别：这里**排除求职意向类偏好字段**
     * （期望职位、期望城市、薪资、到岗状态、工作性质等）。这些是候选人的期望，
     * 不是他的经历事实，实测模型会把它们当成 facts 逐条罗列，导致摘要变成字段清单。</p>
     */
    public static Set<String> candidateFactFieldNames() {
        Set<String> names = new java.util.LinkedHashSet<>(List.of(
                // 工作与项目经历
                "experiences", "projects", "company", "department", "position", "role",
                "start", "end", "description", "achievements", "highlight", "name",
                // 教育经历
                "educationExperiences", "degree", "school", "major", "schoolType", "experience",
                // 能力类自述
                "skills", "strengths"));
        return java.util.Collections.unmodifiableSet(names);
    }

    /**
     * 构建可送入模型的简历负载。
     *
     * @param contentJson 在线简历的 JSON 内容
     * @return 只含白名单字段、且已脱敏的负载；内容为空时返回空对象
     * @throws SensitiveMaskFailedException 脱敏后仍能识别出敏感信息
     */
    public static JSONObject buildAgentSafePayload(String contentJson) {
        JSONObject payload = new JSONObject();
        if (StrUtil.isBlank(contentJson)) {
            return payload;
        }
        if (!contentJson.trim().startsWith("{")) {
            String text = HrmSensitiveTextMasker.maskResumeText(contentJson.trim());
            if (text.length() < MIN_RESUME_TEXT_LENGTH) return payload;
            payload.set("resumeText", text.substring(0, Math.min(MAX_RESUME_TEXT_LENGTH, text.length())));
            if (containsSensitive(payload)) throw new SensitiveMaskFailedException();
            return payload;
        }
        JSONObject source;
        try {
            source = JSONUtil.parseObj(contentJson);
        } catch (RuntimeException ex) {
            // 内容无法解析时不猜测，返回空负载由调用方判定内容不足
            return payload;
        }
        for (String field : ALLOWED_SCALAR_FIELDS) {
            Object value = source.get(field);
            if (value == null) {
                continue;
            }
            payload.set(field, sanitizeValue(field, value));
        }
        for (Map.Entry<String, List<String>> entry : ALLOWED_ARRAY_FIELDS.entrySet()) {
            Object arrayValue = source.get(entry.getKey());
            if (!(arrayValue instanceof JSONArray sourceArray)) {
                continue;
            }
            JSONArray targetArray = new JSONArray();
            for (Object item : sourceArray) {
                if (!(item instanceof JSONObject sourceItem)) {
                    continue;
                }
                JSONObject targetItem = new JSONObject();
                for (String field : entry.getValue()) {
                    Object value = sourceItem.get(field);
                    if (value != null) {
                        targetItem.set(field, sanitizeValue(field, value));
                    }
                }
                if (!targetItem.isEmpty()) {
                    targetArray.add(targetItem);
                }
            }
            payload.set(entry.getKey(), targetArray);
        }
        // 失败关闭：脱敏后仍能识别出敏感信息就不允许继续
        if (containsSensitive(payload)) {
            throw new SensitiveMaskFailedException();
        }
        return payload;
    }

    /**
     * 负载中是否仍存在可识别的敏感信息。
     */
    public static boolean containsSensitive(Object node) {
        if (node == null) {
            return false;
        }
        if (node instanceof JSONObject object) {
            return object.values().stream().anyMatch(HrmResumeAgentPayloadUtils::containsSensitive);
        }
        if (node instanceof JSONArray array) {
            return array.stream().anyMatch(HrmResumeAgentPayloadUtils::containsSensitive);
        }
        return node instanceof String text && HrmSensitiveTextMasker.stillContainsSensitive(text);
    }

    /** 输入的原始字段名中是否出现了禁止进入模型的属性 */
    public static Set<String> findForbiddenFields(String contentJson) {
        Set<String> found = new java.util.LinkedHashSet<>();
        if (StrUtil.isBlank(contentJson)) {
            return found;
        }
        JSONObject source;
        try {
            source = JSONUtil.parseObj(contentJson);
        } catch (RuntimeException ex) {
            return found;
        }
        collectForbiddenFields(source, found);
        return found;
    }

    /**
     * 构建可送入模型的职位负载。
     *
     * <p>职位正文同样按不可信数据处理：只取影响岗位匹配判断的字段，
     * 描述类文本先脱敏（职位描述里偶尔会夹带联系人电话）。</p>
     */
    public static JSONObject buildAgentSafePostPayload(
            cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.post.HrmRecruitPostDO post) {
        JSONObject payload = new JSONObject();
        if (post == null) {
            return payload;
        }
        payload.set("postName", StrUtil.nullToEmpty(post.getPostName()));
        payload.set("description", HrmSensitiveTextMasker.mask(StrUtil.nullToEmpty(post.getDescription())));
        if (post.getEducationRequire() != null) {
            payload.set("educationRequire", post.getEducationRequire());
        }
        if (post.getWorkTime() != null) {
            payload.set("workTime", post.getWorkTime());
        }
        if (post.getJobNature() != null) {
            payload.set("jobNature", post.getJobNature());
        }
        return payload;
    }

    private static void collectForbiddenFields(Object node, Set<String> found) {
        if (node instanceof JSONObject object) {
            for (Map.Entry<String, Object> entry : object.entrySet()) {
                if (FORBIDDEN_ATTRIBUTE_FIELDS.contains(entry.getKey())) {
                    found.add(entry.getKey());
                }
                collectForbiddenFields(entry.getValue(), found);
            }
        } else if (node instanceof JSONArray array) {
            for (Object item : array) {
                collectForbiddenFields(item, found);
            }
        }
    }

    private static Object sanitizeValue(String field, Object value) {
        if (!(value instanceof String text)) {
            return value;
        }
        // 结构化字段按类型脱敏；自由文本统一走文本脱敏
        return switch (field) {
            case "mobile", "phone" -> HrmSensitiveTextMasker.maskMobile(text);
            case "email" -> HrmSensitiveTextMasker.maskEmail(text);
            case "idCard", "idNumber" -> HrmSensitiveTextMasker.maskIdCard(text);
            default -> FREE_TEXT_FIELDS.contains(field)
                    ? HrmSensitiveTextMasker.mask(text)
                    : text;
        };
    }

    /** 脱敏失败：调用方必须阻止本次模型调用 */
    public static class SensitiveMaskFailedException extends RuntimeException {
        public SensitiveMaskFailedException() {
            super("敏感信息脱敏失败，已阻止本次模型调用");
        }
    }
}
