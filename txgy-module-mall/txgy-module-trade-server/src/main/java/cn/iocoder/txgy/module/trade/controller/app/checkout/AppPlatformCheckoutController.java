package cn.iocoder.txgy.module.trade.controller.app.checkout;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.module.trade.controller.app.checkout.vo.AppPlatformCheckoutDetailRespVO;
import cn.iocoder.txgy.module.trade.controller.app.checkout.vo.AppPlatformCheckoutFormalizationPrepareReqVO;
import cn.iocoder.txgy.module.trade.controller.app.checkout.vo.AppPlatformCheckoutFormalizationPrepareRespVO;
import cn.iocoder.txgy.module.trade.controller.app.checkout.vo.AppPlatformCheckoutPreviewReqVO;
import cn.iocoder.txgy.module.trade.controller.app.checkout.vo.AppPlatformCheckoutPreviewRespVO;
import cn.iocoder.txgy.module.trade.controller.app.checkout.vo.AppPlatformCheckoutPageItemRespVO;
import cn.iocoder.txgy.module.trade.controller.app.checkout.vo.AppPlatformCheckoutSubmitReqVO;
import cn.iocoder.txgy.module.trade.service.checkout.PlatformCheckoutService;
import cn.iocoder.txgy.module.trade.service.checkout.PlatformCheckoutFormalizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUser;

@Tag(name = "用户 App - 平台跨租户结算")
@RestController
@RequestMapping("/trade/platform-checkout")
@Validated
@TenantIgnore
@RequiredArgsConstructor
public class AppPlatformCheckoutController {

    private final PlatformCheckoutService checkoutService;
    private final PlatformCheckoutFormalizationService formalizationService;

    @PostMapping("/preview")
    @Operation(summary = "预览平台购物车结算；服务端重读商品真实归属与价格")
    public CommonResult<AppPlatformCheckoutPreviewRespVO> preview(
            @Valid @RequestBody AppPlatformCheckoutPreviewReqVO request) {
        LoginUser loginUser = requiredLoginUser();
        return success(checkoutService.preview(loginUser.getId(), loginUser.getTenantId(), request));
    }

    @PostMapping("/submit")
    @Operation(summary = "幂等保存不可支付的拆单规划；不会创建业务订单、支付单或清理购物车")
    public CommonResult<AppPlatformCheckoutDetailRespVO> submit(
            @Valid @RequestBody AppPlatformCheckoutSubmitReqVO request) {
        LoginUser loginUser = requiredLoginUser();
        return success(checkoutService.submit(loginUser.getId(), loginUser.getTenantId(), request));
    }

    @PostMapping("/{checkoutNo}/formalization/prepare")
    @Operation(summary = "幂等复核正式子订单准备条件；仅记录阻断审计，不创建订单或支付")
    public CommonResult<AppPlatformCheckoutFormalizationPrepareRespVO> prepareFormalization(
            @PathVariable("checkoutNo") @NotBlank String checkoutNo,
            @Valid @RequestBody AppPlatformCheckoutFormalizationPrepareReqVO request) {
        LoginUser loginUser = requiredLoginUser();
        PlatformCheckoutFormalizationService.Preparation preparation = formalizationService.prepare(
                loginUser.getId(), loginUser.getTenantId(), checkoutNo, request.getIdempotencyKey());
        return success(toResponse(preparation));
    }

    @GetMapping("/{checkoutNo}")
    @Operation(summary = "查询本人平台结算单及子订单、资金分摊")
    public CommonResult<AppPlatformCheckoutDetailRespVO> get(
            @PathVariable("checkoutNo") @NotBlank String checkoutNo) {
        LoginUser loginUser = requiredLoginUser();
        return success(checkoutService.get(loginUser.getId(), loginUser.getTenantId(), checkoutNo));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询本人平台结算历史；列表不返回地址等敏感字段")
    public CommonResult<PageResult<AppPlatformCheckoutPageItemRespVO>> page(@Valid PageParam pageParam) {
        LoginUser loginUser = requiredLoginUser();
        return success(checkoutService.page(loginUser.getId(), loginUser.getTenantId(), pageParam));
    }

    private static LoginUser requiredLoginUser() {
        LoginUser loginUser = getLoginUser();
        if (loginUser == null || loginUser.getId() == null || loginUser.getTenantId() == null) {
            throw new IllegalStateException("平台结算需要有效会员会话");
        }
        return loginUser;
    }

    private static AppPlatformCheckoutFormalizationPrepareRespVO toResponse(
            PlatformCheckoutFormalizationService.Preparation preparation) {
        return new AppPlatformCheckoutFormalizationPrepareRespVO()
                .setAttemptId(preparation.attemptId()).setCheckoutNo(preparation.checkoutNo())
                .setSnapshotDigest(preparation.snapshotDigest()).setReady(preparation.ready())
                .setBlockerCodes(preparation.blockers().stream().map(Enum::name).toList())
                .setBlockerReasons(preparation.blockers().stream()
                        .map(PlatformCheckoutFormalizationService.Blocker::getReason).toList())
                .setReason(preparation.reason());
    }

}
