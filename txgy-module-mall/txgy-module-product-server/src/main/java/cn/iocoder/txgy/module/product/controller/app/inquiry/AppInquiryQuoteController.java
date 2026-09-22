package cn.iocoder.txgy.module.product.controller.app.inquiry;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService;
import cn.iocoder.txgy.module.product.service.inquiry.PurchaseInquiryMarketService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/product/purchase-inquiry/quote")
public class AppInquiryQuoteController {

    @Resource private PurchaseInquiryMarketService service;

    @GetMapping("/mine")
    public CommonResult<InquiryQuoteRespVO> mine(@RequestParam Long inquiryId, @RequestParam Long shopId) {
        return success(service.mine(EnterpriseMembershipService.memberId(), inquiryId, shopId));
    }

    @GetMapping("/page")
    public CommonResult<PageResult<InquiryQuoteRespVO>> page(@Valid PageParam page,
                                                              @RequestParam(required = false) Long inquiryId) {
        return success(service.page(EnterpriseMembershipService.memberId(), inquiryId, page));
    }

    @GetMapping("/supplier-page")
    public CommonResult<PageResult<InquiryQuoteRespVO>> supplierPage(@Valid PageParam page,
                                                                      @RequestParam Long shopId,
                                                                      @RequestParam(required = false) Integer status) {
        return success(service.supplierPage(EnterpriseMembershipService.memberId(), shopId, status, page));
    }

    @GetMapping("/get")
    public CommonResult<InquiryQuoteRespVO> get(@RequestParam Long id) {
        return success(service.get(EnterpriseMembershipService.memberId(), id));
    }

    @PostMapping("/save")
    public CommonResult<Long> save(@Valid @RequestBody InquiryQuoteSaveReqVO request) {
        return success(service.saveQuote(EnterpriseMembershipService.memberId(), request));
    }

    @PutMapping("/submit")
    public CommonResult<Boolean> submit(@RequestParam Long id, @RequestParam Long version) {
        service.submitQuote(EnterpriseMembershipService.memberId(), id, version);
        return success(true);
    }

    @PutMapping("/withdraw")
    public CommonResult<Boolean> withdraw(@RequestParam Long id, @RequestParam Long version) {
        service.withdrawQuote(EnterpriseMembershipService.memberId(), id, version);
        return success(true);
    }

    @PutMapping("/accept")
    public CommonResult<Boolean> accept(@RequestParam Long id, @RequestParam Long version) {
        service.acceptQuote(EnterpriseMembershipService.memberId(), id, version);
        return success(true);
    }

    @GetMapping("/messages")
    public CommonResult<List<InquiryQuoteMessageRespVO>> messages(
            @RequestParam Long quoteId,
            @RequestParam(required = false, defaultValue = "0") Long afterId,
            @RequestParam(required = false, defaultValue = "50") Integer pageSize) {
        return success(service.messages(EnterpriseMembershipService.memberId(), quoteId, afterId, pageSize));
    }

    @PostMapping("/messages")
    public CommonResult<Long> sendMessage(@Valid @RequestBody InquiryQuoteMessageSendReqVO request) {
        return success(service.sendMessage(EnterpriseMembershipService.memberId(), request));
    }
}
