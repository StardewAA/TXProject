package cn.iocoder.txgy.module.product.controller.admin.enterprise;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.product.controller.admin.enterprise.vo.*;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.ProductEnterpriseDO;
import cn.iocoder.txgy.module.product.service.enterprise.ProductAdminBusinessScopeService;
import cn.iocoder.txgy.module.product.service.enterprise.ProductEnterpriseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 企业入驻")
@RestController
@RequestMapping("/product/enterprise")
@Validated
public class ProductEnterpriseController {
    @Resource private ProductEnterpriseService service;
    @Resource private ProductAdminBusinessScopeService scopeService;

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('product:enterprise:create')")
    public CommonResult<Long> create(@Valid @RequestBody ProductEnterpriseSaveReqVO req) {
        scopeService.requireTenantManager(scopeService.currentAdminUserId());
        return success(service.create(BeanUtils.toBean(req, ProductEnterpriseDO.class)));
    }

    @PutMapping("/update")
    @PreAuthorize("@ss.hasPermission('product:enterprise:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody ProductEnterpriseSaveReqVO req) {
        Long userId = scopeService.currentAdminUserId();
        ProductAdminBusinessScopeService.Scope scope = scopeService.scope(userId);
        scopeService.requireEnterpriseManager(userId, req.getId());
        ProductEnterpriseDO target = BeanUtils.toBean(req, ProductEnterpriseDO.class);
        if (!scope.unrestricted()) {
            ProductEnterpriseDO existing = service.get(req.getId());
            target.setCreditCode(existing.getCreditCode());
            target.setAuditStatus(existing.getAuditStatus());
            target.setAuditRemark(existing.getAuditRemark());
        }
        service.update(target);
        return success(true);
    }

    @DeleteMapping("/delete")
    @PreAuthorize("@ss.hasPermission('product:enterprise:delete')")
    public CommonResult<Boolean> delete(@RequestParam Long id) {
        scopeService.requireTenantManager(scopeService.currentAdminUserId());
        service.delete(id);
        return success(true);
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('product:enterprise:query')")
    public CommonResult<ProductEnterpriseRespVO> get(@RequestParam Long id) {
        scopeService.requireEnterprise(scopeService.currentAdminUserId(), id);
        return success(BeanUtils.toBean(service.get(id), ProductEnterpriseRespVO.class));
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('product:enterprise:query')")
    public CommonResult<PageResult<ProductEnterpriseRespVO>> page(@Valid ProductEnterprisePageReqVO req) {
        ProductAdminBusinessScopeService.Scope scope = scopeService.currentScope();
        req.setEnterpriseIds(scope.unrestricted() ? null
                : scope.enterpriseIds().isEmpty() ? List.of(-1L) : scope.enterpriseIds());
        return success(BeanUtils.toBean(service.page(req), ProductEnterpriseRespVO.class));
    }

    @GetMapping("/list-approved")
    @PreAuthorize("@ss.hasPermission('product:enterprise:query')")
    public CommonResult<List<ProductEnterpriseRespVO>> approved() {
        ProductAdminBusinessScopeService.Scope scope = scopeService.currentScope();
        List<ProductEnterpriseDO> rows = service.getApproved();
        if (!scope.unrestricted()) rows.removeIf(row -> !scope.enterpriseIds().contains(row.getId()));
        return success(BeanUtils.toBean(rows, ProductEnterpriseRespVO.class));
    }

    @PutMapping("/audit")
    @PreAuthorize("@ss.hasPermission('product:enterprise:audit')")
    public CommonResult<Boolean> audit(@RequestParam Long id, @RequestParam Integer status,
                                       @RequestParam(required = false) String remark) {
        scopeService.requireTenantManager(scopeService.currentAdminUserId());
        service.audit(id, status, remark);
        return success(true);
    }
}
