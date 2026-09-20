package cn.iocoder.txgy.module.hrm.framework.agent.core;

import cn.hutool.json.JSONObject;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_MODEL_OUTPUT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 投递前检查结果结构化校验测试（A06）。
 *
 * <p>重点：只做提醒，因此不校验分数；三个列表可以为空，但字段必须存在。</p>
 */
public class HrmPreSubmitCheckResultValidatorTest {

    private static final String VALID_RESULT = """
            {
              "summary": "整体信息完整，建议补充项目量化成果后再投递",
              "requiredMissing": [
                {"item":"缺少期望到岗时间","note":"招聘方通常需要据此安排面试与入职"}
              ],
              "conflicts": [
                {"point":"工作年限与经历起止时间对不上","evidence":"写着 8 年经验，但经历从 2020 年开始"}
              ],
              "gaps": [
                {"point":"未体现压力管道安装资质","evidence":"职位描述要求具备该资质"}
              ]
            }
            """;

    @Test
    public void testValidate_acceptsCompleteResult() {
        JSONObject result = HrmPreSubmitCheckResultValidator.validate(VALID_RESULT);
        assertTrue(result.getStr("summary").contains("建议补充"));
        assertEquals(1, result.getJSONArray("requiredMissing").size());
        assertEquals(1, result.getJSONArray("conflicts").size());
        assertEquals(1, result.getJSONArray("gaps").size());
    }

    @Test
    public void testValidate_acceptsEmptyLists() {
        String content = """
                {"summary":"本次没有发现明显问题",
                 "requiredMissing":[],"conflicts":[],"gaps":[]}
                """;
        JSONObject result = HrmPreSubmitCheckResultValidator.validate(content);
        assertEquals(0, result.getJSONArray("requiredMissing").size());
    }

    @Test
    public void testValidate_rejectsMissingList() {
        // 字段缺失会让前端拿不到结构，必须整体失败
        String content = """
                {"summary":"整体可用","requiredMissing":[],"conflicts":[]}
                """;
        assertThrows(ServiceException.class,
                () -> HrmPreSubmitCheckResultValidator.validate(content));
    }

    @Test
    public void testValidate_rejectsBlankSummary() {
        String content = """
                {"summary":"   ","requiredMissing":[],"conflicts":[],"gaps":[]}
                """;
        ServiceException exception = assertThrows(ServiceException.class,
                () -> HrmPreSubmitCheckResultValidator.validate(content));
        assertEquals(AGENT_MODEL_OUTPUT_INVALID.getCode(), exception.getCode());
    }

    @Test
    public void testValidate_rejectsSensitiveAttributeKey() {
        // 结果里出现敏感属性字段名，说明模型用无关属性做了判断，直接判定不合格
        String content = """
                {"summary":"建议补充信息","requiredMissing":[],"conflicts":[],
                 "gaps":[{"point":"年龄偏大","evidence":"简历显示 45 岁","age":45}]}
                """;
        assertThrows(ServiceException.class,
                () -> HrmPreSubmitCheckResultValidator.validate(content));
    }

    @Test
    public void testValidate_stripsCodeFence() {
        String content = "```json\n" + VALID_RESULT + "\n```";
        JSONObject result = HrmPreSubmitCheckResultValidator.validate(content);
        assertTrue(result.containsKey("summary"));
    }
}
