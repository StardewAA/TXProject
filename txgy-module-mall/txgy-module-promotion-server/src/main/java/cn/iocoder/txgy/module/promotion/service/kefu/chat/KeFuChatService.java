package cn.iocoder.txgy.module.promotion.service.kefu.chat;

import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.module.promotion.dal.mysql.kefu.KeFuChatRepository;
import cn.iocoder.txgy.module.promotion.dal.mysql.kefu.KeFuScopeRepository;
import cn.iocoder.txgy.module.promotion.service.kefu.scope.KeFuScopeModels.*;
import cn.iocoder.txgy.module.promotion.service.kefu.scope.KeFuScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.time.Clock;
import java.util.*;
import java.util.regex.Pattern;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.promotion.service.kefu.scope.KeFuScopeService.*;
import static cn.iocoder.txgy.module.promotion.service.kefu.chat.KeFuChatModels.*;

/** Persisted text + published FAQ + scoped human reception. No AI calls, attachments, or legacy broadcasts. */
@Service
@RequiredArgsConstructor
// Per-conversation writes remain serialized by an explicit session row lock. READ COMMITTED
// avoids carrying a pre-lock scope-hint snapshot into dedup/handoff decisions and avoids
// unrelated new conversations taking intersecting gap locks for their absent retry keys.
@Transactional(rollbackFor=Exception.class, isolation=Isolation.READ_COMMITTED)
public class KeFuChatService {
    private final KeFuScopeService scopes;
    private final KeFuScopeRepository sessions;
    private final KeFuChatRepository repository;
    private final KeFuChatCardService cards;
    private Clock clock = Clock.systemUTC();
    private static final Actor SELF_SERVICE = new Actor(0,0);
    private static final long MAX_CURSOR = 9_007_199_254_740_991L;
    private static final Pattern CLIENT_ID = Pattern.compile("[a-zA-Z0-9_-]{8,64}");
    private static final Pattern FAQ_ID = Pattern.compile("[a-zA-Z0-9_-]{1,40}");

