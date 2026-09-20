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

    /** 必须完整出现的四个诊断维度 */
    private static final List<String> REQUIRED_DIMENSIONS =
            List.of("COMPLETENESS", "CLARITY", "PROFESSIONALISM", "JOB_MATCH");

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
        JSONObject root = HrmRecruitAgentResultValidationSupport.parseResultObject(rawContent);
        HrmRecruitAgentResultValidationSupport.validateOverallScore(root);
        HrmRecruitAgentResultValidationSupport.validateDimensions(root, REQUIRED_DIMENSIONS);
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
