package cn.iocoder.txgy.module.hrm.service.agent;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.hrm.dal.dataobject.agent.HrmRecruitAgentTaskDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.agent.HrmRecruitAgentTaskMapper;
import cn.iocoder.txgy.module.hrm.enums.agent.HrmRecruitAgentTaskStatusEnum;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.LocalDateTime;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_TASK_IDEMPOTENCY_CONFLICT;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_TASK_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_TASK_STATUS_INVALID;

@Service
@Validated
public class HrmRecruitAgentTaskServiceImpl implements HrmRecruitAgentTaskService {

    @Resource
    private HrmRecruitAgentTaskMapper taskMapper;
    @Resource
    private SecurityFrameworkService securityFrameworkService;

    /**
     * 管理端「审计查询」权限：持有该权限的 HR/审计可以在本租户内查看任意任务
     * （与 {@code HrmRecruitAgentController#getTask} 上的权限注解保持一致）
     */
    private static final String PERMISSION_AGENT_AUDIT_QUERY = "hrm:recruit:agent:audit-query";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrmRecruitAgentTaskDO createTask(Long userId, HrmRecruitAgentTaskCreateBO bo) {
        if (userId == null) {
            throw exception(AGENT_TASK_NOT_EXISTS);
        }
        // 幂等：同一用户同一场景同一幂等键直接复用已有任务
        HrmRecruitAgentTaskDO existing = TenantUtils.executeIgnore(() -> taskMapper
                .selectByUserSceneAndIdempotencyKey(userId, bo.getScene(), bo.getIdempotencyKey()));
        if (existing != null) {
            return existing;
        }
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            tenantId = 0L;
        }
        HrmRecruitAgentTaskDO task = new HrmRecruitAgentTaskDO()
                .setUserId(userId)
                .setScene(bo.getScene())
                .setBizType(bo.getBizType())
                .setBizId(bo.getBizId())
                .setResumeVersionId(bo.getResumeVersionId())
                .setPostId(bo.getPostId())
                .setPostFingerprint(bo.getPostFingerprint())
                .setIdempotencyKey(bo.getIdempotencyKey())
                .setStatus(HrmRecruitAgentTaskStatusEnum.PENDING.getStatus());
        task.setTenantId(tenantId);
        try {
            taskMapper.insert(task);
        } catch (DuplicateKeyException duplicate) {
            // 并发下两个请求同时通过幂等预检，唯一键兜底
            HrmRecruitAgentTaskDO concurrent = TenantUtils.executeIgnore(() -> taskMapper
                    .selectByUserSceneAndIdempotencyKey(userId, bo.getScene(), bo.getIdempotencyKey()));
            if (concurrent != null) {
                return concurrent;
            }
            throw exception(AGENT_TASK_IDEMPOTENCY_CONFLICT);
        }
        return task;
    }

    @Override
    public void markRunning(Long taskId) {
        HrmRecruitAgentTaskDO task = loadTask(taskId);
        if (isFinished(task)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        taskMapper.updateById(new HrmRecruitAgentTaskDO().setId(taskId)
                .setStatus(HrmRecruitAgentTaskStatusEnum.RUNNING.getStatus())
                .setStartTime(task.getStartTime() == null ? now : task.getStartTime()));
    }

    @Override
    public void markSucceeded(Long taskId, String model, String promptVersion) {
        markSucceeded(taskId, model, promptVersion, null, null, null);
    }

    @Override
    public void markSucceeded(Long taskId, String model, String promptVersion,
                              Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        HrmRecruitAgentTaskDO task = loadTask(taskId);
        // 已结束的任务不再改动，避免重复回调覆盖最终状态
        if (isFinished(task)) {
            return;
        }
        LocalDateTime finishTime = LocalDateTime.now();
        taskMapper.updateById(new HrmRecruitAgentTaskDO().setId(taskId)
                .setStatus(HrmRecruitAgentTaskStatusEnum.SUCCEEDED.getStatus())
                .setModel(model).setPromptVersion(promptVersion)
                .setFinishTime(finishTime)
                .setPromptTokens(promptTokens)
                .setCompletionTokens(completionTokens)
                .setTotalTokens(totalTokens)
                .setDurationMs(resolveDuration(task.getStartTime(), finishTime)));
    }

    @Override
    public void markFailed(Long taskId, String errorCode, String errorMessage) {
        HrmRecruitAgentTaskDO task = loadTask(taskId);
        if (isFinished(task)) {
            return;
        }
        LocalDateTime finishTime = LocalDateTime.now();
        taskMapper.updateById(new HrmRecruitAgentTaskDO().setId(taskId)
                .setStatus(HrmRecruitAgentTaskStatusEnum.FAILED.getStatus())
                .setFinishTime(finishTime)
                .setDurationMs(resolveDuration(task.getStartTime(), finishTime))
                .setErrorCode(errorCode)
                // 失败原因只保留有限长度，且调用方已保证不含简历原文
                .setErrorMessage(StrUtil.maxLength(errorMessage, 500)));
    }

    @Override
    public HrmRecruitAgentTaskDO getTask(Long userId, Long taskId) {
        if (userId == null || taskId == null) {
            throw exception(AGENT_TASK_NOT_EXISTS);
        }
        HrmRecruitAgentTaskDO task = TenantUtils.executeIgnore(
                () -> taskMapper.selectByIdAndUserId(taskId, userId));
        if (task == null) {
            throw exception(AGENT_TASK_NOT_EXISTS);
        }
        return task;
    }

    @Override
    public HrmRecruitAgentTaskDO getTaskInTenant(Long taskId) {
        if (taskId == null) {
            throw exception(AGENT_TASK_NOT_EXISTS);
        }
        // 第一道：查询本身带当前租户条件（MyBatis 租户插件），跨租户任务查不到
        HrmRecruitAgentTaskDO task = taskMapper.selectById(taskId);
        // 第二道：再显式比对一次租户归属，避免插件被关闭或绕过时读到别家企业的任务
        Long currentTenantId = TenantContextHolder.getTenantId();
        if (task == null || currentTenantId == null
                || !currentTenantId.equals(task.getTenantId())) {
            throw exception(AGENT_TASK_NOT_EXISTS);
        }
        // 第三道（Q-006 / I-003）：同租户内也不允许用任务 ID 直接读他人任务——
        // 只有「本人发起」或「持有审计查询权限」才放行；两者都不满足时按不存在处理（404 语义）。
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
        boolean owner = loginUserId != null && loginUserId.equals(task.getUserId());
        if (!owner && !securityFrameworkService.hasPermission(PERMISSION_AGENT_AUDIT_QUERY)) {
            throw exception(AGENT_TASK_NOT_EXISTS);
        }
        return task;
    }

    private HrmRecruitAgentTaskDO loadTask(Long taskId) {
        HrmRecruitAgentTaskDO task = taskId == null ? null
                : TenantUtils.executeIgnore(() -> taskMapper.selectById(taskId));
        if (task == null) {
            throw exception(AGENT_TASK_STATUS_INVALID);
        }
        return task;
    }

    private boolean isFinished(HrmRecruitAgentTaskDO task) {
        HrmRecruitAgentTaskStatusEnum status =
                HrmRecruitAgentTaskStatusEnum.valueOfStatus(task.getStatus());
        return status != null && status.isFinished();
    }

    private Long resolveDuration(LocalDateTime startTime, LocalDateTime finishTime) {
        if (startTime == null || finishTime == null) {
            return null;
        }
        return Duration.between(startTime, finishTime).toMillis();
    }
}
