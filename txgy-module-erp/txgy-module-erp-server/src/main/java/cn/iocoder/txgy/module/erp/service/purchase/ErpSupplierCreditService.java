package cn.iocoder.txgy.module.erp.service.purchase;

import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.supplier.credit.*;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpSupplierCreditStatusDO;
import jakarta.validation.Valid;

import java.util.Collection;
import java.util.Map;

public interface ErpSupplierCreditService {

    ErpSupplierCreditRespVO getCredit(Long supplierId);

    ErpSupplierCreditStatusDO ensureStatus(Long supplierId);

    Map<Long, ErpSupplierCreditStatusDO> getStatusMap(Collection<Long> supplierIds);

    void receiveCreditStatus(@Valid ErpSupplierCreditStatusSaveReqVO reqVO);

    void replaceAdmissionCategories(@Valid ErpSupplierAdmissionCategorySaveReqVO reqVO);

    void replaceQualifications(@Valid ErpSupplierQualificationSaveReqVO reqVO);

    Long createRiskEvent(@Valid ErpSupplierRiskEventSaveReqVO reqVO);

    void updateRiskEventStatus(Long id, String status, String resolution);

    ErpSupplierQualificationCheckRespVO checkQualification(@Valid ErpSupplierQualificationCheckReqVO reqVO);

    void validateQualification(Long supplierId, Collection<Long> productIds);
}
