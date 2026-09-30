package cn.iocoder.txgy.module.erp.service.purchase;

import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.contract.ErpPurchaseContractTemplateSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseContractTemplateDO;

import java.util.List;

public interface ErpPurchaseContractTemplateService {
    Long create(ErpPurchaseContractTemplateSaveReqVO reqVO);
    void update(ErpPurchaseContractTemplateSaveReqVO reqVO);
    void delete(Long id);
    ErpPurchaseContractTemplateDO get(Long id);
    List<ErpPurchaseContractTemplateDO> getEnabledList();
}
