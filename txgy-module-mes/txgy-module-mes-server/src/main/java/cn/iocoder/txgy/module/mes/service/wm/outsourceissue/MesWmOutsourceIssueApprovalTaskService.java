package cn.iocoder.txgy.module.mes.service.wm.outsourceissue;

import cn.iocoder.txgy.module.mes.dal.dataobject.wm.outsourceissue.MesWmOutsourceIssueApprovalTaskDO;

/**
 * MES 委外发料审批结果任务 Service 接口
 *
 * @author OPENLAB BS
 */
public interface MesWmOutsourceIssueApprovalTaskService {

    /**
     * 创建审批结果任务（幂等：同一流程实例只创建一次）
     *
     * @param issueId           委外发料单编号
     * @param processInstanceId BPM 流程实例编号
     * @param bpmStatus         BPM 流程实例状态（2 通过、3 驳回）
     * @param reason            审批原因/驳回意见
     * @return 任务编号
     */
    Long createTask(Long issueId, String processInstanceId, Integer bpmStatus, String reason);

    /**
     * 定时扫描待处理任务（跨租户扫描 + 切租户消费）
     */
    void processPendingTasks();

    /**
     * 消费单个任务：审批通过 → 进入"待拣货"；驳回 → 退回草稿
     *
     * @param task 审批结果任务
     */
    void processTask(MesWmOutsourceIssueApprovalTaskDO task);

}
