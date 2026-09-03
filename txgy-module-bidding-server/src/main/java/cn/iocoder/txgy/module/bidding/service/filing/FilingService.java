package cn.iocoder.txgy.module.bidding.service.filing;

import cn.iocoder.txgy.module.bidding.controller.admin.filing.vo.FilingResultReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.filing.vo.FilingSubmitReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.filing.vo.FilingSubmitRespVO;

/**
 * 备案服务（05：BID-ENT-REG-01 备案提交 / BID-SUP-FILE-01 备案结果回推）。
 */
public interface FilingService {

    /**
     * 备案提交（幂等键 = filingType + projectCode；重复提交报 FILING_ALREADY_SUBMITTED）。
     * 返回受理回执（receiptNo/status=PENDING）。
     */
    FilingSubmitRespVO submitFiling(FilingSubmitReqVO reqVO);

    /**
     * 监管回推备案结果（仅 PENDING 可流转至 ACCEPTED/REJECTED）。
     */
    void receiveFilingResult(FilingResultReqVO reqVO);
}
