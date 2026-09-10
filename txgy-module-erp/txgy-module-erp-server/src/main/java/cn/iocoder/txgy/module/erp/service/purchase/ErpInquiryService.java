package cn.iocoder.txgy.module.erp.service.purchase;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.inquiry.*;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.*;

import java.util.Collection;
import java.util.List;

public interface ErpInquiryService {
    Long createInquiry(ErpInquirySaveReqVO reqVO);
    Long createInquiryFromRequest(Long requestId);
    void updateInquiry(ErpInquirySaveReqVO reqVO);
    void deleteInquiry(List<Long> ids);
    void submitInquiry(Long id, Long userId);
    void updateInquiryAuditStatus(Long id, Integer bpmStatus);
    void publishInquiry(Long id);
    void closeInquiry(Long id);
    Long saveQuote(ErpInquiryQuoteSaveReqVO reqVO);
    void saveAwardSelection(ErpAwardSelectionReqVO reqVO);
    void submitAward(Long id, Long userId);
    void updateAwardAuditStatus(Long id, Integer bpmStatus);
    List<Long> confirmAward(Long id);
    ErpInquiryDO getInquiry(Long id);
    PageResult<ErpInquiryDO> getInquiryPage(ErpInquiryPageReqVO reqVO);
    List<ErpInquiryItemDO> getItemList(Collection<Long> inquiryIds);
    List<ErpInquiryQuoteDO> getQuoteList(Long inquiryId);
    List<ErpInquiryQuoteItemDO> getQuoteItemList(Collection<Long> quoteIds);
}
