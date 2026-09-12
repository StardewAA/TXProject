package cn.iocoder.txgy.module.mes.service.pro.project;

import cn.iocoder.txgy.module.mes.dal.dataobject.pro.project.MesProProjectSyncTaskDO;

/**
 * MES 项目同步 Service 接口
 *
 * 消费"销售订单审批通过"事件，自动生成生产项目。
 *
 * @author OPENLAB BS
 */
public interface MesProProjectSyncService {

    /**
     * 创建同步任务（幂等：同一销售订单已有成功/待处理任务时直接返回）
     *
     * @param saleOrderId ERP 销售订单编号
     * @return 任务编号
     */
    Long createSyncTask(Long saleOrderId);

    /**
     * 扫描并处理到期的待处理任务（定时任务调用）
     */
    void processPendingTasks();

    /**
     * 处理单个同步任务：查询 ERP 订单 → 幂等创建项目 → 回写任务状态
     *
     * @param task 同步任务
     */
    void processTask(MesProProjectSyncTaskDO task);

    /**
     * 手动重试失败任务（管理端使用）
     *
     * @param id 任务编号
     */
    void retrySyncTask(Long id);

}
