package cn.iocoder.txgy.module.erp.util;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.module.bpm.api.task.BpmProcessInstanceApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * ERP 单据与 BPM 流程实例联动工具。
 */
@Slf4j
public final class ErpBpmProcessUtils {

    private ErpBpmProcessUtils() {
    }

    /**
     * 事务提交后，以管理员身份取消在途的 BPM 流程实例；流程已结束或不存在时跳过。
     *
     * 注意：调用方必须先把业务单据状态落库（回退草稿、清空 processInstanceId），再调用本方法。
     * 因为取消流程会触发 BPM 的 CANCEL 结果回调，业务侧“仅审批中才接收回调”的守卫会拦截该回调，
     * 避免单据被错误冲成“已取消”。
     *
     * 取消失败仅记录日志、不影响业务事务：业务数据已正确回退，最坏情况仅残留一条 BPM 待办，
     * 可在 BPM 管理后台手工取消。
     *
     * @param bpmProcessInstanceApi BPM 流程实例 API
     * @param userId                操作人编号（记录取消原因）
     * @param processInstanceId     流程实例编号
     * @param reason                取消原因
     */
    public static void cancelProcessInstanceAfterCommit(
            BpmProcessInstanceApi bpmProcessInstanceApi, Long userId, String processInstanceId, String reason) {
        if (StrUtil.isBlank(processInstanceId)) {
            return;
        }
        Runnable cancel = () -> {
            try {
                bpmProcessInstanceApi.cancelProcessInstanceByAdminIfRunning(userId, processInstanceId, reason);
            } catch (Exception ex) {
                log.error("[cancelProcessInstanceAfterCommit][userId({}) processInstanceId({}) 取消 BPM 流程实例失败]",
                        userId, processInstanceId, ex);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cancel.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cancel.run();
            }
        });
    }

}
