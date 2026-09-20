package cn.iocoder.txgy.module.hrm.service.agent;

import cn.iocoder.txgy.module.hrm.controller.admin.recruit.agent.vo.HrmRecruitAgentApplicationReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.agent.vo.HrmRecruitAgentTaskRespVO;

/**
 * 管理端招聘 Agent 服务（A07 / A08 / A09）。
 *
 * <p>三个场景都以「应聘记录」为入口：简历取应聘记录关联的候选人简历快照，
 * 职位取该应聘记录对应的职位，因此结论天然绑定到具体应聘记录与简历版本。</p>
 *
 * <p>AI 只产出结论：不改变候选人、应聘、面试与 Offer 的任何状态，
 * 也不做批量排名或自动排序。</p>
 */
public interface HrmRecruitAgentAdminService {

    /**
     * 生成候选人摘要（A07）。
     *
     * @param userId 登录用户编号（HR）
     * @param reqVO  应聘记录与幂等键
     * @return 任务编号
     */
    Long submitCandidateSummary(Long userId, HrmRecruitAgentApplicationReqVO reqVO);

    /**
     * 生成候选人与职位匹配报告（A08）。
     */
    Long submitApplicationMatch(Long userId, HrmRecruitAgentApplicationReqVO reqVO);

    /**
     * 生成面试问题建议（A09）。
     */
    Long submitInterviewQuestion(Long userId, HrmRecruitAgentApplicationReqVO reqVO);

    /**
     * 查询任务与结果，仅限当前登录租户。
     *
     * <p>重新分析只需换一个幂等键再提交一次，历史任务与结果保留不变。</p>
     */
    HrmRecruitAgentTaskRespVO getTaskResult(Long taskId);
}
