package cn.iocoder.txgy.module.member.controller.app.tenant;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.member.framework.tenant.config.MallTenantAuthProperties;
import cn.iocoder.txgy.module.member.framework.tenant.config.MallTenantProperties;
import cn.iocoder.txgy.module.member.service.tenant.MallAuthPolicy;
import cn.iocoder.txgy.module.member.service.tenant.MallTenantCatalogService;
import jakarta.annotation.security.PermitAll;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
    private final MallTenantAuthProperties auth;
    private final MallTenantProperties tenants;
    private final MallAuthPolicy policy;
    private final MallTenantCatalogService catalog;

    public record Tenant(Long tenantId, String name) { }
    public record Options(boolean enabled, Long publicTenantId, List<Tenant> tenants, boolean crossTenantReady) { }

    @GetMapping("/registration-options") @PermitAll @TenantIgnore
    public CommonResult<Options> getOptions() {
        if (!auth.isEnabled() || !tenants.isEnabled()) return success(new Options(false,null,List.of(),false));
        // Validates public registration AND the platform customer-service enterprise; no fallback.
        Long configuredPublicTenant = tenants.getPublicTenantId();
        if (configuredPublicTenant == null || configuredPublicTenant <= 0) {
            throw exception(MALL_TENANT_UNAVAILABLE);
        }
        // 这是只读公开目录。使用配置中的公共租户校验其企业归属，再恢复原请求上下文；
        // 真正发送、消费验证码及注册仍由 MallAuthPolicy 校验入口租户一致性。
        long[] resolved = new long[1];
        TenantUtils.execute(configuredPublicTenant, () -> {
            resolved[0] = policy.resolve(configuredPublicTenant,true);
        });
        long publicTenant = resolved[0];
        var directory = catalog.getConfig();
        var options = directory.getTenants().stream()
                .filter(item -> tenants.getEntries().stream().anyMatch(entry -> entry.isPublicVisible()
                        && entry.isRegistrationEnabled() && Objects.equals(entry.getTenantId(),item.getTenantId())))
                .map(item -> new Tenant(item.getTenantId(),item.getName())).toList();
        if (!Objects.equals(directory.getPublicTenantId(), publicTenant)
                || options.stream().noneMatch(item -> Objects.equals(item.tenantId(),publicTenant))) {
            throw exception(MALL_TENANT_UNAVAILABLE);
        }
        // Resource scopes / browse switching have not been released. Do not advertise full readiness.
        return success(new Options(true,publicTenant,options,false));
    }
}
