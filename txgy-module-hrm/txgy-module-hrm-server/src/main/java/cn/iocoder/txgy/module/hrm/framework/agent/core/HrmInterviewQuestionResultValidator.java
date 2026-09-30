package cn.iocoder.txgy.module.hrm.framework.agent.core;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;

import java.util.List;
import java.util.Set;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_MODEL_OUTPUT_INVALID;

/**
 * 面试问题建议结果的结构化校验（A09）。
 *
 * <p>问题类型必须落在四类之内，每类问题都要说明提问目的与关注点。
 * 结果里只允许有问题，不允许出现评价结论或录用建议。</p>
 */
public final class HrmInterviewQuestionResultValidator {

    /** 允许的问题类型：专业、项目、岗位能力、核实 */
    private static final Set<String> ALLOWED_CATEGORIES =
            Set.of("PROFESSIONAL", "PROJECT", "COMPETENCY", "VERIFICATION");

    private HrmInterviewQuestionResultValidator() {
    }

    /**
     * 校验并返回结构化问题清单。
     *
     * @param rawContent 模型返回的原始文本
     * @return 通过校验的 JSON 对象
     * @throws cn.iocoder.txgy.framework.common.exception.ServiceException 结构不合法
     */
    public static JSONObject validate(String rawContent) {
        JSONObject root = HrmRecruitAgentResultValidationSupport.parseResultObject(rawContent);
        HrmRecruitAgentResultValidationSupport.validateObjectList(
                root, "questions", List.of("category", "question", "purpose", "focus"));
        JSONArray questions = root.getJSONArray("questions");
        for (Object item : questions) {
            String category = ((JSONObject) item).getStr("category");
            if (category == null || !ALLOWED_CATEGORIES.contains(category)) {
                throw exception(AGENT_MODEL_OUTPUT_INVALID);
            }
        }
        return root;
    }
}
