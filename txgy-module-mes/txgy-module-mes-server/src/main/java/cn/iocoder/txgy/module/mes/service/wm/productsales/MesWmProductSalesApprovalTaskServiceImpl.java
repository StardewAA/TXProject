package cn.iocoder.txgy.module.mes.service.wm.productsales;

import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.mes.dal.dataobject.wm.productsales.MesWmProductSalesDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.wm.productsales.MesWmProductSalesApprovalTaskDO;
import cn.iocoder.txgy.module.mes.dal.mysql.wm.productsales.MesWmProductSalesApprovalTaskMapper;
import cn.iocoder.txgy.module.mes.enums.wm.MesWmProductSalesStatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * MES 销售出库审批结果任务 Service 实现类
 *
 * 流程：BPM 审批事件转发 → 结果任务落库 → 定时任务消费 → 回写出库单状态
 * （通过 → approveProductSales 按 OQC 标志分流；驳回 → rejectProductSales 退回草稿）
 *
 * @author OPENLAB BS
 */
@Slf4j
@Service
@Validated
public class MesWmProductSalesApprovalTaskServiceImpl implements MesWmProductSalesApprovalTaskService {

    /** BPM 流程实例状态：审批通过 */
    private static final Integer BPM_STATUS_APPROVE = 2;
    /** BPM 流程实例状态：驳回 */
    private static final Integer BPM_STATUS_REJECT = 3;
    /** 单次重试间隔基数（分钟），随重试次数递增 */
    private static final int RETRY_INTERVAL_MINUTES = 2;

    @Resource
    private MesWmProductSalesApprovalTaskMapper approvalTaskMapper;
    @Resource
    private MesWmProductSalesService productSalesService;

    @Override
    public Long createTask(Long salesId, String processInstanceId, Integer bpmStatus, String reason) {
        // 幂等：同一流程实例只创建一次（驳回后重新提交会发起新流程实例，产生新任务）
        MesWmProductSalesApprovalTaskDO existTask = approvalTaskMapper.selectByProcessInstanceId(processInstanceId);
        if (existTask != null) {
            return existTask.getId();
        }
        MesWmProductSalesApprovalTaskDO task = MesWmProductSalesApprovalTaskDO.builder()
                .salesId(salesId)
                .processInstanceId(processInstanceId)
                .bpmStatus(bpmStatus)
                .reason(reason)
                .status(MesWmProductSalesApprovalTaskDO.STATUS_PENDING)
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
        List<MesWmProductSalesApprovalTaskDO> tasks = TenantUtils.executeIgnore(() ->
                approvalTaskMapper.selectPendingList(LocalDateTime.now()));
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        for (MesWmProductSalesApprovalTaskDO task : tasks) {
            try {
                // 切换到任务所属租户上下文，保证回写出库单的租户隔离正确
                TenantUtils.execute(task.getTenantId(), () -> processTask(task));
            } catch (Exception e) {
                log.error("[processPendingTasks][任务({}) 处理异常]", task.getId(), e);
            }
        }
    }

    @Override
    public void processTask(MesWmProductSalesApprovalTaskDO task) {
        // 1. 幂等：出库单已不在 BPM 审批中（可能已被人工处理），标记跳过，避免无意义重试
        MesWmProductSalesDO sales = productSalesService.getProductSales(task.getSalesId());
        if (sales == null) {
            markSkipped(task, "销售出库单不存在");
            return;
        }
        if (!Objects.equals(sales.getStatus(), MesWmProductSalesStatusEnum.BPM_APPROVING.getStatus())) {
            markSkipped(task, "销售出库单状态为 " + sales.getStatus() + "，已不在 BPM 审批中");
            return;
        }
        // 2. 根据 BPM 审批结果回写
        try {
            if (Objects.equals(task.getBpmStatus(), BPM_STATUS_APPROVE)) {
                // 审批通过：按 OQC 标志分流（待检测/待拣货）
                productSalesService.approveProductSales(task.getSalesId());
            } else if (Objects.equals(task.getBpmStatus(), BPM_STATUS_REJECT)) {
                // 驳回：退回草稿，可修改后重新提交（重新发起新流程实例）
                productSalesService.rejectProductSales(task.getSalesId());
            } else {
                markSkipped(task, "BPM 状态(" + task.getBpmStatus() + ") 无需处理");
                return;
            }
            markSuccess(task);
        } catch (Exception e) {
            markRetry(task, e.getMessage());
        }
    }

    private void markSuccess(MesWmProductSalesApprovalTaskDO task) {
        approvalTaskMapper.updateById(new MesWmProductSalesApprovalTaskDO().setId(task.getId())
                .setStatus(MesWmProductSalesApprovalTaskDO.STATUS_SUCCESS));
    }

    private void markSkipped(MesWmProductSalesApprovalTaskDO task, String reason) {
        approvalTaskMapper.updateById(new MesWmProductSalesApprovalTaskDO().setId(task.getId())
                .setStatus(MesWmProductSalesApprovalTaskDO.STATUS_SKIPPED)
                .setErrorMessage(reason));
    }

    private void markRetry(MesWmProductSalesApprovalTaskDO task, String errorMessage) {
        String message = errorMessage != null && errorMessage.length() > 450
                ? errorMessage.substring(0, 450) : errorMessage;
        if (task.getRetryCount() >= MesWmProductSalesApprovalTaskDO.MAX_RETRY_COUNT) {
            log.error("[markRetry][任务({}) 超过最大重试次数，标记失败：{}]", task.getId(), message);
            approvalTaskMapper.updateById(new MesWmProductSalesApprovalTaskDO().setId(task.getId())
                    .setStatus(MesWmProductSalesApprovalTaskDO.STATUS_FAILED)
                    .setErrorMessage(message));
            return;
        }
        int nextRetryCount = task.getRetryCount() + 1;
        approvalTaskMapper.updateById(new MesWmProductSalesApprovalTaskDO().setId(task.getId())
                .setStatus(MesWmProductSalesApprovalTaskDO.STATUS_PENDING)
                .setRetryCount(nextRetryCount)
                .setNextRetryTime(LocalDateTime.now().plusMinutes((long) RETRY_INTERVAL_MINUTES * nextRetryCount))
                .setErrorMessage(message));
    }

}
