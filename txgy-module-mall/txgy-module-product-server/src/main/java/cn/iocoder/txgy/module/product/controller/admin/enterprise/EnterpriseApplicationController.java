package cn.iocoder.txgy.module.product.controller.admin.enterprise;
import cn.iocoder.txgy.framework.common.pojo.*;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.EnterpriseApplicationDO;
import cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService;
import cn.iocoder.txgy.module.product.service.enterprise.ProductAdminBusinessScopeService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
@RestController @RequestMapping("/product/enterprise-application")
public class EnterpriseApplicationController {
    @Resource private EnterpriseMembershipService service;
    @Resource private ProductAdminBusinessScopeService scopeService;
    @GetMapping("/page") @PreAuthorize("@ss.hasPermission('product:enterprise:query')")
    public CommonResult<PageResult<EnterpriseApplicationDO>> page(@Valid PageParam page, @RequestParam(required=false) Integer status) {
        scopeService.requireTenantManager(scopeService.currentAdminUserId());
        return success(service.page(page, status));
    }
    @GetMapping("/get") @PreAuthorize("@ss.hasPermission('product:enterprise:query')")
    public CommonResult<EnterpriseApplicationDO> get(@RequestParam Long id) {
        scopeService.requireTenantManager(scopeService.currentAdminUserId());
        return success(service.get(id));
    }
    @PutMapping("/bind-shop") @PreAuthorize("@ss.hasPermission('product:shop:update')")
    public CommonResult<Boolean> bindShop(@RequestParam Long id, @RequestParam Long shopId) {
        scopeService.requireTenantManager(scopeService.currentAdminUserId());
        service.bindShop(id, shopId); return success(true);
    }
    @PutMapping("/audit") @PreAuthorize("@ss.hasPermission('product:enterprise:audit')")
    public CommonResult<Boolean> audit(@RequestParam Long id, @RequestParam Integer status,
        @RequestParam String remark, @RequestParam(required=false) Long existingEnterpriseId,
        @RequestParam(required=false) Long adminUserId) {
        scopeService.requireTenantManager(scopeService.currentAdminUserId());
        service.audit(id, status, remark, existingEnterpriseId, adminUserId,
                SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }
}
