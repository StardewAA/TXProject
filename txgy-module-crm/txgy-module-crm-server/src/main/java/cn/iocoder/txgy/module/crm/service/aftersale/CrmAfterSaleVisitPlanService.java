package cn.iocoder.txgy.module.crm.service.aftersale;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visitplan.CrmAfterSaleVisitPlanPageReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visitplan.CrmAfterSaleVisitPlanSaveReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleVisitPlanDO;
import jakarta.validation.Valid;

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
     * 生成回访任务（按计划周期断点续排：生成到今天为止已到期且尚未生成的任务）
     *
     * @param planId 回访计划编号
     * @param userId 操作人（任务处理人未确定时的兜底）
     * @return 本次生成的任务数量
     */
    int generateTasks(Long planId, Long userId);

    /**
     * 【定时任务】为所有「启用中且仍在有效期」的回访计划批量生成到期未生成的回访任务
     *
     * @return 本次成功生成的任务总数
     */
    int autoGenerateVisitTasks();

}
