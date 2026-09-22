package cn.iocoder.txgy.module.product.controller.app.portal;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.module.product.controller.app.portal.vo.AppPortalHotTenantRespVO;
import cn.iocoder.txgy.module.product.controller.app.portal.vo.AppPortalProductPageReqVO;
import cn.iocoder.txgy.module.product.controller.app.portal.vo.AppPortalProductRespVO;
import cn.iocoder.txgy.module.product.controller.app.portal.vo.AppPortalTenantRespVO;
import cn.iocoder.txgy.module.product.service.portal.MallPortalCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 APP - 平台门户")
@RestController
@RequestMapping("/mall")
@Validated
@TenantIgnore
@PermitAll
@RequiredArgsConstructor
public class AppMallPortalController {

    private final MallPortalCatalogService portalService;

    @GetMapping("/portal/products")
    @Operation(summary = "获得平台跨租户公开商品")
    public CommonResult<PageResult<AppPortalProductRespVO>> getProducts(
            @Valid AppPortalProductPageReqVO request) {
        return success(portalService.getProducts(request));
    }

    @GetMapping("/portal/products/{spuId}")
    @Operation(summary = "获得平台公开商品详情")
    public CommonResult<AppPortalProductRespVO> getProduct(
            @PathVariable @Positive Long spuId) {
        return success(portalService.getProduct(spuId));
    }

    @GetMapping("/portal/tenants/hot")
    @Operation(summary = "获得热门租户")
    public CommonResult<List<AppPortalHotTenantRespVO>> getHotTenants(
            @RequestParam(value = "window", defaultValue = "7d") String window,
            @RequestParam(value = "limit", defaultValue = "20")
            @Min(value = 1, message = "热门租户数量至少为 1")
            @Max(value = 100, message = "热门租户数量不能超过 100") Integer limit) {
        return success(portalService.getHotTenants(window, limit));
    }

    @GetMapping("/tenants/{tenantSlug}")
    @Operation(summary = "获得租户商城公开信息")
    public CommonResult<AppPortalTenantRespVO> getTenant(@PathVariable String tenantSlug) {
        return success(portalService.getTenant(tenantSlug));
    }

    @GetMapping("/tenants/{tenantSlug}/products")
    @Operation(summary = "获得租户商城公开商品")
    public CommonResult<PageResult<AppPortalProductRespVO>> getTenantProducts(
            @PathVariable String tenantSlug, @Valid AppPortalProductPageReqVO request) {
        return success(portalService.getTenantProducts(tenantSlug, request));
    }

    @GetMapping("/tenants/{tenantSlug}/products/{spuId}")
    @Operation(summary = "获得租户商城公开商品详情")
    public CommonResult<AppPortalProductRespVO> getTenantProduct(
            @PathVariable String tenantSlug, @PathVariable @Positive Long spuId) {
        return success(portalService.getTenantProduct(tenantSlug, spuId));
    }
}
