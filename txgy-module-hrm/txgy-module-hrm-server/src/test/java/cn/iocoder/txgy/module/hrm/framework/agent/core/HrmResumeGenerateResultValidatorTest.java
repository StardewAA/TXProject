package cn.iocoder.txgy.module.hrm.framework.agent.core;

import cn.hutool.json.JSONObject;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_MODEL_OUTPUT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HrmResumeGenerateResultValidatorTest {

    private static final String ANSWERS = """
            {"expectedPostName":"项目经理","city":"北京",
             "skills":"项目管理、施工组织、二级建造师证",
             "strengths":"8年现场施工管理经验",
             "experiences":[{"company":"华北工程有限公司","position":"项目经理",
               "start":"2022.03","end":"2024.06","description":"负责施工组织",
               "achievements":"累计交付4个项目"}],
             "educationExperiences":[{"school":"华北大学","degree":"本科","major":"工程管理",
               "start":"2018","end":"2022"}]}
            """;

    private static final String VALID = """
            {"headline":"项目经理｜施工组织","summary":"拥有8年现场施工管理经验，负责施工组织。",
             "skills":["项目管理","施工组织","二级建造师证"],
             "experiences":[{"company":"华北工程有限公司","position":"项目经理","start":"2022.03","end":"2024.06",
               "description":"负责施工组织","achievements":"累计交付4个项目"}],
             "projects":[],
             "educationExperiences":[{"school":"华北大学","degree":"本科","major":"工程管理","start":"2018","end":"2022","experience":""}]}
            """;

    @Test
    void validateAcceptsStructuredDraft() {
        JSONObject result = HrmResumeGenerateResultValidator.validate(VALID, ANSWERS);
        assertEquals("项目经理｜施工组织", result.getStr("headline"));
        assertEquals(1, result.getJSONArray("experiences").size());
    }

    @Test
    void rejectsInventedCompany() {
        assertInvalid(VALID.replace("华北工程有限公司", "华东建筑集团有限公司"));
    }

    @Test
    void rejectsInventedNumberInSummary() {
        assertInvalid(VALID.replace("拥有8年现场施工管理经验", "拥有12年现场施工管理经验"));
    }

    @Test
    void rejectsSensitiveIdentityField() {
        assertInvalid(VALID.replace("\"headline\"", "\"name\":\"张三\",\"headline\""));
    }

    @Test
    void rejectsMissingArrayField() {
        assertInvalid(VALID.replace("\"projects\":[],", ""));
    }

    private void assertInvalid(String content) {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> HrmResumeGenerateResultValidator.validate(content, ANSWERS));
        assertEquals(AGENT_MODEL_OUTPUT_INVALID.getCode(), exception.getCode());
    }
}
