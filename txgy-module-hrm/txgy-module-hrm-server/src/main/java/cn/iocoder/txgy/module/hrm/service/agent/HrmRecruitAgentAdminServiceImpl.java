package cn.iocoder.txgy.module.hrm.service.agent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.agent.vo.HrmRecruitAgentApplicationReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.agent.vo.HrmRecruitAgentTaskRespVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.agent.HrmRecruitAgentAnalysisDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.agent.HrmRecruitAgentTaskDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitApplicationDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.post.HrmRecruitPostDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.resume.HrmRecruitResumeDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.agent.HrmRecruitAgentAnalysisMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.agent.HrmRecruitAgentTaskMapper;
import cn.iocoder.txgy.module.hrm.enums.agent.HrmRecruitAgentSceneEnum;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmCandidateSummaryResultValidator;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmInterviewQuestionResultValidator;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmJobMatchResultValidator;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmRecruitAgentPromptTemplates;
import cn.iocoder.txgy.module.hrm.service.recruit.application.HrmRecruitApplicationService;
import cn.iocoder.txgy.module.hrm.service.recruit.post.HrmRecruitPostService;
import cn.iocoder.txgy.module.hrm.service.recruit.resume.HrmRecruitResumeService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_APPLICATION_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_APPLICATION_RESUME_UNAVAILABLE;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_POST_NOT_AVAILABLE;

/**
 * 管理端招聘 Agent 服务实现（A07 / A08 / A09）。
 *
 * <p>归属与数据范围校验放在这里，执行链路复用
 * {@link HrmRecruitAgentSceneRunner}，与个人端保持同一套安全规则。</p>
 *
 * <p>数据范围：应聘记录、候选人简历快照、职位都在当前登录租户内查询，
 * 跨租户的编号一律按「不存在」处理，不会因为猜编号而读到别家企业的候选人。</p>
 */
@Service
@Validated
public class HrmRecruitAgentAdminServiceImpl implements HrmRecruitAgentAdminService {

    private static final String BIZ_TYPE_APPLICATION = "APPLICATION";

    @Resource
    private HrmRecruitAgentSceneRunner sceneRunner;
    @Resource
    private HrmRecruitAgentTaskService taskService;
    @Resource
    private HrmRecruitApplicationService applicationService;
    @Resource
    private HrmRecruitResumeService recruitResumeService;
    @Resource
    private HrmRecruitPostService postService;
    @Resource
    private HrmRecruitAgentTaskMapper taskMapper;
    @Resource
    private HrmRecruitAgentAnalysisMapper analysisMapper;

    @Override
    public Long submitCandidateSummary(Long userId, HrmRecruitAgentApplicationReqVO reqVO) {
        return runApplicationScene(userId, reqVO,
                HrmRecruitAgentSceneEnum.CANDIDATE_SUMMARY,
                HrmRecruitAgentPromptTemplates.CANDIDATE_SUMMARY_VERSION,
                HrmRecruitAgentPromptTemplates.CANDIDATE_SUMMARY_SYSTEM,
                (content, resumeContent) -> HrmCandidateSummaryResultValidator.validate(content));
    }

    @Override
    public Long submitApplicationMatch(Long userId, HrmRecruitAgentApplicationReqVO reqVO) {
        return runApplicationScene(userId, reqVO,
                HrmRecruitAgentSceneEnum.APPLICATION_MATCH,
                HrmRecruitAgentPromptTemplates.APPLICATION_MATCH_VERSION,
                HrmRecruitAgentPromptTemplates.APPLICATION_MATCH_SYSTEM,
                // 管理端在个人端匹配结构上额外要求风险项
                (content, resumeContent) -> HrmJobMatchResultValidator.validateWithRisks(content));
    }

    @Override
    public Long submitInterviewQuestion(Long userId, HrmRecruitAgentApplicationReqVO reqVO) {
        return runApplicationScene(userId, reqVO,
                HrmRecruitAgentSceneEnum.INTERVIEW_QUESTION,
                HrmRecruitAgentPromptTemplates.INTERVIEW_QUESTION_VERSION,
                HrmRecruitAgentPromptTemplates.INTERVIEW_QUESTION_SYSTEM,
                (content, resumeContent) -> HrmInterviewQuestionResultValidator.validate(content));
    }

