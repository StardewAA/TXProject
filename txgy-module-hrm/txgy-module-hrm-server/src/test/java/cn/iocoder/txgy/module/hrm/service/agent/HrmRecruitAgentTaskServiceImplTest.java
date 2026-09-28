package cn.iocoder.txgy.module.hrm.service.agent;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.hrm.dal.dataobject.agent.HrmRecruitAgentTaskDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.agent.HrmRecruitAgentTaskMapper;
import cn.iocoder.txgy.module.hrm.enums.agent.HrmRecruitAgentSceneEnum;
import cn.iocoder.txgy.module.hrm.enums.agent.HrmRecruitAgentTaskStatusEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static cn.iocoder.txgy.framework.test.core.util.RandomUtils.randomLongId;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_TASK_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agent 任务服务测试。
 *
 * <p>重点验证：幂等不重复建任务、状态机不可回退、归属校验。</p>
 */
@Import(HrmRecruitAgentTaskServiceImpl.class)
public class HrmRecruitAgentTaskServiceImplTest extends BaseDbUnitTest {

    @Resource
    private HrmRecruitAgentTaskServiceImpl taskService;
    @Resource
    private HrmRecruitAgentTaskMapper taskMapper;

    private HrmRecruitAgentTaskCreateBO buildBo(String idempotencyKey) {
        return new HrmRecruitAgentTaskCreateBO()
                .setScene(HrmRecruitAgentSceneEnum.RESUME_ANALYZE.getScene())
                .setBizType("PERSONAL_RESUME_VERSION")
                .setBizId(randomLongId())
                .setResumeVersionId(randomLongId())
                .setPostId(randomLongId())
                .setPostFingerprint("fingerprint-1")
                .setIdempotencyKey(idempotencyKey);
    }

    @Test
    public void testCreateTask_persistsPendingTask() {
        Long userId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        HrmRecruitAgentTaskDO task;
        try {
            task = taskService.createTask(userId, buildBo("key-1"));
        } finally {
            TenantContextHolder.clear();
        }

        assertNotNull(task.getId());
        assertEquals(HrmRecruitAgentTaskStatusEnum.PENDING.getStatus(), task.getStatus());
        assertEquals(userId, task.getUserId());
        assertEquals(101L, task.getTenantId());
        // 任务表不保存简历原文，只保存指纹与业务编号
        assertEquals("fingerprint-1", task.getPostFingerprint());
    }

    @Test
    public void testCreateTask_isIdempotentForSameKey() {
        Long userId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        Long firstId;
        Long secondId;
        try {
            firstId = taskService.createTask(userId, buildBo("same-key")).getId();
            secondId = taskService.createTask(userId, buildBo("same-key")).getId();
        } finally {
            TenantContextHolder.clear();
        }

        assertEquals(firstId, secondId);
        assertEquals(1L, TenantUtils.executeIgnore(() -> taskMapper.selectCount(null)));
    }

    @Test
    public void testCreateTask_sameKeyDifferentUserCreatesSeparateTasks() {
        Long firstUser = randomLongId();
        Long secondUser = randomLongId();
        TenantContextHolder.setTenantId(101L);
        Long firstId;
        Long secondId;
        try {
            firstId = taskService.createTask(firstUser, buildBo("shared-key")).getId();
            secondId = taskService.createTask(secondUser, buildBo("shared-key")).getId();
        } finally {
            TenantContextHolder.clear();
        }

        assertTrue(!firstId.equals(secondId));
    }

    @Test
    public void testMarkSucceeded_recordsModelAndDuration() {
        Long userId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        Long taskId;
        try {
            taskId = taskService.createTask(userId, buildBo("success-key")).getId();
            taskService.markRunning(taskId);
            taskService.markSucceeded(taskId, "qwen-flash", "resume-analyze-v1");
        } finally {
            TenantContextHolder.clear();
        }

        HrmRecruitAgentTaskDO task = taskService.getTask(userId, taskId);
        assertEquals(HrmRecruitAgentTaskStatusEnum.SUCCEEDED.getStatus(), task.getStatus());
        assertEquals("qwen-flash", task.getModel());
        assertEquals("resume-analyze-v1", task.getPromptVersion());
        assertNotNull(task.getStartTime());
        assertNotNull(task.getFinishTime());
        assertNotNull(task.getDurationMs());
        assertNull(task.getErrorCode());
    }

    @Test
    public void testMarkFailed_recordsErrorWithoutResumeText() {
        Long userId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        Long taskId;
        try {
            taskId = taskService.createTask(userId, buildBo("fail-key")).getId();
            taskService.markRunning(taskId);
            taskService.markFailed(taskId, "AGENT_MODEL_CALL_FAILED", "模型调用超时");
        } finally {
            TenantContextHolder.clear();
        }

        HrmRecruitAgentTaskDO task = taskService.getTask(userId, taskId);
        assertEquals(HrmRecruitAgentTaskStatusEnum.FAILED.getStatus(), task.getStatus());
        assertEquals("AGENT_MODEL_CALL_FAILED", task.getErrorCode());
        assertEquals("模型调用超时", task.getErrorMessage());
    }

    @Test
    public void testFinishedTask_isNotMutatedByLaterCalls() {
        Long userId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        Long taskId;
        try {
            taskId = taskService.createTask(userId, buildBo("frozen-key")).getId();
            taskService.markRunning(taskId);
            taskService.markSucceeded(taskId, "qwen-flash", "v1");
            // 重复回调不得把已完成任务改坏
            taskService.markFailed(taskId, "AGENT_MODEL_CALL_FAILED", "迟到的失败回调");
            taskService.markRunning(taskId);
        } finally {
            TenantContextHolder.clear();
        }

        HrmRecruitAgentTaskDO task = taskService.getTask(userId, taskId);
        assertEquals(HrmRecruitAgentTaskStatusEnum.SUCCEEDED.getStatus(), task.getStatus());
        assertEquals("qwen-flash", task.getModel());
        assertNull(task.getErrorCode());
    }

    @Test
    public void testGetTask_rejectsOtherUser() {
        Long ownerId = randomLongId();
        Long otherUserId = randomLongId();
        TenantContextHolder.setTenantId(101L);
        Long taskId;
        try {
            taskId = taskService.createTask(ownerId, buildBo("private-key")).getId();
        } finally {
            TenantContextHolder.clear();
        }

        ServiceException exception = assertThrows(ServiceException.class,
                () -> taskService.getTask(otherUserId, taskId));
        assertEquals(AGENT_TASK_NOT_EXISTS.getCode(), exception.getCode());
    }
}
