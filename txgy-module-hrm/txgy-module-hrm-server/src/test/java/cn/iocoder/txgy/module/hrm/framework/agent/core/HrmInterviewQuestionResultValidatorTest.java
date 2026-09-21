package cn.iocoder.txgy.module.hrm.framework.agent.core;

import cn.hutool.json.JSONObject;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_MODEL_OUTPUT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 面试问题建议结果结构化校验测试（A09）。
 *
 * <p>重点：问题类型必须落在四类之内，每类问题都要有目的与关注点。</p>
 */
public class HrmInterviewQuestionResultValidatorTest {

    private static final String VALID_RESULT = """
            {
              "questions": [
                {"category":"PROFESSIONAL","question":"压力管道保温层的厚度如何确定？",
                 "purpose":"核实专业知识深度","focus":"是否了解保温计算依据"},
                {"category":"PROJECT","question":"请介绍一个你负责的管道保温项目，你承担了什么角色？",
                 "purpose":"了解项目经历真实性","focus":"角色边界与个人贡献"},
                {"category":"COMPETENCY","question":"项目工期与甲方要求冲突时你怎么处理？",
                 "purpose":"考察协调与问题解决能力","focus":"处理思路与结果"},
                {"category":"VERIFICATION","question":"简历提到二级建造师证，是否已注册？",
                 "purpose":"核实证书状态","focus":"注册单位与有效期"}
              ]
            }
            """;

    @Test
    public void testValidate_acceptsCompleteResult() {
        JSONObject result = HrmInterviewQuestionResultValidator.validate(VALID_RESULT);
        assertEquals(4, result.getJSONArray("questions").size());
    }

    @Test
    public void testValidate_acceptsEmptyQuestions() {
        // 允许为空：由前端如实提示「本次没有生成问题」，不制造假内容
        JSONObject result = HrmInterviewQuestionResultValidator.validate("{\"questions\":[]}");
        assertEquals(0, result.getJSONArray("questions").size());
    }

    @Test
    public void testValidate_rejectsUnknownCategory() {
        String content = """
                {"questions":[{"category":"PERSONAL","question":"你结婚了吗？",
                  "purpose":"了解稳定性","focus":"婚育情况"}]}
                """;
        ServiceException exception = assertThrows(ServiceException.class,
                () -> HrmInterviewQuestionResultValidator.validate(content));
        assertEquals(AGENT_MODEL_OUTPUT_INVALID.getCode(), exception.getCode());
    }

    @Test
    public void testValidate_rejectsMissingFocus() {
        // 缺少关注点的问题无法判断该听什么，必须整体失败
        String content = """
                {"questions":[{"category":"PROFESSIONAL","question":"介绍一下保温工艺",
                  "purpose":"考察专业性"}]}
                """;
        assertThrows(ServiceException.class,
                () -> HrmInterviewQuestionResultValidator.validate(content));
    }

    @Test
    public void testValidate_rejectsTooManyQuestions() {
        StringBuilder builder = new StringBuilder("{\"questions\":[");
        for (int index = 0; index < 11; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append("{\"category\":\"PROFESSIONAL\",\"question\":\"问题")
                    .append(index).append("\",\"purpose\":\"目的\",\"focus\":\"关注点\"}");
        }
        builder.append("]}");
        assertThrows(ServiceException.class,
                () -> HrmInterviewQuestionResultValidator.validate(builder.toString()));
    }

    @Test
    public void testValidate_rejectsSensitiveAttributeKey() {
        String content = """
                {"questions":[{"category":"COMPETENCY","question":"能否接受出差？",
                  "purpose":"了解适配度","focus":"出差意愿","childbearing":"计划生育"}]}
                """;
        assertThrows(ServiceException.class,
                () -> HrmInterviewQuestionResultValidator.validate(content));
    }

    @Test
    public void testValidate_stripsCodeFence() {
        String content = "```json\n" + VALID_RESULT + "\n```";
        JSONObject result = HrmInterviewQuestionResultValidator.validate(content);
        assertTrue(result.containsKey("questions"));
    }
}
