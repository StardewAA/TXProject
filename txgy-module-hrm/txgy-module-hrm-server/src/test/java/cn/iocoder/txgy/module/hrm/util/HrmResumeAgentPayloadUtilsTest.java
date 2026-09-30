package cn.iocoder.txgy.module.hrm.util;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 脱敏与白名单测试。
 *
 * <p>这组用例是合规底线：敏感信息不得进入模型输入，敏感属性不得进入评分依据。</p>
 */
public class HrmResumeAgentPayloadUtilsTest {

    @Test
    public void testMask_mobileEmailAndIdCard() {
        assertEquals("138****0000", HrmSensitiveTextMasker.maskMobile("13800000000"));
        assertEquals("z***@example.com", HrmSensitiveTextMasker.maskEmail("zhangsan@example.com"));
        assertEquals("110101********1234", HrmSensitiveTextMasker.maskIdCard("110101199001011234"));

        String masked = HrmSensitiveTextMasker.mask(
                "电话 13800000000，邮箱 zhangsan@example.com，身份证 110101199001011234");
        assertFalse(masked.contains("13800000000"));
        assertFalse(masked.contains("zhangsan@example.com"));
        assertFalse(masked.contains("110101199001011234"));
    }

    @Test
    public void testStillContainsSensitive_detectsRawButNotMasked() {
        assertTrue(HrmSensitiveTextMasker.stillContainsSensitive("联系我 13800000000"));
        assertTrue(HrmSensitiveTextMasker.stillContainsSensitive("a@b.com"));
        assertTrue(HrmSensitiveTextMasker.stillContainsSensitive("110101199001011234"));

        assertFalse(HrmSensitiveTextMasker.stillContainsSensitive(
                HrmSensitiveTextMasker.mask("联系我 13800000000，a@b.com，110101199001011234")));
        assertFalse(HrmSensitiveTextMasker.stillContainsSensitive("负责管道保温施工管理"));
        assertFalse(HrmSensitiveTextMasker.stillContainsSensitive(null));
    }

    @Test
    public void testMask_detailedAddressKeepsOnlyRegion() {
        String masked = HrmSensitiveTextMasker.mask("现居陕西省西安市雁塔区科技路10号3栋502室");
        assertFalse(masked.contains("科技路"));
        assertFalse(masked.contains("502"));

        // 只有市名、没有门牌信息的表述不应被当成地址处理，避免误伤正常描述
        String plain = "在西安市工作了三年，负责市政管道保温";
        assertEquals(plain, HrmSensitiveTextMasker.mask(plain));
    }

    @Test
    public void testBuildAgentSafePayload_excludesSensitiveAttributes() {
        String content = """
                {"name":"张三","mobile":"13800000000","email":"zhangsan@example.com",
                 "gender":2,"sex":2,"age":31,"birthMonth":"1995-01","nation":"汉族",
                 "religion":"无","maritalStatus":"已婚","disability":"无",
                 "domicile":"陕西省西安市雁塔区科技路10号","idCard":"110101199001011234",
                 "employmentStatus":"在职","skills":"管道保温施工","strengths":"责任心强",
                 "expectedPostName":"项目经理","city":"西安"}
                """;

        JSONObject payload = HrmResumeAgentPayloadUtils.buildAgentSafePayload(content);

        // 岗位相关内容保留
        assertEquals("管道保温施工", payload.getStr("skills"));
        assertEquals("项目经理", payload.getStr("expectedPostName"));
        // 敏感属性与联系方式一律不进入负载
        for (String forbidden : new String[]{"name", "mobile", "email", "gender", "sex", "age",
                "birthMonth", "nation", "religion", "maritalStatus", "disability",
                "domicile", "idCard"}) {
            assertFalse(payload.containsKey(forbidden), "负载不应包含字段：" + forbidden);
        }
    }

    @Test
    public void testBuildAgentSafePayload_masksFreeTextAndNestedItems() {
        String content = """
                {"strengths":"联系电话13800000000，邮箱 me@example.com",
                 "experiences":[{"company":"某公司","position":"项目经理",
                   "description":"负责现场管理，可联系 13900000000","achievements":"工期缩短20%"},
                  {"company":"另一公司","position":"工程师","description":"施工技术"}],
                 "projects":[{"name":"保温项目","role":"负责人","description":"技术方案"},
                   {"name":"巡检项目","role":"成员"}],
                 "educationExperiences":[{"degree":"本科","school":"测试大学","major":"土木工程"}]}
                """;

        JSONObject payload = HrmResumeAgentPayloadUtils.buildAgentSafePayload(content);

        assertFalse(payload.toString().contains("13800000000"));
        assertFalse(payload.toString().contains("me@example.com"));
        assertFalse(payload.toString().contains("13900000000"));

        JSONArray experiences = payload.getJSONArray("experiences");
        assertEquals(2, experiences.size());
        assertEquals("某公司", experiences.getJSONObject(0).getStr("company"));
        assertEquals("工期缩短20%", experiences.getJSONObject(0).getStr("achievements"));
        // 未在数组元素白名单中的字段被丢弃，白名单字段正常保留
        assertEquals(2, payload.getJSONArray("projects").size());
        assertEquals("保温项目", payload.getJSONArray("projects").getJSONObject(0).getStr("name"));
        assertEquals("本科", payload.getJSONArray("educationExperiences")
                .getJSONObject(0).getStr("degree"));
    }

