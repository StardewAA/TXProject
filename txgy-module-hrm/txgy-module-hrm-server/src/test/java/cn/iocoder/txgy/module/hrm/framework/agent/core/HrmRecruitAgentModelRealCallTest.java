package cn.iocoder.txgy.module.hrm.framework.agent.core;

import cn.hutool.json.JSONObject;
import cn.iocoder.txgy.module.hrm.framework.agent.config.HrmRecruitAgentModelProperties;
import cn.iocoder.txgy.module.hrm.util.HrmResumeAgentPayloadUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实模型受控联调测试。
 *
 * <p>默认跳过：只有设置了 HRM_AGENT_MODEL_API_KEY 环境变量时才会执行，
 * 避免日常构建意外产生外部调用与费用。</p>
 *
 * <p>使用**合成的**简历与职位数据，不涉及任何真实用户信息，
 * 用于验证「脱敏白名单 → 提示词 → 真实模型 → 结构化校验」整条链路。</p>
 *
 * <p>运行方式（密钥只通过环境变量传入，不落文件）：
 * <pre>
 * HRM_AGENT_MODEL_API_KEY=sk-xxx mvn test -Dtest=HrmRecruitAgentModelRealCallTest
 * </pre></p>
 */
@EnabledIfEnvironmentVariable(named = "HRM_AGENT_MODEL_API_KEY", matches = ".+")
public class HrmRecruitAgentModelRealCallTest {

    /** 合成简历：故意包含手机号与敏感属性，用于验证它们不会进入模型 */
    private static final String SAMPLE_RESUME = """
            {"name":"测试用户","mobile":"13800000000","email":"tester@example.com",
             "gender":2,"age":31,"birthMonth":"1995-01","nation":"汉族","maritalStatus":"已婚",
             "domicile":"陕西省西安市雁塔区科技路10号3栋502室","idCard":"110101199001011234",
             "identity":"worker","employmentStatus":"离职-随时到岗","jobType":"fulltime",
             "salary":"15-20k","expectedPostName":"防腐保温项目经理","expectedIndustry":"防腐保温",
             "city":"西安","expectedCities":["西安","咸阳"],
             "skills":"熟悉管道保温施工工艺、聚氨酯发泡、岩棉与硅酸铝保温层施工，能看懂施工图纸，持二级建造师证",
             "strengths":"8 年现场施工管理经验，独立负责过 6 个工业管道保温项目，擅长控制工期与成本",
             "experiences":[
               {"company":"某某防腐工程有限公司","industry":"防腐保温","department":"工程部","position":"项目经理",
                "start":"2020-01","end":"2023-06",
                "description":"负责工业管道保温项目的现场施工组织、进度与质量管控，协调甲方与监理",
                "achievements":"累计交付 4 个项目，平均工期缩短 12%，一次验收通过率 100%"},
               {"company":"某某安装工程公司","industry":"建筑安装","department":"技术部","position":"技术员",
                "start":"2016-07","end":"2019-12",
                "description":"负责保温材料选型、施工方案编制与现场技术交底",
                "achievements":"优化材料选型方案，单项目材料成本下降 8%"}
             ],
             "projects":[
               {"name":"某石化厂管道保温改造项目","role":"项目负责人","start":"2021-03","end":"2021-12",
                "description":"负责 3 公里工艺管道的保温改造，包含脚手架搭设、保温层施工与外包铁皮",
                "achievements":"提前 20 天完工，获得甲方书面表扬","highlight":true}
             ],
             "educationExperiences":[
               {"degree":"本科","school":"某某工业大学","major":"土木工程","schoolType":"普通高校",
                "start":"2012-09","end":"2016-06","experience":"担任班级学习委员，获校级二等奖学金"}
             ]}
            """;

    private static final String SAMPLE_POST_NAME = "防腐保温项目经理";

    private static final String SAMPLE_POST_DESCRIPTION =
            "负责工业管道与设备的防腐保温工程施工管理，包括施工方案编制、现场进度与质量控制、"
                    + "材料与人工成本管控、甲方与监理沟通协调。要求熟悉聚氨酯发泡、岩棉、硅酸铝等保温工艺，"
                    + "具备 5 年以上现场施工管理经验，能独立带项目，接受长期出差。";

