package cn.iocoder.txgy.module.hrm.service.agent;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo.HrmPortalRecruitAgentSubmitReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.agent.vo.HrmPortalRecruitAgentTaskRespVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.agent.HrmRecruitAgentAnalysisDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.agent.HrmRecruitAgentTaskDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.personal.resume.HrmPersonalResumeVersionDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.post.HrmRecruitPostDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.agent.HrmRecruitAgentAnalysisMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.agent.HrmRecruitAgentTaskMapper;
import cn.hutool.json.JSONObject;
import cn.iocoder.txgy.module.hrm.enums.agent.HrmRecruitAgentSceneEnum;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmRecruitAgentPromptTemplates;
import cn.iocoder.txgy.module.hrm.enums.agent.HrmRecruitAgentTaskStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.recruit.post.HrmRecruitPostStatusEnum;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmRecruitAgentModelClient;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmRecruitAgentModelRequest;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmRecruitAgentModelResponse;
import cn.iocoder.txgy.module.hrm.service.personal.resume.HrmPersonalResumeVersionService;
import cn.iocoder.txgy.module.hrm.service.recruit.post.HrmRecruitPostService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static cn.iocoder.txgy.framework.test.core.util.RandomUtils.randomLongId;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_MODEL_NOT_CONFIGURED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_MODEL_OUTPUT_INVALID;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_RESUME_CONTENT_EMPTY;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_TASK_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 简历诊断服务测试（A01）。
 *
 * <p>全部使用可控的模型模拟响应，不做任何真实模型调用。</p>
 */
@Import({HrmRecruitAgentAnalyzeServiceImpl.class, HrmRecruitAgentSceneRunner.class,
        HrmRecruitAgentTaskServiceImpl.class})
public class HrmRecruitAgentAnalyzeServiceImplTest extends BaseDbUnitTest {

    @Resource
    private HrmRecruitAgentAnalyzeServiceImpl analyzeService;
    @Resource
    private HrmRecruitAgentTaskMapper taskMapper;
    @Resource
    private HrmRecruitAgentAnalysisMapper analysisMapper;

    @MockitoBean
    private HrmPersonalResumeVersionService personalResumeVersionService;
    @MockitoBean
    private HrmRecruitPostService postService;
    @MockitoBean
    private HrmRecruitAgentModelClient modelClient;

    private static final String RESUME_CONTENT =
            "{\"skills\":\"管道保温施工\",\"strengths\":\"责任心强\","
                    + "\"expectedPostName\":\"项目经理\",\"gender\":2,\"mobile\":\"13800000000\","
                    + "\"experiences\":[{\"company\":\"某公司\",\"position\":\"项目经理\"}]}";

    private static final String VALID_MODEL_OUTPUT = """
            {
              "overallScore": 78,
              "dimensions": [
                {"key":"COMPLETENESS","score":80,"comment":"信息较完整",
                 "evidence":[{"field":"experiences","note":"包含工作经历"}]},
                {"key":"CLARITY","score":75,"comment":"表述清晰",
                 "evidence":[{"field":"skills","note":"技能描述具体"}]},
                {"key":"PROFESSIONALISM","score":70,"comment":"专业性尚可",
                 "evidence":[{"field":"strengths","note":"自我评价具体"}]},
                {"key":"JOB_MATCH","score":85,"comment":"与岗位较匹配",
                 "evidence":[{"field":"expectedPostName","note":"求职意向与岗位一致"}]}
              ],
              "strengths": ["工作经历连续"],
              "missingInfo": ["缺少证书信息"],
              "risks": [],
              "suggestions": [
                {"module":"SKILLS","fieldPath":"skills","advice":"补充具体工艺","reason":"岗位要求"}
              ]
            }
            """;

