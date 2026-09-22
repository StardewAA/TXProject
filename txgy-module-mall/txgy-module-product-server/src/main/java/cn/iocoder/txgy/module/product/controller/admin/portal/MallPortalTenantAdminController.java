package cn.iocoder.txgy.module.product.controller.admin.portal;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.module.product.controller.admin.portal.vo.MallPortalTenantPageReqVO;
import cn.iocoder.txgy.module.product.controller.admin.portal.vo.MallPortalTenantRespVO;
import cn.iocoder.txgy.module.product.controller.admin.portal.vo.MallPortalTenantUpdateReqVO;
import cn.iocoder.txgy.module.product.service.portal.MallPortalTenantAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 商城平台租户目录")
@RestController
@RequestMapping("/product/portal-tenant")
@Validated
@TenantIgnore
@RequiredArgsConstructor
public class MallPortalTenantAdminController {

    private final MallPortalTenantAdminService service;

    @GetMapping("/page")
    @Operation(summary = "获得平台租户目录分页")
    @PreAuthorize("@ss.hasPermission('product:portal-tenant:query')")
    public CommonResult<PageResult<MallPortalTenantRespVO>> getPage(
            @Valid MallPortalTenantPageReqVO request) {
        return success(BeanUtils.toBean(service.getPage(request), MallPortalTenantRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得平台租户目录详情")
    @Parameter(name = "id", description = "目录编号", required = true)
    @PreAuthorize("@ss.hasPermission('product:portal-tenant:query')")
    public CommonResult<MallPortalTenantRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(service.get(id), MallPortalTenantRespVO.class));
    }

    @PutMapping("/update")
    @Operation(summary = "更新平台租户目录公开配置")
    @PreAuthorize("@ss.hasPermission('product:portal-tenant:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody MallPortalTenantUpdateReqVO request) {
        service.update(request);
        return success(true);
    }
}
