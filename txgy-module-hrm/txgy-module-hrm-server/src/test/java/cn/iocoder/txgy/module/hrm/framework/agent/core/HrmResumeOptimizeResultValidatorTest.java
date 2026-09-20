package cn.iocoder.txgy.module.hrm.framework.agent.core;

import cn.hutool.json.JSONObject;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_MODEL_OUTPUT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 简历优化结果校验测试（A02）。
 *
 * <p>重点：只允许改写表述，不允许新增事实；不合格的建议应被丢弃，
 * 而不是让用户拿不到其它有效建议。</p>
 */
public class HrmResumeOptimizeResultValidatorTest {

    /** 简历原文，用于校验 originalValue 是否真实存在 */
    private static final String RESUME_CONTENT = """
            {"skills":"熟悉管道保温施工工艺，持二级建造师证",
             "strengths":"8 年现场施工管理经验，独立负责过 6 个工业管道保温项目",
             "experiences":[{"company":"某某防腐工程有限公司","position":"项目经理",
               "description":"负责工业管道保温项目的现场施工组织、进度与质量管控",
               "achievements":"累计交付 4 个项目，平均工期缩短 12%"}]}
            """;

    private static String wrap(String items) {
        return "{\"items\":[" + items + "]}";
    }

    private static String item(String module, String fieldPath, String operation,
                              String original, String suggested, String reason) {
        return """
                {"module":"%s","fieldPath":"%s","operation":"%s","originalValue":"%s",
                 "suggestedValue":"%s","reason":"%s"}"""
                .formatted(module, fieldPath, operation, original, suggested, reason);
    }

    @Test
    public void testValidate_acceptsPureRewriting() {
        String content = wrap(item("WORK_EXPERIENCE", "description", "REPLACE",
                "负责工业管道保温项目的现场施工组织、进度与质量管控",
                "统筹工业管道保温项目的现场施工组织，负责进度与质量管控，协调甲方与监理",
                "补充协调职责，更贴合项目经理岗位要求"));

        JSONObject result = HrmResumeOptimizeResultValidator.validate(content, RESUME_CONTENT);
        assertEquals(1, result.getJSONArray("items").size());
    }

    @Test
    public void testValidate_dropsItemWhoseOriginalIsNotInResume() {
        // 模型编造了原文片段，必须被剔除
        String content = wrap(item("SKILLS", "skills", "REPLACE",
                "这段原文在简历里根本不存在",
                "熟悉管道保温施工工艺，持一级建造师证",
                "提升专业度"));

        JSONObject result = HrmResumeOptimizeResultValidator.validate(content, RESUME_CONTENT);
        assertTrue(result.getJSONArray("items").isEmpty());
    }

    @Test
    public void testValidate_dropsItemIntroducingNewFact() {
        // 原文里没有一级建造师，建议值不得凭空加上
        String content = wrap(item("SKILLS", "skills", "REPLACE",
                "熟悉管道保温施工工艺，持二级建造师证",
                "熟悉管道保温施工工艺，持一级建造师证",
                "提升资质表述"));

        JSONObject result = HrmResumeOptimizeResultValidator.validate(content, RESUME_CONTENT);
        assertTrue(result.getJSONArray("items").isEmpty());
    }

    @Test
    public void testValidate_dropsItemIntroducingNewOrganizationOrYear() {
        String withNewOrg = wrap(item("WORK_EXPERIENCE", "achievements", "REPLACE",
                "累计交付 4 个项目，平均工期缩短 12%",
                "累计交付 4 个项目，服务过中建三局有限公司，平均工期缩短 12%",
                "补充客户信息"));
        assertTrue(HrmResumeOptimizeResultValidator.validate(withNewOrg, RESUME_CONTENT)
                .getJSONArray("items").isEmpty());

        String withNewYear = wrap(item("WORK_EXPERIENCE", "achievements", "REPLACE",
                "累计交付 4 个项目，平均工期缩短 12%",
                "2024 年累计交付 4 个项目，平均工期缩短 12%",
                "补充时间"));
        assertTrue(HrmResumeOptimizeResultValidator.validate(withNewYear, RESUME_CONTENT)
                .getJSONArray("items").isEmpty());
    }

    @Test
    public void testValidate_rejectsFactFields() {
        // 公司、时间、职位等事实字段一律不得改动
        for (String fieldPath : new String[]{"company", "position", "start", "end",
                "degree", "school", "major", "name"}) {
            String content = wrap(item("WORK_EXPERIENCE", fieldPath, "REPLACE",
                    "某某防腐工程有限公司", "某某防腐集团有限公司", "提升表述"));
            assertTrue(HrmResumeOptimizeResultValidator.validate(content, RESUME_CONTENT)
                            .getJSONArray("items").isEmpty(),
                    "事实字段不应允许改写：" + fieldPath);
        }
    }

    @Test
    public void testValidate_rejectsUnsupportedOperation() {
        for (String operation : new String[]{"ADD", "REMOVE", "DELETE"}) {
            String content = wrap(item("SKILLS", "skills", operation,
                    "熟悉管道保温施工工艺，持二级建造师证",
                    "熟悉管道保温施工工艺，持二级建造师证，精通聚氨酯发泡",
                    "补充技能"));
            assertTrue(HrmResumeOptimizeResultValidator.validate(content, RESUME_CONTENT)
                            .getJSONArray("items").isEmpty(),
                    "一期只支持 REPLACE，不应接受：" + operation);
        }
    }

