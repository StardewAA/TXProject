package cn.iocoder.txgy.module.erp.service.purchase;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.plan.ErpPurchasePlanPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.plan.ErpPurchasePlanSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchasePlanDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchasePlanItemDO;

import java.util.Collection;
import java.util.List;

public interface ErpPurchasePlanService {
    Long createPurchasePlan(ErpPurchasePlanSaveReqVO reqVO);
    void updatePurchasePlan(ErpPurchasePlanSaveReqVO reqVO);
    void deletePurchasePlan(List<Long> ids);
    /** 提交审批：草稿 / 审批驳回 / 已撤销 → 审批中 */
    void submitPurchasePlan(Long id, Long userId);
    /** 审批通过：审批中 → 审批通过 */
    void approvePurchasePlan(Long id, Long userId);
    /** 审批驳回：审批中 → 审批驳回 */
    void rejectPurchasePlan(Long id, Long userId);
    ErpPurchasePlanDO getPurchasePlan(Long id);
    ErpPurchasePlanDO validatePurchasePlan(Long id);
    PageResult<ErpPurchasePlanDO> getPurchasePlanPage(ErpPurchasePlanPageReqVO reqVO);
    List<ErpPurchasePlanItemDO> getItemListByPlanId(Long planId);
    List<ErpPurchasePlanItemDO> getItemListByPlanIds(Collection<Long> planIds);
}
