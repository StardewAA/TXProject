package cn.iocoder.txgy.module.member.controller.app.tenant;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.module.member.framework.tenant.config.MallTenantAuthProperties;
import cn.iocoder.txgy.module.member.framework.tenant.config.MallTenantProperties;
import cn.iocoder.txgy.module.member.service.tenant.MallTenantCatalogService;
import jakarta.annotation.security.PermitAll;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.MALL_TENANT_UNAVAILABLE;

/** Registration intent directory, not permission to activate cross-tenant shopping. */
@RestController
@RequestMapping("/member/mall-auth")
@RequiredArgsConstructor
public class AppMallRegistrationOptionsController {
    private static final long RETIRED_PUBLIC_TENANT_ID = 3000L;

    private final MallTenantAuthProperties auth;
    private final MallTenantProperties tenants;
    private final MallTenantCatalogService catalog;

    public record Tenant(Long tenantId, String name) { }
    public record Options(boolean enabled, Long publicTenantId, List<Tenant> tenants,
                          boolean crossTenantReady, boolean selectionRequired) { }

    @GetMapping("/registration-options") @PermitAll @TenantIgnore
    public CommonResult<Options> getOptions() {
        if (!auth.isEnabled() || !tenants.isEnabled()) {
            return success(new Options(false, null, List.of(), false, false));
        }
        var directory = catalog.getConfig();
        List<Tenant> options = new ArrayList<>();
        for (var item : directory.getTenants()) {
            Long tenantId = item.getTenantId();
            if (tenantId == null || tenantId == RETIRED_PUBLIC_TENANT_ID
                    || tenants.getEntries().stream().noneMatch(entry -> entry.isPublicVisible()
                    && entry.isRegistrationEnabled() && Objects.equals(entry.getTenantId(), tenantId))) {
                continue;
            }
            options.add(new Tenant(tenantId, item.getName()));
        }
        if (options.isEmpty()) {
            throw exception(MALL_TENANT_UNAVAILABLE);
        }
        // 3000 已隔离；注册必须显式选择一个真实可用租户，不再提供公共默认值。
        return success(new Options(true, null, List.copyOf(options), false, true));
    }

}
