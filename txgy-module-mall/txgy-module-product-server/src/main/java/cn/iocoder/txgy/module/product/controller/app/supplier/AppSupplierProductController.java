package cn.iocoder.txgy.module.product.controller.app.supplier;

import cn.iocoder.txgy.framework.common.pojo.*;
import cn.iocoder.txgy.module.product.service.supplier.SupplierProductService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService.memberId;

@RestController
@RequestMapping("/product/supplier-product")
public class AppSupplierProductController {
    @Resource private SupplierProductService service;
    @Resource private cn.iocoder.txgy.module.product.service.review.ProductSpuReviewService reviewService;
    @GetMapping("/options")
    public CommonResult<Map<String, Object>> options(@RequestParam Long shopId) { return success(service.options(memberId(), shopId)); }
    @GetMapping("/page")
    public CommonResult<PageResult<SupplierProductService.Summary>> page(@RequestParam Long shopId, @Valid PageParam page,
            @RequestParam(required=false) String name, @RequestParam(required=false) Integer status,
            @RequestParam(required=false) Integer reviewStatus) {
        return success(service.page(memberId(), shopId, page, name, status, reviewStatus));
    }
    @GetMapping("/get")
    public CommonResult<SupplierProductService.Detail> get(@RequestParam Long shopId, @RequestParam Long id) {
        return success(service.detail(memberId(), shopId, id));
    }
    @PostMapping("/save")
    public CommonResult<Long> save(@Valid @RequestBody SupplierProductSaveReqVO req) { return success(service.save(memberId(), req)); }
    @PutMapping("/status")
    public CommonResult<Boolean> status(@RequestParam Long shopId, @RequestParam Long id, @RequestParam Integer status,
            @RequestParam Long expectedVersion) {
        service.status(memberId(), shopId, id, status, expectedVersion); return success(true);
    }
    public record ReviewRequest(@jakarta.validation.constraints.NotNull Long shopId,
            @jakarta.validation.constraints.NotNull Long id,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.PositiveOrZero Long expectedVersion,
            String submissionId) {}
    @PostMapping("/review-submit")
    public CommonResult<Boolean> submit(@Valid @RequestBody ReviewRequest req) {
        reviewService.submit(memberId(), req.shopId(), req.id(), req.expectedVersion()); return success(true);
    }
    @PostMapping("/review-withdraw")
    public CommonResult<Boolean> withdraw(@Valid @RequestBody ReviewRequest req) {
        reviewService.withdraw(memberId(), req.shopId(), req.id(), req.expectedVersion(), req.submissionId()); return success(true);
    }
    @GetMapping("/review-history")
    public CommonResult<java.util.List<cn.iocoder.txgy.module.product.dal.dataobject.review.ProductSpuReviewRecordDO>> history(
            @RequestParam Long shopId, @RequestParam Long id) {
        service.detail(memberId(), shopId, id);
        return success(reviewService.history(id));
    }
}
