package cn.iocoder.txgy.module.hrm.framework.agent.core;

import cn.hutool.json.JSONObject;

import java.util.List;
import java.util.Set;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_MODEL_OUTPUT_INVALID;

/**
 * 简历诊断结果的结构化校验（A01）。
 *
 * <p>模型输出属于不可信数据：字段、类型、取值范围、长度与依据字段都要逐一校验，
 * 任何一项不合格都让整个任务失败，不产出半成品结论。</p>
 */
public final class HrmResumeAnalyzeResultValidator {

    /** 有目标职位时必须完整出现的四个诊断维度 */
    private static final List<String> REQUIRED_DIMENSIONS =
            List.of("COMPLETENESS", "CLARITY", "PROFESSIONALISM", "JOB_MATCH");

    /**
     * 不指定职位、对整份简历做诊断时的六个维度。
     *
     * <p>与上面那组的区别：去掉 JOB_MATCH（本来就没有可比对的职位），
     * 拆出 STAR 应用、内容可读、成果量化三项 —— 这三项在「简历本身写得好不好」
     * 这个问题上各自独立成维度，混在 CLARITY/PROFESSIONALISM 里看不出差距。</p>
     */
    private static final List<String> REQUIRED_FULL_DIMENSIONS = List.of(
            "CLARITY", "STAR", "READABILITY", "COMPLETENESS", "PROFESSIONALISM", "QUANTIFICATION");


    /** 允许的建议模块 */
    private static final Set<String> ALLOWED_MODULES = Set.of(
            "BASIC_INFO", "JOB_INTENTION", "EDUCATION",
            "WORK_EXPERIENCE", "PROJECT_EXPERIENCE", "SKILLS", "SELF_INTRO");

    private HrmResumeAnalyzeResultValidator() {
    }

    /**
     * 校验并返回结构化结果。
     *
     * @param rawContent 模型返回的原始文本
     * @return 通过校验的 JSON 对象
     * @throws cn.iocoder.txgy.framework.common.exception.ServiceException 结构不合法
     */
    public static JSONObject validate(String rawContent) {
        return validate(rawContent, REQUIRED_DIMENSIONS);
    }

    /**
     * 校验「整份简历」诊断结果：维度集合换成 {@link #REQUIRED_FULL_DIMENSIONS}。
     *
     * <p>其余字段要求与带职位的诊断完全一致，因此共用同一段实现。</p>
     */
    public static JSONObject validateFull(String rawContent) {
        return validate(rawContent, REQUIRED_FULL_DIMENSIONS);
    }

    private static JSONObject validate(String rawContent, List<String> requiredDimensions) {
        JSONObject root = HrmRecruitAgentResultValidationSupport.parseResultObject(rawContent);
        HrmRecruitAgentResultValidationSupport.validateOverallScore(root);
        HrmRecruitAgentResultValidationSupport.validateDimensions(root, requiredDimensions);
        HrmRecruitAgentResultValidationSupport.validateTextList(root, "strengths");
        HrmRecruitAgentResultValidationSupport.validateTextList(root, "missingInfo");
        HrmRecruitAgentResultValidationSupport.validateTextList(root, "risks");
        validateSuggestions(root);
        return root;
    }

    private static void validateSuggestions(JSONObject root) {
        HrmRecruitAgentResultValidationSupport.validateObjectList(
                root, "suggestions", List.of("module", "fieldPath", "advice", "reason"));
        var suggestions = root.getJSONArray("suggestions");
        for (Object item : suggestions) {
            JSONObject suggestion = (JSONObject) item;
            String module = suggestion.getStr("module");
            if (module == null || !ALLOWED_MODULES.contains(module)) {
                throw exception(AGENT_MODEL_OUTPUT_INVALID);
            }
            String fieldPath = suggestion.getStr("fieldPath");
            if (fieldPath == null
                    || !HrmRecruitAgentResultValidationSupport.ALLOWED_EVIDENCE_FIELDS.contains(fieldPath)) {
                throw exception(AGENT_MODEL_OUTPUT_INVALID);
            }
        }
    }
}