    public KeFuChatModels.Thread open(Actor member,String scope,Long shop) {
        // Determine reopening under the same unique session lock before resetting self-service state.
        Session session=scopes.open(member,scope,shop);
        repository.ensureMeta(session);
        // A new/reopened WAITING session with no assigned agent may retain human intent from an earlier batch.
        // This is deliberate: do not silently switch the customer back to automatic replies.
        return view(session,member,false);
    }
    public KeFuChatModels.Thread thread(Actor actor,String id,boolean agent) {
        return view(access(actor,id,agent,false),actor,agent);
    }
    public List<KeFuChatModels.Thread> workbench(Actor actor,String scope,Long shop,String state,int page,int size) {
        long tenant=scopes.chatScope(actor,scope,shop);
        if(!Set.of("WAITING","ACTIVE","CLOSED").contains(Objects.toString(state,"")) || page<1 || page>10000 || size<1 || size>100)
            throw exception(INVALID);
        List<KeFuChatModels.Thread> result=new ArrayList<>();
        for(String id:repository.queueIds(tenant,scope,shop,state,size,(page-1)*size)) {
            Session session=sessions.get(tenant,id,false);
            // Queue metadata only is visible across agents. Message APIs enforce assignment separately.
            result.add(view(session,actor,true));
        }
        return result;
    }
    public MessagePage messages(Actor actor,String id,boolean agent,Long after,int limit) {
        if(after==null || after<0 || after>MAX_CURSOR || limit<1 || limit>100)throw exception(INVALID);
        Session session=access(actor,id,agent,false);
        List<Message> rows=repository.messages(session,after,limit+1);
        boolean more=rows.size()>limit;
        List<Message> page=more?List.copyOf(rows.subList(0,limit)):rows;
        return new MessagePage(page,page.isEmpty()?after:page.getLast().id(),more);
    }
    public SendResult send(Actor actor,SendRequest request,boolean agent) {
        validateSend(request,agent);
        Session session=access(actor,request.sessionId(),agent,true);
        Message retry=repository.retry(session,actor,request.clientMessageId());
        if(retry!=null) {
            if(!samePayload(retry,request))throw exception(CONFLICT);
            List<Message> retried=new ArrayList<>();retried.add(retry);
            Message automatic=agent?null:repository.retry(session,SELF_SERVICE,"faq_"+request.clientMessageId());
            if(automatic!=null)retried.add(automatic);
            return new SendResult(List.copyOf(retried),view(session,actor,agent));
        }
        if(session.version()!=request.version() || "CLOSED".equals(session.state()))throw exception(CONFLICT);
        if(agent && (!"ACTIVE".equals(session.state()) || !assigned(session,actor)))throw exception(FORBIDDEN);
        if(repository.recentCount(session,actor,clock.millis()-60_000)>=60)
            throw new cn.iocoder.txgy.framework.common.exception.ServiceException(429,"发送过于频繁，请稍后再试");
        KeFuChatCardService.Card card=request.resourceType()==null?null:cards.build(session,actor,request.resourceType(),request.resourceId());
        // Resource cards are self-explanatory. Keeping their snapshot disclaimer in text_body
        // rendered the same sentence once as a chat bubble and again inside the card.
        String text=card==null?request.text().trim():"";
        Message message=repository.insert(session,actor,agent?"AGENT":"MEMBER",request.clientMessageId(),text,null,null,null,clock.millis(),card);
        List<Message> sent=new ArrayList<>();sent.add(message);
        // Under the same session row lock as claim/human/close. No delayed auto answer can race a takeover.
        if(!agent && card!=null) {
            sent.add(repository.insert(session,SELF_SERVICE,"FAQ","faq_"+request.clientMessageId(),
                    resourceAcknowledgement(card.type()),null,null,null,clock.millis(),null));
        } else if(!agent && "WAITING".equals(session.state()) && !repository.human(session)) {
            FaqMatch match=match(session,text);
            sent.add(repository.insert(session,SELF_SERVICE,match==null?"SYSTEM":"FAQ","faq_"+request.clientMessageId(),
                    match==null?"暂未找到匹配的常见问题。您可以转人工；无人值守时可留言，处理状态以本会话为准。":match.entry().answer(),
                    match==null?null:match.entry().id(),match==null?null:match.version(),match==null?null:match.scope(),clock.millis(),null));
        }
        return new SendResult(List.copyOf(sent),view(session,actor,agent));
    }