    /** 准备：本人简历版本 + 已发布职位 + 可用的模型 */
    private Long prepare(Long userId, String modelOutput) {
        HrmPersonalResumeVersionDO version = new HrmPersonalResumeVersionDO()
                .setId(randomLongId()).setUserId(userId).setVersionNo(1).setContent(RESUME_CONTENT);
        HrmRecruitPostDO post = new HrmRecruitPostDO()
                .setId(randomLongId()).setPostName("项目经理")
                .setStatus(HrmRecruitPostStatusEnum.PUBLISHED.getStatus());
        when(personalResumeVersionService.getVersion(userId, version.getId())).thenReturn(version);
        when(postService.getPortalRecruitPostPublished(post.getId())).thenReturn(post);
        when(modelClient.isReady()).thenReturn(true);
        when(modelClient.modelName()).thenReturn("qwen-flash");
        when(modelClient.chat(any())).thenReturn(new HrmRecruitAgentModelResponse()
                .setContent(modelOutput).setModel("qwen-flash").setDurationMs(1200L));
        return version.getId();
    }

    private HrmPortalRecruitAgentSubmitReqVO buildReq(Long resumeVersionId, Long postId, String key) {
        return new HrmPortalRecruitAgentSubmitReqVO()
                .setResumeVersionId(resumeVersionId).setPostId(postId).setIdempotencyKey(key);
    }

    @Test
    public void testSubmitResumeAnalyze_persistsTaskAndAnalysis() {
        Long userId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        Long resumeVersionId = prepare(userId, VALID_MODEL_OUTPUT);
        HrmRecruitPostDO post = new HrmRecruitPostDO().setId(randomLongId()).setPostName("项目经理")
                .setStatus(HrmRecruitPostStatusEnum.PUBLISHED.getStatus());
        when(postService.getPortalRecruitPostPublished(post.getId())).thenReturn(post);

        Long taskId;
        try {
            taskId = analyzeService.submitResumeAnalyze(userId,
                    buildReq(resumeVersionId, post.getId(), "persist-key"));
        } finally {
            TenantContextHolder.clear();
        }

        HrmRecruitAgentTaskDO task = TenantUtils.executeIgnore(() -> taskMapper.selectById(taskId));
        assertEquals(HrmRecruitAgentTaskStatusEnum.SUCCEEDED.getStatus(), task.getStatus());
        assertEquals("qwen-flash", task.getModel());
        assertNotNull(task.getPromptVersion());
        assertNull(task.getErrorCode());

        HrmRecruitAgentAnalysisDO analysis = TenantUtils.executeIgnore(
                () -> analysisMapper.selectByTaskId(taskId));
        assertNotNull(analysis);
        assertEquals(78, analysis.getOverallScore());
        assertEquals(resumeVersionId, analysis.getResumeVersionId());
        assertEquals(post.getId(), analysis.getPostId());
        assertNotNull(analysis.getPostFingerprint());
        assertNotNull(analysis.getResultJson());

        // 结果可通过查询接口取回，并带上免责标识
        HrmPortalRecruitAgentTaskRespVO respVO = analyzeService.getTaskResult(userId, taskId);
        assertEquals(HrmRecruitAgentTaskStatusEnum.SUCCEEDED.getStatus(), respVO.getStatus());
        assertEquals(78, respVO.getOverallScore());
        assertNotNull(respVO.getResult());
        assertEquals("AI 辅助分析，仅供参考", respVO.getDisclaimer());
    }

    @Test
    public void testSubmitResumeAnalyze_doesNotSendSensitiveFieldsToModel() {
        Long userId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        Long resumeVersionId = prepare(userId, VALID_MODEL_OUTPUT);
        HrmRecruitPostDO post = new HrmRecruitPostDO().setId(randomLongId()).setPostName("项目经理")
                .setStatus(HrmRecruitPostStatusEnum.PUBLISHED.getStatus());
        when(postService.getPortalRecruitPostPublished(post.getId())).thenReturn(post);

        try {
            analyzeService.submitResumeAnalyze(userId, buildReq(resumeVersionId, post.getId(), "mask-key"));
        } finally {
            TenantContextHolder.clear();
        }

        ArgumentCaptor<HrmRecruitAgentModelRequest> captor =
                ArgumentCaptor.forClass(HrmRecruitAgentModelRequest.class);
        verify(modelClient).chat(captor.capture());
        String sentPayload = captor.getValue().getUserPayload();
        // 手机号与敏感属性都不得出现在发给模型的内容里
        assertFalse(sentPayload.contains("13800000000"));
        assertFalse(sentPayload.contains("\"gender\""));
        assertFalse(sentPayload.contains("\"mobile\""));
    }

