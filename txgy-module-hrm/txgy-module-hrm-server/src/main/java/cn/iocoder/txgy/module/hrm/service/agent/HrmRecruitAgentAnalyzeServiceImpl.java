package cn.iocoder.txgy.module.hrm.service.agent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo.HrmPortalRecruitAgentSubmitReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo.HrmPortalRecruitAgentTaskRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo.HrmPortalResumeGenerateReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo.HrmPortalResumeStructureReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo.HrmPortalResumeOptimizeReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.agent.HrmRecruitAgentAnalysisDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.agent.HrmRecruitAgentTaskDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.personal.resume.HrmPersonalResumeVersionDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.personal.resume.HrmPersonalResumeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.post.HrmRecruitPostDO;
import cn.iocoder.txgy.module.hrm.enums.agent.HrmRecruitAgentSceneEnum;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmJobMatchResultValidator;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmPreSubmitCheckResultValidator;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmRecruitAgentPromptTemplates;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmResumeAnalyzeResultValidator;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmResumeGenerateResultValidator;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmResumeOptimizeResultValidator;
import cn.iocoder.txgy.module.hrm.service.personal.resume.HrmPersonalResumeVersionService;
import cn.iocoder.txgy.module.hrm.service.personal.resume.HrmPersonalResumeService;
import cn.iocoder.txgy.module.hrm.service.recruit.post.HrmRecruitPostService;
import cn.iocoder.txgy.module.hrm.util.HrmResumeAgentPayloadUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.function.BiFunction;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_POST_REQUIRED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_RESUME_CONTENT_EMPTY;

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
    private HrmPersonalResumeService personalResumeService;
    @Resource
    private HrmRecruitPostService postService;

    @Override
    public Long submitResumeAnalyze(Long userId, HrmPortalRecruitAgentSubmitReqVO reqVO) {
        // 不传职位 = 对整份简历本身做诊断：换一套不含 JOB_MATCH 的提示词与校验器。
        // 传了职位就保持原样，结论里会多一项「职位匹配」。
        if (reqVO.getPostId() == null) {
            return runPersonalScene(userId, reqVO,
                    HrmRecruitAgentSceneEnum.RESUME_ANALYZE,
                    HrmRecruitAgentPromptTemplates.RESUME_ANALYZE_FULL_VERSION,
                    HrmRecruitAgentPromptTemplates.RESUME_ANALYZE_FULL_SYSTEM,
                    (content, resumeContent) -> HrmResumeAnalyzeResultValidator.validateFull(content));
        }
        return runPersonalScene(userId, reqVO,
                HrmRecruitAgentSceneEnum.RESUME_ANALYZE,
                HrmRecruitAgentPromptTemplates.RESUME_ANALYZE_VERSION,
                HrmRecruitAgentPromptTemplates.RESUME_ANALYZE_SYSTEM,
                (content, resumeContent) -> HrmResumeAnalyzeResultValidator.validate(content));
    }

    @Override
    public Long submitJobMatch(Long userId, HrmPortalRecruitAgentSubmitReqVO reqVO) {
        requirePost(reqVO.getPostId());
        return runPersonalScene(userId, reqVO,
                HrmRecruitAgentSceneEnum.JOB_MATCH,
                HrmRecruitAgentPromptTemplates.JOB_MATCH_VERSION,
                HrmRecruitAgentPromptTemplates.JOB_MATCH_SYSTEM,
                (content, resumeContent) -> HrmJobMatchResultValidator.validate(content));
    }

    @Override
    public Long submitPreSubmitCheck(Long userId, HrmPortalRecruitAgentSubmitReqVO reqVO) {
        // 没有职位就无从检查「投这个岗位还差什么」，因此这两个场景仍然必须带职位
        requirePost(reqVO.getPostId());
        return runPersonalScene(userId, reqVO,
                HrmRecruitAgentSceneEnum.PRE_SUBMIT_CHECK,
                HrmRecruitAgentPromptTemplates.PRE_SUBMIT_CHECK_VERSION,
                HrmRecruitAgentPromptTemplates.PRE_SUBMIT_CHECK_SYSTEM,
                (content, resumeContent) -> HrmPreSubmitCheckResultValidator.validate(content));
    }

    @Override
    public Long submitResumeOptimize(Long userId, HrmPortalResumeOptimizeReqVO reqVO) {
        HrmPersonalResumeVersionDO version = resolveResume(userId, reqVO.getResumeVersionId(), null);
        // 优化对整份简历同样成立，没传职位就按整份来
        HrmRecruitPostDO post = loadPost(reqVO.getPostId());
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

    @Override
    public Long submitResumeGenerate(Long userId, HrmPortalResumeGenerateReqVO reqVO) {
        // 将问答字段映射到简历白名单字段，统一经过脱敏后再送入模型。
        JSONObject source = new JSONObject()
                .set("expectedPostName", reqVO.getTargetRole())
                .set("city", reqVO.getTargetCity())
                .set("skills", reqVO.getSkills())
                .set("strengths", reqVO.getStrengths());
        if (StrUtil.isNotBlank(reqVO.getEducation())) {
            source.set("educationExperiences", new JSONArray().set(
                    new JSONObject().set("experience", reqVO.getEducation())));
        }
        if (StrUtil.isNotBlank(reqVO.getExperience())) {
            source.set("experiences", new JSONArray().set(
                    new JSONObject().set("description", reqVO.getExperience())));
        }
        if (StrUtil.isNotBlank(reqVO.getProjects())) {
            source.set("projects", new JSONArray().set(
                    new JSONObject().set("description", reqVO.getProjects())));
        }
        JSONObject safeAnswers = HrmResumeAgentPayloadUtils.buildAgentSafePayload(source.toString());
        JSONObject modelPayload = new JSONObject().set("answers", safeAnswers);
        return sceneRunner.run(new HrmRecruitAgentRunBO()
                .setUserId(userId)
                .setScene(HrmRecruitAgentSceneEnum.RESUME_GENERATE)
                .setPromptVersion(HrmRecruitAgentPromptTemplates.RESUME_GENERATE_VERSION)
                .setSystemPrompt(HrmRecruitAgentPromptTemplates.RESUME_GENERATE_SYSTEM)
                .setResumeContent(safeAnswers.toString())
                .setAgentPayload(modelPayload)
                .setBizType("PERSONAL_RESUME_GENERATION")
                .setIdempotencyKey(reqVO.getIdempotencyKey())
                .setResultValidator(HrmResumeGenerateResultValidator::validate));
    }

    @Override
    public Long submitResumeStructure(Long userId, HrmPortalResumeStructureReqVO reqVO) {
        /*
         * 正文直接当纯文本送进 buildAgentSafePayload：它走的是「非 JSON」的分支，
         * 会做脱敏、长度上限（8000）与「脱敏后仍能识别出敏感信息就失败关闭」，
         * 产出 { resumeText: ... } —— 与 PLAIN_TEXT_RULE 约定的一致。
         *
         * 不用自己拼 JSON 的原因：那个方法已经把「什么字段能进模型」的白名单
         * 和脱敏都做完了，绕开它自己拼等于把这两道口子重新打开一次。
         */
        JSONObject safeAnswers = HrmResumeAgentPayloadUtils.buildAgentSafePayload(reqVO.getText());
        if (!safeAnswers.containsKey("resumeText")) {
            // 太短（<50 字）或脱敏不通过：这时调模型既没必要也拿不到有用结果，直接告诉用户
            throw exception(AGENT_RESUME_CONTENT_EMPTY);
        }
        // 输出结构与生成场景完全一致，所以复用同一个校验器：
        // 「事实必须能在素材里找到」这条护栏，在只该搬运、不该编造的整理任务上更关键。
        return sceneRunner.run(new HrmRecruitAgentRunBO()
                .setUserId(userId)
                .setScene(HrmRecruitAgentSceneEnum.RESUME_STRUCTURE)
                .setPromptVersion(HrmRecruitAgentPromptTemplates.RESUME_STRUCTURE_VERSION)
                .setSystemPrompt(HrmRecruitAgentPromptTemplates.RESUME_STRUCTURE_SYSTEM)
                .setResumeContent(safeAnswers.toString())
                .setAgentPayload(new JSONObject().set("answers", safeAnswers))
                .setBizType("PERSONAL_RESUME_STRUCTURE")
                .setIdempotencyKey(reqVO.getIdempotencyKey())
                .setResultValidator(HrmResumeGenerateResultValidator::validate));
    }

    /** 个人端三个场景流程一致，只有场景标识、提示词与校验器不同 */
    private Long runPersonalScene(Long userId, HrmPortalRecruitAgentSubmitReqVO reqVO,
                                  HrmRecruitAgentSceneEnum scene, String promptVersion, String systemPrompt,
                                  BiFunction<String, String, JSONObject> validator) {
        HrmPersonalResumeVersionDO version = resolveResume(userId, reqVO.getResumeVersionId(), reqVO.getResumeId());
        HrmRecruitPostDO post = loadPost(reqVO.getPostId());
        return sceneRunner.run(buildRunBO(userId, version, post)
                .setScene(scene)
                .setPromptVersion(promptVersion)
                .setSystemPrompt(systemPrompt)
                .setIdempotencyKey(reqVO.getIdempotencyKey())
                .setResultValidator(validator));
    }

    /**
     * 取「已发布且在招」的目标职位；没传职位时返回 null。
     *
     * <p>后续链路（指纹、脱敏负载、落库）都已按 post 可能为 null 写过，
     * 因此这里返回 null 是合法输入，而不是错误。</p>
     */
    private HrmRecruitPostDO loadPost(Long postId) {
        return postId == null ? null : postService.getPortalRecruitPostPublished(postId);
    }

    /** 必须带职位的场景（职位匹配、投递前检查）在调模型之前先拦下来 */
    private void requirePost(Long postId) {
        if (postId == null) {
            throw exception(AGENT_POST_REQUIRED);
        }
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

    private HrmPersonalResumeVersionDO resolveResume(Long userId, Long versionId, Long resumeId) {
        if (resumeId == null) return loadOwnedVersion(userId, versionId);
        HrmPersonalResumeDO resume = personalResumeService.getResume(userId, resumeId);
        if (resume == null || StrUtil.isBlank(resume.getContent())) {
            throw new IllegalArgumentException("简历内容为空或不存在");
        }
        HrmPersonalResumeVersionDO version = new HrmPersonalResumeVersionDO();
        version.setId(resume.getId());
        version.setUserId(userId);
        version.setContent(resume.getContent());
        return version;
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
