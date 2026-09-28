package cn.iocoder.txgy.module.mes.service.pro.workorder;

import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.workorder.MesWorkOrderApprovalTaskDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.workorder.MesProWorkOrderDO;
import cn.iocoder.txgy.module.mes.dal.mysql.pro.workorder.MesWorkOrderApprovalTaskMapper;
import cn.iocoder.txgy.module.mes.enums.pro.MesProWorkOrderStatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * MES 生产工单审批结果任务 Service 实现类
 *
 * 流程：BPM 审批事件转发 → 结果任务落库 → 定时任务消费 → 回写工单状态
 * （通过 → approveWorkOrder 置为"已批准"；驳回 → rejectWorkOrder 退回"已确认"）
 *
 * @author OPENLAB BS
 */
@Slf4j
@Service
@Validated
public class MesWorkOrderApprovalTaskServiceImpl implements MesWorkOrderApprovalTaskService {

    /** BPM 流程实例状态：审批通过 */
    private static final Integer BPM_STATUS_APPROVE = 2;
    /** BPM 流程实例状态：驳回 */
    private static final Integer BPM_STATUS_REJECT = 3;
    /** 单次重试间隔基数（分钟），随重试次数递增 */
    private static final int RETRY_INTERVAL_MINUTES = 2;

    @Resource
    private MesWorkOrderApprovalTaskMapper approvalTaskMapper;
    @Resource
    private MesProWorkOrderService workOrderService;

    @Override
    public Long createTask(Long workOrderId, String processInstanceId, Integer bpmStatus, String reason) {
        // 幂等：同一流程实例只创建一次（驳回后重新提交会发起新流程实例，产生新任务）
        MesWorkOrderApprovalTaskDO existTask = approvalTaskMapper.selectByProcessInstanceId(processInstanceId);
        if (existTask != null) {
            return existTask.getId();
        }
        MesWorkOrderApprovalTaskDO task = MesWorkOrderApprovalTaskDO.builder()
                .workOrderId(workOrderId)
                .processInstanceId(processInstanceId)
                .bpmStatus(bpmStatus)
                .reason(reason)
                .status(MesWorkOrderApprovalTaskDO.STATUS_PENDING)
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
        List<MesWorkOrderApprovalTaskDO> tasks = TenantUtils.executeIgnore(() ->
                approvalTaskMapper.selectPendingList(LocalDateTime.now()));
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        for (MesWorkOrderApprovalTaskDO task : tasks) {
            try {
                // 切换到任务所属租户上下文，保证回写工单的租户隔离正确
                TenantUtils.execute(task.getTenantId(), () -> processTask(task));
            } catch (Exception e) {
                log.error("[processPendingTasks][任务({}) 处理异常]", task.getId(), e);
            }
        }
    }

    @Override
    public void processTask(MesWorkOrderApprovalTaskDO task) {
        // 1. 幂等：工单已不在审批中（可能已被人工处理），标记跳过，避免无意义重试
        MesProWorkOrderDO workOrder = workOrderService.getWorkOrder(task.getWorkOrderId());
        if (workOrder == null) {
            markSkipped(task, "生产工单不存在");
            return;
        }
        if (!Objects.equals(workOrder.getStatus(), MesProWorkOrderStatusEnum.APPROVING.getStatus())) {
            markSkipped(task, "工单状态为 " + workOrder.getStatus() + "，已不在审批中");
            return;
        }
        // 2. 根据 BPM 审批结果回写
        try {
            if (Objects.equals(task.getBpmStatus(), BPM_STATUS_APPROVE)) {
                workOrderService.approveWorkOrder(task.getWorkOrderId());
            } else if (Objects.equals(task.getBpmStatus(), BPM_STATUS_REJECT)) {
                workOrderService.rejectWorkOrder(task.getWorkOrderId());
            } else {
                markSkipped(task, "BPM 状态(" + task.getBpmStatus() + ") 无需处理");
                return;
            }
            markSuccess(task);
        } catch (Exception e) {
            markRetry(task, e.getMessage());
        }
    }

    private void markSuccess(MesWorkOrderApprovalTaskDO task) {
        approvalTaskMapper.updateById(new MesWorkOrderApprovalTaskDO().setId(task.getId())
                .setStatus(MesWorkOrderApprovalTaskDO.STATUS_SUCCESS));
    }

    private void markSkipped(MesWorkOrderApprovalTaskDO task, String reason) {
        approvalTaskMapper.updateById(new MesWorkOrderApprovalTaskDO().setId(task.getId())
                .setStatus(MesWorkOrderApprovalTaskDO.STATUS_SKIPPED)
                .setErrorMessage(reason));
    }

    private void markRetry(MesWorkOrderApprovalTaskDO task, String errorMessage) {
        String message = errorMessage != null && errorMessage.length() > 450
                ? errorMessage.substring(0, 450) : errorMessage;
        if (task.getRetryCount() >= MesWorkOrderApprovalTaskDO.MAX_RETRY_COUNT) {
            log.error("[markRetry][任务({}) 超过最大重试次数，标记失败：{}]", task.getId(), message);
            approvalTaskMapper.updateById(new MesWorkOrderApprovalTaskDO().setId(task.getId())
                    .setStatus(MesWorkOrderApprovalTaskDO.STATUS_FAILED)
                    .setErrorMessage(message));
            return;
        }
        int nextRetryCount = task.getRetryCount() + 1;
        approvalTaskMapper.updateById(new MesWorkOrderApprovalTaskDO().setId(task.getId())
                .setStatus(MesWorkOrderApprovalTaskDO.STATUS_PENDING)
                .setRetryCount(nextRetryCount)
                .setNextRetryTime(LocalDateTime.now().plusMinutes((long) RETRY_INTERVAL_MINUTES * nextRetryCount))
                .setErrorMessage(message));
    }

}