    @Test
    public void testSubmitResumeAnalyze_isIdempotentAndCallsModelOnce() {
        Long userId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        Long resumeVersionId = prepare(userId, VALID_MODEL_OUTPUT);
        HrmRecruitPostDO post = new HrmRecruitPostDO().setId(randomLongId()).setPostName("项目经理")
                .setStatus(HrmRecruitPostStatusEnum.PUBLISHED.getStatus());
        when(postService.getPortalRecruitPostPublished(post.getId())).thenReturn(post);

        Long firstTaskId;
        Long secondTaskId;
        try {
            firstTaskId = analyzeService.submitResumeAnalyze(userId,
                    buildReq(resumeVersionId, post.getId(), "dup-key"));
            secondTaskId = analyzeService.submitResumeAnalyze(userId,
                    buildReq(resumeVersionId, post.getId(), "dup-key"));
        } finally {
            TenantContextHolder.clear();
        }

        assertEquals(firstTaskId, secondTaskId);
        // 已成功的任务不再重复调用模型
        verify(modelClient, times(1)).chat(any());
    }

    @Test
    public void testSubmitResumeAnalyze_marksFailedWhenModelNotConfigured() {
        Long userId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        HrmPersonalResumeVersionDO version = new HrmPersonalResumeVersionDO()
                .setId(randomLongId()).setUserId(userId).setVersionNo(1).setContent(RESUME_CONTENT);
        HrmRecruitPostDO post = new HrmRecruitPostDO().setId(randomLongId()).setPostName("项目经理")
                .setStatus(HrmRecruitPostStatusEnum.PUBLISHED.getStatus());
        when(personalResumeVersionService.getVersion(userId, version.getId())).thenReturn(version);
        when(postService.getPortalRecruitPostPublished(post.getId())).thenReturn(post);
        when(modelClient.isReady()).thenReturn(false);
        when(modelClient.chat(any())).thenThrow(
                new ServiceException(AGENT_MODEL_NOT_CONFIGURED.getCode(), AGENT_MODEL_NOT_CONFIGURED.getMsg()));

        ServiceException exception = assertThrows(ServiceException.class, () -> {
            TenantContextHolder.setTenantId(101L);
            try {
                analyzeService.submitResumeAnalyze(userId,
                        buildReq(version.getId(), post.getId(), "noconfig-key"));
            } finally {
                TenantContextHolder.clear();
            }
        });
        assertEquals(AGENT_MODEL_NOT_CONFIGURED.getCode(), exception.getCode());

        // 失败任务必须留痕，且不得生成半成品结果
        HrmRecruitAgentTaskDO task = TenantUtils.executeIgnore(
                () -> taskMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<HrmRecruitAgentTaskDO>()
                        .eq(HrmRecruitAgentTaskDO::getUserId, userId)));
        assertNotNull(task);
        assertEquals(HrmRecruitAgentTaskStatusEnum.FAILED.getStatus(), task.getStatus());
        assertNotNull(task.getErrorCode());
        assertNull(TenantUtils.executeIgnore(() -> analysisMapper.selectByTaskId(task.getId())));
    }

    @Test
    public void testSubmitResumeAnalyze_marksFailedWhenOutputInvalid() {
        Long userId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        Long resumeVersionId = prepare(userId, "{\"overallScore\": 200}");
        HrmRecruitPostDO post = new HrmRecruitPostDO().setId(randomLongId()).setPostName("项目经理")
                .setStatus(HrmRecruitPostStatusEnum.PUBLISHED.getStatus());
        when(postService.getPortalRecruitPostPublished(post.getId())).thenReturn(post);

        Long taskId = null;
        ServiceException exception = assertThrows(ServiceException.class, () -> {
            TenantContextHolder.setTenantId(101L);
            try {
                analyzeService.submitResumeAnalyze(userId,
                        buildReq(resumeVersionId, post.getId(), "invalid-key"));
            } finally {
                TenantContextHolder.clear();
            }
        });
        assertEquals(AGENT_MODEL_OUTPUT_INVALID.getCode(), exception.getCode());

        HrmRecruitAgentTaskDO task = TenantUtils.executeIgnore(
                () -> taskMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<HrmRecruitAgentTaskDO>()
                        .eq(HrmRecruitAgentTaskDO::getUserId, userId)));
        assertNotNull(task);
        assertEquals(HrmRecruitAgentTaskStatusEnum.FAILED.getStatus(), task.getStatus());
        assertNull(TenantUtils.executeIgnore(() -> analysisMapper.selectByTaskId(task.getId())));
    }

    @Test
    public void testSubmitResumeAnalyze_rejectsEmptyResumeContent() {
        Long userId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        HrmPersonalResumeVersionDO version = new HrmPersonalResumeVersionDO()
                .setId(randomLongId()).setUserId(userId).setVersionNo(1).setContent(null);
        HrmRecruitPostDO post = new HrmRecruitPostDO().setId(randomLongId()).setPostName("项目经理")
                .setStatus(HrmRecruitPostStatusEnum.PUBLISHED.getStatus());
        when(personalResumeVersionService.getVersion(userId, version.getId())).thenReturn(version);
        when(postService.getPortalRecruitPostPublished(post.getId())).thenReturn(post);

        ServiceException exception = assertThrows(ServiceException.class, () -> {
            TenantContextHolder.setTenantId(101L);
            try {
                analyzeService.submitResumeAnalyze(userId,
                        buildReq(version.getId(), post.getId(), "empty-key"));
            } finally {
                TenantContextHolder.clear();
            }
        });
        assertEquals(AGENT_RESUME_CONTENT_EMPTY.getCode(), exception.getCode());
    }

    /** A05 职位匹配的合法模型输出 */
    private static final String VALID_MATCH_OUTPUT = """
            {
              "overallScore": 76,
              "dimensions": [
                {"key":"SKILL","score":80,"comment":"技能基本覆盖",
                 "evidence":[{"field":"skills","note":"具备管道保温施工技能"}]},
                {"key":"EXPERIENCE","score":75,"comment":"经验年限满足",
                 "evidence":[{"field":"experiences","note":"有现场施工管理经验"}]},
                {"key":"EDUCATION","score":90,"comment":"学历满足要求",
                 "evidence":[{"field":"educationExperiences","note":"本科"}]},
                {"key":"RESPONSIBILITY","score":70,"comment":"职责部分重合",
                 "evidence":[{"field":"description","note":"负责过施工组织"}]},
                {"key":"KEYWORD","score":65,"comment":"关键词覆盖一般",
                 "evidence":[{"field":"postName","note":"岗位名含防腐保温"}]}
              ],
              "matched": [{"point":"具备现场施工管理经验","evidence":"负责过项目"}],
              "missing": [{"point":"未体现出差意愿","evidence":"简历未提及"}],
              "toConfirm": [{"point":"是否持证","reason":"简历未说明证书等级"}]
            }
            """;

    @Test
    public void testSubmitJobMatch_usesMatchSceneAndPersistsResult() {
        Long userId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        Long resumeVersionId = prepare(userId, VALID_MATCH_OUTPUT);
        HrmRecruitPostDO post = new HrmRecruitPostDO().setId(randomLongId()).setPostName("项目经理")
                .setStatus(HrmRecruitPostStatusEnum.PUBLISHED.getStatus());
        when(postService.getPortalRecruitPostPublished(post.getId())).thenReturn(post);

        Long taskId;
        try {
            taskId = analyzeService.submitJobMatch(userId,
                    buildReq(resumeVersionId, post.getId(), "match-key"));
        } finally {
            TenantContextHolder.clear();
        }

        HrmRecruitAgentTaskDO task = TenantUtils.executeIgnore(() -> taskMapper.selectById(taskId));
        assertEquals(HrmRecruitAgentTaskStatusEnum.SUCCEEDED.getStatus(), task.getStatus());
        // 场景必须是匹配，提示词版本也要与诊断区分开
        assertEquals(HrmRecruitAgentSceneEnum.JOB_MATCH.getScene(), task.getScene());
        assertEquals(HrmRecruitAgentPromptTemplates.JOB_MATCH_VERSION, task.getPromptVersion());

        HrmRecruitAgentAnalysisDO analysis = TenantUtils.executeIgnore(
                () -> analysisMapper.selectByTaskId(taskId));
        assertNotNull(analysis);
        assertEquals(HrmRecruitAgentSceneEnum.JOB_MATCH.getScene(), analysis.getScene());
        assertEquals(76, analysis.getOverallScore());

        // 三类结论都能取回
        HrmPortalRecruitAgentTaskRespVO respVO = analyzeService.getTaskResult(userId, taskId);
        assertEquals(HrmRecruitAgentSceneEnum.JOB_MATCH.getScene(), respVO.getScene());
        JSONObject result = (JSONObject) respVO.getResult();
        assertEquals(1, result.getJSONArray("matched").size());
        assertEquals(1, result.getJSONArray("missing").size());
        assertEquals(1, result.getJSONArray("toConfirm").size());
    }

    @Test
    public void testSubmitJobMatch_marksFailedWhenOutputUsesAnalyzeShape() {
        Long userId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        // 模型返回了诊断结构：匹配场景必须判不合格，不能张冠李戴
        Long resumeVersionId = prepare(userId, VALID_MODEL_OUTPUT);
        HrmRecruitPostDO post = new HrmRecruitPostDO().setId(randomLongId()).setPostName("项目经理")
                .setStatus(HrmRecruitPostStatusEnum.PUBLISHED.getStatus());
        when(postService.getPortalRecruitPostPublished(post.getId())).thenReturn(post);

        ServiceException exception = assertThrows(ServiceException.class, () -> {
            TenantContextHolder.setTenantId(101L);
            try {
                analyzeService.submitJobMatch(userId, buildReq(resumeVersionId, post.getId(), "wrong-shape"));
            } finally {
                TenantContextHolder.clear();
            }
        });
        assertEquals(AGENT_MODEL_OUTPUT_INVALID.getCode(), exception.getCode());

        HrmRecruitAgentTaskDO task = TenantUtils.executeIgnore(
                () -> taskMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<HrmRecruitAgentTaskDO>()
                                .eq(HrmRecruitAgentTaskDO::getUserId, userId)));
        assertEquals(HrmRecruitAgentTaskStatusEnum.FAILED.getStatus(), task.getStatus());
        assertNull(TenantUtils.executeIgnore(() -> analysisMapper.selectByTaskId(task.getId())));
    }

    @Test
    public void testGetAnalyzeResult_rejectsOtherUser() {        Long ownerId = randomLongId();
        Long otherUserId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        Long resumeVersionId = prepare(ownerId, VALID_MODEL_OUTPUT);
        HrmRecruitPostDO post = new HrmRecruitPostDO().setId(randomLongId()).setPostName("项目经理")
                .setStatus(HrmRecruitPostStatusEnum.PUBLISHED.getStatus());
        when(postService.getPortalRecruitPostPublished(post.getId())).thenReturn(post);

        Long taskId;
        try {
            taskId = analyzeService.submitResumeAnalyze(ownerId,
                    buildReq(resumeVersionId, post.getId(), "owner-key"));
        } finally {
            TenantContextHolder.clear();
        }

        ServiceException exception = assertThrows(ServiceException.class,
                () -> analyzeService.getTaskResult(otherUserId, taskId));
        assertEquals(AGENT_TASK_NOT_EXISTS.getCode(), exception.getCode());
    }

    /* ===== A06 投递前检查 ===== */

    private static final String PRE_CHECK_MODEL_OUTPUT = """
            {
              "summary": "整体信息完整，建议补充项目量化成果",
              "requiredMissing": [{"item":"缺少期望到岗时间","note":"便于安排面试与入职"}],
              "conflicts": [{"point":"工作年限与经历起止时间对不上","evidence":"经历从 2020 年开始"}],
              "gaps": [{"point":"未体现压力管道安装资质","evidence":"职位要求具备该资质"}]
            }
            """;

    @Test
    public void testSubmitPreSubmitCheck_persistsTaskWithoutScore() {
        Long userId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        Long resumeVersionId = prepare(userId, PRE_CHECK_MODEL_OUTPUT);
        HrmRecruitPostDO post = new HrmRecruitPostDO().setId(randomLongId()).setPostName("项目经理")
                .setStatus(HrmRecruitPostStatusEnum.PUBLISHED.getStatus());
        when(postService.getPortalRecruitPostPublished(post.getId())).thenReturn(post);

        Long taskId;
        try {
            taskId = analyzeService.submitPreSubmitCheck(userId,
                    buildReq(resumeVersionId, post.getId(), "precheck-key"));
        } finally {
            TenantContextHolder.clear();
        }

        HrmRecruitAgentTaskDO task = TenantUtils.executeIgnore(() -> taskMapper.selectById(taskId));
        assertEquals(HrmRecruitAgentTaskStatusEnum.SUCCEEDED.getStatus(), task.getStatus());
        assertEquals(HrmRecruitAgentSceneEnum.PRE_SUBMIT_CHECK.getScene(), task.getScene());
        assertEquals(HrmRecruitAgentPromptTemplates.PRE_SUBMIT_CHECK_VERSION, task.getPromptVersion());

        HrmRecruitAgentAnalysisDO analysis = TenantUtils.executeIgnore(
                () -> analysisMapper.selectByTaskId(taskId));
        assertNotNull(analysis);
        // 投递前检查只做提醒，不产出任何分数
        assertNull(analysis.getOverallScore());
        assertNotNull(analysis.getResultJson());
        assertTrue(analysis.getResultJson().contains("requiredMissing"));
    }

    @Test
    public void testSubmitPreSubmitCheck_recordsTokenUsage() {
        Long userId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        Long resumeVersionId = prepare(userId, PRE_CHECK_MODEL_OUTPUT);
        HrmRecruitPostDO post = new HrmRecruitPostDO().setId(randomLongId()).setPostName("项目经理")
                .setStatus(HrmRecruitPostStatusEnum.PUBLISHED.getStatus());
        when(postService.getPortalRecruitPostPublished(post.getId())).thenReturn(post);
        // 提供方返回用量时必须落库，用于调用量审计
        when(modelClient.chat(any())).thenReturn(new HrmRecruitAgentModelResponse()
                .setContent(PRE_CHECK_MODEL_OUTPUT).setModel("glm-4.5-air").setDurationMs(900L)
                .setPromptTokens(900).setCompletionTokens(400).setTotalTokens(1300));

        Long taskId;
        try {
            taskId = analyzeService.submitPreSubmitCheck(userId,
                    buildReq(resumeVersionId, post.getId(), "precheck-usage-key"));
        } finally {
            TenantContextHolder.clear();
        }

        HrmRecruitAgentTaskDO task = TenantUtils.executeIgnore(() -> taskMapper.selectById(taskId));
        assertEquals(900, task.getPromptTokens());
        assertEquals(400, task.getCompletionTokens());
        assertEquals(1300, task.getTotalTokens());
    }
}
