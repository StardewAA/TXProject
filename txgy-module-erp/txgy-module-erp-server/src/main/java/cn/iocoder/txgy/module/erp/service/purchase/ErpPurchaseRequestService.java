package cn.iocoder.txgy.module.erp.service.purchase;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.request.ErpPurchaseRequestPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.request.ErpPurchaseRequestSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseRequestDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseRequestItemDO;

import java.util.Collection;
import java.util.List;

public interface ErpPurchaseRequestService {
    Long createPurchaseRequest(ErpPurchaseRequestSaveReqVO reqVO);
    void updatePurchaseRequest(ErpPurchaseRequestSaveReqVO reqVO);
    void deletePurchaseRequest(List<Long> ids);
    void submitPurchaseRequest(Long id, Long userId);
    /** 反审批采购申请（审批中 / 已审批 → 草稿），并联动取消 BPM 在途流程实例 */
    void reverseApprovePurchaseRequest(Long id, Long userId, String reason);
    void updatePurchaseRequestAuditStatus(Long id, Integer bpmStatus);
    ErpPurchaseRequestDO getPurchaseRequest(Long id);
    ErpPurchaseRequestDO validatePurchaseRequest(Long id);
    PageResult<ErpPurchaseRequestDO> getPurchaseRequestPage(ErpPurchaseRequestPageReqVO reqVO);
    List<ErpPurchaseRequestItemDO> getItemListByRequestId(Long requestId);
    List<ErpPurchaseRequestItemDO> getItemListByRequestIds(Collection<Long> requestIds);

    /**
     * 累加采购申请明细的「已转询价数量」。
     *
     * <p>申请转询价成功后调用，使申请侧能显示已转情况、并为后续「是否已全部转询价」判断提供依据。
     * 原实现该字段只在创建时写 0、从不累加，等于死字段。</p>
     *
     * @param requestId 采购申请编号
     * @return 累加的明细条数
     */
    int accumulateConvertedCount(Long requestId);
}
