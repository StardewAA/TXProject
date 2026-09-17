package cn.iocoder.txgy.module.mes.service.wm.itemreceipt;

import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.mes.dal.dataobject.wm.itemreceipt.MesWmItemReceiptDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.wm.itemreceipt.MesWmReceiptApprovalTaskDO;
import cn.iocoder.txgy.module.mes.dal.mysql.wm.itemreceipt.MesWmReceiptApprovalTaskMapper;
import cn.iocoder.txgy.module.mes.enums.wm.MesWmItemReceiptStatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * MES 入库单审批结果任务 Service 实现类
 *
 * 流程：BPM 审批事件转发 → 结果任务落库 → 定时任务消费 → 回写入库单状态
 * （通过 → approveItemReceipt 进入"待上架"；驳回 → rejectItemReceipt 退回草稿）
 *
 * 与报工审批的 MesFeedbackApprovalTaskServiceImpl 同构。
 *
 * @author OPENLAB BS
 */
@Slf4j
@Service
@Validated
public class MesWmReceiptApprovalTaskServiceImpl implements MesWmReceiptApprovalTaskService {

    /** BPM 流程实例状态：审批通过 */
    private static final Integer BPM_STATUS_APPROVE = 2;
    /** BPM 流程实例状态：驳回 */
    private static final Integer BPM_STATUS_REJECT = 3;
    /** 单次重试间隔基数（分钟），随重试次数递增 */
    private static final int RETRY_INTERVAL_MINUTES = 2;

    @Resource
    private MesWmReceiptApprovalTaskMapper approvalTaskMapper;
    @Resource
    private MesWmItemReceiptService receiptService;

    @Override
    public Long createTask(Long receiptId, String processInstanceId, Integer bpmStatus, String reason) {
        // 幂等：同一流程实例只创建一次（驳回后重新提交会发起新流程实例，产生新任务）
        MesWmReceiptApprovalTaskDO existTask = approvalTaskMapper.selectByProcessInstanceId(processInstanceId);
        if (existTask != null) {
            return existTask.getId();
        }
        MesWmReceiptApprovalTaskDO task = MesWmReceiptApprovalTaskDO.builder()
                .receiptId(receiptId)
                .processInstanceId(processInstanceId)
                .bpmStatus(bpmStatus)
                .reason(reason)
                .status(MesWmReceiptApprovalTaskDO.STATUS_PENDING)
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
        List<MesWmReceiptApprovalTaskDO> tasks = TenantUtils.executeIgnore(() ->
                approvalTaskMapper.selectPendingList(LocalDateTime.now()));
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        for (MesWmReceiptApprovalTaskDO task : tasks) {
            try {
                // 切换到任务所属租户上下文，保证回写入库单的租户隔离正确
                TenantUtils.execute(task.getTenantId(), () -> processTask(task));
            } catch (Exception e) {
                log.error("[processPendingTasks][任务({}) 处理异常]", task.getId(), e);
            }
        }
    }

    @Override
    public void processTask(MesWmReceiptApprovalTaskDO task) {
        // 1. 幂等：入库单已不在 BPM 审批中（可能已被人工处理），标记跳过，避免无意义重试
        MesWmItemReceiptDO receipt = receiptService.getItemReceipt(task.getReceiptId());
        if (receipt == null) {
            markSkipped(task, "入库单不存在");
            return;
        }
        if (!Objects.equals(receipt.getStatus(), MesWmItemReceiptStatusEnum.BPM_APPROVING.getStatus())) {
            markSkipped(task, "入库单状态为 " + receipt.getStatus() + "，已不在 BPM 审批中");
            return;
        }
        // 2. 根据 BPM 审批结果回写
        try {
            if (Objects.equals(task.getBpmStatus(), BPM_STATUS_APPROVE)) {
                // 审批通过：进入"待上架"，后续上架/入库流程不变
                receiptService.approveItemReceipt(task.getReceiptId());
            } else if (Objects.equals(task.getBpmStatus(), BPM_STATUS_REJECT)) {
                // 驳回：退回草稿，可修改后重新提交（重新发起新流程实例）
                receiptService.rejectItemReceipt(task.getReceiptId());
            } else {
                markSkipped(task, "BPM 状态(" + task.getBpmStatus() + ") 无需处理");
                return;
            }
            markSuccess(task);
        } catch (Exception e) {
            markRetry(task, e.getMessage());
        }
    }

    private void markSuccess(MesWmReceiptApprovalTaskDO task) {
        approvalTaskMapper.updateById(new MesWmReceiptApprovalTaskDO().setId(task.getId())
                .setStatus(MesWmReceiptApprovalTaskDO.STATUS_SUCCESS));
    }

    private void markSkipped(MesWmReceiptApprovalTaskDO task, String reason) {
        approvalTaskMapper.updateById(new MesWmReceiptApprovalTaskDO().setId(task.getId())
                .setStatus(MesWmReceiptApprovalTaskDO.STATUS_SKIPPED)
                .setErrorMessage(reason));
    }

    private void markRetry(MesWmReceiptApprovalTaskDO task, String errorMessage) {
        String message = errorMessage != null && errorMessage.length() > 450
                ? errorMessage.substring(0, 450) : errorMessage;
        if (task.getRetryCount() >= MesWmReceiptApprovalTaskDO.MAX_RETRY_COUNT) {
            log.error("[markRetry][任务({}) 超过最大重试次数，标记失败：{}]", task.getId(), message);
            approvalTaskMapper.updateById(new MesWmReceiptApprovalTaskDO().setId(task.getId())
                    .setStatus(MesWmReceiptApprovalTaskDO.STATUS_FAILED)
                    .setErrorMessage(message));
            return;
        }
        int nextRetryCount = task.getRetryCount() + 1;
        approvalTaskMapper.updateById(new MesWmReceiptApprovalTaskDO().setId(task.getId())
                .setStatus(MesWmReceiptApprovalTaskDO.STATUS_PENDING)
                .setRetryCount(nextRetryCount)
                .setNextRetryTime(LocalDateTime.now().plusMinutes((long) RETRY_INTERVAL_MINUTES * nextRetryCount))
                .setErrorMessage(message));
    }

}
