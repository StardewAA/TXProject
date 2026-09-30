package cn.iocoder.txgy.module.product.controller.admin.shop;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.product.controller.admin.shop.vo.ProductShopRespVO;
import cn.iocoder.txgy.module.product.controller.admin.shop.vo.ProductShopSaveReqVO;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.ProductEnterpriseDO;
import cn.iocoder.txgy.module.product.dal.dataobject.shop.ProductShopDO;
import cn.iocoder.txgy.module.product.service.enterprise.ProductEnterpriseService;
import cn.iocoder.txgy.module.product.service.enterprise.ProductAdminBusinessScopeService;
import cn.iocoder.txgy.module.product.service.shop.ProductShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 产业链企业店铺")
@RestController
@RequestMapping("/product/shop")
@Validated
public class ProductShopController {

    @Resource
    private ProductShopService shopService;
    @Resource
    private ProductEnterpriseService enterpriseService;
    @Resource
    private ProductAdminBusinessScopeService businessScopeService;

    @PostMapping("/create")
    @Operation(summary = "创建企业店铺")
    @PreAuthorize("@ss.hasPermission('product:shop:create')")
    public CommonResult<Long> createShop(@Valid @RequestBody ProductShopSaveReqVO reqVO) {
        businessScopeService.requireTenantManager(businessScopeService.currentAdminUserId());
        return success(shopService.createShop(BeanUtils.toBean(reqVO, ProductShopDO.class)));
    }

    @PutMapping("/update")
    @Operation(summary = "更新企业店铺")
    @PreAuthorize("@ss.hasPermission('product:shop:update')")
    public CommonResult<Boolean> updateShop(@Valid @RequestBody ProductShopSaveReqVO reqVO) {
        businessScopeService.requireCurrentShopResource(reqVO.getId());
        shopService.updateShop(BeanUtils.toBean(reqVO, ProductShopDO.class));
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除企业店铺")
    @Parameter(name = "id", description = "店铺编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('product:shop:delete')")
    public CommonResult<Boolean> deleteShop(@RequestParam Long id) {
        businessScopeService.requireTenantManager(businessScopeService.currentAdminUserId());
        shopService.deleteShop(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得企业店铺")
    @Parameter(name = "id", description = "店铺编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('product:shop:query')")
    public CommonResult<ProductShopRespVO> getShop(@RequestParam Long id) {
        businessScopeService.requireCurrentShopResource(id);
        return success(toRespVO(shopService.getShop(id)));
    }

    @GetMapping("/list")
    @Operation(summary = "获得企业店铺列表")
    @PreAuthorize("@ss.hasPermission('product:shop:query')")
    public CommonResult<List<ProductShopRespVO>> getShopList() {
        ProductAdminBusinessScopeService.Scope scope = businessScopeService.currentScope();
        List<ProductShopDO> shops = scope.unrestricted() ? shopService.getShopList()
                : businessScopeService.authorizedShops(scope.adminUserId());
        return success(shops.stream().map(this::toRespVO).collect(Collectors.toList()));
    }

    @GetMapping("/authorized-admin-user-ids")
    @Operation(summary = "获得该店铺全部合法后台处置人（负责人 + 被授权账号/客服）的后台用户编号")
    @Parameter(name = "shopId", description = "店铺编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('product:shop:query')")
    public CommonResult<List<Long>> getAuthorizedAdminUserIds(@RequestParam Long shopId) {
        businessScopeService.requireCurrentShopResource(shopId);
        return success(shopService.getAuthorizedAdminUserIds(shopId));
    }

    @GetMapping("/my")
    @Operation(summary = "获得当前后台账号负责的企业店铺")
    @PreAuthorize("@ss.hasPermission('product:spu:query')")
    public CommonResult<ProductShopRespVO> getMyManagedShop() {
        List<ProductShopDO> shops = businessScopeService.authorizedShops(businessScopeService.currentAdminUserId());
        return success(shops.isEmpty() ? null : toRespVO(shops.getFirst()));
    }

    private ProductShopRespVO toRespVO(ProductShopDO shop) {
        if (shop == null) {
            return null;
        }
        ProductShopRespVO vo = BeanUtils.toBean(shop, ProductShopRespVO.class);
        vo.setTenantId(shop.getTenantId());
        ProductEnterpriseDO enterprise = enterpriseService.get(shop.getEnterpriseId());
        if (enterprise != null) {
            vo.setEnterpriseName(enterprise.getName());
            if (vo.getTenantId() == null) {
                vo.setTenantId(enterprise.getTenantId());
            }
        }
        return vo;
    }
}

