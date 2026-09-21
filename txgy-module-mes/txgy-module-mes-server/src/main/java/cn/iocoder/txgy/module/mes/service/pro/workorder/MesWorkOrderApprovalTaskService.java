package cn.iocoder.txgy.module.mes.service.pro.workorder;

import cn.iocoder.txgy.module.mes.dal.dataobject.pro.workorder.MesWorkOrderApprovalTaskDO;

/**
 * MES 生产工单审批结果任务 Service 接口
 *
 * @author OPENLAB BS
 */
public interface MesWorkOrderApprovalTaskService {

    /**
     * 创建审批结果任务（幂等：同一流程实例只创建一次）
     *
     * @param workOrderId       生产工单编号
     * @param processInstanceId BPM 流程实例编号
     * @param bpmStatus         BPM 流程实例状态（2 审批通过、3 驳回）
     * @param reason            审批原因/驳回意见
     * @return 任务编号
     */
    Long createTask(Long workOrderId, String processInstanceId, Integer bpmStatus, String reason);

    /**
     * 定时扫描并消费待处理任务
     */
    void processPendingTasks();

    /**
     * 消费单条任务：按 BPM 审批结果回写工单状态
     *
     * @param task 任务
     */
    void processTask(MesWorkOrderApprovalTaskDO task);

}
