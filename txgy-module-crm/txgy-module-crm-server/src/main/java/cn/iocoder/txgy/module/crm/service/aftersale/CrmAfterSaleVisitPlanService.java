package cn.iocoder.txgy.module.crm.service.aftersale;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visitplan.CrmAfterSaleVisitPlanPageReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visitplan.CrmAfterSaleVisitPlanSaveReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleVisitPlanDO;
import jakarta.validation.Valid;

import java.time.LocalDate;

/**
 * 售后回访计划 Service 接口
 *
 * @author txgycloud
 */
public interface CrmAfterSaleVisitPlanService {

    /**
     * 创建回访计划
     *
     * @param createReqVO 创建信息
     * @param userId      用户编号
     * @return 回访计划编号
     */
    Long createVisitPlan(@Valid CrmAfterSaleVisitPlanSaveReqVO createReqVO, Long userId);

    /**
     * 更新回访计划
     *
     * @param updateReqVO 更新信息
     */
    void updateVisitPlan(@Valid CrmAfterSaleVisitPlanSaveReqVO updateReqVO);

    /**
     * 删除回访计划
     *
     * @param id 编号
     */
    void deleteVisitPlan(Long id);

    /**
     * 获得回访计划
     *
     * @param id 编号
     * @return 回访计划
     */
    CrmAfterSaleVisitPlanDO getVisitPlan(Long id);

    /**
     * 校验回访计划是否存在
     *
     * @param id 编号
     * @return 回访计划
     */
    CrmAfterSaleVisitPlanDO validateVisitPlan(Long id);

    /**
     * 获得回访计划分页
     *
     * @param pageReqVO 分页查询
     * @return 回访计划分页
     */
    PageResult<CrmAfterSaleVisitPlanDO> getVisitPlanPage(CrmAfterSaleVisitPlanPageReqVO pageReqVO);

    /**
     * 生成回访任务（按计划周期滚动生成「下一个周期」的任务）
     *
     * @param planId 回访计划编号
     * @param userId 操作人（任务处理人未确定时的兜底）
     * @return 本次生成的任务数量（0 或 1）
     */
    int generateTasks(Long planId, Long userId);

    /**
     * 生成下一个周期的回访任务（本期任务完成后滚动生成）
     *
     * <p>规则：
     * 1. 下一期日期 = 本期任务日期 + 周期（周期倍数按周期类型换算）；
     * 2. 下一期日期晚于「质保到期日」或「计划结束日」时停止生成，返回 null；
     * 3. 计划不存在/非启用、或周期类型为「质保到期前重点回访」（单次）时不生成；
     * 4. 同一周期已存在任务时幂等跳过，避免重复生成。
     *
     * @param planId       回访计划编号
     * @param operator     操作人（处理人兜底）
     * @param baseDate     本期任务日期；为空表示「初始化生成首期任务」
     * @param baseCycleKey 本期任务的周期标识；为空时按已有任务数量兜底
     * @return 新生成的任务编号；未生成时返回 null
     */
    Long generateNextTask(Long planId, Long operator, LocalDate baseDate, String baseCycleKey);

    /**
     * 【定时任务】为所有「启用中且仍在有效期」的回访计划批量生成到期未生成的回访任务
     *
     * @return 本次成功生成的任务总数
     */
    int autoGenerateVisitTasks();

}
