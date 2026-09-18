package cn.iocoder.txgy.module.crm.service.aftersale;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visittask.CrmAfterSaleVisitTaskPageReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visittask.CrmAfterSaleVisitTaskSaveReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleVisitTaskDO;
import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.List;

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
     * <p>当 pageReqVO.remindType = 临期（1）时，按「待办：回访任务临期」口径实时查询：
     * 任务状态 = 待处理 + 处理人在（本人 + 下属）范围内 + 计划回访日在提前提醒天数内
     *
     * @param pageReqVO 分页查询
     * @param userId    当前登录用户编号（待办归属过滤使用）
     * @return 回访任务分页
     */
    PageResult<CrmAfterSaleVisitTaskDO> getVisitTaskPage(CrmAfterSaleVisitTaskPageReqVO pageReqVO, Long userId);

    /**
     * 获得「回访任务临期」待办数量（实时查询，与分页查询同一口径，不落库）
     *
     * @param userId 当前登录用户编号
     * @return 待办数量
     */
    Long getVisitTaskRemindCount(Long userId);

    /**
     * 获得指定质保档案下的回访任务列表（按业务编号升序）
     *
     * <p>供回访记录表单的「关联任务编号」下拉选择使用
     *
     * @param warrantyId 质保档案编号
     * @return 回访任务列表
     */
    List<CrmAfterSaleVisitTaskDO> getVisitTaskListByWarrantyId(Long warrantyId);

    /**
     * 按当前售后设置实时计算窗口期截止日期
     *
     * <p>窗口期截止日期 = 计划回访日期 + 售后设置「回访任务逾期判定天数」；
     * 实时计算（不读任务上的 window_end_date 快照），保证售后设置改动后对存量任务立即生效。
     *
     * @param taskDate 计划回访日期
     * @return 窗口期截止日期；计划回访日期为空时返回 null
     */
    LocalDate calcWindowEndDate(LocalDate taskDate);

    /**
     * 硬时间锁校验：未到计划回访日期时禁止提交回访记录
     *
     * <p>售后设置开启「允许提前提交回访记录」(allow_early_visit) 时放行；
     * 窗口期内、已逾期的任务均放行（逾期由前端弹窗二次确认）。
     * 由回访记录入口与任务完成入口共用，保证判定口径唯一。
     *
     * @param task 回访任务（为空或计划回访日期为空时不校验）
     */
    void validateTaskArrived(CrmAfterSaleVisitTaskDO task);

    /**
     * 【定时任务】自动标记逾期：超过「窗口期截止日期」仍未完成的待处理任务标记为已逾期
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
