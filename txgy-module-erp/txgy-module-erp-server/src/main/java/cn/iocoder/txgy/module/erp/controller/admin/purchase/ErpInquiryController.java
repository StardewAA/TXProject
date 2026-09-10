package cn.iocoder.txgy.module.erp.controller.admin.purchase;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.collection.MapUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.erp.controller.admin.product.vo.product.ErpProductRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.inquiry.*;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.*;
import cn.iocoder.txgy.module.erp.service.product.ErpProductService;
import cn.iocoder.txgy.module.erp.service.purchase.ErpInquiryService;
import cn.iocoder.txgy.module.erp.service.purchase.ErpPurchaseRequestService;
import cn.iocoder.txgy.module.erp.service.purchase.ErpSupplierService;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpSupplierDO;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - ERP 询价比价与定标")
@RestController @RequestMapping("/erp/inquiry") @Validated
public class ErpInquiryController {
    @Resource private ErpInquiryService inquiryService;
    @Resource private ErpPurchaseRequestService requestService;
    @Resource private ErpProductService productService;
    @Resource private ErpSupplierService supplierService;
    @Resource private AdminUserApi adminUserApi;

    @PostMapping("/create") @PreAuthorize("@ss.hasPermission('erp:inquiry:create')")
    public CommonResult<Long> create(@Valid @RequestBody ErpInquirySaveReqVO reqVO) { return success(inquiryService.createInquiry(reqVO)); }
    @PostMapping("/create-from-request") @PreAuthorize("@ss.hasPermission('erp:purchase-request:convert')")
    public CommonResult<Long> createFromRequest(@RequestParam("requestId") Long id) { return success(inquiryService.createInquiryFromRequest(id)); }
    @PutMapping("/update") @PreAuthorize("@ss.hasPermission('erp:inquiry:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody ErpInquirySaveReqVO reqVO) { inquiryService.updateInquiry(reqVO); return success(true); }
    @DeleteMapping("/delete") @PreAuthorize("@ss.hasPermission('erp:inquiry:delete')")
    public CommonResult<Boolean> delete(@RequestParam("ids") List<Long> ids) { inquiryService.deleteInquiry(ids); return success(true); }
    @PutMapping("/submit") @PreAuthorize("@ss.hasPermission('erp:inquiry:submit')")
    public CommonResult<Boolean> submit(@RequestParam("id") Long id) { inquiryService.submitInquiry(id, getLoginUserId()); return success(true); }
    @PutMapping("/publish") @PreAuthorize("@ss.hasPermission('erp:inquiry:publish')")
    public CommonResult<Boolean> publish(@RequestParam("id") Long id) { inquiryService.publishInquiry(id); return success(true); }
    @PutMapping("/close") @PreAuthorize("@ss.hasPermission('erp:inquiry:close')")
    public CommonResult<Boolean> close(@RequestParam("id") Long id) { inquiryService.closeInquiry(id); return success(true); }
    @PostMapping("/quote/save") @PreAuthorize("@ss.hasPermission('erp:inquiry-quote:manage')")
    public CommonResult<Long> saveQuote(@Valid @RequestBody ErpInquiryQuoteSaveReqVO reqVO) { return success(inquiryService.saveQuote(reqVO)); }
    @PutMapping("/award/save") @PreAuthorize("@ss.hasPermission('erp:award:update')")
    public CommonResult<Boolean> saveAward(@Valid @RequestBody ErpAwardSelectionReqVO reqVO) { inquiryService.saveAwardSelection(reqVO); return success(true); }
    @PutMapping("/award/submit") @PreAuthorize("@ss.hasPermission('erp:award:submit')")
    public CommonResult<Boolean> submitAward(@RequestParam("id") Long id) { inquiryService.submitAward(id, getLoginUserId()); return success(true); }
    @PutMapping("/award/confirm") @PreAuthorize("@ss.hasPermission('erp:award:confirm')")
    public CommonResult<List<Long>> confirmAward(@RequestParam("id") Long id) { return success(inquiryService.confirmAward(id)); }

    @GetMapping("/get") @PreAuthorize("@ss.hasAnyPermissions('erp:inquiry:query', 'erp:award:query')")
    public CommonResult<ErpInquiryRespVO> get(@RequestParam("id") Long id) {
        ErpInquiryDO inquiry = inquiryService.getInquiry(id);
        return success(inquiry == null ? null : build(inquiry, true));
    }
    @GetMapping("/page") @Operation(summary = "获得询价/定标分页")
    @PreAuthorize("@ss.hasAnyPermissions('erp:inquiry:query', 'erp:award:query')")
    public CommonResult<PageResult<ErpInquiryRespVO>> page(@Valid ErpInquiryPageReqVO reqVO) {
        PageResult<ErpInquiryDO> result = inquiryService.getInquiryPage(reqVO);
        return success(BeanUtils.toBean(result, ErpInquiryRespVO.class, vo -> {
            ErpInquiryDO source = result.getList().stream().filter(i -> i.getId().equals(vo.getId())).findFirst().orElseThrow();
            ErpInquiryRespVO built = build(source, false);
            vo.setItems(built.getItems()).setProductNames(built.getProductNames()).setCreatorName(built.getCreatorName())
                    .setPurchaseRequestNo(built.getPurchaseRequestNo());
        }));
    }

    private ErpInquiryRespVO build(ErpInquiryDO inquiry, boolean includeQuotes) {
        ErpInquiryRespVO vo = BeanUtils.toBean(inquiry, ErpInquiryRespVO.class);
        List<ErpInquiryItemDO> items = inquiryService.getItemList(List.of(inquiry.getId()));
        Map<Long, ErpProductRespVO> products = productService.getProductVOMap(convertSet(items, ErpInquiryItemDO::getProductId));
        vo.setItems(BeanUtils.toBean(items, ErpInquiryRespVO.Item.class, item -> MapUtils.findAndThen(products, item.getProductId(),
                p -> item.setProductName(p.getName()).setProductBarCode(p.getBarCode()).setProductUnitName(p.getUnitName()))));
        vo.setProductNames(CollUtil.join(vo.getItems(), "，", ErpInquiryRespVO.Item::getProductName));
        if (inquiry.getPurchaseRequestId() != null) {
            ErpPurchaseRequestDO request = requestService.getPurchaseRequest(inquiry.getPurchaseRequestId());
            if (request != null) vo.setPurchaseRequestNo(request.getNo());
        }
        if (inquiry.getCreator() != null) {
            AdminUserRespDTO user = adminUserApi.getUser(Long.parseLong(inquiry.getCreator())).getCheckedData();
            if (user != null) vo.setCreatorName(user.getNickname());
        }
        if (!includeQuotes) return vo;
        List<ErpInquiryQuoteDO> quotes = inquiryService.getQuoteList(inquiry.getId());
        List<ErpInquiryQuoteItemDO> quoteItems = inquiryService.getQuoteItemList(convertSet(quotes, ErpInquiryQuoteDO::getId));
        Map<Long, List<ErpInquiryQuoteItemDO>> quoteItemMap = convertMultiMap(quoteItems, ErpInquiryQuoteItemDO::getQuoteId);
        Map<Long, ErpSupplierDO> suppliers = supplierService.getSupplierMap(convertSet(quotes, ErpInquiryQuoteDO::getSupplierId));
        vo.setQuotes(BeanUtils.toBean(quotes, ErpInquiryRespVO.Quote.class, quote -> {
            MapUtils.findAndThen(suppliers, quote.getSupplierId(), s -> quote.setSupplierName(s.getName()));
            quote.setItems(BeanUtils.toBean(quoteItemMap.get(quote.getId()), ErpInquiryRespVO.QuoteItem.class, item ->
                    MapUtils.findAndThen(products, item.getProductId(), p -> item.setProductName(p.getName()).setProductUnitName(p.getUnitName()))));
        }));
        return vo;
    }
}
