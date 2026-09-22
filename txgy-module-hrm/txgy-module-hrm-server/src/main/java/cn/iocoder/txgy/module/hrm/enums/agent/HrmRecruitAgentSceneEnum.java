package cn.iocoder.txgy.module.hrm.enums.agent;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * HRM 招聘 Agent 场景。
 *
 * <p>每个场景对应一类模型任务，提示词模板与输出结构按场景区分。</p>
 */
@Getter
@AllArgsConstructor
public enum HrmRecruitAgentSceneEnum {

    /** 个人端：在线简历诊断 */
    RESUME_ANALYZE("RESUME_ANALYZE", "简历诊断", true),
    /** 个人端：按模块生成优化建议 */
    RESUME_OPTIMIZE("RESUME_OPTIMIZE", "简历优化", true),
    /** 个人端：本人简历与单个职位匹配 */
    JOB_MATCH("JOB_MATCH", "职位匹配", true),
    /** 个人端：投递前检查，结论仅作提示，不阻断投递 */
    PRE_SUBMIT_CHECK("PRE_SUBMIT_CHECK", "投递前检查", true),
    /** 管理端：候选人简历摘要 */
    CANDIDATE_SUMMARY("CANDIDATE_SUMMARY", "候选人摘要", false),
    /** 管理端：候选人与职位匹配 */
    APPLICATION_MATCH("APPLICATION_MATCH", "应聘匹配", false),
    /** 管理端：面试问题建议 */
    INTERVIEW_QUESTION("INTERVIEW_QUESTION", "面试问题建议", false),
    /** 管理端：招聘沟通草稿（条件纳入，未确认前不启用） */
    CONVERSATION_DRAFT("CONVERSATION_DRAFT", "沟通草稿", false);

    private final String scene;
    private final String name;
    /** 是否为个人端场景：个人端按登录用户归属，管理端按企业租户与数据范围归属 */
    private final boolean portal;

    public static HrmRecruitAgentSceneEnum valueOfScene(String scene) {
        return Arrays.stream(values())
                .filter(item -> item.scene.equals(scene))
                .findFirst().orElse(null);
    }
}