    @Test
    public void testValidate_rejectsUnknownModule() {
        String content = wrap(item("UNKNOWN_MODULE", "skills", "REPLACE",
                "熟悉管道保温施工工艺，持二级建造师证",
                "熟练掌握管道保温施工工艺，持二级建造师证",
                "提升表述"));
        assertTrue(HrmResumeOptimizeResultValidator.validate(content, RESUME_CONTENT)
                .getJSONArray("items").isEmpty());
    }

    @Test
    public void testValidate_keepsGoodItemsWhenSomeAreBad() {
        // 一条合格 + 一条编造：应保留合格的，只剔除坏的
        String content = wrap(
                item("WORK_EXPERIENCE", "description", "REPLACE",
                        "负责工业管道保温项目的现场施工组织、进度与质量管控",
                        "统筹工业管道保温项目的现场施工组织，负责进度与质量管控，协调甲方与监理",
                        "补充协调职责")
                        + "," +
                        item("SKILLS", "skills", "REPLACE",
                                "熟悉管道保温施工工艺，持二级建造师证",
                                "熟悉管道保温施工工艺，持一级建造师证",
                                "提升资质"));

        JSONObject result = HrmResumeOptimizeResultValidator.validate(content, RESUME_CONTENT);
        assertEquals(1, result.getJSONArray("items").size());
        assertEquals("description",
                result.getJSONArray("items").getJSONObject(0).getStr("fieldPath"));
    }

    @Test
    public void testValidate_limitsItemCount() {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (i > 0) items.append(',');
            items.append(item("WORK_EXPERIENCE", "description", "REPLACE",
                    "负责工业管道保温项目的现场施工组织、进度与质量管控",
                    "统筹工业管道保温项目的现场施工组织，负责进度与质量管控，协调甲方与监理",
                    "理由" + i));
        }
        JSONObject result = HrmResumeOptimizeResultValidator.validate(wrap(items.toString()), RESUME_CONTENT);
        assertTrue(result.getJSONArray("items").size() <= 8);
    }

    @Test
    public void testValidate_acceptsEmptyItems() {
        JSONObject result = HrmResumeOptimizeResultValidator.validate("{\"items\":[]}", RESUME_CONTENT);
        assertTrue(result.getJSONArray("items").isEmpty());
    }

    // ===== 以下三组来自真实模型实测，用于防止回归 =====

    @Test
    public void testValidate_defaultsMissingOperationToReplace() {
        // 实测模型有时会漏掉 operation 字段；一期只支持 REPLACE，缺省应按 REPLACE 处理而不是丢弃
        String content = """
                {"items":[{"module":"WORK_EXPERIENCE","fieldPath":"description",
                  "originalValue":"负责工业管道保温项目的现场施工组织、进度与质量管控",
                  "suggestedValue":"全面负责工业管道保温项目的现场施工组织、进度与质量管控",
                  "reason":"强化负责范围"}]}
                """;
        JSONObject result = HrmResumeOptimizeResultValidator.validate(content, RESUME_CONTENT);
        assertEquals(1, result.getJSONArray("items").size());
        assertEquals("REPLACE",
                result.getJSONArray("items").getJSONObject(0).getStr("operation"));
    }

    @Test
    public void testValidate_normalizesModuleAlias() {
        // 实测模型用 STRENGTHS 表示个人优势；应归一到 SELF_INTRO 而不是丢弃
        String content = wrap(item("STRENGTHS", "strengths", "REPLACE",
                "8 年现场施工管理经验，独立负责过 6 个工业管道保温项目",
                "拥有 8 年现场施工管理经验，独立负责 6 个工业管道保温项目",
                "强化经验年限表述"));

        JSONObject result = HrmResumeOptimizeResultValidator.validate(content, RESUME_CONTENT);
        assertEquals(1, result.getJSONArray("items").size());
        assertEquals("SELF_INTRO",
                result.getJSONArray("items").getJSONObject(0).getStr("module"));
    }

    @Test
    public void testValidate_doesNotTreatCertificateWordingAsNewFact() {
        // 实测「二级建造师证」→「二级建造师证书」被误判为新增事实，属于护栏误伤。
        // originalValue 必须逐字取自 RESUME_CONTENT 中的 skills 字段
        String content = wrap(item("SKILLS", "skills", "REPLACE",
                "熟悉管道保温施工工艺，持二级建造师证",
                "精通管道保温施工工艺，持有二级建造师证书",
                "使用更专业的技能表述"));

        JSONObject result = HrmResumeOptimizeResultValidator.validate(content, RESUME_CONTENT);
        assertEquals(1, result.getJSONArray("items").size());
    }

    @Test
    public void testValidate_stillRejectsGenuinelyNewCertificate() {
        // 换成原文没有的证书等级时仍必须拦截
        String content = wrap(item("SKILLS", "skills", "REPLACE",
                "熟悉管道保温施工工艺，持二级建造师证",
                "熟悉管道保温施工工艺，持一级建造师证",
                "提升资质"));
        assertTrue(HrmResumeOptimizeResultValidator.validate(content, RESUME_CONTENT)
                .getJSONArray("items").isEmpty());
    }

    @Test
    public void testValidate_rejectsInvalidStructure() {
        assertInvalid("这不是 JSON");
        assertInvalid("");
        assertInvalid(null);
        // 缺少 items 字段
        assertInvalid("{\"other\":1}");
    }

    @Test
    public void testValidate_rejectsForbiddenAttributeInResult() {
        String content = "{\"items\":[],\"age\":31}";
        assertInvalid(content);
    }

    private void assertInvalid(String content) {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> HrmResumeOptimizeResultValidator.validate(content, RESUME_CONTENT));
        assertEquals(AGENT_MODEL_OUTPUT_INVALID.getCode(), exception.getCode());
    }
}
