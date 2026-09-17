package cn.iocoder.txgy.module.mes.service.wm.productsales;

import cn.iocoder.txgy.module.mes.dal.dataobject.wm.productsales.MesWmProductSalesApprovalTaskDO;

/**
 * MES 销售出库审批结果任务 Service 接口
 *
 * @author OPENLAB BS
 */
public interface MesWmProductSalesApprovalTaskService {

    /**
     * 创建审批结果任务（幂等：同一流程实例只创建一次）
     *
     * @param salesId           销售出库单编号
     * @param processInstanceId BPM 流程实例编号
     * @param bpmStatus         BPM 流程实例状态（2 通过、3 驳回）
     * @param reason            审批原因/驳回意见
     * @return 任务编号
     */
    Long createTask(Long salesId, String processInstanceId, Integer bpmStatus, String reason);

    /**
     * 定时扫描待处理任务（跨租户扫描 + 切租户消费）
     */
    void processPendingTasks();

    /**
     * 消费单个任务：审批通过 → 按 OQC 标志分流；驳回 → 退回草稿
     *
     * @param task 审批结果任务
     */
    void processTask(MesWmProductSalesApprovalTaskDO task);

}
