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
    void updatePurchaseRequestAuditStatus(Long id, Integer bpmStatus);
    ErpPurchaseRequestDO getPurchaseRequest(Long id);
    ErpPurchaseRequestDO validatePurchaseRequest(Long id);
    PageResult<ErpPurchaseRequestDO> getPurchaseRequestPage(ErpPurchaseRequestPageReqVO reqVO);
    List<ErpPurchaseRequestItemDO> getItemListByRequestId(Long requestId);
    List<ErpPurchaseRequestItemDO> getItemListByRequestIds(Collection<Long> requestIds);
}
