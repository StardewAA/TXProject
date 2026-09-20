package cn.iocoder.txgy.module.member.service.tenant;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.member.framework.tenant.config.MallTenantAuthProperties;
import cn.iocoder.txgy.module.member.framework.tenant.config.MallTenantProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.*;

@Service
@RequiredArgsConstructor
public class MallAuthPolicy {
    private final MallTenantProperties tenants;
    private final MallTenantAuthProperties auth;
    private final MallTenantCatalogService catalog;
    private final MallCustomerAffiliationService customers;

    public long resolve(Long requestedTenant, boolean registration) {
        requireConfigured();
        // Revalidate status/expiry on send AND consume; never fall back to the legacy tenant.
        var directory = catalog.getConfig();
        Long target = requestedTenant == null ? directory.getPublicTenantId() : requestedTenant;
        if (target == null || directory.getTenants().stream().noneMatch(t -> Objects.equals(t.getTenantId(), target))) {
            throw exception(MALL_TENANT_UNAVAILABLE);
        }
        Long headerTenant = TenantContextHolder.getTenantId();
        if (headerTenant != null && !Objects.equals(headerTenant, target)) throw exception(AUTH_TENANT_MISMATCH);
        if (registration && tenants.getEntries().stream().noneMatch(e -> Objects.equals(e.getTenantId(), target)
                && e.isPublicVisible() && e.isRegistrationEnabled())) throw exception(MALL_TENANT_UNAVAILABLE);
        if (registration) customers.validateRegistration(target);
        return target;
    }

    public void requireConfigured() {
        if (!tenants.isEnabled() || !auth.isEnabled() || !StringUtils.hasText(auth.getHmacSecret())
                || auth.getHmacSecret().getBytes(StandardCharsets.UTF_8).length < 32
                || !StringUtils.hasText(auth.getSmsTemplateCode())
                || auth.getCodeTtlSeconds() < 60 || auth.getCodeTtlSeconds() > 600
                || auth.getResendSeconds() < 30 || auth.getResendSeconds() > auth.getCodeTtlSeconds()
                || auth.getMaxAttempts() < 1 || auth.getMaxAttempts() > 10
                || auth.getMobileDailyLimit() < 1 || auth.getMobileDailyLimit() > 30
                || auth.getIpHourlyLimit() < 1 || auth.getIpHourlyLimit() > 1000
                || auth.getMobileVerifyLimit() < 1 || auth.getMobileVerifyLimit() > 100
                || auth.getIpVerifyLimit() < 1 || auth.getIpVerifyLimit() > 3000) {
            throw exception(MALL_TENANT_UNAVAILABLE);
        }
    }
}
