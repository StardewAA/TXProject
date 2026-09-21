package cn.iocoder.txgy.module.hrm.framework.agent.core;

import cn.hutool.json.JSONObject;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_MODEL_OUTPUT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 简历诊断结果结构化校验测试。
 *
 * <p>模型输出是不可信数据：字段、范围、长度、依据来源以及敏感属性都必须被拦截。</p>
 */
public class HrmResumeAnalyzeResultValidatorTest {

    /** 一份结构完整、合法的诊断结果 */
    private static final String VALID_RESULT = """
            {
              "overallScore": 78,
              "dimensions": [
                {"key":"COMPLETENESS","score":80,"comment":"信息较完整",
                 "evidence":[{"field":"experiences","note":"包含 3 段工作经历"}]},
                {"key":"CLARITY","score":75,"comment":"表述清晰",
                 "evidence":[{"field":"skills","note":"技能描述具体"}]},
                {"key":"PROFESSIONALISM","score":70,"comment":"专业性尚可",
                 "evidence":[{"field":"projects","note":"项目成果有量化"}]},
                {"key":"JOB_MATCH","score":85,"comment":"与岗位较匹配",
                 "evidence":[{"field":"expectedPostName","note":"求职意向与岗位一致"}]}
              ],
              "strengths": ["工作经历连续", "项目成果有量化"],
              "missingInfo": ["缺少证书信息"],
              "risks": ["近两段经历时间有重叠"],
              "suggestions": [
                {"module":"SKILLS","fieldPath":"skills","advice":"补充具体工艺与设备",
                 "reason":"岗位要求管道保温施工经验"}
              ]
            }
            """;

    @Test
    public void testValidate_acceptsCompleteResult() {
        JSONObject result = HrmResumeAnalyzeResultValidator.validate(VALID_RESULT);
        assertEquals(78, result.getInt("overallScore"));
        assertEquals(4, result.getJSONArray("dimensions").size());
        assertNotNull(result.getJSONArray("strengths"));
    }

    @Test
    public void testValidate_stripsMarkdownCodeFence() {
        String fenced = "```json\n" + VALID_RESULT + "\n```";
        JSONObject result = HrmResumeAnalyzeResultValidator.validate(fenced);
        assertEquals(78, result.getInt("overallScore"));
    }

    @Test
    public void testValidate_rejectsNonJson() {
        assertInvalid("这不是 JSON");
        assertInvalid("");
        assertInvalid(null);
        assertInvalid("[1,2,3]");
    }

    @Test
    public void testValidate_rejectsMissingDimension() {
        // 缺少 JOB_MATCH：只有总分和部分分项不能算通过
        String content = """
                {
                  "overallScore": 78,
                  "dimensions": [
                    {"key":"COMPLETENESS","score":80,"comment":"信息较完整",
                     "evidence":[{"field":"experiences","note":"包含 3 段工作经历"}]},
                    {"key":"CLARITY","score":75,"comment":"表述清晰",
                     "evidence":[{"field":"skills","note":"技能描述具体"}]},
                    {"key":"PROFESSIONALISM","score":70,"comment":"专业性尚可",
                     "evidence":[{"field":"projects","note":"项目成果有量化"}]}
                  ],
                  "strengths": ["工作经历连续"],
                  "missingInfo": [],
                  "risks": [],
                  "suggestions": []
                }
                """;
        assertInvalid(content);
    }

    @Test
    public void testValidate_rejectsEmptyDimensions() {
        String content = """
                {"overallScore": 60, "dimensions": [], "strengths": [],
                 "missingInfo": [], "risks": [], "suggestions": []}
                """;
        assertInvalid(content);
    }

    @Test
    public void testValidate_rejectsOutOfRangeScore() {
        assertInvalid(VALID_RESULT.replace("\"overallScore\": 78", "\"overallScore\": 101"));
        assertInvalid(VALID_RESULT.replace("\"overallScore\": 78", "\"overallScore\": -1"));
        assertInvalid(VALID_RESULT.replace("\"score\":80", "\"score\":200"));
    }

    @Test
    public void testValidate_rejectsMissingOrEmptyEvidence() {
        // 结论必须可追溯，不能只给分数
        assertInvalid(VALID_RESULT.replace(
                "\"evidence\":[{\"field\":\"experiences\",\"note\":\"包含 3 段工作经历\"}]",
                "\"evidence\":[]"));
        assertInvalid(VALID_RESULT.replace(
                "\"evidence\":[{\"field\":\"experiences\",\"note\":\"包含 3 段工作经历\"}]",
                "\"evidence\":null"));
    }

    @Test
    public void testValidate_rejectsEvidenceFieldOutsideWhitelist() {
        assertInvalid(VALID_RESULT.replace("\"field\":\"experiences\"", "\"field\":\"secretField\""));
    }

    @Test
    public void testValidate_rejectsForbiddenAttributeInResult() {
        // 结论中出现敏感属性字段，说明模型用无关属性做了判断
        assertInvalid(VALID_RESULT.replace("\"overallScore\": 78",
                "\"overallScore\": 78, \"gender\": 2"));
        assertInvalid(VALID_RESULT.replace("\"risks\": [\"近两段经历时间有重叠\"]",
                "\"risks\": [\"近两段经历时间有重叠\"], \"age\": 31"));
    }

    @Test
    public void testValidate_rejectsInvalidSuggestionModuleOrField() {
        assertInvalid(VALID_RESULT.replace("\"module\":\"SKILLS\"", "\"module\":\"UNKNOWN_MODULE\""));
        assertInvalid(VALID_RESULT.replace("\"fieldPath\":\"skills\"", "\"fieldPath\":\"unknownPath\""));
    }

    @Test
    public void testValidate_rejectsBlankAndOverlongText() {
        assertInvalid(VALID_RESULT.replace("\"missingInfo\": [\"缺少证书信息\"]",
                "\"missingInfo\": [\"   \"]"));
        assertInvalid(VALID_RESULT.replace("\"risks\": [\"近两段经历时间有重叠\"]",
                "\"risks\": [\"" + "长".repeat(501) + "\"]"));
    }

    @Test
    public void testValidate_rejectsTooManyItems() {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            if (i > 0) items.append(',');
            items.append("\"优势").append(i).append("\"");
        }
        assertInvalid(VALID_RESULT.replace("[\"工作经历连续\", \"项目成果有量化\"]", "[" + items + "]"));
    }

    private void assertInvalid(String content) {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> HrmResumeAnalyzeResultValidator.validate(content));
        assertEquals(AGENT_MODEL_OUTPUT_INVALID.getCode(), exception.getCode());
    }
}
