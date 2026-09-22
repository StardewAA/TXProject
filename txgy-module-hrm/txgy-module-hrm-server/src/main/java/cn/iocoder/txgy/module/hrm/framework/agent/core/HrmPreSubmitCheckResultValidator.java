package cn.iocoder.txgy.module.hrm.framework.agent.core;

import cn.hutool.json.JSONObject;

import java.util.List;

/**
 * 投递前检查结果的结构化校验（A06）。
 *
 * <p>刻意不校验任何分数：本场景只做提醒，不产出「通过 / 不通过」的判定，
 * 因此结果里也不允许出现 overallScore 这类会被误读为闸门的字段。</p>
 */
public final class HrmPreSubmitCheckResultValidator {

    private HrmPreSubmitCheckResultValidator() {
    }

    /**
     * 校验并返回结构化检查结果。
     *
     * @param rawContent 模型返回的原始文本
     * @return 通过校验的 JSON 对象
     * @throws cn.iocoder.txgy.framework.common.exception.ServiceException 结构不合法
     */
    public static JSONObject validate(String rawContent) {
        JSONObject root = HrmRecruitAgentResultValidationSupport.parseResultObject(rawContent);
        HrmRecruitAgentResultValidationSupport.requireText(root.getStr("summary"));
        // 三个列表允许为空，但字段必须存在，避免前端拿不到结构
        HrmRecruitAgentResultValidationSupport.validateObjectList(
                root, "requiredMissing", List.of("item", "note"));
        HrmRecruitAgentResultValidationSupport.validateObjectList(
                root, "conflicts", List.of("point", "evidence"));
        HrmRecruitAgentResultValidationSupport.validateObjectList(
                root, "gaps", List.of("point", "evidence"));
        return root;
    }
}
