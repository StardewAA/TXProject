package cn.iocoder.txgy.module.member.service.tenant;

import cn.iocoder.txgy.framework.common.biz.system.tenant.TenantCommonApi;
import cn.iocoder.txgy.framework.common.exception.ErrorCode;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.member.framework.tenant.config.MallTenantAuthProperties;
import cn.iocoder.txgy.module.member.framework.tenant.config.MallTenantProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.Locale;
import java.util.Objects;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.*;

/** Image registration has no SMS-template dependency. It does not enable tenant switching. */
@Service
public class MallAccountPolicy {
    public static final ErrorCode INVALID_ACCOUNT = new ErrorCode(1_004_008_190, "登录名须以字母开头，包含6至20位字母、数字或下划线");
    public static final ErrorCode ACCOUNT_USED = new ErrorCode(1_004_008_191, "该登录名已被使用");
    public static final ErrorCode IMAGE_REQUIRED = new ErrorCode(1_004_008_192, "请使用登录名和图片验证码注册，已有账号请登录");
    private final MallTenantProperties tenants;
    private final MallTenantAuthProperties auth;
    private final MallTenantCatalogService catalog;
    private final MallCustomerAffiliationService customers;
    private final TenantCommonApi tenantApi;
    private final Long legacyTenant;
    private final boolean enabled;

    public MallAccountPolicy(MallTenantProperties tenants, MallTenantAuthProperties auth,
            MallTenantCatalogService catalog, MallCustomerAffiliationService customers, TenantCommonApi tenantApi,
            @Value("${mall.registration.platform-tenant-id:1}") Long legacyTenant,
            @Value("${mall.registration.picture-enabled:false}") boolean enabled) {
        this.tenants = tenants; this.auth = auth; this.catalog = catalog; this.customers = customers;
        this.tenantApi = tenantApi; this.legacyTenant = legacyTenant; this.enabled = enabled;
    }
    public boolean isEnabled() { return enabled; }
    public void rejectLegacyRegistration() { if (enabled) throw exception(IMAGE_REQUIRED); }

    public long resolve(Long requested, boolean registration) {
        if (!enabled || tenants.isEnabled() != auth.isEnabled()) throw exception(MALL_TENANT_UNAVAILABLE);
        Long target;
        if (!tenants.isEnabled()) {
            target = requested == null ? legacyTenant : requested;
            if (target == null || target <= 0 || !Objects.equals(target, legacyTenant)) throw exception(MALL_TENANT_UNAVAILABLE);
            var valid = tenantApi.validTenant(target);
            if (valid == null || !Boolean.TRUE.equals(valid.getCheckedData())) throw exception(MALL_TENANT_UNAVAILABLE);
        } else {
            var directory = catalog.getConfig();
            target = requested == null ? directory.getPublicTenantId() : requested;
            final Long chosen = target;
            if (target == null || directory.getTenants().stream().noneMatch(t -> Objects.equals(t.getTenantId(), chosen)))
                throw exception(MALL_TENANT_UNAVAILABLE);
            if (registration && tenants.getEntries().stream().noneMatch(e -> Objects.equals(e.getTenantId(), chosen)
                    && e.isPublicVisible() && e.isRegistrationEnabled())) throw exception(MALL_TENANT_UNAVAILABLE);
        }
        if (TenantContextHolder.getTenantId() != null && !Objects.equals(target, TenantContextHolder.getTenantId()))
            throw exception(AUTH_TENANT_MISMATCH);
        if (registration) customers.validateRegistration(target);
        return target;
    }
    public static String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z][a-z0-9_]{5,19}")) throw exception(INVALID_ACCOUNT);
        return normalized;
    }
}
