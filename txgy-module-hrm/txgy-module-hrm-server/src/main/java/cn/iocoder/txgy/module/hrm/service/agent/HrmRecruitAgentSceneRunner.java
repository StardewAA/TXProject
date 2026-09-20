package cn.iocoder.txgy.module.hrm.service.agent;

import cn.hutool.json.JSONObject;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.hrm.dal.dataobject.agent.HrmRecruitAgentAnalysisDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.agent.HrmRecruitAgentTaskDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.agent.HrmRecruitAgentAnalysisMapper;
import cn.iocoder.txgy.module.hrm.enums.agent.HrmRecruitAgentTaskStatusEnum;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmRecruitAgentModelClient;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmRecruitAgentModelRequest;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmRecruitAgentModelResponse;
import cn.iocoder.txgy.module.hrm.util.HrmResumeAgentPayloadUtils;
import cn.iocoder.txgy.module.hrm.util.HrmResumeFingerprintUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_MODEL_OUTPUT_INVALID;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_RESUME_CONTENT_EMPTY;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_SENSITIVE_MASK_FAILED;

/**
 * Agent 场景执行器：所有场景共用的那一段链路。
 *
 * <p>顺序固定为「幂等建任务 → 脱敏白名单负载 → 模型调用 → 结构化校验 → 落库」。
 * 个人端与管理端只在「归属与数据范围校验」上不同，那一步留在各自的服务里，
 * 其余环节共用本执行器，避免安全校验出现两份实现。</p>
 *
 * <p>注意事务边界：模型调用耗时不可控，因此整个流程**不包在一个事务里**。
 * 否则一旦后续失败，之前写入的任务失败状态会被一起回滚，页面就永远看到任务停在执行中。
 * 任务状态流转各自独立提交，只有「保存结果 + 标记成功」这一步用局部事务保证原子性。</p>
 */
@Service
@Validated
public class HrmRecruitAgentSceneRunner {

    @Resource
    private HrmRecruitAgentTaskService taskService;
    @Resource
    private HrmRecruitAgentAnalysisMapper analysisMapper;
    @Resource
    private HrmRecruitAgentModelClient modelClient;
    @Resource
    private PlatformTransactionManager transactionManager;

