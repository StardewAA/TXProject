package cn.iocoder.txgy.module.promotion.service.kefu.scope;

import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.promotion.service.kefu.scope.KeFuScopeService.FORBIDDEN;

/** Never accept tenant/member/agent identity from request JSON or cross-tenant admin impersonation. */
public final class KeFuScopeIdentity {
    private KeFuScopeIdentity() {}

    public static KeFuScopeModels.Actor require(int expectedType) {
        var login = SecurityFrameworkUtils.getLoginUser();
        var tenant = TenantContextHolder.getTenantId();
        if (login == null || login.getId() == null || login.getId() <= 0
                || !Objects.equals(login.getUserType(), expectedType)
                || tenant == null || tenant <= 0 || TenantContextHolder.isIgnore()
                || !Objects.equals(login.getTenantId(), tenant)
                || login.getVisitTenantId() != null && !Objects.equals(login.getVisitTenantId(), tenant)) {
            throw exception(FORBIDDEN);
        }
        return new KeFuScopeModels.Actor(login.getId(), expectedType);
    }
}
