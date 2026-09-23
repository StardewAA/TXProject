package cn.iocoder.txgy.module.product.controller.admin.shop;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.product.api.shop.dto.ProductAdminAuthorizedShopRespDTO;
import cn.iocoder.txgy.module.product.service.enterprise.ProductAdminBusinessScopeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 商城经营数据范围")
@RestController
@RequestMapping("/product/admin-business-scope")
@Validated
public class ProductAdminBusinessScopeController {

    @Resource private ProductAdminBusinessScopeService scopeService;

    @GetMapping("/shops")
    @Operation(summary = "获得当前后台账号获授权的有效店铺")
    @PreAuthorize("@ss.hasAnyPermissions('product:shop:query', 'product:spu:query', "
            + "'product:inquiry-supplier:query', 'product:inquiry-supplier:quote', "
            + "'product:inquiry-supplier:message', 'product:business-member:query', "
            + "'product:business-member:update', 'trade:supplier-order:query', "
            + "'trade:supplier-after-sale:query', 'trade:supplier-settlement:query', "
            + "'promotion:kefu-agent:manage', 'promotion:kefu-faq:manage')")
    public CommonResult<List<ProductAdminAuthorizedShopRespDTO>> shops() {
        return success(scopeService.authorizedShopOptions(scopeService.currentAdminUserId()));
    }
}