    /**
     * 执行一次场景，返回任务编号。
     *
     * @param bo 已完成归属校验的输入
     * @return 任务编号
     */
    public Long run(HrmRecruitAgentRunBO bo) {
        String postFingerprint = HrmResumeFingerprintUtils.fingerprintPost(bo.getPost());

        // 1. 幂等：同一幂等键复用任务；已成功的任务不再重复调用模型
        HrmRecruitAgentTaskDO task = taskService.createTask(bo.getUserId(), new HrmRecruitAgentTaskCreateBO()
                .setScene(bo.getScene().getScene())
                .setBizType(bo.getBizType())
                .setBizId(bo.getBizId())
                .setResumeVersionId(bo.getResumeVersionId())
                .setPostId(bo.getPost().getId())
                .setPostFingerprint(postFingerprint)
                .setIdempotencyKey(bo.getIdempotencyKey()));
        if (HrmRecruitAgentTaskStatusEnum.SUCCEEDED.getStatus().equals(task.getStatus())) {
            return task.getId();
        }

        // 2. 构建脱敏白名单负载；脱敏失败或内容不足直接拒绝，不调用模型
        JSONObject payload;
        try {
            JSONObject resumePayload = HrmResumeAgentPayloadUtils
                    .buildAgentSafePayload(bo.getResumeContent());
            if (resumePayload.isEmpty()) {
                throw exception(AGENT_RESUME_CONTENT_EMPTY);
            }
            payload = new JSONObject()
                    .set("resume", resumePayload)
                    .set("post", HrmResumeAgentPayloadUtils.buildAgentSafePostPayload(bo.getPost()));
        } catch (HrmResumeAgentPayloadUtils.SensitiveMaskFailedException ex) {
            failTask(task, AGENT_SENSITIVE_MASK_FAILED.getCode(), "敏感信息脱敏失败，已阻止模型调用");
            throw exception(AGENT_SENSITIVE_MASK_FAILED);
        }

        // 3. 调用模型；未配置或额度不足时明确失败，不返回任何编造内容
        taskService.markRunning(task.getId());
        HrmRecruitAgentModelResponse response;
        try {
            response = modelClient.chat(new HrmRecruitAgentModelRequest()
                    .setScene(bo.getScene().getScene())
                    .setPromptVersion(bo.getPromptVersion())
                    .setSystemPrompt(bo.getSystemPrompt())
                    .setUserPayload(payload.toString()));
        } catch (ServiceException ex) {
            failTask(task, ex.getCode(), ex.getMessage());
            throw ex;
        }

        // 4. 结构化校验；不合格整任务失败，不落半成品
        JSONObject result;
        try {
            result = bo.getResultValidator().apply(response.getContent(), bo.getResumeContent());
        } catch (ServiceException ex) {
            failTask(task, AGENT_MODEL_OUTPUT_INVALID.getCode(), "模型输出结构不符合要求");
            throw exception(AGENT_MODEL_OUTPUT_INVALID);
        }

        // 5. 保存结果与标记成功在同一局部事务内，避免出现「有结果但任务仍是执行中」
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            saveAnalysis(task, bo, postFingerprint, result, response);
            taskService.markSucceeded(task.getId(), response.getModel(), bo.getPromptVersion(),
                    response.getPromptTokens(), response.getCompletionTokens(), response.getTotalTokens());
        });
        return task.getId();
    }

    /** 失败原因只写入可对外展示的简短说明，不含简历原文 */
    private void failTask(HrmRecruitAgentTaskDO task, Integer errorCode, String errorMessage) {
        taskService.markFailed(task.getId(), String.valueOf(errorCode), errorMessage);
    }

    private void saveAnalysis(HrmRecruitAgentTaskDO task, HrmRecruitAgentRunBO bo, String postFingerprint,
                              JSONObject result, HrmRecruitAgentModelResponse response) {
        Object scoreValue = result.get("overallScore");
        HrmRecruitAgentAnalysisDO analysis = new HrmRecruitAgentAnalysisDO()
                .setTaskId(task.getId())
                .setScene(task.getScene())
                .setResumeVersionId(bo.getResumeVersionId())
                .setPostId(bo.getPost().getId())
                .setPostFingerprint(postFingerprint)
                .setOverallScore(scoreValue instanceof Number number ? number.intValue() : null)
                .setResultJson(result.toString())
                .setSummary(buildSummary(result))
                .setPromptVersion(bo.getPromptVersion())
                .setModel(response.getModel());
        analysis.setTenantId(task.getTenantId());
        analysisMapper.insert(analysis);
    }

    /** 摘要只取优势首条；没有优势字段的场景（如面试问题）退回综合得分 */
    private String buildSummary(JSONObject result) {
        var strengths = result.getJSONArray("strengths");
        if (strengths != null && !strengths.isEmpty()) {
            return cn.hutool.core.util.StrUtil.maxLength(String.valueOf(strengths.get(0)), 1000);
        }
        return cn.hutool.core.util.StrUtil.maxLength(String.valueOf(result.get("overallScore")), 1000);
    }

    /**
     * 按任务编号读取分析结果。
     *
     * <p>个人端任务与结果不在企业租户维度上，因此个人端调用时需要忽略租户；
     * 管理端直接在当前租户内查询即可。</p>
     */
    public HrmRecruitAgentAnalysisDO getAnalysis(Long taskId, boolean ignoreTenant) {
        return ignoreTenant
                ? TenantUtils.executeIgnore(() -> analysisMapper.selectByTaskId(taskId))
                : analysisMapper.selectByTaskId(taskId);
    }
}
