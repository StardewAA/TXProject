package cn.iocoder.txgy.module.infra.service.worklog;
import java.util.Objects;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.exception.enums.GlobalErrorCodeConstants.*;
public final class WorkLogPolicy {
    private WorkLogPolicy() { }
    public static Long actor(String scope, Long loginId, Long requestedId, boolean canViewAll) {
        if (loginId == null) throw exception(UNAUTHORIZED);
        if (scope == null || "SELF".equals(scope)) return loginId;
        if (!"ALL".equals(scope)) throw exception(BAD_REQUEST);
        if (!canViewAll) throw exception(FORBIDDEN);
        return requestedId;
    }
    public static void checkDetail(Long tenant, Long rowTenant, Long actor, Long rowActor) {
        // Return identical denial for other members and other tenants.
        if (tenant == null || !Objects.equals(tenant, rowTenant)
                || (actor != null && !Objects.equals(actor, rowActor))) throw exception(FORBIDDEN);
    }
}
