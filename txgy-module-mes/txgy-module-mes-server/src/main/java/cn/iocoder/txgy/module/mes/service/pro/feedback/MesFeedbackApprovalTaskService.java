package cn.iocoder.txgy.module.mes.service.pro.feedback;

import cn.iocoder.txgy.module.mes.dal.dataobject.pro.feedback.MesFeedbackApprovalTaskDO;

/**
 * MES 报工审批结果任务 Service 接口
 *
 * @author OPENLAB BS
 */
public interface MesFeedbackApprovalTaskService {

    /**
     * 创建审批结果消费任务（幂等：同一流程实例只创建一次）
     *
     * @param feedbackId        报工单编号
     * @param processInstanceId BPM 流程实例编号
     * @param bpmStatus         BPM 流程实例状态
     * @param reason            审批原因
     * @return 任务编号
     */
    Long createTask(Long feedbackId, String processInstanceId, Integer bpmStatus, String reason);

    /**
     * 定时扫描待处理任务并消费（跨租户扫描，逐条切租户处理）
     */
    void processPendingTasks();

    /**
     * 消费单个任务：根据 BPM 审批结果回写报工单状态
     *
     * 审批通过 → approveFeedback 执行全部业务动作；
     * 驳回 → rejectFeedback 退回草稿
     *
     * @param task 任务
     */
    void processTask(MesFeedbackApprovalTaskDO task);

}
