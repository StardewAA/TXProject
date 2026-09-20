package cn.iocoder.txgy.module.hrm.service.agent;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
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
import cn.iocoder.txgy.module.hrm.enums.agent.HrmRecruitAgentTaskStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.recruit.post.HrmRecruitPostStatusEnum;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmRecruitAgentModelClient;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmRecruitAgentModelRequest;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmRecruitAgentModelResponse;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmRecruitAgentPromptTemplates;
import cn.iocoder.txgy.module.hrm.service.recruit.application.HrmRecruitApplicationService;
import cn.iocoder.txgy.module.hrm.service.recruit.post.HrmRecruitPostService;
import cn.iocoder.txgy.module.hrm.service.recruit.resume.HrmRecruitResumeService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static cn.iocoder.txgy.framework.test.core.util.RandomUtils.randomLongId;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_APPLICATION_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_APPLICATION_RESUME_UNAVAILABLE;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_POST_NOT_AVAILABLE;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_TASK_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端招聘 Agent 服务测试（A07 / A08 / A09）。
 *
 * <p>重点：应聘记录、简历快照与职位都在租户内校验；AI 不改变任何业务状态；
 * 发给模型的内容不含敏感字段。全部使用可控的模型模拟响应。</p>
 */
@Import({HrmRecruitAgentAdminServiceImpl.class, HrmRecruitAgentSceneRunner.class,
        HrmRecruitAgentTaskServiceImpl.class})
public class HrmRecruitAgentAdminServiceImplTest extends BaseDbUnitTest {

    private static final Long TENANT_ID = 2019L;

    @Resource
    private HrmRecruitAgentAdminServiceImpl agentService;
    @Resource
    private HrmRecruitAgentTaskMapper taskMapper;
    @Resource
    private HrmRecruitAgentAnalysisMapper analysisMapper;

    @MockitoBean
    private HrmRecruitApplicationService applicationService;
    @MockitoBean
    private HrmRecruitResumeService recruitResumeService;
    @MockitoBean
    private HrmRecruitPostService postService;
    @MockitoBean
    private HrmRecruitAgentModelClient modelClient;

    private static final String RESUME_CONTENT =
            "{\"skills\":\"管道保温施工\",\"strengths\":\"责任心强\","
                    + "\"expectedPostName\":\"项目经理\",\"gender\":2,\"mobile\":\"13800000000\","
                    + "\"experiences\":[{\"company\":\"某公司\",\"position\":\"项目经理\"}]}";

    private static final String SUMMARY_OUTPUT = """
            {
              "coreSkills": ["管道保温施工"],
              "workYears": "简历未提供",
              "experienceSummary": "负责过工业管道保温项目。",
              "achievements": [],
              "facts": [{"field":"experiences","note":"在某公司任项目经理"}],
              "missingInfo": ["未提供期望到岗时间"],
              "inferences": ["长期驻外的接受度需核实"]
            }
            """;

    private static final String MATCH_OUTPUT = """
            {
              "overallScore": 76,
              "dimensions": [
                {"key":"SKILL","score":80,"comment":"技能基本覆盖",
                 "evidence":[{"field":"skills","note":"具备管道保温施工技能"}]},
                {"key":"EXPERIENCE","score":75,"comment":"经验年限满足",
                 "evidence":[{"field":"experiences","note":"有项目管理经历"}]},
                {"key":"EDUCATION","score":90,"comment":"学历满足要求",
                 "evidence":[{"field":"degree","note":"本科"}]},
                {"key":"RESPONSIBILITY","score":70,"comment":"职责部分重合",
                 "evidence":[{"field":"description","note":"负责过施工组织"}]},
                {"key":"KEYWORD","score":65,"comment":"关键词覆盖一般",
                 "evidence":[{"field":"postName","note":"岗位名含防腐保温"}]}
              ],
              "matched": [{"point":"具备现场施工管理经验","evidence":"有项目经历"}],
              "missing": [{"point":"未体现出差意愿","evidence":"简历未提及"}],
              "toConfirm": [{"point":"是否持有一级建造师证","reason":"简历未写明"}],
              "risks": ["经历间隔较长，需要核实原因"]
            }
            """;

