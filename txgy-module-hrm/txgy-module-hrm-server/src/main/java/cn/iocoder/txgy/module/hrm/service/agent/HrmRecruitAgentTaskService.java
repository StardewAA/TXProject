package cn.iocoder.txgy.module.hrm.service.agent;

import cn.iocoder.txgy.module.hrm.dal.dataobject.agent.HrmRecruitAgentTaskDO;

/**
 * Agent 任务服务。
 *
 * <p>负责任务的幂等创建与状态流转。状态机固定为
 * PENDING → RUNNING → SUCCEEDED / FAILED，结束状态不可再次流转，
 * 避免重试或重复回调把已完成的任务改坏。</p>
 */
public interface HrmRecruitAgentTaskService {

    /**
     * 幂等创建任务。同一用户、同一场景、同一幂等键重复调用返回同一个任务。
     *
     * @param userId 发起用户编号，由服务端会话取得
     * @param bo     任务参数
     * @return 任务（新建或已存在的）
     */
    HrmRecruitAgentTaskDO createTask(Long userId, HrmRecruitAgentTaskCreateBO bo);

    /**
     * 标记任务开始执行。已完成或已失败的任务不会被改动。
     */
    void markRunning(Long taskId);

    /**
     * 标记任务成功。只有成功状态才会被调用方写入分析结果。
     */
    void markSucceeded(Long taskId, String model, String promptVersion);

    /**
     * 标记任务成功，并记录模型调用量。
     *
     * <p>调用量属于审计信息：提供方未返回 usage 时传空，不要用 0 顶替，
     * 否则会把「没拿到」误记成「确实消耗了 0 个 token」。</p>
     */
    void markSucceeded(Long taskId, String model, String promptVersion,
                       Integer promptTokens, Integer completionTokens, Integer totalTokens);

    /**
     * 标记任务失败。
     *
     * @param errorMessage 失败原因，调用方必须保证不含简历原文
     */
    void markFailed(Long taskId, String errorCode, String errorMessage);

    /**
     * 按归属查询任务；非本人或不存在均抛异常。
     */
    HrmRecruitAgentTaskDO getTask(Long userId, Long taskId);

    /**
     * 管理端按当前租户查询任务。
     *
     * <p>与个人端不同：这里不做「是否本人」判断，但必须落在当前登录租户内，
     * 跨租户的任务一律按不存在处理。</p>
     */
    HrmRecruitAgentTaskDO getTaskInTenant(Long taskId);
}
