package cn.iocoder.txgy.module.hrm.framework.agent.core;

import cn.hutool.json.JSONObject;

import java.util.List;

/**
 * 职位匹配结果的结构化校验（A05 / A08）。
 *
 * <p>与诊断的区别：匹配聚焦「已满足 / 未满足 / 待确认」三类结论，
 * 且明确要求无法从简历判断的信息进入待确认，而不是被当作缺失。</p>
 *
 * <p>个人端（A05）与管理端（A08）共用同一套维度与结构，
 * 只有管理端额外要求风险项，因此校验逻辑只有一份实现。</p>
 */
public final class HrmJobMatchResultValidator {

    /** 必须完整出现的五个匹配维度 */
    private static final List<String> REQUIRED_DIMENSIONS =
            List.of("SKILL", "EXPERIENCE", "EDUCATION", "RESPONSIBILITY", "KEYWORD");

    private HrmJobMatchResultValidator() {
    }

    /**
     * 校验并返回结构化匹配结果（A05 个人端）。
     *
     * @param rawContent 模型返回的原始文本
     * @return 通过校验的 JSON 对象
     * @throws cn.iocoder.txgy.framework.common.exception.ServiceException 结构不合法
     */
    public static JSONObject validate(String rawContent) {
        return validateInternal(rawContent, false);
    }

    /**
     * 校验并返回结构化匹配结果（A08 管理端）。
     *
     * <p>管理端在个人端结构上额外要求 risks（风险点），其余完全一致。</p>
     */
    public static JSONObject validateWithRisks(String rawContent) {
        return validateInternal(rawContent, true);
    }

    private static JSONObject validateInternal(String rawContent, boolean withRisks) {
        JSONObject root = HrmRecruitAgentResultValidationSupport.parseResultObject(rawContent);
        HrmRecruitAgentResultValidationSupport.validateOverallScore(root);
        HrmRecruitAgentResultValidationSupport.validateDimensions(root, REQUIRED_DIMENSIONS);
        // 三类结论都允许为空，但字段必须存在，避免前端拿不到结构
        HrmRecruitAgentResultValidationSupport.validateObjectList(
                root, "matched", List.of("point", "evidence"));
        HrmRecruitAgentResultValidationSupport.validateObjectList(
                root, "missing", List.of("point", "evidence"));
        HrmRecruitAgentResultValidationSupport.validateObjectList(
                root, "toConfirm", List.of("point", "reason"));
        if (withRisks) {
            HrmRecruitAgentResultValidationSupport.validateTextList(root, "risks");
        }
        return root;
    }
}
