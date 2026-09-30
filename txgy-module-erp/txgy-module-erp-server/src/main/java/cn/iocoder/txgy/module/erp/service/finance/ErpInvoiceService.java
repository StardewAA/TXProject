package cn.iocoder.txgy.module.erp.service.finance;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.erp.controller.admin.finance.vo.invoice.ErpInvoicePageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.finance.vo.invoice.ErpInvoiceSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.finance.ErpInvoiceDO;

import java.util.List;

public interface ErpInvoiceService {
    Long createInvoice(ErpInvoiceSaveReqVO reqVO);
    void updateInvoice(ErpInvoiceSaveReqVO reqVO);
    void deleteInvoice(List<Long> ids);
    /** 认证：待认证 → 已认证，并写入认证时间 */
    void certifyInvoice(Long id);
    /** 作废：非已作废 → 已作废 */
    void voidInvoice(Long id);
    ErpInvoiceDO getInvoice(Long id);
    ErpInvoiceDO validateInvoice(Long id);
    PageResult<ErpInvoiceDO> getInvoicePage(ErpInvoicePageReqVO reqVO);
}
