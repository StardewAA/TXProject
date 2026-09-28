package cn.iocoder.txgy.module.erp.service.purchase;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.inspection.ErpArrivalInspectionPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.inspection.ErpArrivalInspectionSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpArrivalInspectionDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpArrivalInspectionItemDO;

import java.util.Collection;
import java.util.List;

public interface ErpArrivalInspectionService {
    Long createArrivalInspection(ErpArrivalInspectionSaveReqVO reqVO);
    void updateArrivalInspection(ErpArrivalInspectionSaveReqVO reqVO);
    void deleteArrivalInspection(List<Long> ids);
    /** 提交审批：草稿(0) / 审批驳回(30) / 已撤销(40) → 审批中(10) */
    void submitArrivalInspection(Long id, Long userId);
    /** 审批通过：审批中(10) → 审批通过(20) */
    void approveArrivalInspection(Long id, Long userId);
    /** 审批驳回：审批中(10) → 审批驳回(30) */
    void rejectArrivalInspection(Long id, Long userId);
    ErpArrivalInspectionDO getArrivalInspection(Long id);
    ErpArrivalInspectionDO validateArrivalInspection(Long id);
    PageResult<ErpArrivalInspectionDO> getArrivalInspectionPage(ErpArrivalInspectionPageReqVO reqVO);
    List<ErpArrivalInspectionItemDO> getItemListByInspectionId(Long inspectionId);
    List<ErpArrivalInspectionItemDO> getItemListByInspectionIds(Collection<Long> inspectionIds);
}
