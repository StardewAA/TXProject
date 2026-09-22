package cn.iocoder.txgy.module.product.controller.admin.spu;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.product.dal.dataobject.review.ProductSpuReviewRecordDO;
import cn.iocoder.txgy.module.product.service.review.ProductSpuReviewService;
import cn.iocoder.txgy.module.product.service.spu.ProductSpuService;
import cn.iocoder.txgy.module.product.service.shop.ProductShopService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService.invalid;

@RestController
@RequestMapping("/product/spu-review")
public class ProductSpuReviewController {
    @Resource private ProductSpuReviewService reviewService;
    @Resource private ProductSpuService spuService;
    @Resource private ProductShopService shopService;
    @GetMapping("/policy")
    @PreAuthorize("@ss.hasPermission('product:spu:query')")
    public CommonResult<ProductSpuReviewService.Policy> policy() { return success(reviewService.policy()); }
    @GetMapping("/history")
    @PreAuthorize("@ss.hasPermission('product:spu:query')")
    public CommonResult<List<ProductSpuReviewRecordDO>> history(@RequestParam Long id) {
        var p = spuService.getSpu(id);
        if (p == null) throw invalid("商品不存在");
        var managed = shopService.getShopByManagerUserId(SecurityFrameworkUtils.getLoginUserId());
        if (managed != null && !Objects.equals(managed.getId(), p.getShopId())) throw invalid("无权查看其他店铺商品审核记录");
        return success(reviewService.history(id));
    }
    public record Decision(@NotNull Long id, @NotNull @PositiveOrZero Long expectedVersion,
                           @NotBlank @Size(max=64) String submissionId, @NotNull Boolean approved,
                           @Size(max=1000) String reason) {}
    @PostMapping("/decision")
    @PreAuthorize("@ss.hasPermission('product:spu:review')")
    public CommonResult<Boolean> decision(@Valid @RequestBody Decision req) {
        reviewService.decide(req.id(), req.expectedVersion(), req.submissionId(), req.approved(), req.reason());
        return success(true);
    }
    public record Submission(@NotNull Long id, @NotNull @PositiveOrZero Long expectedVersion,
                             @Size(max=64) String submissionId) {}
    @PostMapping("/submit")
    @PreAuthorize("@ss.hasPermission('product:spu:update')")
    public CommonResult<Boolean> submit(@Valid @RequestBody Submission req) {
        reviewService.adminSubmit(req.id(), req.expectedVersion()); return success(true);
    }
    @PostMapping("/withdraw")
    @PreAuthorize("@ss.hasPermission('product:spu:update')")
    public CommonResult<Boolean> withdraw(@Valid @RequestBody Submission req) {
        reviewService.adminWithdraw(req.id(), req.expectedVersion(), req.submissionId()); return success(true);
    }
}