    /** 三个场景流程一致，只有场景标识、提示词与校验器不同 */
    private Long runApplicationScene(Long userId, HrmRecruitAgentApplicationReqVO reqVO,
                                     HrmRecruitAgentSceneEnum scene, String promptVersion, String systemPrompt,
                                     BiFunction<String, String, JSONObject> validator) {
        // 1. 应聘记录必须在当前租户内
        HrmRecruitApplicationDO application = applicationService.getApplication(reqVO.getApplicationId());
        if (application == null) {
            throw exception(AGENT_APPLICATION_NOT_EXISTS);
        }
        // 2. 职位必须仍然存在；职位被删除时不产出结论，避免拿旧结论误导 HR
        HrmRecruitPostDO post = postService.getRecruitPost(application.getPostId());
        if (post == null) {
            throw exception(AGENT_POST_NOT_AVAILABLE);
        }
        // 3. 简历取应聘记录关联的候选人快照；附件简历没有结构化内容，无法分析
        HrmRecruitResumeDO resume = application.getResumeId() == null ? null
                : recruitResumeService.getAuthorizedRecruitResume(application.getResumeId());
        if (resume == null || StrUtil.isBlank(resume.getContent())) {
            throw exception(AGENT_APPLICATION_RESUME_UNAVAILABLE);
        }

        return sceneRunner.run(new HrmRecruitAgentRunBO()
                .setUserId(userId)
                .setScene(scene)
                .setPromptVersion(promptVersion)
                .setSystemPrompt(systemPrompt)
                .setResumeContent(resume.getContent())
                .setResumeVersionId(resume.getId())
                .setBizType(BIZ_TYPE_APPLICATION)
                .setBizId(application.getId())
                .setPost(post)
                .setIdempotencyKey(reqVO.getIdempotencyKey())
                .setResultValidator(validator));
    }

    @Override
    public Map<Long, HrmRecruitAgentMatchScoreBO> getApplicationMatchScores(Collection<Long> applicationIds) {
        if (applicationIds == null || applicationIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<HrmRecruitAgentTaskDO> tasks = taskMapper.selectSucceededByBizIds(BIZ_TYPE_APPLICATION,
                HrmRecruitAgentSceneEnum.APPLICATION_MATCH.getScene(), applicationIds);
        if (tasks.isEmpty()) {
            return Collections.emptyMap();
        }
        // 任务按编号倒序返回，每个应聘记录取第一条即最近一次成功的分析
        Map<Long, HrmRecruitAgentTaskDO> latestByApplication = new LinkedHashMap<>();
        tasks.forEach(task -> latestByApplication.putIfAbsent(task.getBizId(), task));
        Map<Long, Integer> scoreByTaskId = analysisMapper
                .selectListByTaskIds(latestByApplication.values().stream()
                        .map(HrmRecruitAgentTaskDO::getId).toList())
                .stream()
                .filter(analysis -> analysis.getOverallScore() != null)
                .collect(Collectors.toMap(HrmRecruitAgentAnalysisDO::getTaskId,
                        HrmRecruitAgentAnalysisDO::getOverallScore, (left, right) -> left));
        Map<Long, HrmRecruitAgentMatchScoreBO> result = new HashMap<>();
        latestByApplication.forEach((applicationId, task) -> {
            Integer score = scoreByTaskId.get(task.getId());
            if (score != null) {
                result.put(applicationId, new HrmRecruitAgentMatchScoreBO(applicationId, task.getId(), score));
            }
        });
        return result;
    }

    @Override
    public HrmRecruitAgentTaskRespVO getTaskResult(Long taskId) {
        HrmRecruitAgentTaskDO task = taskService.getTaskInTenant(taskId);
        HrmRecruitAgentTaskRespVO respVO = new HrmRecruitAgentTaskRespVO()
                .setTaskId(task.getId())
                .setScene(task.getScene())
                .setStatus(task.getStatus())
                .setApplicationId(task.getBizId())
                .setResumeId(task.getResumeVersionId())
                .setPostId(task.getPostId())
                .setPostFingerprint(task.getPostFingerprint())
                .setCreatedAt(task.getCreateTime())
                .setModel(task.getModel())
                .setPromptVersion(task.getPromptVersion())
                .setPromptTokens(task.getPromptTokens())
                .setCompletionTokens(task.getCompletionTokens())
                .setTotalTokens(task.getTotalTokens())
                .setErrorCode(task.getErrorCode())
                .setErrorMessage(task.getErrorMessage());
        HrmRecruitAgentAnalysisDO analysis = sceneRunner.getAnalysis(task.getId(), false);
        if (analysis != null) {
            respVO.setOverallScore(analysis.getOverallScore());
            respVO.setResult(StrUtil.isBlank(analysis.getResultJson())
                    ? null : JSONUtil.parseObj(analysis.getResultJson()));
        }
        return respVO;
    }
}
