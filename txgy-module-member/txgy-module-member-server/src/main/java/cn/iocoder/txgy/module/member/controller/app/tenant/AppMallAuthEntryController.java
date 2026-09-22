package cn.iocoder.txgy.module.member.controller.app.tenant;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.module.member.framework.tenant.config.MallTenantAuthProperties;
import cn.iocoder.txgy.module.member.service.tenant.MallAuthPolicy;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.MALL_TENANT_UNAVAILABLE;

/** Protocol discovery is not tenant selection permission. Never expose keys or template settings. */
@RestController
@RequestMapping("/member/mall-auth")
public class AppMallAuthEntryController {
    private final MallTenantAuthProperties properties;
    private final MallAuthPolicy policy;
    private final Long legacyTenantId;
    @jakarta.annotation.Resource
    private cn.iocoder.txgy.module.member.service.tenant.MallAccountPolicy accountPolicy;
    @Value("${mall.registration.picture-enabled:false}")
    private boolean pictureEnabled;

    public AppMallAuthEntryController(MallTenantAuthProperties properties, MallAuthPolicy policy,
            @Value("${mall.registration.platform-tenant-id:1}") Long legacyTenantId) {
        this.properties = properties;
        this.policy = policy;
        this.legacyTenantId = legacyTenantId;
    }

    public record Entry(String protocol, Long tenantId, boolean available, boolean registrationAvailable,
                        boolean tenantSelection, String registrationMethod, boolean loginNameEnabled) { }

    public CommonResult<Entry> config() {
        return config(null);
    }

    @GetMapping("/config") @PermitAll @TenantIgnore
    public CommonResult<Entry> config(@RequestParam(value = "tenantId", required = false) Long requestedTenant) {
        Long target = requestedTenant == null ? legacyTenantId : requestedTenant;
        if (target == null || target <= 0) throw exception(MALL_TENANT_UNAVAILABLE);
        if (pictureEnabled) {
            boolean available = imageAllowed(target, false);
            return CommonResult.success(new Entry(properties.isEnabled() ? "challenge-v1" : "legacy", target,
                    available, available && imageAllowed(target, true), false, "image-v1", true));
        }
        if (!properties.isEnabled()) {
            // Foreign discovery must never advertise the fixed legacy registration endpoint.
            boolean legacy = target.equals(legacyTenantId);
            return CommonResult.success(new Entry(legacy ? "legacy" : "challenge-v1", target, legacy, legacy, false, "sms", false));
        }
        // If activation/configuration is incomplete, clients MUST NOT fall back to legacy auth.
        boolean available = allowed(target, false);
        return CommonResult.success(new Entry("challenge-v1", target, available,
                available && allowed(target, true), false, "sms", false));
    }

    private boolean allowed(Long target, boolean registration) {
        try { return target.equals(policy.resolve(target, registration)); }
        catch (ServiceException error) { return false; }
    }
    private boolean imageAllowed(Long target, boolean registration) {
        try { return target.equals(accountPolicy.resolve(target, registration)); }
        catch (RuntimeException error) { return false; }
    }
}