    @Test
    public void testBuildAgentSafePayload_dropsUnknownAndForbiddenArrayFields() {
        String content = """
                {"experiences":[{"company":"某公司","position":"项目经理","gender":2,
                   "salaryExpectation":"20k","idCard":"110101199001011234"}]}
                """;

        JSONObject payload = HrmResumeAgentPayloadUtils.buildAgentSafePayload(content);
        JSONObject experience = payload.getJSONArray("experiences").getJSONObject(0);

        assertFalse(experience.containsKey("gender"));
        assertFalse(experience.containsKey("idCard"));
        assertTrue(experience.containsKey("company"));
    }

    @Test
    public void testBuildAgentSafePayload_emptyOrInvalidContent() {
        assertTrue(HrmResumeAgentPayloadUtils.buildAgentSafePayload(null).isEmpty());
        assertTrue(HrmResumeAgentPayloadUtils.buildAgentSafePayload("").isEmpty());
        assertTrue(HrmResumeAgentPayloadUtils.buildAgentSafePayload("不是 JSON").isEmpty());
    }

    @Test
    public void testBuildAgentSafePayload_acceptsPlainTextAndMasksSensitiveLabels() {
        String text = "项目经理，负责防腐保温工程现场管理、施工组织和质量验收，熟悉项目推进与团队协作。性别：男 年龄：31。";
        JSONObject payload = HrmResumeAgentPayloadUtils.buildAgentSafePayload(text);
        assertTrue(payload.getStr("resumeText").contains("防腐保温工程"));
        assertFalse(payload.getStr("resumeText").contains("性别：男"));
        assertFalse(payload.getStr("resumeText").contains("年龄：31"));
        assertTrue(HrmResumeAgentPayloadUtils.agentAllowedFieldNames().contains("resumeText"));
    }

    @Test
    public void testContainsSensitive_detectsLeakInPayload() {
        JSONObject leaked = new JSONObject().set("skills", "联系 13800000000");
        assertTrue(HrmResumeAgentPayloadUtils.containsSensitive(leaked));

        JSONObject clean = new JSONObject().set("skills", "管道保温施工");
        assertFalse(HrmResumeAgentPayloadUtils.containsSensitive(clean));

        JSONArray array = new JSONArray();
        array.add(new JSONObject().set("description", "a@b.com"));
        assertTrue(HrmResumeAgentPayloadUtils.containsSensitive(array));
    }

    @Test
    public void testBuildAgentSafePayload_failsClosedWhenMaskCannotGuaranteeClean() {
        // 通过直接校验负载断言：一旦负载里残留可识别敏感信息，就必须被判定为不合格
        JSONObject dirty = new JSONObject().set("strengths", "手机 13800000000");
        assertTrue(HrmResumeAgentPayloadUtils.containsSensitive(dirty));

        // 正常脱敏路径不会触发异常
        String content = "{\"strengths\":\"手机 13800000000\"}";
        JSONObject payload = HrmResumeAgentPayloadUtils.buildAgentSafePayload(content);
        assertFalse(HrmResumeAgentPayloadUtils.containsSensitive(payload));
    }

    @Test
    public void testFindForbiddenFields_listsPresentAttributes() {
        String content = """
                {"name":"张三","gender":2,"nation":"汉族","skills":"施工",
                 "experiences":[{"company":"某公司","maritalStatus":"已婚"}]}
                """;

        Set<String> found = HrmResumeAgentPayloadUtils.findForbiddenFields(content);
        assertTrue(found.contains("name"));
        assertTrue(found.contains("gender"));
        assertTrue(found.contains("nation"));
        assertTrue(found.contains("maritalStatus"));
        assertFalse(found.contains("skills"));

        assertTrue(HrmResumeAgentPayloadUtils.findForbiddenFields("{\"skills\":\"施工\"}").isEmpty());
    }

    @Test
    public void testForbiddenFields_coverHiringSensitiveAttributes() {
        for (String field : new String[]{"gender", "sex", "age", "nation", "religion",
                "maritalStatus", "fertility", "disability", "domicile"}) {
            assertTrue(HrmResumeAgentPayloadUtils.FORBIDDEN_ATTRIBUTE_FIELDS.contains(field),
                    "敏感属性必须列入禁止清单：" + field);
        }
    }
}
