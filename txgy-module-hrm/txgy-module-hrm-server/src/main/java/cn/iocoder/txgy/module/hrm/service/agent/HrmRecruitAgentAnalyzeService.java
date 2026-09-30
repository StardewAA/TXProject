package cn.iocoder.txgy.module.hrm.service.agent;

import cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo.HrmPortalRecruitAgentSubmitReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo.HrmPortalRecruitAgentTaskRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo.HrmPortalResumeOptimizeReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo.HrmPortalResumeGenerateReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo.HrmPortalResumeStructureReqVO;

/**
 * 员工端招聘 Agent 服务。
 *
 * <p>当前覆盖四个场景：
 * <ul>
 *   <li>A01 简历诊断：完整度、清晰度、专业性、岗位匹配</li>
 *   <li>A05 职位匹配：技能、经验、学历、职责、关键词，以及已匹配 / 缺失 / 待确认</li>
 *   <li>A02 简历优化：按模块产出可逐项确认的改写建议（只改写表述，不新增事实）</li>
 *   <li>A06 投递前检查：必填信息、明显矛盾与主要差距，只作提醒，不阻断投递</li>
 * </ul>
 * 四者共用同一套「校验 → 脱敏白名单 → 幂等任务 → 模型调用 → 结构化校验 → 落库」流程。</p>
 */
public interface HrmRecruitAgentAnalyzeService {

    /**
     * 提交简历诊断（A01）。同一幂等键重复提交返回同一任务，不会重复调用模型。
     *
     * @param userId 登录用户编号，由服务端会话取得
     * @param reqVO  提交参数
     * @return 任务编号
     */
    Long submitResumeAnalyze(Long userId, HrmPortalRecruitAgentSubmitReqVO reqVO);

    /**
     * 提交职位匹配（A05）。同一幂等键重复提交返回同一任务，不会重复调用模型。
     */
    Long submitJobMatch(Long userId, HrmPortalRecruitAgentSubmitReqVO reqVO);

    /**
     * 提交投递前检查（A06）。
     *
     * <p>结论只作提醒，调用方不得据此阻断投递。</p>
     */
    Long submitPreSubmitCheck(Long userId, HrmPortalRecruitAgentSubmitReqVO reqVO);

    /**
     * 提交简历优化（A02）。产出建议项供用户逐项确认，不会直接改动简历。
     */
    Long submitResumeOptimize(Long userId, HrmPortalResumeOptimizeReqVO reqVO);

    /**
     * 根据求职者填写的方向与经历生成结构化简历草稿。
     */
    Long submitResumeGenerate(Long userId, HrmPortalResumeGenerateReqVO reqVO);

    /**
     * 把附件里提取出的简历正文整理成结构化草稿（只整理，不新增事实）。
     */
    Long submitResumeStructure(Long userId, HrmPortalResumeStructureReqVO reqVO);

    /**
     * 查询 Agent 任务与结果，含归属校验。
     */
    HrmPortalRecruitAgentTaskRespVO getTaskResult(Long userId, Long taskId);
}
