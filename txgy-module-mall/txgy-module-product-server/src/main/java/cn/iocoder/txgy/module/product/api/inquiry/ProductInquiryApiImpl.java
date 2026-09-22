package cn.iocoder.txgy.module.product.api.inquiry;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.product.api.inquiry.dto.InquiryAcceptedQuoteRespDTO;
import cn.iocoder.txgy.module.product.service.inquiry.PurchaseInquiryMarketService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductInquiryApiImpl implements ProductInquiryApi {

    @Resource private PurchaseInquiryMarketService service;

    @Override
    public CommonResult<InquiryAcceptedQuoteRespDTO> getAcceptedQuote(Long memberId, Long quoteId) {
        return CommonResult.success(service.getAcceptedQuote(memberId, quoteId));
    }
}
