package cn.iocoder.txgy.module.crm.service.aftersale;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visittask.CrmAfterSaleVisitTaskPageReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visittask.CrmAfterSaleVisitTaskSaveReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleVisitTaskDO;
import jakarta.validation.Valid;

/**
 * 售后回访任务 Service 接口
 *
 * @author txgycloud
 */
public interface CrmAfterSaleVisitTaskService {

    /**
     * 手动创建回访任务（planId = 0）
     *
     * @param createReqVO 创建信息
     * @param userId      用户编号
     * @return 回访任务编号
     */
    Long createVisitTask(@Valid CrmAfterSaleVisitTaskSaveReqVO createReqVO, Long userId);

    /**
     * 更新回访任务（仅待处理任务可编辑）
     *
     * @param updateReqVO 更新信息
     * @param userId      用户编号
     */
    void updateVisitTask(@Valid CrmAfterSaleVisitTaskSaveReqVO updateReqVO, Long userId);

    /**
     * 删除回访任务（仅未完成的任务可删除）
     *
     * @param id     编号
     * @param userId 用户编号
     */
    void deleteVisitTask(Long id, Long userId);

    /**
     * 完成任务：登记回访记录后回写任务状态，并更新质保档案最近回访时间
     *
     * @param id     编号
     * @param userId 用户编号
     */
    void finishVisitTask(Long id, Long userId);

    /**
     * 获得回访任务
     *
     * @param id 编号
     * @return 回访任务
     */
    CrmAfterSaleVisitTaskDO getVisitTask(Long id);

    /**
     * 校验回访任务是否存在
     *
     * @param id 编号
     * @return 回访任务
     */
    CrmAfterSaleVisitTaskDO validateVisitTask(Long id);

    /**
     * 获得回访任务分页
     *
     * @param pageReqVO 分页查询
     * @return 回访任务分页
     */
    PageResult<CrmAfterSaleVisitTaskDO> getVisitTaskPage(CrmAfterSaleVisitTaskPageReqVO pageReqVO);

    /**
     * 【定时任务】自动标记逾期：超过「逾期判定天数」仍未完成的待处理任务标记为已逾期
     *
     * @return 本次标记为逾期的任务数量
     */
    int autoMarkOverdueVisitTasks();

    /**
     * 【定时任务】回访任务临期/到期站内信提醒（按提前提醒天数、overdue_sent_date 防重复）
     *
     * @return 本次发送提醒的任务数量
     */
    int remindDueVisitTasks();

}