    private static String resourceAcknowledgement(String type) {
        return "ORDER".equals(type)
                ? "您好，已收到您发送的订单，请说明需要咨询的问题。"
                : "您好，已收到您发送的商品，请说明需要咨询的规格、价格或库存问题。";
    }
    public KeFuChatModels.Thread read(Actor actor,ReadRequest request,boolean agent) {
        if(request==null || request.messageId()==null || request.messageId()<0 || request.messageId()>MAX_CURSOR)throw exception(INVALID);
        Session session=access(actor,request.sessionId(),agent,true);
        if(request.messageId()>0 && !repository.hasMessage(session,request.messageId()))throw exception(FORBIDDEN);
        repository.read(session,actor,agent,request.messageId());
        return view(session,actor,agent);
    }
    public KeFuChatModels.Thread human(Actor member,ChangeRequest request) {
        if(request==null || request.version()==null || request.version()<0 || request.version()>=MAX_CURSOR)throw exception(INVALID);
        Session session=access(member,request.sessionId(),false,true);
        if(repository.human(session) && (session.version()==request.version() || session.version()-1==request.version()))return view(session,member,false);
        if(session.version()!=request.version() || !"WAITING".equals(session.state()))throw exception(CONFLICT);
        repository.human(session,true);
        sessions.transition(session,"WAITING",null);
        sessions.audit(session.tenantId(),session.scope(),session.shopId(),session.id(),"HANDOFF",member,null,session.version()+1,"会员请求人工接待，离线时保留留言");
        return view(sessions.get(session.tenantId(),session.id(),true),member,false);
    }
    public Heartbeat heartbeat(Actor actor,HeartbeatRequest request) {
        if(request==null || request.online()==null)throw exception(INVALID);
        long tenant=scopes.chatScope(actor,request.scope(),request.shopId());
        long expires=request.online()?clock.millis()+90_000:0;
        repository.heartbeat(tenant,request.scope(),request.shopId(),actor,expires);
        return new Heartbeat(request.online(),expires);
    }
    public FaqConfig faq(Actor actor,String scope,Long shop) {
        long tenant=scopes.faqScope(actor,scope,shop);return faqView(tenant,scope,shop,false);
    }
    public FaqConfig saveFaq(Actor actor,FaqSaveRequest request) {
        if(request==null || request.version()==null || request.version()<0 || request.version()>=MAX_CURSOR)throw exception(INVALID);
        validateEntries(request.entries());
        long tenant=scopes.faqScope(actor,request.scope(),request.shopId());
        repository.ensureFaq(tenant,request.scope(),request.shopId());
        var before=repository.faq(tenant,request.scope(),request.shopId(),true);
        if(before.version()!=request.version())throw exception(CONFLICT);
        repository.saveFaq(tenant,request.scope(),request.shopId(),request.version(),JsonUtils.toJsonString(request.entries()));
        sessions.audit(tenant,request.scope(),request.shopId(),null,"FAQ_DRAFT",actor,null,request.version()+1,"保存常见问题草稿");
        return faqView(tenant,request.scope(),request.shopId(),false);
    }
    public FaqConfig publishFaq(Actor actor,FaqPublishRequest request) {
        if(request==null || request.version()==null || request.version()<1 || request.version()>=MAX_CURSOR || request.reason()==null
                || request.reason().isBlank() || request.reason().length()>500)throw exception(INVALID);
        long tenant=scopes.faqScope(actor,request.scope(),request.shopId());
        var before=repository.faq(tenant,request.scope(),request.shopId(),true);
        if(before.version()!=request.version())throw exception(CONFLICT);
        validateEntries(repository.decode(before.draft()));
        repository.publishFaq(tenant,request.scope(),request.shopId(),request.version());
        sessions.audit(tenant,request.scope(),request.shopId(),null,"FAQ_PUBLISH",actor,null,request.version()+1,request.reason().trim());
        return faqView(tenant,request.scope(),request.shopId(),false);
    }