    @Test
    public void testRealCall_producesValidDiagnosis() {
        HrmRecruitAgentModelClientImpl client = buildClientFromEnvironment();
        assertTrue(client.isReady(), "环境变量配置不完整，无法进行真实调用");

        // 1. 脱敏白名单：敏感字段与联系方式必须被拦在外面
        JSONObject payload = buildSafePayload();
        String sent = payload.toString();

        // 2. 真实模型调用
        HrmRecruitAgentModelResponse response = client.chat(new HrmRecruitAgentModelRequest()
                .setScene("RESUME_ANALYZE")
                .setPromptVersion(HrmRecruitAgentPromptTemplates.RESUME_ANALYZE_VERSION)
                .setSystemPrompt(HrmRecruitAgentPromptTemplates.RESUME_ANALYZE_SYSTEM)
                .setUserPayload(sent));
        assertNotNull(response.getContent());
        assertNotNull(response.getDurationMs());

        // 3. 结构化校验必须通过；失败时打印原始输出便于定位提示词问题
        JSONObject result;
        try {
            result = HrmResumeAnalyzeResultValidator.validate(response.getContent());
        } catch (RuntimeException ex) {
            System.out.println("=== 校验失败，模型原始输出如下 ===");
            System.out.println(response.getContent());
            throw ex;
        }

        // 4. 结果必须完整：四个维度 + 依据，不能只有总分
        assertEquals(4, result.getJSONArray("dimensions").size());
        assertTrue(result.getInt("overallScore") >= 0 && result.getInt("overallScore") <= 100);
        assertFalse(result.getJSONArray("strengths").isEmpty(), "应给出优势");
        for (Object item : result.getJSONArray("dimensions")) {
            JSONObject dimension = (JSONObject) item;
            assertFalse(dimension.getJSONArray("evidence").isEmpty(),
                    "维度 " + dimension.getStr("key") + " 必须给出依据");
        }

        // 5. 输出中不得出现敏感属性字段
        assertFalse(HrmResumeAgentPayloadUtils.containsSensitive(result));

        // 便于人工核对真实效果
        System.out.println("=== 真实模型诊断结果 ===");
        System.out.println("模型：" + response.getModel() + "，耗时：" + response.getDurationMs() + "ms");
        System.out.println("综合得分：" + result.getInt("overallScore"));
        for (Object item : result.getJSONArray("dimensions")) {
            JSONObject dimension = (JSONObject) item;
            System.out.println("  " + dimension.getStr("key") + " = " + dimension.getInt("score")
                    + "：" + dimension.getStr("comment"));
        }
        System.out.println("优势：" + result.getJSONArray("strengths"));
        System.out.println("缺失信息：" + result.getJSONArray("missingInfo"));
        System.out.println("风险点：" + result.getJSONArray("risks"));
        System.out.println("建议条数：" + result.getJSONArray("suggestions").size());
    }

    /* ===== A06 / A07 / A08 / A09 真实调用 ===== */

    @Test
    public void testRealCall_producesValidPreSubmitCheck() {
        JSONObject result = runScene("PRE_SUBMIT_CHECK",
                HrmRecruitAgentPromptTemplates.PRE_SUBMIT_CHECK_VERSION,
                HrmRecruitAgentPromptTemplates.PRE_SUBMIT_CHECK_SYSTEM,
                HrmPreSubmitCheckResultValidator::validate);

        // 投递前检查只做提醒：不得出现分数或通过与否的判定
        assertNotNull(result.getStr("summary"));
        assertFalse(result.containsKey("overallScore"), "投递前检查不应产出分数");
        assertNotNull(result.getJSONArray("requiredMissing"));
        assertNotNull(result.getJSONArray("conflicts"));
        assertNotNull(result.getJSONArray("gaps"));
        assertFalse(HrmResumeAgentPayloadUtils.containsSensitive(result));

        System.out.println("=== A06 投递前检查 ===");
        System.out.println("结论：" + result.getStr("summary"));
        System.out.println("建议补充：" + result.getJSONArray("requiredMissing"));
        System.out.println("可能矛盾：" + result.getJSONArray("conflicts"));
        System.out.println("岗位差距：" + result.getJSONArray("gaps"));
    }

