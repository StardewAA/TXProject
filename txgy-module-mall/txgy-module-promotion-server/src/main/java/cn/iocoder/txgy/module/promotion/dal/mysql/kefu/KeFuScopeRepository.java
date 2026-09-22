package cn.iocoder.txgy.module.promotion.dal.mysql.kefu;

import cn.iocoder.txgy.module.promotion.service.kefu.scope.KeFuScopeModels.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Explicit tenant predicates on EVERY read/write; never depends on an ignored-table setting. */
@Repository
@RequiredArgsConstructor
public class KeFuScopeRepository {
    private final JdbcTemplate jdbc;

    private static final RowMapper<Session> SESSION = (rs, row) -> new Session(
            rs.getString("id"), rs.getLong("tenant_id"), rs.getString("scope"), rs.getLong("shop_id"),
            rs.getLong("member_id"), rs.getString("state"), (Integer) rs.getObject("agent_type"),
            (Long) rs.getObject("agent_id"), rs.getLong("version"),
            rs.getTimestamp("create_time").toLocalDateTime(), rs.getTimestamp("update_time").toLocalDateTime());
    private static final RowMapper<Agent> AGENT = (rs, row) -> new Agent(rs.getString("scope"),
            rs.getLong("shop_id"), rs.getInt("actor_type"), rs.getLong("actor_id"),
            rs.getBoolean("enabled"), rs.getLong("version"));

