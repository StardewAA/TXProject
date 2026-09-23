package cn.iocoder.txgy.module.promotion.dal.mysql.kefu;

import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.module.promotion.service.kefu.chat.KeFuChatModels.*;
import cn.iocoder.txgy.module.promotion.service.kefu.chat.KeFuChatCardService;
import cn.iocoder.txgy.module.promotion.service.kefu.scope.KeFuScopeModels.Actor;
import cn.iocoder.txgy.module.promotion.service.kefu.scope.KeFuScopeModels.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class KeFuChatRepository {
    private final JdbcTemplate jdbc;
    private static final RowMapper<Message> MESSAGE = (rs, row) -> new Message(rs.getLong("id"), rs.getString("session_id"),
            rs.getString("sender_role"), rs.getString("text_body"), rs.getLong("created_at"), rs.getString("client_message_id"),
            rs.getString("faq_id"), rs.getObject("faq_version") == null ? null : rs.getLong("faq_version"),
            rs.getString("card_json")==null?null:JsonUtils.parseObjectQuietly(rs.getString("card_json"),KeFuChatCardService.Card.class));

    public void ensureMeta(Session session) {
        jdbc.update("INSERT INTO promotion_kefu_chat_meta(tenant_id,session_id) VALUES(?,?) ON DUPLICATE KEY UPDATE session_id=session_id",
                session.tenantId(), session.id());
    }
    public boolean human(Session session) {
        var rows = jdbc.query("SELECT human_requested FROM promotion_kefu_chat_meta WHERE tenant_id=? AND session_id=?",
                (rs,row)->rs.getBoolean(1), session.tenantId(), session.id());
        return !rows.isEmpty() && rows.getFirst();
    }
    public void human(Session session, boolean value) {
        ensureMeta(session);
        jdbc.update("UPDATE promotion_kefu_chat_meta SET human_requested=?,update_time=CURRENT_TIMESTAMP WHERE tenant_id=? AND session_id=?",
                value, session.tenantId(), session.id());
    }
    public List<String> queueIds(long tenant,String scope,long shop,String state,int size,int offset) {
        return jdbc.query("SELECT s.id FROM promotion_kefu_session s JOIN promotion_kefu_chat_meta m ON m.tenant_id=s.tenant_id AND m.session_id=s.id "
                        + "WHERE s.tenant_id=? AND s.scope=? AND s.shop_id=? AND s.state=? AND m.human_requested=TRUE ORDER BY s.update_time DESC,s.id LIMIT ? OFFSET ?",
                (rs,row)->rs.getString(1),tenant,scope,shop,state,size,offset);
    }
    public Message retry(Session session,Actor actor,String key) {
        // The scope hint may already have established a REPEATABLE READ snapshot before waiting
        // on the session lock. Deduplication must see the previous sender's committed row NOW.
        var rows=jdbc.query("SELECT * FROM promotion_kefu_chat_message WHERE tenant_id=? AND session_id=? AND sender_type=? AND sender_id=? AND client_message_id=? FOR UPDATE",
                MESSAGE,session.tenantId(),session.id(),actor.type(),actor.id(),key);
        return rows.isEmpty()?null:rows.getFirst();
    }
    public long recentCount(Session session,Actor actor,long since) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM promotion_kefu_chat_message WHERE tenant_id=? AND session_id=? AND sender_type=? AND sender_id=? AND created_at>=?",
                Long.class,session.tenantId(),session.id(),actor.type(),actor.id(),since);
    }
    public long recentRoleCount(Session session,String role,long since) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM promotion_kefu_chat_message WHERE tenant_id=? AND session_id=? AND sender_role=? AND created_at>=?",
                Long.class,session.tenantId(),session.id(),role,since);
    }
    public Message insert(Session session,Actor actor,String role,String key,String text,String faqId,Long faqVersion,String faqScope,long now,KeFuChatCardService.Card card) {
        jdbc.update("INSERT INTO promotion_kefu_chat_message(tenant_id,session_id,sender_type,sender_id,sender_role,client_message_id,text_body,faq_id,faq_version,faq_scope,created_at,card_json) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                session.tenantId(),session.id(),actor.type(),actor.id(),role,key,text,faqId,faqVersion,faqScope,now,card==null?null:JsonUtils.toJsonString(card));
        jdbc.update("UPDATE promotion_kefu_session SET update_time=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=?",session.tenantId(),session.id());
        return retry(session,actor,key);
    }
    public List<Message> messages(Session session,long after,int limit) {
        return jdbc.query("SELECT * FROM promotion_kefu_chat_message WHERE tenant_id=? AND session_id=? AND id>? ORDER BY id LIMIT ?",
                MESSAGE,session.tenantId(),session.id(),after,limit);
    }
    public KeFuChatCardService.Card latestCard(Session session) {
        var rows = jdbc.query("SELECT card_json FROM promotion_kefu_chat_message WHERE tenant_id=? AND session_id=? "
                        + "AND card_json IS NOT NULL ORDER BY id DESC LIMIT 1",
                (rs,row)->JsonUtils.parseObjectQuietly(rs.getString(1), KeFuChatCardService.Card.class),
                session.tenantId(),session.id());
        return rows.isEmpty()?null:rows.getFirst();
    }
    public boolean hasMessage(Session session,long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM promotion_kefu_chat_message WHERE tenant_id=? AND session_id=? AND id=?",Long.class,
                session.tenantId(),session.id(),id)==1;
    }
    public long last(Session session) {
        return jdbc.queryForObject("SELECT COALESCE(MAX(id),0) FROM promotion_kefu_chat_message WHERE tenant_id=? AND session_id=?",Long.class,session.tenantId(),session.id());
    }
    public long read(Session session,Actor actor,boolean agent) {
        var rows=jdbc.query("SELECT message_id FROM promotion_kefu_chat_read WHERE tenant_id=? AND session_id=? AND actor_type=? AND actor_id=? AND viewer_role=?",
                (rs,row)->rs.getLong(1),session.tenantId(),session.id(),actor.type(),actor.id(),agent?"AGENT":"MEMBER");
        return rows.isEmpty()?0:rows.getFirst();
    }
    public void read(Session session,Actor actor,boolean agent,long id) {
        jdbc.update("INSERT INTO promotion_kefu_chat_read(tenant_id,session_id,actor_type,actor_id,viewer_role,message_id) VALUES(?,?,?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE message_id=GREATEST(message_id,VALUES(message_id))",
                session.tenantId(),session.id(),actor.type(),actor.id(),agent?"AGENT":"MEMBER",id);
    }
    public long unread(Session session,Actor actor,boolean agent,long read) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM promotion_kefu_chat_message WHERE tenant_id=? AND session_id=? AND id>? "
                        + "AND NOT(sender_type=? AND sender_id=? AND sender_role=?)",Long.class,
                session.tenantId(),session.id(),read,actor.type(),actor.id(),agent?"AGENT":"MEMBER");
    }
    public void heartbeat(long tenant,String scope,long shop,Actor actor,long expires) {
        jdbc.update("INSERT INTO promotion_kefu_chat_presence(tenant_id,scope,shop_id,actor_type,actor_id,expires_at) VALUES(?,?,?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE expires_at=VALUES(expires_at)",tenant,scope,shop,actor.type(),actor.id(),expires);
    }
    public long online(long tenant,String scope,long shop,long now,long buyer) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM promotion_kefu_chat_presence p JOIN promotion_kefu_agent a ON a.tenant_id=p.tenant_id AND a.scope=p.scope "
                        + "AND a.shop_id=p.shop_id AND a.actor_type=p.actor_type AND a.actor_id=p.actor_id "
                        + "WHERE p.tenant_id=? AND p.scope=? AND p.shop_id=? AND p.expires_at>? AND a.enabled=TRUE AND NOT(p.actor_type=1 AND p.actor_id=?)",
                Long.class,tenant,scope,shop,now,buyer);
    }
    public record FaqRow(long version,long publishedVersion,String draft,String published) {}
    public FaqRow faq(long tenant,String scope,long shop,boolean lock) {
        var rows=jdbc.query("SELECT version,published_version,draft_json,published_json FROM promotion_kefu_faq_config WHERE tenant_id=? AND scope=? AND shop_id=?"
                        +(lock?" FOR UPDATE":""),(rs,row)->new FaqRow(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4)),tenant,scope,shop);
        return rows.isEmpty()?new FaqRow(0,0,"[]","[]"):rows.getFirst();
    }
    public void ensureFaq(long tenant,String scope,long shop) {
        jdbc.update("INSERT INTO promotion_kefu_faq_config(tenant_id,scope,shop_id,draft_json,published_json) VALUES(?,?,?,'[]','[]') ON DUPLICATE KEY UPDATE shop_id=shop_id",tenant,scope,shop);
    }
    public void saveFaq(long tenant,String scope,long shop,long version,String json) {
        if(jdbc.update("UPDATE promotion_kefu_faq_config SET version=version+1,draft_json=?,update_time=CURRENT_TIMESTAMP WHERE tenant_id=? AND scope=? AND shop_id=? AND version=?",
                json,tenant,scope,shop,version)!=1)throw new IllegalStateException("Locked FAQ version changed");
    }
    public void publishFaq(long tenant,String scope,long shop,long version) {
        // Use an explicit publication version: MySQL evaluates SET assignments left-to-right, unlike H2.
        if(jdbc.update("UPDATE promotion_kefu_faq_config SET version=version+1,published_version=?,published_json=draft_json,update_time=CURRENT_TIMESTAMP "
                +"WHERE tenant_id=? AND scope=? AND shop_id=? AND version=?",version+1,tenant,scope,shop,version)!=1)throw new IllegalStateException("Locked FAQ version changed");
    }
    public List<FaqEntry> decode(String json) {
        // Never use parseArray here: its malformed-input logger can expose private draft FAQ text.
        FaqEntry[] value=JsonUtils.parseObjectQuietly(json,FaqEntry[].class);
        if(value==null)throw new IllegalStateException("FAQ configuration unavailable");
        return List.of(value);
    }
}