    private static final String QUESTION_OUTPUT = """
            {
              "questions": [
                {"category":"PROFESSIONAL","question":"保温层厚度如何确定？",
                 "purpose":"核实专业知识","focus":"保温计算依据"},
                {"category":"PROJECT","question":"介绍一个你负责的项目",
                 "purpose":"了解项目经历","focus":"个人贡献"},
                {"category":"COMPETENCY","question":"工期与要求冲突时怎么处理？",
                 "purpose":"考察协调能力","focus":"处理思路"},
                {"category":"VERIFICATION","question":"简历提到二级建造师，是否已注册？",
                 "purpose":"核实证书状态","focus":"注册单位"}
              ]
            }
            """;

    /** prepare 建立的简历快照编号与职位编号，供断言使用 */
    private Long preparedResumeId;
    private Long preparedPostId;

    /** 准备一条租户内的应聘记录、简历快照与职位，并让模型返回指定内容 */
    private Long prepare(Long userId, String modelOutput) {
        Long applicationId = randomLongId();
        Long resumeId = randomLongId();
        HrmRecruitPostDO post = new HrmRecruitPostDO().setId(randomLongId()).setPostName("项目经理")
                .setStatus(HrmRecruitPostStatusEnum.PUBLISHED.getStatus());
        HrmRecruitApplicationDO application = new HrmRecruitApplicationDO()
                .setId(applicationId).setCandidateId(randomLongId())
                .setPostId(post.getId()).setResumeId(resumeId);
        HrmRecruitResumeDO resume = new HrmRecruitResumeDO()
                .setId(resumeId).setCandidateId(application.getCandidateId())
                .setContent(RESUME_CONTENT).setVersionNo(1).setStatus(0);
        when(applicationService.getApplication(applicationId)).thenReturn(application);
        when(recruitResumeService.getAuthorizedRecruitResume(resumeId)).thenReturn(resume);
        when(postService.getRecruitPost(post.getId())).thenReturn(post);
        when(modelClient.isReady()).thenReturn(true);
        when(modelClient.modelName()).thenReturn("glm-4.5-air");
        when(modelClient.chat(any())).thenReturn(new HrmRecruitAgentModelResponse()
                .setContent(modelOutput).setModel("glm-4.5-air").setDurationMs(1200L)
                .setPromptTokens(900).setCompletionTokens(500).setTotalTokens(1400));
        preparedResumeId = resumeId;
        preparedPostId = post.getId();
        return applicationId;
    }

    private HrmRecruitAgentApplicationReqVO buildReq(Long applicationId, String key) {
        return new HrmRecruitAgentApplicationReqVO()
                .setApplicationId(applicationId).setIdempotencyKey(key);
    }

