package cn.iocoder.txgy.module.hrm.service.agent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo.HrmPortalRecruitAgentSubmitReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo.HrmPortalRecruitAgentTaskRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo.HrmPortalResumeOptimizeReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.agent.HrmRecruitAgentAnalysisDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.agent.HrmRecruitAgentTaskDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.personal.resume.HrmPersonalResumeVersionDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.post.HrmRecruitPostDO;
import cn.iocoder.txgy.module.hrm.enums.agent.HrmRecruitAgentSceneEnum;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmJobMatchResultValidator;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmPreSubmitCheckResultValidator;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmRecruitAgentPromptTemplates;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmResumeAnalyzeResultValidator;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmResumeOptimizeResultValidator;
import cn.iocoder.txgy.module.hrm.service.personal.resume.HrmPersonalResumeVersionService;
import cn.iocoder.txgy.module.hrm.service.recruit.post.HrmRecruitPostService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.function.BiFunction;

/**
 * 员工端招聘 Agent 服务实现。
 *
 * <p>本类只做「归属与有效性校验」，把简历内容与职位交给
 * {@link HrmRecruitAgentSceneRunner} 执行统一链路。</p>
 */
@Service
@Validated
public class HrmRecruitAgentAnalyzeServiceImpl implements HrmRecruitAgentAnalyzeService {

    private static final String BIZ_TYPE_PERSONAL_RESUME_VERSION = "PERSONAL_RESUME_VERSION";

    @Resource
    private HrmRecruitAgentSceneRunner sceneRunner;
    @Resource
    private HrmRecruitAgentTaskService taskService;
    @Resource
    private HrmPersonalResumeVersionService personalResumeVersionService;
    @Resource
    private HrmRecruitPostService postService;

    @Override
    public Long submitResumeAnalyze(Long userId, HrmPortalRecruitAgentSubmitReqVO reqVO) {
        return runPersonalScene(userId, reqVO,
                HrmRecruitAgentSceneEnum.RESUME_ANALYZE,
                HrmRecruitAgentPromptTemplates.RESUME_ANALYZE_VERSION,
                HrmRecruitAgentPromptTemplates.RESUME_ANALYZE_SYSTEM,
                (content, resumeContent) -> HrmResumeAnalyzeResultValidator.validate(content));
    }

    @Override
    public Long submitJobMatch(Long userId, HrmPortalRecruitAgentSubmitReqVO reqVO) {
        return runPersonalScene(userId, reqVO,
                HrmRecruitAgentSceneEnum.JOB_MATCH,
                HrmRecruitAgentPromptTemplates.JOB_MATCH_VERSION,
                HrmRecruitAgentPromptTemplates.JOB_MATCH_SYSTEM,
                (content, resumeContent) -> HrmJobMatchResultValidator.validate(content));
    }

    @Override
    public Long submitPreSubmitCheck(Long userId, HrmPortalRecruitAgentSubmitReqVO reqVO) {
        return runPersonalScene(userId, reqVO,
                HrmRecruitAgentSceneEnum.PRE_SUBMIT_CHECK,
                HrmRecruitAgentPromptTemplates.PRE_SUBMIT_CHECK_VERSION,
                HrmRecruitAgentPromptTemplates.PRE_SUBMIT_CHECK_SYSTEM,
                (content, resumeContent) -> HrmPreSubmitCheckResultValidator.validate(content));
    }

    @Override
    public Long submitResumeOptimize(Long userId, HrmPortalResumeOptimizeReqVO reqVO) {
        HrmPersonalResumeVersionDO version = loadOwnedVersion(userId, reqVO.getResumeVersionId());
        HrmRecruitPostDO post = postService.getPortalRecruitPostPublished(reqVO.getPostId());
        return sceneRunner.run(buildRunBO(userId, version, post)
                .setScene(HrmRecruitAgentSceneEnum.RESUME_OPTIMIZE)
                .setPromptVersion(HrmRecruitAgentPromptTemplates.RESUME_OPTIMIZE_VERSION)
                .setSystemPrompt(HrmRecruitAgentPromptTemplates.resumeOptimizeSystem(
                        HrmRecruitAgentPromptTemplates.styleInstruction(reqVO.getStyle()),
                        HrmRecruitAgentPromptTemplates.moduleInstruction(reqVO.getModules())))
                .setIdempotencyKey(reqVO.getIdempotencyKey())
                // 优化场景需要拿简历原文校验「原文片段」是否真实存在
                .setResultValidator(HrmResumeOptimizeResultValidator::validate));
    }

    /** 个人端三个场景流程一致，只有场景标识、提示词与校验器不同 */
    private Long runPersonalScene(Long userId, HrmPortalRecruitAgentSubmitReqVO reqVO,
                                  HrmRecruitAgentSceneEnum scene, String promptVersion, String systemPrompt,
                                  BiFunction<String, String, JSONObject> validator) {
        HrmPersonalResumeVersionDO version = loadOwnedVersion(userId, reqVO.getResumeVersionId());
        HrmRecruitPostDO post = postService.getPortalRecruitPostPublished(reqVO.getPostId());
        return sceneRunner.run(buildRunBO(userId, version, post)
                .setScene(scene)
                .setPromptVersion(promptVersion)
                .setSystemPrompt(systemPrompt)
                .setIdempotencyKey(reqVO.getIdempotencyKey())
                .setResultValidator(validator));
    }

    private HrmRecruitAgentRunBO buildRunBO(Long userId, HrmPersonalResumeVersionDO version,
                                           HrmRecruitPostDO post) {
        return new HrmRecruitAgentRunBO()
                .setUserId(userId)
                .setResumeContent(version.getContent())
                .setResumeVersionId(version.getId())
                .setBizType(BIZ_TYPE_PERSONAL_RESUME_VERSION)
                .setBizId(version.getId())
                .setPost(post);
    }

    /** 归属校验：版本必须是本人的；非本人与不存在一律按「不存在」处理 */
    private HrmPersonalResumeVersionDO loadOwnedVersion(Long userId, Long resumeVersionId) {
        return personalResumeVersionService.getVersion(userId, resumeVersionId);
    }

    @Override
    public HrmPortalRecruitAgentTaskRespVO getTaskResult(Long userId, Long taskId) {
        HrmRecruitAgentTaskDO task = taskService.getTask(userId, taskId);
        HrmPortalRecruitAgentTaskRespVO respVO = new HrmPortalRecruitAgentTaskRespVO()
                .setTaskId(task.getId())
                .setScene(task.getScene())
                .setStatus(task.getStatus())
                .setResumeVersionId(task.getResumeVersionId())
                .setPostId(task.getPostId())
                .setPostFingerprint(task.getPostFingerprint())
                .setCreatedAt(task.getCreateTime())
                .setModel(task.getModel())
                .setPromptVersion(task.getPromptVersion())
                .setErrorCode(task.getErrorCode())
                .setErrorMessage(task.getErrorMessage());
        HrmRecruitAgentAnalysisDO analysis = sceneRunner.getAnalysis(task.getId(), true);
        if (analysis != null) {
            respVO.setOverallScore(analysis.getOverallScore());
            respVO.setResult(StrUtil.isBlank(analysis.getResultJson())
                    ? null : JSONUtil.parseObj(analysis.getResultJson()));
        }
        return respVO;
    }
}
