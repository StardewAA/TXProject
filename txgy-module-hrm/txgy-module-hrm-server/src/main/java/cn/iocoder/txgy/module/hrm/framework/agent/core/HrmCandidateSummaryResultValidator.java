package cn.iocoder.txgy.module.hrm.framework.agent.core;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.iocoder.txgy.module.hrm.util.HrmResumeAgentPayloadUtils;

import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_MODEL_OUTPUT_INVALID;

/**
 * 候选人摘要结果的结构化校验（A07）。
 *
 * <p>摘要的关键风险是「把 AI 推断写成了简历事实」，因此 facts 里的 field
 * 必须落在允许的字段词表内；推断类内容只能出现在 inferences 里。</p>
 */
public final class HrmCandidateSummaryResultValidator {

    private HrmCandidateSummaryResultValidator() {
    }

    /**
     * 校验并返回结构化摘要。
     *
     * @param rawContent 模型返回的原始文本
     * @return 通过校验的 JSON 对象
     * @throws cn.iocoder.txgy.framework.common.exception.ServiceException 结构不合法
     */
    public static JSONObject validate(String rawContent) {
        JSONObject root = HrmRecruitAgentResultValidationSupport.parseResultObject(rawContent);
        HrmRecruitAgentResultValidationSupport.validateTextList(root, "coreSkills");
        HrmRecruitAgentResultValidationSupport.requireText(root.getStr("workYears"));
        HrmRecruitAgentResultValidationSupport.requireText(root.getStr("experienceSummary"));
        HrmRecruitAgentResultValidationSupport.validateTextList(root, "achievements");
        HrmRecruitAgentResultValidationSupport.validateTextList(root, "missingInfo");
        HrmRecruitAgentResultValidationSupport.validateTextList(root, "inferences");
        validateFacts(root);
        return root;
    }

    /** 事实项必须带真实存在的字段名，防止模型用无关属性充当事实依据 */
    private static void validateFacts(JSONObject root) {
        HrmRecruitAgentResultValidationSupport.validateObjectList(
                root, "facts", List.of("field", "note"));
        JSONArray facts = root.getJSONArray("facts");
        for (Object item : facts) {
            String field = HrmRecruitAgentResultValidationSupport
                    .requireString((JSONObject) item, "field");
            // 只接受经历、项目、学历与技能类字段；求职意向类偏好字段不算事实
            if (!HrmResumeAgentPayloadUtils.candidateFactFieldNames().contains(field)) {
                throw exception(AGENT_MODEL_OUTPUT_INVALID);
            }
        }
    }
}