    private <T> T inTenant(java.util.function.Supplier<T> action) {
        TenantContextHolder.setTenantId(TENANT_ID);
        try {
            return action.get();
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    public void testSubmitCandidateSummary_persistsTaskBoundToApplication() {
        Long userId = randomLongId();
        Long applicationId = prepare(userId, SUMMARY_OUTPUT);

        Long taskId = inTenant(() -> agentService.submitCandidateSummary(userId,
                buildReq(applicationId, "summary-key")));

        HrmRecruitAgentTaskDO task = TenantUtils.executeIgnore(() -> taskMapper.selectById(taskId));
        assertEquals(HrmRecruitAgentTaskStatusEnum.SUCCEEDED.getStatus(), task.getStatus());
        assertEquals(HrmRecruitAgentSceneEnum.CANDIDATE_SUMMARY.getScene(), task.getScene());
        assertEquals("APPLICATION", task.getBizType());
        assertEquals(applicationId, task.getBizId());
        // 结果必须关联到具体应聘记录与简历快照
        assertEquals(preparedResumeId, task.getResumeVersionId());
        assertEquals(preparedPostId, task.getPostId());
        assertEquals(HrmRecruitAgentPromptTemplates.CANDIDATE_SUMMARY_VERSION, task.getPromptVersion());
        // 调用量用于审计
        assertEquals(1400, task.getTotalTokens());

        HrmRecruitAgentAnalysisDO analysis = TenantUtils.executeIgnore(
                () -> analysisMapper.selectByTaskId(taskId));
        assertNotNull(analysis);
        assertNull(analysis.getOverallScore());
        assertNotNull(analysis.getResultJson());
    }

    @Test
    public void testSubmitCandidateSummary_returnsResultWithFactsAndInferences() {
        Long userId = randomLongId();
        Long applicationId = prepare(userId, SUMMARY_OUTPUT);

        Long taskId = inTenant(() -> agentService.submitCandidateSummary(userId,
                buildReq(applicationId, "summary-read-key")));

        HrmRecruitAgentTaskRespVO respVO = inTenant(() -> agentService.getTaskResult(taskId));
        assertEquals(HrmRecruitAgentTaskStatusEnum.SUCCEEDED.getStatus(), respVO.getStatus());
        assertEquals(applicationId, respVO.getApplicationId());
        assertEquals(preparedResumeId, respVO.getResumeId());
        assertNotNull(respVO.getResult());
        assertEquals("AI 辅助分析，仅供参考", respVO.getDisclaimer());
    }

    @Test
    public void testSubmitApplicationMatch_requiresRisks() {
        Long userId = randomLongId();
        Long applicationId = prepare(userId, MATCH_OUTPUT);

        Long taskId = inTenant(() -> agentService.submitApplicationMatch(userId,
                buildReq(applicationId, "match-key")));

        HrmRecruitAgentTaskDO task = TenantUtils.executeIgnore(() -> taskMapper.selectById(taskId));
        assertEquals(HrmRecruitAgentTaskStatusEnum.SUCCEEDED.getStatus(), task.getStatus());
        assertEquals(HrmRecruitAgentSceneEnum.APPLICATION_MATCH.getScene(), task.getScene());

        HrmRecruitAgentAnalysisDO analysis = TenantUtils.executeIgnore(
                () -> analysisMapper.selectByTaskId(taskId));
        assertEquals(76, analysis.getOverallScore());
        assertNotNull(analysis.getResultJson());
        // 管理端额外要求风险项
        org.junit.jupiter.api.Assertions.assertTrue(analysis.getResultJson().contains("risks"));
    }

    @Test
    public void testSubmitInterviewQuestion_persistsQuestions() {
        Long userId = randomLongId();
        Long applicationId = prepare(userId, QUESTION_OUTPUT);

        Long taskId = inTenant(() -> agentService.submitInterviewQuestion(userId,
                buildReq(applicationId, "question-key")));

        HrmRecruitAgentTaskDO task = TenantUtils.executeIgnore(() -> taskMapper.selectById(taskId));
        assertEquals(HrmRecruitAgentTaskStatusEnum.SUCCEEDED.getStatus(), task.getStatus());
        assertEquals(HrmRecruitAgentSceneEnum.INTERVIEW_QUESTION.getScene(), task.getScene());
        assertEquals(HrmRecruitAgentPromptTemplates.INTERVIEW_QUESTION_VERSION, task.getPromptVersion());

        HrmRecruitAgentAnalysisDO analysis = TenantUtils.executeIgnore(
                () -> analysisMapper.selectByTaskId(taskId));
        assertNotNull(analysis);
        assertNull(analysis.getOverallScore());
        org.junit.jupiter.api.Assertions.assertTrue(analysis.getResultJson().contains("VERIFICATION"));
    }

    @Test
    public void testSubmitCandidateSummary_doesNotSendSensitiveFieldsToModel() {
        Long userId = randomLongId();
        Long applicationId = prepare(userId, SUMMARY_OUTPUT);

        inTenant(() -> agentService.submitCandidateSummary(userId, buildReq(applicationId, "mask-key")));

        ArgumentCaptor<HrmRecruitAgentModelRequest> captor =
                ArgumentCaptor.forClass(HrmRecruitAgentModelRequest.class);
        verify(modelClient).chat(captor.capture());
        String sentPayload = captor.getValue().getUserPayload();
        assertFalse(sentPayload.contains("13800000000"));
        assertFalse(sentPayload.contains("\"gender\""));
        assertFalse(sentPayload.contains("\"mobile\""));
    }

    @Test
    public void testSubmitCandidateSummary_rejectsUnknownApplication() {
        Long userId = randomLongId();
        Long applicationId = randomLongId();
        when(applicationService.getApplication(applicationId)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> inTenant(() -> agentService.submitCandidateSummary(userId,
                        buildReq(applicationId, "missing-app-key"))));
        assertEquals(AGENT_APPLICATION_NOT_EXISTS.getCode(), exception.getCode());
    }

    @Test
    public void testSubmitCandidateSummary_rejectsApplicationWithoutStructuredResume() {
        Long userId = randomLongId();
        Long applicationId = randomLongId();
        HrmRecruitPostDO post = new HrmRecruitPostDO().setId(randomLongId()).setPostName("项目经理");
        when(applicationService.getApplication(applicationId)).thenReturn(new HrmRecruitApplicationDO()
                .setId(applicationId).setPostId(post.getId()).setResumeId(randomLongId()));
        when(postService.getRecruitPost(post.getId())).thenReturn(post);
        // 附件简历没有结构化内容，无法分析
        when(recruitResumeService.getAuthorizedRecruitResume(any())).thenReturn(
                new HrmRecruitResumeDO().setId(randomLongId()).setFileUrl("hrm/recruit/resume/a.pdf"));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> inTenant(() -> agentService.submitCandidateSummary(userId,
                        buildReq(applicationId, "attachment-key"))));
        assertEquals(AGENT_APPLICATION_RESUME_UNAVAILABLE.getCode(), exception.getCode());
    }

    @Test
    public void testSubmitCandidateSummary_rejectsApplicationWithoutResume() {
        Long userId = randomLongId();
        Long applicationId = randomLongId();
        HrmRecruitPostDO post = new HrmRecruitPostDO().setId(randomLongId()).setPostName("项目经理");
        when(applicationService.getApplication(applicationId)).thenReturn(new HrmRecruitApplicationDO()
                .setId(applicationId).setPostId(post.getId()));
        when(postService.getRecruitPost(post.getId())).thenReturn(post);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> inTenant(() -> agentService.submitCandidateSummary(userId,
                        buildReq(applicationId, "no-resume-key"))));
        assertEquals(AGENT_APPLICATION_RESUME_UNAVAILABLE.getCode(), exception.getCode());
    }