    private Session access(Actor actor,String id,boolean agent,boolean write) {
        Session session=scopes.chatAccess(actor,id,agent,write);
        if(agent) {
            if(actor.type()==1 && actor.id()==session.memberId())throw exception(FORBIDDEN);
            if("WAITING".equals(session.state())?!repository.human(session):!assigned(session,actor))throw exception(FORBIDDEN);
        }
        return session;
    }
    private static boolean assigned(Session session,Actor actor) {
        return Objects.equals(session.agentType(),actor.type()) && Objects.equals(session.agentId(),actor.id());
    }
    private KeFuChatModels.Thread view(Session session,Actor actor,boolean agent) {
        boolean human=repository.human(session);
        long online=repository.online(session.tenantId(),session.scope(),session.shopId(),clock.millis(),session.memberId());
        String mode="CLOSED".equals(session.state())?"CLOSED":"ACTIVE".equals(session.state())?"HUMAN":human?"WAITING_HUMAN":"SELF_SERVICE";
        String availability="CLOSED".equals(mode)?"本轮接待已结束，可重新发起咨询":online>0
                ?"有坐席近期在线，是否接待以实际领取状态为准":"当前暂无有效在线坐席，可留言；不承诺排队人数或回复时间";
        long read=repository.read(session,actor,agent);
        List<Question> questions="SELF_SERVICE".equals(mode)?published(session).stream().map(m->new Question(m.entry().id(),m.entry().question())).toList():List.of();
        return new KeFuChatModels.Thread(session,mode,human,online,availability,repository.last(session),read,
                repository.unread(session,actor,agent,read),questions,false);
    }
    private FaqConfig faqView(long tenant,String scope,long shop,boolean lock) {
        var row=repository.faq(tenant,scope,shop,lock);
        return new FaqConfig(scope,shop,row.version(),row.publishedVersion(),repository.decode(row.draft()),repository.decode(row.published()));
    }
    private record FaqMatch(String scope,long version,FaqEntry entry) {}
    private List<FaqMatch> published(Session session) {
        List<FaqMatch> result=new ArrayList<>();Set<String> used=new HashSet<>();
        List<String> sources="SHOP".equals(session.scope())?List.of("SHOP","PLATFORM"):List.of("PLATFORM");
        for(String scope:sources) {
            var config=repository.faq(session.tenantId(),scope,"SHOP".equals(scope)?session.shopId():0,false);
            if(config.publishedVersion()<=0)continue;
            for(FaqEntry item:repository.decode(config.published()))if(item.enabled()&&used.add(item.id()))result.add(new FaqMatch(scope,config.publishedVersion(),item));
        }
        return List.copyOf(result);
    }
    private FaqMatch match(Session session,String text) {
        String value=normalize(text);
        List<FaqMatch> matches=published(session);
        // Shop-specific policy wins over general platform policy; use specificity only inside the same scope.
        for(String scope:List.of("SHOP","PLATFORM")) {
            List<FaqMatch> scoped=matches.stream().filter(item->scope.equals(item.scope())).toList();
            for(FaqMatch item:scoped)if(normalize(item.entry().question()).equals(value))return item;
            FaqMatch winner=null;int length=0;
            for(FaqMatch item:scoped)for(String keyword:item.entry().keywords()) {
                String normalized=normalize(keyword);
                if(value.contains(normalized)&&normalized.length()>length){winner=item;length=normalized.length();}
            }
            if(winner!=null)return winner;
        }
        return null;
    }
    private static String normalize(String value) { return value.strip().toLowerCase(Locale.ROOT); }
    private static boolean samePayload(Message message,SendRequest request) {
        if(request.resourceType()!=null)return message.card()!=null && Objects.equals(message.card().type(),request.resourceType())
                && Objects.equals(message.card().resourceId(),request.resourceId());
        return message.card()==null && Objects.equals(message.text(),request.text().trim());
    }
    private static void validateSend(SendRequest r,boolean agent) {
        if(r==null || r.version()==null || r.version()<0 || r.version()>MAX_CURSOR || r.clientMessageId()==null || !CLIENT_ID.matcher(r.clientMessageId()).matches())throw exception(INVALID);
        boolean resource=r.resourceType()!=null || r.resourceId()!=null;
        if(resource) {
            if(agent || !Set.of("PRODUCT","ORDER").contains(Objects.toString(r.resourceType(),"")) || r.resourceId()==null || r.resourceId()<=0
                    || r.text()!=null && !r.text().isBlank())throw exception(INVALID);
        } else if(r.text()==null || r.text().isBlank() || r.text().length()>2000 || r.text().indexOf('\0')>=0)throw exception(INVALID);
    }
    private static void validateEntries(List<FaqEntry> entries) {
        if(entries==null || entries.size()>30)throw exception(INVALID);
        Set<String> ids=new HashSet<>();
        for(FaqEntry entry:entries) {
            if(entry==null || entry.id()==null || !FAQ_ID.matcher(entry.id()).matches() || !ids.add(entry.id())
                    || entry.question()==null || entry.question().isBlank() || entry.question().length()>100
                    || entry.answer()==null || entry.answer().isBlank() || entry.answer().length()>1000
                    || entry.question().indexOf('\0')>=0 || entry.answer().indexOf('\0')>=0
                    || entry.keywords()==null || entry.keywords().size()>8)throw exception(INVALID);
            for(String keyword:entry.keywords())if(keyword==null || keyword.strip().length()<2 || keyword.length()>30 || keyword.indexOf('\0')>=0)throw exception(INVALID);
        }
    }
}
