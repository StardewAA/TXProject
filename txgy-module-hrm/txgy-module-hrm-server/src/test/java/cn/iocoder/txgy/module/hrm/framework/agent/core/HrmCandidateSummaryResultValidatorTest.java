package cn.iocoder.txgy.module.hrm.framework.agent.core;

import cn.hutool.json.JSONObject;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_MODEL_OUTPUT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 候选人摘要结果结构化校验测试（A07）。
 *
 * <p>重点：事实（facts）必须指向真实存在的字段，防止把 AI 推断写成了简历事实。</p>
 */
public class HrmCandidateSummaryResultValidatorTest {

    private static final String VALID_RESULT = """
            {
              "coreSkills": ["管道保温施工", "现场施工组织"],
              "workYears": "简历未提供",
              "experienceSummary": "8 年防腐保温现场施工管理经验，负责过多个工业管道保温项目。",
              "achievements": ["累计交付 6 个项目"],
              "facts": [
                {"field":"experiences","note":"在某防腐工程有限公司任项目经理"},
                {"field":"degree","note":"本科学历"}
              ],
              "missingInfo": ["未提供期望到岗时间"],
              "inferences": ["长期驻外项目的接受度需要核实"]
            }
            """;

    @Test
    public void testValidate_acceptsCompleteResult() {
        JSONObject result = HrmCandidateSummaryResultValidator.validate(VALID_RESULT);
        assertEquals(2, result.getJSONArray("coreSkills").size());
        assertEquals("简历未提供", result.getStr("workYears"));
        assertEquals(2, result.getJSONArray("facts").size());
        assertEquals(1, result.getJSONArray("inferences").size());
    }

    @Test
    public void testValidate_acceptsEmptyLists() {
        String content = """
                {"coreSkills":[],"workYears":"简历未提供","experienceSummary":"简历内容较少",
                 "achievements":[],"facts":[],"missingInfo":[],"inferences":[]}
                """;
        JSONObject result = HrmCandidateSummaryResultValidator.validate(content);
        assertEquals(0, result.getJSONArray("facts").size());
    }

    @Test
    public void testValidate_rejectsUnknownFactField() {
        // facts 的 field 必须是负载白名单里的字段，编造的字段名一律拒绝
        String content = """
                {"coreSkills":[],"workYears":"简历未提供","experienceSummary":"摘要",
                 "achievements":[],
                 "facts":[{"field":"selfIntro","note":"自我介绍"}],
                 "missingInfo":[],"inferences":[]}
                """;
        ServiceException exception = assertThrows(ServiceException.class,
                () -> HrmCandidateSummaryResultValidator.validate(content));
        assertEquals(AGENT_MODEL_OUTPUT_INVALID.getCode(), exception.getCode());
    }

    @Test
    public void testValidate_rejectsMissingInferencesField() {
        // 三个分类字段缺一不可，否则无法区分事实与推断
        String content = """
                {"coreSkills":[],"workYears":"简历未提供","experienceSummary":"摘要",
                 "achievements":[],"facts":[],"missingInfo":[]}
                """;
        assertThrows(ServiceException.class,
                () -> HrmCandidateSummaryResultValidator.validate(content));
    }

    @Test
    public void testValidate_rejectsBlankWorkYears() {
        String content = """
                {"coreSkills":[],"workYears":" ","experienceSummary":"摘要",
                 "achievements":[],"facts":[],"missingInfo":[],"inferences":[]}
                """;
        assertThrows(ServiceException.class,
                () -> HrmCandidateSummaryResultValidator.validate(content));
    }

    @Test
    public void testValidate_rejectsSensitiveAttributeKey() {
        String content = """
                {"coreSkills":[],"workYears":"简历未提供","experienceSummary":"摘要",
                 "achievements":[],"facts":[],"missingInfo":[],"inferences":[],
                 "maritalStatus":"已婚"}
                """;
        assertThrows(ServiceException.class,
                () -> HrmCandidateSummaryResultValidator.validate(content));
    }

    @Test
    public void testValidate_stripsCodeFence() {
        String content = "```json\n" + VALID_RESULT + "\n```";
        JSONObject result = HrmCandidateSummaryResultValidator.validate(content);
        assertTrue(result.containsKey("experienceSummary"));
    }
}
