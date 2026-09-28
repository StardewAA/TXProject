package cn.iocoder.txgy.module.mes.service.wm.outsourceissue;

import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.mes.dal.dataobject.wm.outsourceissue.MesWmOutsourceIssueDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.wm.outsourceissue.MesWmOutsourceIssueApprovalTaskDO;
import cn.iocoder.txgy.module.mes.dal.mysql.wm.outsourceissue.MesWmOutsourceIssueApprovalTaskMapper;
import cn.iocoder.txgy.module.mes.enums.wm.MesWmOutsourceIssueStatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * MES 委外发料审批结果任务 Service 实现类
 *
 * 流程：BPM 审批事件转发 → 结果任务落库 → 定时任务消费 → 回写发料单状态
 * （通过 → approveOutsourceIssue 进入"待拣货"；驳回 → rejectOutsourceIssue 退回草稿）
 *
 * @author OPENLAB BS
 */
@Slf4j
@Service
@Validated
public class MesWmOutsourceIssueApprovalTaskServiceImpl implements MesWmOutsourceIssueApprovalTaskService {

    /** BPM 流程实例状态：审批通过 */
    private static final Integer BPM_STATUS_APPROVE = 2;
    /** BPM 流程实例状态：驳回 */
    private static final Integer BPM_STATUS_REJECT = 3;
    /** 单次重试间隔基数（分钟），随重试次数递增 */
    private static final int RETRY_INTERVAL_MINUTES = 2;

    @Resource
    private MesWmOutsourceIssueApprovalTaskMapper approvalTaskMapper;
    @Resource
    private MesWmOutsourceIssueService outsourceIssueService;

    @Override
    public Long createTask(Long issueId, String processInstanceId, Integer bpmStatus, String reason) {
        // 幂等：同一流程实例只创建一次（驳回后重新提交会发起新流程实例，产生新任务）
        MesWmOutsourceIssueApprovalTaskDO existTask = approvalTaskMapper.selectByProcessInstanceId(processInstanceId);
        if (existTask != null) {
            return existTask.getId();
        }
        MesWmOutsourceIssueApprovalTaskDO task = MesWmOutsourceIssueApprovalTaskDO.builder()
                .issueId(issueId)
                .processInstanceId(processInstanceId)
                .bpmStatus(bpmStatus)
                .reason(reason)
                .status(MesWmOutsourceIssueApprovalTaskDO.STATUS_PENDING)
                .retryCount(0)
                .nextRetryTime(LocalDateTime.now())
                .build();
        approvalTaskMapper.insert(task);
        return task.getId();
    }

    /**
     * 定时扫描待处理任务。
     *
     * 初次延迟 30 秒、之后每 30 秒扫描一次：给 BPM 事务提交与事件转发留出时间窗口。
     * 任务表为跨租户扫描（TenantBaseDO 自动带租户隔离），需 ignore 后统一查出，再逐条切租户处理。
     */
    @Override
    @Scheduled(initialDelay = 30_000, fixedDelay = 30_000)
    public void processPendingTasks() {
        List<MesWmOutsourceIssueApprovalTaskDO> tasks = TenantUtils.executeIgnore(() ->
                approvalTaskMapper.selectPendingList(LocalDateTime.now()));
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        for (MesWmOutsourceIssueApprovalTaskDO task : tasks) {
            try {
                // 切换到任务所属租户上下文，保证回写发料单的租户隔离正确
                TenantUtils.execute(task.getTenantId(), () -> processTask(task));
            } catch (Exception e) {
                log.error("[processPendingTasks][任务({}) 处理异常]", task.getId(), e);
            }
        }
    }

    @Override
    public void processTask(MesWmOutsourceIssueApprovalTaskDO task) {
        // 1. 幂等：发料单已不在 BPM 审批中（可能已被人工处理），标记跳过，避免无意义重试
        MesWmOutsourceIssueDO issue = outsourceIssueService.getOutsourceIssue(task.getIssueId());
        if (issue == null) {
            markSkipped(task, "委外发料单不存在");
            return;
        }
        if (!Objects.equals(issue.getStatus(), MesWmOutsourceIssueStatusEnum.BPM_APPROVING.getStatus())) {
            markSkipped(task, "委外发料单状态为 " + issue.getStatus() + "，已不在 BPM 审批中");
            return;
        }
        // 2. 根据 BPM 审批结果回写
        try {
            if (Objects.equals(task.getBpmStatus(), BPM_STATUS_APPROVE)) {
                // 审批通过：进入"待拣货"，后续拣货/出库流程不变
                outsourceIssueService.approveOutsourceIssue(task.getIssueId());
            } else if (Objects.equals(task.getBpmStatus(), BPM_STATUS_REJECT)) {
                // 驳回：退回草稿，可修改后重新提交（重新发起新流程实例）
                outsourceIssueService.rejectOutsourceIssue(task.getIssueId());
            } else {
                markSkipped(task, "BPM 状态(" + task.getBpmStatus() + ") 无需处理");
                return;
            }
            markSuccess(task);
        } catch (Exception e) {
            markRetry(task, e.getMessage());
        }
    }

    private void markSuccess(MesWmOutsourceIssueApprovalTaskDO task) {
        approvalTaskMapper.updateById(new MesWmOutsourceIssueApprovalTaskDO().setId(task.getId())
                .setStatus(MesWmOutsourceIssueApprovalTaskDO.STATUS_SUCCESS));
    }

    private void markSkipped(MesWmOutsourceIssueApprovalTaskDO task, String reason) {
        approvalTaskMapper.updateById(new MesWmOutsourceIssueApprovalTaskDO().setId(task.getId())
                .setStatus(MesWmOutsourceIssueApprovalTaskDO.STATUS_SKIPPED)
                .setErrorMessage(reason));
    }

    private void markRetry(MesWmOutsourceIssueApprovalTaskDO task, String errorMessage) {
        String message = errorMessage != null && errorMessage.length() > 450
                ? errorMessage.substring(0, 450) : errorMessage;
        if (task.getRetryCount() >= MesWmOutsourceIssueApprovalTaskDO.MAX_RETRY_COUNT) {
            log.error("[markRetry][任务({}) 超过最大重试次数，标记失败：{}]", task.getId(), message);
            approvalTaskMapper.updateById(new MesWmOutsourceIssueApprovalTaskDO().setId(task.getId())
                    .setStatus(MesWmOutsourceIssueApprovalTaskDO.STATUS_FAILED)
                    .setErrorMessage(message));
            return;
        }
        int nextRetryCount = task.getRetryCount() + 1;
        approvalTaskMapper.updateById(new MesWmOutsourceIssueApprovalTaskDO().setId(task.getId())
                .setStatus(MesWmOutsourceIssueApprovalTaskDO.STATUS_PENDING)
                .setRetryCount(nextRetryCount)
                .setNextRetryTime(LocalDateTime.now().plusMinutes((long) RETRY_INTERVAL_MINUTES * nextRetryCount))
                .setErrorMessage(message));
    }

}
