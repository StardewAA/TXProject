package cn.iocoder.txgy.module.erp.service.purchase;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.contract.ErpPurchaseContractPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.contract.ErpPurchaseContractSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseContractDO;

import java.util.List;

public interface ErpPurchaseContractService {
    Long createPurchaseContract(ErpPurchaseContractSaveReqVO reqVO);
    void updatePurchaseContract(ErpPurchaseContractSaveReqVO reqVO);
    void deletePurchaseContract(List<Long> ids);
    /** 提交审批：草稿 / 审批驳回 / 已撤销 → 审批中 */
    void submitPurchaseContract(Long id);
    /** 审批通过：审批中 → 审批通过 */
    void approvePurchaseContract(Long id);
    /** 审批驳回：审批中 → 审批驳回 */
    void rejectPurchaseContract(Long id);
    ErpPurchaseContractDO getPurchaseContract(Long id);
    ErpPurchaseContractDO validatePurchaseContract(Long id);
    PageResult<ErpPurchaseContractDO> getPurchaseContractPage(ErpPurchaseContractPageReqVO reqVO);
}