    @Test
    public void testSubmitCandidateSummary_rejectsDeletedPost() {
        Long userId = randomLongId();
        Long applicationId = randomLongId();
        when(applicationService.getApplication(applicationId)).thenReturn(new HrmRecruitApplicationDO()
                .setId(applicationId).setPostId(randomLongId()).setResumeId(randomLongId()));
        when(postService.getRecruitPost(any())).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> inTenant(() -> agentService.submitCandidateSummary(userId,
                        buildReq(applicationId, "no-post-key"))));
        assertEquals(AGENT_POST_NOT_AVAILABLE.getCode(), exception.getCode());
    }

    @Test
    public void testGetTaskResult_rejectsOtherTenant() {
        Long userId = randomLongId();
        Long applicationId = prepare(userId, SUMMARY_OUTPUT);
        Long taskId = inTenant(() -> agentService.submitCandidateSummary(userId,
                buildReq(applicationId, "tenant-key")));

        // 换一个租户后，按主键也读不到别家企业的任务
        ServiceException exception = assertThrows(ServiceException.class, () -> {
            TenantContextHolder.setTenantId(TENANT_ID + 1);
            try {
                agentService.getTaskResult(taskId);
            } finally {
                TenantContextHolder.clear();
            }
        });
        assertEquals(AGENT_TASK_NOT_EXISTS.getCode(), exception.getCode());
    }

    @Test
    public void testSubmitCandidateSummary_isIdempotentPerKey() {
        Long userId = randomLongId();
        Long applicationId = prepare(userId, SUMMARY_OUTPUT);

        Long firstTaskId = inTenant(() -> agentService.submitCandidateSummary(userId,
                buildReq(applicationId, "same-key")));
        Long secondTaskId = inTenant(() -> agentService.submitCandidateSummary(userId,
                buildReq(applicationId, "same-key")));

        assertEquals(firstTaskId, secondTaskId);
        // 同一幂等键复用任务，不重复调用模型
        verify(modelClient, org.mockito.Mockito.times(1)).chat(any());
    }

    @Test
    public void testSubmitCandidateSummary_newKeyTriggersReanalysis() {
        Long userId = randomLongId();
        Long applicationId = prepare(userId, SUMMARY_OUTPUT);

        Long firstTaskId = inTenant(() -> agentService.submitCandidateSummary(userId,
                buildReq(applicationId, "reanalyze-key-1")));
        Long secondTaskId = inTenant(() -> agentService.submitCandidateSummary(userId,
                buildReq(applicationId, "reanalyze-key-2")));

        // 换幂等键即人工重新分析，历史任务保留不变
        org.junit.jupiter.api.Assertions.assertNotEquals(firstTaskId, secondTaskId);
        assertNotNull(TenantUtils.executeIgnore(() -> taskMapper.selectById(firstTaskId)));
        verify(modelClient, org.mockito.Mockito.times(2)).chat(any());
    }
}
