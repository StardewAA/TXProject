package cn.iocoder.txgy.module.product.controller.admin.inquiry;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.product.controller.app.inquiry.InquiryPublicDetailRespVO;
import cn.iocoder.txgy.module.product.controller.app.inquiry.InquiryPublicSummaryRespVO;
import cn.iocoder.txgy.module.product.controller.app.inquiry.InquiryQuoteMessageRespVO;
import cn.iocoder.txgy.module.product.controller.app.inquiry.InquiryQuoteMessageSendReqVO;
import cn.iocoder.txgy.module.product.controller.app.inquiry.InquiryQuoteRespVO;
import cn.iocoder.txgy.module.product.controller.app.inquiry.InquiryQuoteSaveReqVO;
import cn.iocoder.txgy.module.product.service.enterprise.ProductAdminBusinessScopeService;
import cn.iocoder.txgy.module.product.service.inquiry.PurchaseInquiryMarketService;
import cn.iocoder.txgy.module.product.service.supplier.SupplierProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 供应商询价机会与报价")
@RestController
@RequestMapping("/product/purchase-inquiry/supplier")
@Validated
public class ProductSupplierInquiryController {

    @Resource private ProductAdminBusinessScopeService scopeService;
    @Resource private PurchaseInquiryMarketService inquiryService;
    @Resource private SupplierProductService supplierProductService;

    public record ShopOption(Long id, Long enterpriseId, String name, String logoUrl,
                             Integer status, Long tenantId) {}

    @GetMapping("/shops")
    @Operation(summary = "获得当前后台账号获授权的经营店铺")
    @PreAuthorize("@ss.hasPermission('product:inquiry-supplier:query')")
    public CommonResult<List<ShopOption>> shops() {
        return success(scopeService.authorizedShops(scopeService.currentAdminUserId()).stream()
                .map(shop -> new ShopOption(shop.getId(), shop.getEnterpriseId(), shop.getName(),
                        shop.getLogoUrl(), shop.getStatus(), shop.getTenantId())).toList());
    }

    @GetMapping("/opportunity-page")
    @Operation(summary = "获得当前店铺可参与的询价机会")
    @PreAuthorize("@ss.hasPermission('product:inquiry-supplier:query')")
    public CommonResult<PageResult<InquiryPublicSummaryRespVO>> opportunityPage(
            @Valid PageParam page, @RequestParam Long shopId,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String keyword) {
        return success(inquiryService.adminOpportunityPage(scopeService.currentAdminUserId(), shopId, page, state, keyword));
    }

    @GetMapping("/opportunity-get")
    @Operation(summary = "获得当前店铺可参与的询价详情")
    @PreAuthorize("@ss.hasPermission('product:inquiry-supplier:query')")
    public CommonResult<InquiryPublicDetailRespVO> opportunityGet(@RequestParam Long id,
                                                                  @RequestParam Long shopId) {
        return success(inquiryService.adminOpportunityGet(scopeService.currentAdminUserId(), shopId, id));
    }

    @GetMapping("/quote/sku-options")
    @Operation(summary = "获得当前店铺可用于报价的在售 SKU")
    @PreAuthorize("@ss.hasPermission('product:inquiry-supplier:quote')")
    public CommonResult<List<SupplierProductService.QuoteSkuOption>> quoteSkuOptions(@RequestParam Long shopId) {
        return success(supplierProductService.adminQuoteSkuOptions(scopeService.currentAdminUserId(), shopId));
    }

    @GetMapping("/quote/mine")
    @Operation(summary = "获得当前店铺对询价的报价")
    @PreAuthorize("@ss.hasPermission('product:inquiry-supplier:query')")
    public CommonResult<InquiryQuoteRespVO> mine(@RequestParam Long inquiryId, @RequestParam Long shopId) {
        return success(inquiryService.adminMine(scopeService.currentAdminUserId(), inquiryId, shopId));
    }

    @GetMapping("/quote/page")
    @Operation(summary = "获得当前店铺的报价分页")
    @PreAuthorize("@ss.hasPermission('product:inquiry-supplier:query')")
    public CommonResult<PageResult<InquiryQuoteRespVO>> quotePage(@Valid PageParam page,
                                                                  @RequestParam Long shopId,
                                                                  @RequestParam(required = false) Integer status) {
        return success(inquiryService.adminSupplierPage(scopeService.currentAdminUserId(), shopId, status, page));
    }

    @GetMapping("/quote/get")
    @Operation(summary = "获得获授权店铺的报价详情")
    @PreAuthorize("@ss.hasPermission('product:inquiry-supplier:query')")
    public CommonResult<InquiryQuoteRespVO> quoteGet(@RequestParam Long id) {
        return success(inquiryService.adminGet(scopeService.currentAdminUserId(), id));
    }

    @PostMapping("/quote/save")
    @Operation(summary = "保存当前店铺报价草稿")
    @PreAuthorize("@ss.hasPermission('product:inquiry-supplier:quote')")
    public CommonResult<Long> quoteSave(@Valid @RequestBody InquiryQuoteSaveReqVO request) {
        return success(inquiryService.adminSaveQuote(scopeService.currentAdminUserId(), request));
    }

    @PutMapping("/quote/submit")
    @Operation(summary = "提交当前店铺报价")
    @PreAuthorize("@ss.hasPermission('product:inquiry-supplier:quote')")
    public CommonResult<Boolean> quoteSubmit(@RequestParam Long id, @RequestParam Long version) {
        inquiryService.adminSubmitQuote(scopeService.currentAdminUserId(), id, version);
        return success(true);
    }

    @PutMapping("/quote/withdraw")
    @Operation(summary = "撤回当前店铺报价")
    @PreAuthorize("@ss.hasPermission('product:inquiry-supplier:quote')")
    public CommonResult<Boolean> quoteWithdraw(@RequestParam Long id, @RequestParam Long version) {
        inquiryService.adminWithdrawQuote(scopeService.currentAdminUserId(), id, version);
        return success(true);
    }

    @GetMapping("/quote/messages")
    @Operation(summary = "获得报价沟通消息")
    @PreAuthorize("@ss.hasPermission('product:inquiry-supplier:message')")
    public CommonResult<List<InquiryQuoteMessageRespVO>> messages(
            @RequestParam Long quoteId,
            @RequestParam(required = false, defaultValue = "0") Long afterId,
            @RequestParam(required = false, defaultValue = "50") Integer pageSize) {
        return success(inquiryService.adminMessages(scopeService.currentAdminUserId(), quoteId, afterId, pageSize));
    }

    @PostMapping("/quote/messages")
    @Operation(summary = "以后台供应商身份发送报价沟通消息")
    @PreAuthorize("@ss.hasPermission('product:inquiry-supplier:message')")
    public CommonResult<Long> sendMessage(@Valid @RequestBody InquiryQuoteMessageSendReqVO request) {
        return success(inquiryService.adminSendMessage(scopeService.currentAdminUserId(), request));
    }
}