    public Session open(long tenant, String scope, long shop, long member) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO promotion_kefu_session(id,tenant_id,scope,shop_id,member_id) "
                        + "VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id",
                id, tenant, scope, shop, member);
        Session session = jdbc.query("SELECT * FROM promotion_kefu_session WHERE tenant_id=? AND scope=? "
                + "AND shop_id=? AND member_id=? FOR UPDATE", SESSION, tenant, scope, shop, member).getFirst();
        if (id.equals(session.id())) {
            audit(tenant, scope, shop, id, "OPEN", new Actor(member, 1), null, 0, "会员发起接待");
        }
        return session;
    }

    public Session get(long tenant, String id, boolean lock) {
        List<Session> rows = jdbc.query("SELECT * FROM promotion_kefu_session WHERE tenant_id=? AND id=?"
                + (lock ? " FOR UPDATE" : ""), SESSION, tenant, id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /**
     * Looks up only a platform conversation owned by the authenticated member account.
     *
     * <p>The target tenant is deliberately not an input.  It is recovered from the server-created
     * binding and joined back to the session tenant, so changing a request header or guessing a
     * session UUID cannot move a conversation into another shop scope.</p>
     */
    public Session getPlatformOwned(long memberTenant, long member, String id, boolean lock) {
        List<Session> rows = jdbc.query("SELECT s.* FROM promotion_kefu_platform_session p "
                        + "JOIN promotion_kefu_session s ON s.id=p.session_id AND s.tenant_id=p.session_tenant_id "
                        + "WHERE p.member_tenant_id=? AND p.member_id=? AND p.session_id=?"
                        + (lock ? " FOR UPDATE" : ""), SESSION, memberTenant, member, id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public boolean hasPlatformOwner(String id) {
        List<Boolean> rows = jdbc.query("SELECT TRUE FROM promotion_kefu_platform_session WHERE session_id=?",
                (rs, row) -> rs.getBoolean(1), id);
        return !rows.isEmpty();
    }

    /** Binds a target-tenant session to one authenticated platform member account, once. */
    public boolean bindPlatformOwner(Session session, long memberTenant, long member, long productSkuId) {
        jdbc.update("INSERT INTO promotion_kefu_platform_session(session_id,session_tenant_id,member_tenant_id,member_id,product_sku_id) "
                        + "VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE session_id=session_id",
                session.id(), session.tenantId(), memberTenant, member, productSkuId);
        Integer matches = jdbc.queryForObject("SELECT COUNT(*) FROM promotion_kefu_platform_session "
                        + "WHERE session_id=? AND session_tenant_id=? AND member_tenant_id=? AND member_id=?",
                Integer.class, session.id(), session.tenantId(), memberTenant, member);
        return Objects.equals(matches, 1);
    }

    public List<Session> list(long tenant, String scope, long shop, String state, int limit, int offset) {
        return jdbc.query("SELECT s.* FROM promotion_kefu_session s WHERE s.tenant_id=? AND s.scope=? AND s.shop_id=? "
                + "AND s.state=? AND EXISTS(SELECT 1 FROM promotion_kefu_chat_meta m WHERE m.tenant_id=s.tenant_id AND m.session_id=s.id AND m.human_requested=TRUE) "
                + "ORDER BY s.update_time DESC,s.id LIMIT ? OFFSET ?", SESSION,
                tenant, scope, shop, state, limit, offset);
    }

    public boolean humanRequested(Session session) {
        // Direct scope endpoints may retain a pre-lock RR snapshot from their session hint.
        // Under grant -> session -> meta locks, observe the handoff that committed before the claim.
        List<Boolean> rows = jdbc.query("SELECT human_requested FROM promotion_kefu_chat_meta "
                        + "WHERE tenant_id=? AND session_id=? FOR UPDATE",
                (rs, row) -> rs.getBoolean(1), session.tenantId(), session.id());
        return !rows.isEmpty() && rows.getFirst();
    }

    public void transition(Session session, String state, Actor agent) {
        int changed = jdbc.update("UPDATE promotion_kefu_session SET state=?,agent_type=?,agent_id=?,"
                        + "version=version+1,update_time=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=? AND version=?",
                state, agent == null ? null : agent.type(), agent == null ? null : agent.id(),
                session.tenantId(), session.id(), session.version());
        if (changed != 1) {
            throw new IllegalStateException("Locked customer-service session changed unexpectedly");
        }
    }

    public void ensureAgent(long tenant, String scope, long shop, Actor actor) {
        jdbc.update("INSERT INTO promotion_kefu_agent(tenant_id,scope,shop_id,actor_type,actor_id) VALUES(?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE actor_id=actor_id", tenant, scope, shop, actor.type(), actor.id());
    }

    public Agent agent(long tenant, String scope, long shop, Actor actor) {
        List<Agent> rows = jdbc.query("SELECT * FROM promotion_kefu_agent WHERE tenant_id=? AND scope=? "
                + "AND shop_id=? AND actor_type=? AND actor_id=? FOR UPDATE", AGENT,
                tenant, scope, shop, actor.type(), actor.id());
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<Agent> agents(long tenant, String scope, long shop, int limit, int offset) {
        return jdbc.query("SELECT * FROM promotion_kefu_agent WHERE tenant_id=? AND scope=? AND shop_id=? "
                + "ORDER BY actor_type,actor_id LIMIT ? OFFSET ?", AGENT, tenant, scope, shop, limit, offset);
    }

    public void setAgent(long tenant, Agent agent, boolean enabled) {
        jdbc.update("UPDATE promotion_kefu_agent SET enabled=?,version=version+1,update_time=CURRENT_TIMESTAMP "
                        + "WHERE tenant_id=? AND scope=? AND shop_id=? AND actor_type=? AND actor_id=?",
                enabled, tenant, agent.scope(), agent.shopId(), agent.actorType(), agent.actorId());
    }

    public List<Session> assigned(long tenant, String scope, long shop, Actor actor) {
        return jdbc.query("SELECT * FROM promotion_kefu_session WHERE tenant_id=? AND scope=? AND shop_id=? "
                + "AND state='ACTIVE' AND agent_type=? AND agent_id=? ORDER BY id FOR UPDATE",
                SESSION, tenant, scope, shop, actor.type(), actor.id());
    }

    public void audit(long tenant, String scope, long shop, String session, String event,
                      Actor actor, Actor target, long version, String reason) {
        jdbc.update("INSERT INTO promotion_kefu_scope_audit(id,tenant_id,scope,shop_id,session_id,event_type,"
                        + "actor_type,actor_id,target_type,target_id,version,reason) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), tenant, scope, shop, session, event, actor.type(), actor.id(),
                target == null ? null : target.type(), target == null ? null : target.id(), version, reason);
    }
}
