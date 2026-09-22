package cn.iocoder.txgy.module.hrm.framework.agent.core;

import cn.hutool.json.JSONObject;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_MODEL_OUTPUT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 职位匹配结果结构化校验测试（A05）。
 */
public class HrmJobMatchResultValidatorTest {

    private static final String VALID_RESULT = """
            {
              "overallScore": 76,
              "dimensions": [
                {"key":"SKILL","score":80,"comment":"技能基本覆盖",
                 "evidence":[{"field":"skills","note":"具备管道保温施工技能"}]},
                {"key":"EXPERIENCE","score":75,"comment":"经验年限满足",
                 "evidence":[{"field":"experiences","note":"8 年现场施工管理经验"}]},
                {"key":"EDUCATION","score":90,"comment":"学历满足要求",
                 "evidence":[{"field":"degree","note":"本科"}]},
                {"key":"RESPONSIBILITY","score":70,"comment":"职责部分重合",
                 "evidence":[{"field":"description","note":"负责过施工组织与进度管控"}]},
                {"key":"KEYWORD","score":65,"comment":"关键词覆盖一般",
                 "evidence":[{"field":"postName","note":"岗位名含防腐保温"}]}
              ],
              "matched": [
                {"point":"具备 5 年以上现场施工管理经验","evidence":"8 年经验，负责过 6 个项目"}
              ],
              "missing": [
                {"point":"未体现接受长期出差","evidence":"简历未提及出差意愿"}
              ],
              "toConfirm": [
                {"point":"是否持有一级建造师证","reason":"简历只提到二级建造师"}
              ]
            }
            """;

    @Test
    public void testValidate_acceptsCompleteResult() {
        JSONObject result = HrmJobMatchResultValidator.validate(VALID_RESULT);
        assertEquals(76, result.getInt("overallScore"));
        assertEquals(5, result.getJSONArray("dimensions").size());
        assertEquals(1, result.getJSONArray("matched").size());
        assertEquals(1, result.getJSONArray("missing").size());
        assertEquals(1, result.getJSONArray("toConfirm").size());
    }

    @Test
    public void testValidate_acceptsEmptyConclusionLists() {
        // 三类结论允许为空数组，但字段必须存在，否则前端拿不到结构
        String content = """
                {
                  "overallScore": 76,
                  "dimensions": [
                    {"key":"SKILL","score":80,"comment":"技能基本覆盖",
                     "evidence":[{"field":"skills","note":"具备管道保温施工技能"}]},
                    {"key":"EXPERIENCE","score":75,"comment":"经验年限满足",
                     "evidence":[{"field":"experiences","note":"8 年现场施工管理经验"}]},
                    {"key":"EDUCATION","score":90,"comment":"学历满足要求",
                     "evidence":[{"field":"degree","note":"本科"}]},
                    {"key":"RESPONSIBILITY","score":70,"comment":"职责部分重合",
                     "evidence":[{"field":"description","note":"负责过施工组织"}]},
                    {"key":"KEYWORD","score":65,"comment":"关键词覆盖一般",
                     "evidence":[{"field":"postName","note":"岗位名含防腐保温"}]}
                  ],
                  "matched": [],
                  "missing": [],
                  "toConfirm": []
                }
                """;
        JSONObject result = HrmJobMatchResultValidator.validate(content);
        assertTrue(result.getJSONArray("matched").isEmpty());
        assertTrue(result.getJSONArray("missing").isEmpty());
        assertTrue(result.getJSONArray("toConfirm").isEmpty());
    }

    @Test
    public void testValidate_rejectsMissingConclusionField() {
        // 缺少 toConfirm 字段：结构不完整必须判不合格
        String content = """
                {
                  "overallScore": 76,
                  "dimensions": [
                    {"key":"SKILL","score":80,"comment":"技能基本覆盖",
                     "evidence":[{"field":"skills","note":"具备管道保温施工技能"}]},
                    {"key":"EXPERIENCE","score":75,"comment":"经验年限满足",
                     "evidence":[{"field":"experiences","note":"8 年现场施工管理经验"}]},
                    {"key":"EDUCATION","score":90,"comment":"学历满足要求",
                     "evidence":[{"field":"degree","note":"本科"}]},
                    {"key":"RESPONSIBILITY","score":70,"comment":"职责部分重合",
                     "evidence":[{"field":"description","note":"负责过施工组织"}]},
                    {"key":"KEYWORD","score":65,"comment":"关键词覆盖一般",
                     "evidence":[{"field":"postName","note":"岗位名含防腐保温"}]}
                  ],
                  "matched": [],
                  "missing": []
                }
                """;
        assertInvalid(content);
    }

    @Test
    public void testValidate_rejectsMissingDimension() {
        String content = VALID_RESULT.replace("\"key\":\"KEYWORD\"", "\"key\":\"UNKNOWN\"");
        assertInvalid(content);
    }

    @Test
    public void testValidate_rejectsOutOfRangeScore() {
        assertInvalid(VALID_RESULT.replace("\"overallScore\": 76", "\"overallScore\": 120"));
        assertInvalid(VALID_RESULT.replace("\"score\":80", "\"score\":-5"));
    }

    @Test
    public void testValidate_rejectsEvidenceOutsideWhitelist() {
        assertInvalid(VALID_RESULT.replace("\"field\":\"skills\"", "\"field\":\"unknownField\""));
    }