    @Test
    public void testRealCall_producesValidCandidateSummary() {
        JSONObject result = runScene("CANDIDATE_SUMMARY",
                HrmRecruitAgentPromptTemplates.CANDIDATE_SUMMARY_VERSION,
                HrmRecruitAgentPromptTemplates.CANDIDATE_SUMMARY_SYSTEM,
                HrmCandidateSummaryResultValidator::validate);

        assertFalse(result.getJSONArray("coreSkills").isEmpty(), "应给出核心技能");
        assertNotNull(result.getStr("workYears"));
        assertNotNull(result.getStr("experienceSummary"));
        // 三类内容必须分开：事实带真实字段名，推断单独成列
        assertNotNull(result.getJSONArray("facts"));
        assertNotNull(result.getJSONArray("missingInfo"));
        assertNotNull(result.getJSONArray("inferences"));
        for (Object item : result.getJSONArray("facts")) {
            String field = ((JSONObject) item).getStr("field");
            assertTrue(HrmResumeAgentPayloadUtils.candidateFactFieldNames().contains(field),
                    "事实字段必须来自事实字段白名单，实际为：" + field);
        }
        assertFalse(HrmResumeAgentPayloadUtils.containsSensitive(result));

        System.out.println("=== A07 候选人摘要 ===");
        System.out.println("核心技能：" + result.getJSONArray("coreSkills"));
        System.out.println("工作年限：" + result.getStr("workYears"));
        System.out.println("主要经历：" + result.getStr("experienceSummary"));
        System.out.println("简历事实：" + result.getJSONArray("facts"));
        System.out.println("缺失信息：" + result.getJSONArray("missingInfo"));
        System.out.println("AI 推断：" + result.getJSONArray("inferences"));
    }

    @Test
    public void testRealCall_producesValidApplicationMatch() {
        JSONObject result = runScene("APPLICATION_MATCH",
                HrmRecruitAgentPromptTemplates.APPLICATION_MATCH_VERSION,
                HrmRecruitAgentPromptTemplates.APPLICATION_MATCH_SYSTEM,
                HrmJobMatchResultValidator::validateWithRisks);

        assertEquals(5, result.getJSONArray("dimensions").size());
        assertNotNull(result.getJSONArray("matched"));
        assertNotNull(result.getJSONArray("missing"));
        assertNotNull(result.getJSONArray("toConfirm"));
        // 管理端结构额外要求风险项
        assertNotNull(result.getJSONArray("risks"), "管理端匹配报告必须包含风险项");
        for (Object item : result.getJSONArray("dimensions")) {
            assertFalse(((JSONObject) item).getJSONArray("evidence").isEmpty(),
                    "每个维度都必须给出依据");
        }
        assertFalse(HrmResumeAgentPayloadUtils.containsSensitive(result));

        System.out.println("=== A08 候选人与职位匹配 ===");
        System.out.println("匹配度：" + result.getInt("overallScore"));
        System.out.println("优势：" + result.getJSONArray("matched"));
        System.out.println("缺口：" + result.getJSONArray("missing"));
        System.out.println("风险：" + result.getJSONArray("risks"));
        System.out.println("待核实：" + result.getJSONArray("toConfirm"));
    }

    @Test
    public void testRealCall_producesValidInterviewQuestions() {
        JSONObject result = runScene("INTERVIEW_QUESTION",
                HrmRecruitAgentPromptTemplates.INTERVIEW_QUESTION_VERSION,
                HrmRecruitAgentPromptTemplates.INTERVIEW_QUESTION_SYSTEM,
                HrmInterviewQuestionResultValidator::validate);

        assertFalse(result.getJSONArray("questions").isEmpty(), "应生成面试问题");
        java.util.Set<String> categories = new java.util.LinkedHashSet<>();
        for (Object item : result.getJSONArray("questions")) {
            JSONObject question = (JSONObject) item;
            categories.add(question.getStr("category"));
            assertNotNull(question.getStr("purpose"));
            assertNotNull(question.getStr("focus"));
        }
        // 四类问题都应有覆盖，避免只问一类
        assertTrue(categories.size() >= 2, "问题类型过于单一，实际为：" + categories);
        assertFalse(HrmResumeAgentPayloadUtils.containsSensitive(result));

        System.out.println("=== A09 面试问题建议 ===");
        System.out.println("类型覆盖：" + categories);
        for (Object item : result.getJSONArray("questions")) {
            JSONObject question = (JSONObject) item;
            System.out.println("  [" + question.getStr("category") + "] " + question.getStr("question")
                    + "（目的：" + question.getStr("purpose") + "）");
        }
    }

