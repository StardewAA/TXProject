package cn.iocoder.txgy.module.product.controller.admin.enterprise;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.product.controller.admin.enterprise.vo.ProductAdminBusinessMemberRespVO;
import cn.iocoder.txgy.module.product.controller.admin.enterprise.vo.ProductAdminBusinessMemberSaveReqVO;
import cn.iocoder.txgy.module.product.service.enterprise.ProductAdminBusinessAuthorizationService;
import cn.iocoder.txgy.module.product.service.enterprise.ProductAdminBusinessScopeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 商城企业后台成员授权")
@RestController
@RequestMapping("/product/admin-business-member")
@Validated
public class ProductAdminBusinessMemberController {

    @Resource private ProductAdminBusinessAuthorizationService authorizationService;
    @Resource private ProductAdminBusinessScopeService scopeService;

    @GetMapping("/list")
    @Operation(summary = "查询企业后台成员与店铺授权")
    @PreAuthorize("@ss.hasPermission('product:business-member:query')")
    public CommonResult<List<ProductAdminBusinessMemberRespVO>> list(@RequestParam Long enterpriseId) {
        return success(authorizationService.list(scopeService.currentAdminUserId(), enterpriseId));
    }

    @PutMapping("/save")
    @Operation(summary = "授予或停用企业后台成员经营范围")
    @PreAuthorize("@ss.hasPermission('product:business-member:update')")
    public CommonResult<Boolean> save(@Valid @RequestBody ProductAdminBusinessMemberSaveReqVO request) {
        authorizationService.save(scopeService.currentAdminUserId(), request);
        return success(true);
    }
}