    @Test
    public void testValidate_rejectsForbiddenAttributeInResult() {
        // 匹配结论里出现年龄、性别等属性，说明模型用无关属性做了判断
        assertInvalid(VALID_RESULT.replace("\"overallScore\": 76", "\"overallScore\": 76, \"age\": 31"));
        assertInvalid(VALID_RESULT.replace("\"point\":\"是否持有一级建造师证\"",
                "\"point\":\"是否持有一级建造师证\", \"gender\": 2"));
    }

    @Test
    public void testValidate_rejectsIncompleteConclusionItem() {
        // point 缺失
        assertInvalid(VALID_RESULT.replace("\"point\":\"未体现接受长期出差\",\"evidence\":\"简历未提及出差意愿\"",
                "\"evidence\":\"简历未提及出差意愿\""));
        // reason 缺失
        assertInvalid(VALID_RESULT.replace("\"reason\":\"简历只提到二级建造师\"", "\"reason\":\"\""));
    }

    @Test
    public void testValidate_rejectsBlankText() {
        assertInvalid(VALID_RESULT.replace("\"comment\":\"技能基本覆盖\"", "\"comment\":\"  \""));
    }

    @Test
    public void testValidate_rejectsNonJson() {
        assertInvalid("这不是 JSON");
        assertInvalid("");
        assertInvalid(null);
    }

    private void assertInvalid(String content) {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> HrmJobMatchResultValidator.validate(content));
        assertEquals(AGENT_MODEL_OUTPUT_INVALID.getCode(), exception.getCode());
    }

    @Test
    public void testValidate_stripsCodeFence() {
        JSONObject result = HrmJobMatchResultValidator.validate("```json\n" + VALID_RESULT + "\n```");
        assertNotNull(result.getJSONArray("dimensions"));
    }

    /* ===== A08 管理端：在个人端结构上额外要求风险项 ===== */

    private static final String VALID_RESULT_WITH_RISKS = """
            {
              "overallScore": 76,
              "dimensions": [
                {"key":"SKILL","score":80,"comment":"技能基本覆盖",
                 "evidence":[{"field":"skills","note":"具备管道保温施工技能"}]},
                {"key":"EXPERIENCE","score":75,"comment":"经验年限满足",
                 "evidence":[{"field":"experiences","note":"8 年现场施工管理经验"}]},
                {"key":"EDUCATION","score":90,"comment":"学历满足要求",
                 "evidence":[{"field":"degree","note":"本科"}]},
                {"key":"RESPONSIBILITY","score":70,"comment":"职责部分重合",
                 "evidence":[{"field":"description","note":"负责过施工组织与进度管控"}]},
                {"key":"KEYWORD","score":65,"comment":"关键词覆盖一般",
                 "evidence":[{"field":"postName","note":"岗位名含防腐保温"}]}
              ],
              "matched": [{"point":"具备现场施工管理经验","evidence":"负责过 6 个项目"}],
              "missing": [{"point":"未体现接受长期出差","evidence":"简历未提及出差意愿"}],
              "toConfirm": [{"point":"是否持有一级建造师证","reason":"简历只提到二级建造师"}],
              "risks": ["近两段经历间隔时间较长，需要核实原因"]
            }
            """;

    @Test
    public void testValidateWithRisks_acceptsCompleteResult() {
        JSONObject result = HrmJobMatchResultValidator.validateWithRisks(VALID_RESULT_WITH_RISKS);
        assertEquals(76, result.getInt("overallScore"));
        assertEquals(1, result.getJSONArray("risks").size());
    }

    @Test
    public void testValidateWithRisks_acceptsEmptyRisks() {
        String content = VALID_RESULT_WITH_RISKS.replace(
                "\"risks\": [\"近两段经历间隔时间较长，需要核实原因\"]", "\"risks\": []");
        assertEquals(0, HrmJobMatchResultValidator.validateWithRisks(content).getJSONArray("risks").size());
    }

    @Test
    public void testValidateWithRisks_rejectsMissingRisks() {
        // 管理端结构要求风险字段存在，缺失说明模型没按结构输出
        String content = """
                {
                  "overallScore": 76,
                  "dimensions": [
                    {"key":"SKILL","score":80,"comment":"技能基本覆盖",
                     "evidence":[{"field":"skills","note":"具备管道保温施工技能"}]},
                    {"key":"EXPERIENCE","score":75,"comment":"经验年限满足",
                     "evidence":[{"field":"experiences","note":"8 年现场施工管理经验"}]},
                    {"key":"EDUCATION","score":90,"comment":"学历满足要求",
                     "evidence":[{"field":"degree","note":"本科"}]},
                    {"key":"RESPONSIBILITY","score":70,"comment":"职责部分重合",
                     "evidence":[{"field":"description","note":"负责过施工组织与进度管控"}]},
                    {"key":"KEYWORD","score":65,"comment":"关键词覆盖一般",
                     "evidence":[{"field":"postName","note":"岗位名含防腐保温"}]}
                  ],
                  "matched": [{"point":"具备现场施工管理经验","evidence":"负责过 6 个项目"}],
                  "missing": [{"point":"未体现接受长期出差","evidence":"简历未提及出差意愿"}],
                  "toConfirm": [{"point":"是否持有一级建造师证","reason":"简历只提到二级建造师"}]
                }
                """;
        ServiceException exception = assertThrows(ServiceException.class,
                () -> HrmJobMatchResultValidator.validateWithRisks(content));
        assertEquals(AGENT_MODEL_OUTPUT_INVALID.getCode(), exception.getCode());
    }

    @Test
    public void testValidate_personalSceneStillAcceptsResultWithoutRisks() {
        // 个人端（A05）不要求风险项，加了 risks 校验不能把原有场景判失败
        assertNotNull(HrmJobMatchResultValidator.validate(VALID_RESULT));
    }
}
