package cn.iocoder.txgy.module.erp.service.purchase;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.evaluation.ErpSupplierEvaluationPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.evaluation.ErpSupplierEvaluationSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpSupplierEvaluationDO;

import java.util.List;

public interface ErpSupplierEvaluationService {
    Long createSupplierEvaluation(ErpSupplierEvaluationSaveReqVO reqVO);
    void updateSupplierEvaluation(ErpSupplierEvaluationSaveReqVO reqVO);
    void deleteSupplierEvaluation(List<Long> ids);
    ErpSupplierEvaluationDO getSupplierEvaluation(Long id);
    ErpSupplierEvaluationDO validateSupplierEvaluation(Long id);
    PageResult<ErpSupplierEvaluationDO> getSupplierEvaluationPage(ErpSupplierEvaluationPageReqVO reqVO);
}
