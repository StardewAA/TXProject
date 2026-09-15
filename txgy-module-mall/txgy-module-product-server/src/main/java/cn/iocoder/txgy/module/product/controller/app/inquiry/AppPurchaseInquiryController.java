package cn.iocoder.txgy.module.product.controller.app.inquiry;
import cn.iocoder.txgy.framework.common.pojo.*;
import cn.iocoder.txgy.module.product.dal.dataobject.inquiry.InquiryDO;
import cn.iocoder.txgy.module.product.service.inquiry.PurchaseInquiryService;
import cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
@RestController @RequestMapping("/product/purchase-inquiry")
public class AppPurchaseInquiryController {
 @Resource private PurchaseInquiryService service;
 @GetMapping("/page") public CommonResult<PageResult<InquiryDO>> page(@Valid PageParam page,
   @RequestParam(required=false) String keyword,@RequestParam(required=false) Integer status) {
  return success(service.page(EnterpriseMembershipService.memberId(),page,keyword,status));
 }
 @GetMapping("/get") public CommonResult<Map<String,Object>> get(@RequestParam Long id) {
  return success(service.detail(EnterpriseMembershipService.memberId(),id));
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
