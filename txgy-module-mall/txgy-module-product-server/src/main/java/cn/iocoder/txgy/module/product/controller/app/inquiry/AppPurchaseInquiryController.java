package cn.iocoder.txgy.module.product.controller.app.inquiry;
import cn.iocoder.txgy.framework.common.pojo.*;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.module.product.service.inquiry.PurchaseInquiryService;
import cn.iocoder.txgy.module.product.service.inquiry.PurchaseInquiryMarketService;
import cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
@RestController @RequestMapping("/product/purchase-inquiry")
public class AppPurchaseInquiryController {
 @Resource private PurchaseInquiryService service;
 @Resource private PurchaseInquiryMarketService marketService;
 @GetMapping("/public-page") @PermitAll @TenantIgnore
 public CommonResult<PageResult<InquiryPublicSummaryRespVO>> publicPage(@Valid PageParam page,
   @RequestParam(required=false) String state,@RequestParam(required=false) String keyword) {
  return success(marketService.publicPage(page,state,keyword));
 }
 @GetMapping("/public-get") @PermitAll @TenantIgnore
 public CommonResult<InquiryPublicDetailRespVO> publicGet(@RequestParam Long id) {
  return success(marketService.publicGet(id));
 }
 @GetMapping("/page") public CommonResult<PageResult<InquiryRespVO>> page(@Valid PageParam page,
   @RequestParam(required=false) String keyword,@RequestParam(required=false) Integer status) {
  return success(BeanUtils.toBean(service.page(EnterpriseMembershipService.memberId(),page,keyword,status),
    InquiryRespVO.class));
 }
 @GetMapping("/get") public CommonResult<InquiryDetailRespVO> get(@RequestParam Long id) {
  var detail=service.detail(EnterpriseMembershipService.memberId(),id);
  return success(new InquiryDetailRespVO(BeanUtils.toBean(detail.order(),InquiryRespVO.class),
    detail.items(),detail.invitedShopIds(),detail.invitedScopes()));
 }
 @GetMapping("/supplier-shop-options") public CommonResult<List<InquirySupplierShopOptionRespVO>> supplierShopOptions() {
  return success(service.supplierShopOptions(EnterpriseMembershipService.memberId()));
 }
 @GetMapping("/invitation-options") public CommonResult<List<InquiryInvitationOptionRespVO>> invitationOptions(
   @RequestParam(required=false) String keyword,@RequestParam(required=false) Integer limit) {
  return success(service.invitationOptions(EnterpriseMembershipService.memberId(),keyword,limit));
 }
 @PostMapping("/save") public CommonResult<Long> save(@Valid @RequestBody InquirySaveReqVO req) {
  return success(service.save(EnterpriseMembershipService.memberId(),req));
 }
 @PutMapping("/submit") public CommonResult<Boolean> submit(@RequestParam Long id) {
  service.submit(EnterpriseMembershipService.memberId(),id); return success(true);
 }
 @PutMapping("/close") public CommonResult<Boolean> close(@RequestParam Long id) {
  service.close(EnterpriseMembershipService.memberId(),id); return success(true);
 }
}