    /** 构建脱敏白名单负载，并断言敏感信息一律没有进入模型输入 */
    private JSONObject buildSafePayload() {
        JSONObject resumePayload = HrmResumeAgentPayloadUtils.buildAgentSafePayload(SAMPLE_RESUME);
        JSONObject postPayload = new JSONObject()
                .set("postName", SAMPLE_POST_NAME)
                .set("description", SAMPLE_POST_DESCRIPTION);
        JSONObject payload = new JSONObject().set("resume", resumePayload).set("post", postPayload);

        String sent = payload.toString();
        assertFalse(sent.contains("13800000000"), "手机号不得进入模型输入");
        assertFalse(sent.contains("tester@example.com"), "邮箱不得进入模型输入");
        assertFalse(sent.contains("110101199001011234"), "身份证号不得进入模型输入");
        assertFalse(sent.contains("科技路"), "详细地址不得进入模型输入");
        assertFalse(sent.contains("\"gender\""), "性别不得进入模型输入");
        assertFalse(sent.contains("\"age\""), "年龄不得进入模型输入");
        assertFalse(sent.contains("\"nation\""), "民族不得进入模型输入");
        assertFalse(sent.contains("\"maritalStatus\""), "婚育状况不得进入模型输入");
        assertFalse(sent.contains("\"domicile\""), "户籍地址不得进入模型输入");
        return payload;
    }

    /** 跑一个场景：真实调用 + 结构化校验；校验失败时打印原始输出便于定位提示词问题 */
    private JSONObject runScene(String scene, String promptVersion, String systemPrompt,
                               java.util.function.Function<String, JSONObject> validator) {
        HrmRecruitAgentModelClientImpl client = buildClientFromEnvironment();
        assertTrue(client.isReady(), "环境变量配置不完整，无法进行真实调用");

        HrmRecruitAgentModelResponse response = client.chat(new HrmRecruitAgentModelRequest()
                .setScene(scene)
                .setPromptVersion(promptVersion)
                .setSystemPrompt(systemPrompt)
                .setUserPayload(buildSafePayload().toString()));
        assertNotNull(response.getContent());
        System.out.println("[" + scene + "] 模型=" + response.getModel()
                + " 耗时=" + response.getDurationMs() + "ms"
                + " promptTokens=" + response.getPromptTokens()
                + " completionTokens=" + response.getCompletionTokens()
                + " totalTokens=" + response.getTotalTokens());

        try {
            return validator.apply(response.getContent());
        } catch (RuntimeException ex) {
            System.out.println("=== [" + scene + "] 校验失败，模型原始输出如下 ===");
            System.out.println(response.getContent());
            throw ex;
        }
    }

    /** 只从环境变量读取配置，测试代码里不出现任何密钥字面量 */
    private HrmRecruitAgentModelClientImpl buildClientFromEnvironment() {
        HrmRecruitAgentModelProperties properties = new HrmRecruitAgentModelProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(env("HRM_AGENT_MODEL_BASE_URL",
                "https://dashscope.aliyuncs.com/compatible-mode/v1"));
        properties.setApiKey(env("HRM_AGENT_MODEL_API_KEY", null));
        properties.setModel(env("HRM_AGENT_MODEL_NAME", "qwen-flash"));
        // 思考型模型（GLM-4.5 系列等）的推理过程会挤占输出额度，联调时按平台关闭思考
        properties.setExtraBodyJson(env("HRM_AGENT_MODEL_EXTRA_BODY", null));
        return new HrmRecruitAgentModelClientImpl(properties);
    }

    private String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
