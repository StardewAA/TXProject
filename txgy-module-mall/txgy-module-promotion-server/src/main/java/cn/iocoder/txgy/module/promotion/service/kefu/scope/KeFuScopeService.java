package cn.iocoder.txgy.module.promotion.service.kefu.scope;

import cn.iocoder.txgy.framework.common.exception.ErrorCode;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.member.api.user.MemberUserApi;
import cn.iocoder.txgy.module.product.api.shop.ProductShopApi;
import cn.iocoder.txgy.module.promotion.dal.mysql.kefu.KeFuScopeRepository;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.promotion.service.kefu.scope.KeFuScopeModels.*;

/** Shared scope/assignment boundary. New chat code reuses these locks; legacy WebSocket remains separate. */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class KeFuScopeService {
    public static final ErrorCode DISABLED = new ErrorCode(1013016000, "店铺客服接待功能尚未开放");
    public static final ErrorCode INVALID = new ErrorCode(1013016001, "客服范围或请求参数不正确");
    public static final ErrorCode FORBIDDEN = new ErrorCode(1013016002, "无权访问该客服会话或接待范围");
    public static final ErrorCode CONFLICT = new ErrorCode(1013016003, "会话状态已变化，请刷新后重试");
    private final KeFuScopeRepository repository;
    private final ProductShopApi shopApi;
    private final MemberUserApi memberApi;
    private final AdminUserApi adminApi;

    @Value("${mall.customer-service.scoped-enabled:false}")
    private boolean enabled;

    public Session open(Actor member, String scope, Long shop) {
        long tenant = context(member);
        if (member.type() != 1) throw exception(FORBIDDEN);
        scope(scope, shop);
        activeActor(member);
        activeShop(scope, shop);
        return openAtTenant(tenant, member, scope, shop);
    }

    /**
     * Opens a shop conversation whose target tenant was resolved from a public SKU by the server.
     * The current tenant remains the member's account tenant; shop data, queue and grants stay in
     * the target tenant.  No request-provided tenant id is accepted.
     */
    public Session openPlatform(Actor member, long targetTenant, long shop, long productSkuId) {
        long memberTenant = context(member);
        if (member.type() != 1 || targetTenant <= 0 || shop <= 0 || productSkuId <= 0) {
            throw exception(INVALID);
        }
        activeActor(member);
        return inTenant(targetTenant, () -> {
            activeShop("SHOP", shop);
            Session session = openAtTenant(targetTenant, member, "SHOP", shop);
            if (!repository.bindPlatformOwner(session, memberTenant, member.id(), productSkuId)) {
                // A numerically colliding legacy member from another account tenant must never
                // inherit an existing platform session.
                throw exception(FORBIDDEN);
            }
            return session;
        });
    }

    private Session openAtTenant(long tenant, Actor member, String scope, long shop) {
        Session session = repository.open(tenant, scope, shop, member.id());
        if ("CLOSED".equals(session.state())) {
            repository.transition(session, "WAITING", null);
            repository.audit(tenant, scope, shop, session.id(), "REOPEN", member, null,
                    session.version() + 1, "会员重新发起接待");
            session = repository.get(tenant, session.id(), true);
        }
        return session;
    }

    public Session own(Actor member, String id) {
        long tenant = context(member);
        if (member.type() != 1) throw exception(FORBIDDEN);
        activeActor(member);
        Session session = required(tenant, id, false);
        if (session.memberId() != member.id()) throw exception(FORBIDDEN);
        return session;
    }

    public List<Session> list(Actor actor, String scope, Long shop, String state, int page, int size) {
        long tenant = context(actor);
        scope(scope, shop);
        if (!List.of("WAITING", "ACTIVE", "CLOSED").contains(state == null ? "" : state)) throw exception(INVALID);
        int offset = offset(page, size);
        authorize(tenant, scope, shop, actor);
        return repository.list(tenant, scope, shop, state, size, offset);
    }

    public Session claim(Actor actor, String id, Long version) {
        return change(actor, id, version, false);
    }

    public Session close(Actor actor, String id, Long version) {
        return change(actor, id, version, true);
    }

    private Session change(Actor actor, String id, Long version, boolean close) {
        long tenant = context(actor);
        if (version == null || version < 0) throw exception(INVALID);
        Session hint = required(tenant, id, false);
        // All operations lock the grant BEFORE the session. Revocation uses exactly the same order.
        authorize(tenant, hint.scope(), hint.shopId(), actor);
        Session session = required(tenant, id, true);
        // Older scoped claim routes must not bypass a buyer's explicit handoff from self-service.
        if (!close && !repository.humanRequested(session)) throw exception(FORBIDDEN);
        String desired = close ? "CLOSED" : "ACTIVE";
        boolean owns = Objects.equals(session.agentType(), actor.type()) && Objects.equals(session.agentId(), actor.id());
        // Exact retry of the previous transition only; an old request cannot close a reopened conversation.
        if (desired.equals(session.state()) && owns && session.version() - 1 == version) return session;
        if (session.version() != version || (close ? !"ACTIVE".equals(session.state()) || !owns
                : !"WAITING".equals(session.state()))) throw exception(CONFLICT);
        // A member agent must not act as both buyer and customer-service agent in their own session.
        if (actor.type() == 1 && actor.id() == session.memberId()) throw exception(FORBIDDEN);
        repository.transition(session, desired, actor);
        repository.audit(tenant, session.scope(), session.shopId(), id, close ? "CLOSE" : "CLAIM",
                actor, null, session.version() + 1, close ? "坐席结束接待" : "坐席领取接待");
        return required(tenant, id, true);
    }

    /** Controller requires the dedicated grant permission; no supplier role or old kefu permission implies it. */
    public void grant(Actor operator, GrantRequest request) {
        long tenant = context(operator);
        if (operator.type() != 2) throw exception(FORBIDDEN);
        if (request == null || request.actorType() == null || request.actorId() == null
                || request.enabled() == null || request.reason() == null || request.reason().isBlank()
                || request.version() == null || request.version() < 0
                || request.reason().length() > 500) throw exception(INVALID);
        scope(request.scope(), request.shopId());
        Actor target = new Actor(request.actorId(), request.actorType());
        validActor(target);
        activeActor(operator);
        if (request.enabled()) {
            activeActor(target);
            activeShop(request.scope(), request.shopId());
        } // Revocation must remain possible for a disabled/deleted shop/account.
        repository.ensureAgent(tenant, request.scope(), request.shopId(), target);
        Agent grant = repository.agent(tenant, request.scope(), request.shopId(), target);
        if (grant.enabled() == request.enabled() && grant.version() - 1 == request.version()) return;
        if (grant.version() != request.version()) throw exception(CONFLICT);
        if (grant.enabled() == request.enabled()) return;
        repository.setAgent(tenant, grant, request.enabled());
        repository.audit(tenant, grant.scope(), grant.shopId(), null, request.enabled() ? "GRANT" : "REVOKE",
                operator, target, grant.version() + 1, request.reason().trim());
        if (!request.enabled()) {
            for (Session session : repository.assigned(tenant, grant.scope(), grant.shopId(), target)) {
                repository.transition(session, "WAITING", null);
                repository.audit(tenant, grant.scope(), grant.shopId(), session.id(), "REQUEUE", operator,
                        target, session.version() + 1, "坐席授权撤回，退回待接待");
            }
        }
    }

    public List<Agent> agents(Actor operator, String scope, Long shop, int page, int size) {
        long tenant = context(operator);
        if (operator.type() != 2) throw exception(FORBIDDEN);
        scope(scope, shop);
        activeActor(operator);
        return repository.agents(tenant, scope, shop, size, offset(page, size));
    }

    /** Returns only the signed-in agent's enabled grants, for safe workbench scope selection. */
    public List<Agent> myScopes(Actor actor) {
        long tenant = context(actor);
        activeActor(actor);
        return repository.enabledScopes(tenant, actor);
    }

    /** Scope grant is locked before a session, in the same transaction as chat reads/writes. */
    public Session chatAccess(Actor actor, String id, boolean asAgent, boolean write) {
        long tenant = context(actor);
        if (asAgent) {
            Session hint = required(tenant, id, false);
            authorize(tenant, hint.scope(), hint.shopId(), actor);
            // Agent reads must keep assignment stable until the private projection is fetched.
            // Otherwise another agent can claim WAITING after authorization, and an RC message
            // query can expose that new owner's later reply. Preserve grant -> session order.
            return required(tenant, id, write || asAgent);
        }
        if (actor.type() != 1) throw exception(FORBIDDEN);
        validSessionId(id);
        activeActor(actor);
        Session platformSession = repository.getPlatformOwned(tenant, actor.id(), id, write);
        if (platformSession != null) {
            if (platformSession.memberId() != actor.id()) throw exception(FORBIDDEN);
            if (write) inTenant(platformSession.tenantId(), () -> {
                activeShop(platformSession.scope(), platformSession.shopId());
                return null;
            });
            return platformSession;
        }
        Session session = repository.get(tenant, id, write);
        // A target-tenant account with a colliding numeric member id cannot claim a platform
        // member's conversation through the legacy tenant-local path.
        if (session == null || repository.hasPlatformOwner(id) || session.memberId() != actor.id()) {
            throw exception(FORBIDDEN);
        }
        if (write) activeShop(session.scope(), session.shopId());
        return session;
    }

    public long chatScope(Actor actor, String scope, Long shop) {
        long tenant = context(actor); scope(scope, shop); authorize(tenant, scope, shop, actor); return tenant;
    }

    /** The caller must additionally hold the independent FAQ publish permission, not a receiving grant. */
    public long faqScope(Actor actor, String scope, Long shop) {
        long tenant = context(actor);
        if (actor.type() != 2) throw exception(FORBIDDEN);
        scope(scope, shop); activeActor(actor); activeShop(scope, shop); return tenant;
    }

    private long context(Actor actor) {
        if (!enabled) throw exception(DISABLED);
        validActor(actor);
        Long tenant = TenantContextHolder.getTenantId();
        if (tenant == null || tenant <= 0 || TenantContextHolder.isIgnore()) throw exception(FORBIDDEN);
        return tenant;
    }

    private void validActor(Actor actor) {
        if (actor == null || actor.id() <= 0 || (actor.type() != 1 && actor.type() != 2)) throw exception(FORBIDDEN);
    }

    private void scope(String scope, Long shop) {
        if (shop == null || !("PLATFORM".equals(scope) && shop == 0 || "SHOP".equals(scope) && shop > 0)) {
            throw exception(INVALID);
        }
    }

    private void activeActor(Actor actor) {
        Integer status;
        if (actor.type() == 1) {
            var member = memberApi.getUser(actor.id()).getCheckedData();
            status = member == null ? null : member.getStatus();
        } else {
            var admin = adminApi.getUser(actor.id()).getCheckedData();
            status = admin == null ? null : admin.getStatus();
        }
        if (!Integer.valueOf(0).equals(status)) throw exception(FORBIDDEN);
    }

    private void activeShop(String scope, long shop) {
        if (!"SHOP".equals(scope)) return;
        var value = shopApi.getShop(shop).getCheckedData();
        if (value == null || !Objects.equals(value.getId(), shop) || !Integer.valueOf(0).equals(value.getStatus())) {
            throw exception(FORBIDDEN);
        }
    }

    private void authorize(long tenant, String scope, long shop, Actor actor) {
        activeActor(actor);
        activeShop(scope, shop);
        Agent grant = repository.agent(tenant, scope, shop, actor);
        if (grant == null || !grant.enabled()) throw exception(FORBIDDEN);
    }

    private Session required(long tenant, String id, boolean lock) {
        validSessionId(id);
        Session session = repository.get(tenant, id, lock);
        if (session == null) throw exception(FORBIDDEN);
        return session;
    }

    private void validSessionId(String id) {
        if (id == null || id.isBlank() || id.length() > 36) throw exception(INVALID);
    }

    private <T> T inTenant(long tenant, Supplier<T> action) {
        Long previousTenant = TenantContextHolder.getTenantId();
        Boolean previousIgnore = TenantContextHolder.isIgnore();
        SecurityContext previousSecurity = SecurityContextHolder.getContext();
        try {
            TenantContextHolder.setTenantId(tenant);
            TenantContextHolder.setIgnore(false);
            // RPC tenant headers must never be paired with the member's account-tenant principal.
            // Internal /rpc-api endpoints are service-trusted; send only the server-resolved target
            // tenant and restore the caller identity immediately after the bounded RPC operation.
            SecurityContextHolder.clearContext();
            return action.get();
        } finally {
            TenantContextHolder.setTenantId(previousTenant);
            TenantContextHolder.setIgnore(previousIgnore);
            SecurityContextHolder.setContext(previousSecurity);
        }
    }

    private int offset(int page, int size) {
        if (page < 1 || page > 10000 || size < 1 || size > 100) throw exception(INVALID);
        return (page - 1) * size;
    }
}
