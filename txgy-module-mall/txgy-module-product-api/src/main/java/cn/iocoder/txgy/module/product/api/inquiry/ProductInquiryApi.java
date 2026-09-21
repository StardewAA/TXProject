package cn.iocoder.txgy.module.product.api.inquiry;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.product.api.inquiry.dto.InquiryAcceptedQuoteRespDTO;
import cn.iocoder.txgy.module.product.enums.ApiConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 采购询价内部接口。
 *
 * <p>调用方必须从当前登录会员上下文取得 {@code memberId}，不能信任外部请求直接传入的会员编号。</p>
 */
@FeignClient(name = ApiConstants.NAME, contextId = "productInquiry")
public interface ProductInquiryApi {

    @GetMapping(ApiConstants.PREFIX + "/inquiry/accepted-quote")
    CommonResult<InquiryAcceptedQuoteRespDTO> getAcceptedQuote(
            @RequestParam("memberId") Long memberId,
            @RequestParam("quoteId") Long quoteId);

}
