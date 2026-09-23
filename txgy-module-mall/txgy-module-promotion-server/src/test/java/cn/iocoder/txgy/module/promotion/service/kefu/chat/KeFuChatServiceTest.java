package cn.iocoder.txgy.module.promotion.service.kefu.chat;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.member.api.user.MemberUserApi;
import cn.iocoder.txgy.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.txgy.module.product.api.shop.ProductShopApi;
import cn.iocoder.txgy.module.product.api.shop.dto.ProductShopRespDTO;
import cn.iocoder.txgy.module.product.api.sku.ProductPublicSkuApi;
import cn.iocoder.txgy.module.product.api.sku.dto.ProductPublicSkuRespDTO;
import cn.iocoder.txgy.module.promotion.dal.mysql.kefu.*;
import cn.iocoder.txgy.module.promotion.service.kefu.scope.*;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static cn.iocoder.txgy.module.promotion.service.kefu.chat.KeFuChatModels.*;
import static cn.iocoder.txgy.module.promotion.service.kefu.scope.KeFuScopeModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** SQL, real transactional proxies, scope grants and scoped text/FAQ loop; all identities are isolated fakes. */
class KeFuChatServiceTest {
    JdbcTemplate jdbc;KeFuScopeRepository sessions;KeFuChatRepository repository;
    KeFuScopeService scopeTarget,scopes;KeFuChatService target,service;KeFuChatCardService cards;
    MemberUserApi members;AdminUserApi admins;ProductShopApi shops;KeFuAiReplyService aiReplies;
    ProductPublicSkuApi publicSkus;
    final Actor buyer=new Actor(11,1),agent=new Actor(22,1),other=new Actor(33,1),admin=new Actor(22,2),operator=new Actor(99,2);
    long now=1_800_000_000_000L;
    @BeforeEach void setup() {
        var source=new DriverManagerDataSource();
        String mysql=System.getenv("MALL_KEFU_CHAT_TEST_JDBC");
        if(mysql!=null){
            assertTrue(mysql.matches("jdbc:mysql://127\\.0\\.0\\.1:3306/mall_kefu_chat_test_20260917(?:\\?.*)?"));
            source.setUrl(mysql);source.setUsername(System.getenv("MALL_KEFU_CHAT_TEST_USER"));source.setPassword(System.getenv("MALL_KEFU_CHAT_TEST_PASSWORD"));
        }else source.setUrl("jdbc:h2:mem:kefu-chat-"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        jdbc=new JdbcTemplate(source);Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while(root!=null&&!Files.exists(root.resolve("deploy/mysql/repair/2026-09-17-promotion-kefu-scope.sql")))root=root.getParent();assertNotNull(root);
        new ResourceDatabasePopulator(new FileSystemResource(root.resolve("deploy/mysql/repair/2026-09-17-promotion-kefu-scope.sql")),
                new FileSystemResource(root.resolve("deploy/mysql/repair/2026-09-17-promotion-kefu-chat.sql"))).execute(source);
        clearTables();sessions=spy(new KeFuScopeRepository(jdbc));repository=spy(new KeFuChatRepository(jdbc));
        members=mock(MemberUserApi.class);admins=mock(AdminUserApi.class);shops=mock(ProductShopApi.class);cards=mock(KeFuChatCardService.class);aiReplies=mock(KeFuAiReplyService.class);publicSkus=mock(ProductPublicSkuApi.class);
        when(members.getUser(anyLong())).thenAnswer(c->CommonResult.success(new MemberUserRespDTO().setId(c.getArgument(0)).setStatus(0)));
        when(admins.getUser(anyLong())).thenAnswer(c->CommonResult.success(new AdminUserRespDTO().setId(c.getArgument(0)).setStatus(0)));
        when(shops.getShop(anyLong())).thenAnswer(c->{long id=c.getArgument(0);return CommonResult.success((TenantContextHolder.getTenantId()==1L?(id==101||id==102):id==201)?new ProductShopRespDTO().setId(id).setStatus(0):null);});
        var manager=new DataSourceTransactionManager(source);
        scopeTarget=new KeFuScopeService(sessions,shops,members,admins);ReflectionTestUtils.setField(scopeTarget,"enabled",true);scopes=proxy(scopeTarget,manager);
        target=new KeFuChatService(scopes,sessions,repository,cards,aiReplies,publicSkus);clock(now);service=proxy(target,manager);TenantContextHolder.setTenantId(1L);
    }
    @SuppressWarnings("unchecked") static <T>T proxy(T value,DataSourceTransactionManager manager){var p=new ProxyFactory(value);p.setProxyTargetClass(true);p.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));return(T)p.getProxy();}
    void clock(long time){ReflectionTestUtils.setField(target,"clock",Clock.fixed(Instant.ofEpochMilli(time),ZoneOffset.UTC));}
    @AfterEach void cleanup(){TenantContextHolder.clear();SecurityContextHolder.clearContext();clearTables();}
    void clearTables(){for(String table:List.of("promotion_kefu_chat_message","promotion_kefu_chat_read","promotion_kefu_chat_meta","promotion_kefu_chat_presence","promotion_kefu_faq_config","promotion_kefu_scope_audit","promotion_kefu_platform_session","promotion_kefu_session","promotion_kefu_agent"))jdbc.update("DELETE FROM "+table);}
    KeFuChatModels.Thread open(){return service.open(buyer,"SHOP",101L);}
    void grant(Actor actor){scopes.grant(operator,new GrantRequest("SHOP",101L,actor.type(),actor.id(),true,0L,"隔离测试"));}
    SendRequest text(Session s,String key,String value){return new SendRequest(s.id(),s.version(),key,value,null,null);}
    FaqEntry entry(String id,String answer,String...keywords){return new FaqEntry(id,"如何退货",List.of(keywords),answer,true);}
    FaqConfig publish(String scope,long shop,List<FaqEntry> entries){var saved=service.saveFaq(operator,new FaqSaveRequest(scope,shop,0L,entries));return service.publishFaq(operator,new FaqPublishRequest(scope,shop,saved.version(),"核验后发布"));}
    Session human(){var s=open().session();return service.human(buyer,new ChangeRequest(s.id(),s.version())).session();}
    Session active(){grant(agent);var s=human();return scopes.claim(agent,s.id(),s.version());}
    long count(){return jdbc.queryForObject("SELECT COUNT(*) FROM promotion_kefu_chat_message",Long.class);}

    @Test void capabilityDisabledByDefaultWithoutDatabaseOrIdentity(){var result=new KeFuChatCapability().get();assertFalse(result.enabled());assertFalse(result.attachmentsEnabled());assertEquals("FAQ_HUMAN",result.mode());}
    @Test void disabledWritesStopBeforeAnyPersistence(){ReflectionTestUtils.setField(scopeTarget,"enabled",false);assertThrows(ServiceException.class,this::open);assertEquals(0,count());verifyNoInteractions(cards);}
    @Test void openIsIdempotentAndDoesNotClaimHumanOnline(){var a=open();var b=open();assertEquals(a.session().id(),b.session().id());assertEquals("SELF_SERVICE",a.mode());assertEquals(0,a.onlineAgents());assertTrue(a.availabilityMessage().contains("留言"));assertTrue(a.faq().isEmpty());assertEquals(0,count());}
    @Test void platformProductOwnershipCreatesTargetTenantThreadWithoutTrustingClientShop(){
        var ownership=new ProductPublicSkuRespDTO().setTenantId(2L).setShopId(201L).setSkuId(9001L).setSaleable(true);
        when(publicSkus.getOwnershipList(List.of(9001L))).thenReturn(List.of(ownership));
        var thread=service.openProduct(buyer,"SHOP",201L,9001L);
        assertEquals(2L,thread.session().tenantId());assertEquals(201L,thread.session().shopId());assertEquals(1L,TenantContextHolder.getTenantId());
        assertEquals(thread.session(),service.thread(buyer,thread.session().id(),false).session());
        assertThrows(ServiceException.class,()->service.thread(other,thread.session().id(),false));
        assertThrows(ServiceException.class,()->service.openProduct(buyer,"SHOP",101L,9001L));
        ownership.setSaleable(false);
        assertThrows(ServiceException.class,()->service.openProduct(buyer,"SHOP",201L,9001L));
    }
    @Test void targetTenantCardRpcUsesScopedPrincipalAndRestoresItAfterFailure(){
        var login=new LoginUser().setId(11L).setUserType(1).setTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(login,null,List.of()));
        var originalAuthentication=SecurityContextHolder.getContext().getAuthentication();
        var ownership=new ProductPublicSkuRespDTO().setTenantId(2L).setShopId(201L).setSkuId(9001L).setSaleable(true);
        when(publicSkus.getOwnershipList(List.of(9001L))).thenReturn(List.of(ownership));
        var session=service.openProduct(buyer,"SHOP",201L,9001L).session();
        when(cards.build(session,buyer,"PRODUCT",42L)).thenAnswer(call->{
            assertEquals(2L,TenantContextHolder.getTenantId());
            assertEquals(11L,SecurityFrameworkUtils.getLoginUserId());
            assertEquals(2L,SecurityFrameworkUtils.getLoginUser().getTenantId());
            assertNull(SecurityFrameworkUtils.getLoginUser().getVisitTenantId());
            assertNotSame(login,SecurityFrameworkUtils.getLoginUser());
            throw new IllegalStateException("product card unavailable");
        });

        assertThrows(IllegalStateException.class,()->service.send(buyer,
                new SendRequest(session.id(),session.version(),"request01",null,"PRODUCT",42L),false));

        assertEquals(1L,TenantContextHolder.getTenantId());
        assertSame(originalAuthentication,SecurityContextHolder.getContext().getAuthentication());
        assertEquals(11L,SecurityFrameworkUtils.getLoginUserId());
        assertEquals(0,count());
    }
    @Test void draftFaqNeverAnswersUntilPublished(){service.saveFaq(operator,new FaqSaveRequest("SHOP",101L,0L,List.of(entry("return","秘密草稿","退货"))));var s=open().session();var result=service.send(buyer,text(s,"request01","退货怎么办"),false);assertEquals("SYSTEM",result.messages().getLast().senderRole());assertFalse(result.messages().getLast().text().contains("秘密"));}
    @Test void publishedFaqAnswersWithVersionAndExplicitRole(){var config=publish("SHOP",101,List.of(entry("return","请先在本人订单申请售后","退货")));var t=open();assertEquals(1,t.faq().size());var result=service.send(buyer,text(t.session(),"request01","请问如何退货"),false);assertEquals(2,result.messages().size());assertEquals("FAQ",result.messages().getLast().senderRole());assertEquals(config.publishedVersion(),result.messages().getLast().faqVersion());assertEquals("return",result.messages().getLast().faqId());}
    @Test void unmatchedQuestionUsesAiWithoutChangingPublishedFaqPriority(){when(aiReplies.reply(eq("SHOP"),eq(101L),eq(11L),anyString(),eq("这个材料耐温多少"),anyString())).thenReturn(new KeFuAiReplyService.Answer("请以商品参数页标注的耐温范围为准，需要选型时可转人工。",List.of()));var s=open().session();var result=service.send(buyer,text(s,"request01","这个材料耐温多少"),false);assertEquals("AI",result.messages().getLast().senderRole());assertTrue(result.messages().getLast().text().contains("商品参数页"));}
    @Test void aiRecommendationBecomesValidatedClickableProductCards(){var s=open().session();var card=new KeFuChatCardService.Card("PRODUCT",100L,101L,"保温管 DN100","",100L,null,"https://example.test/pipe.jpg",12800,1);when(aiReplies.reply(anyString(),anyLong(),anyLong(),anyString(),anyString(),anyString())).thenReturn(new KeFuAiReplyService.Answer("为您推荐以下在售保温管。",List.of(100L)));when(cards.build(s,buyer,"PRODUCT",100L)).thenReturn(card);var result=service.send(buyer,text(s,"request01","推荐保温管"),false);assertEquals(3,result.messages().size());assertEquals("AI",result.messages().get(1).senderRole());assertEquals(card,result.messages().get(2).card());assertEquals(result.messages(),service.send(buyer,text(s,"request01","推荐保温管"),false).messages());}
    @Test void aiFailureFallsBackToSafeSystemMessage(){when(aiReplies.reply(anyString(),anyLong(),anyLong(),anyString(),anyString(),anyString())).thenReturn(null);var s=open().session();var result=service.send(buyer,text(s,"request01","未知问题"),false);assertEquals("SYSTEM",result.messages().getLast().senderRole());assertTrue(result.messages().getLast().text().contains("转人工"));}
    @Test void faqHitDoesNotSpendAiQuota(){publish("SHOP",101,List.of(entry("return","固定答复","退货")));var s=open().session();assertEquals("FAQ",service.send(buyer,text(s,"request01","退货"),false).messages().getLast().senderRole());verifyNoInteractions(aiReplies);}
    @Test void aiRepliesAreLimitedToTenPerConversationPerMinute(){when(aiReplies.reply(anyString(),anyLong(),anyLong(),anyString(),anyString(),anyString())).thenReturn(new KeFuAiReplyService.Answer("AI答复",List.of()));var s=open().session();for(int i=0;i<10;i++)assertEquals("AI",service.send(buyer,text(s,"request_"+i,"问题"+i),false).messages().getLast().senderRole());assertEquals("SYSTEM",service.send(buyer,text(s,"request_10","问题10"),false).messages().getLast().senderRole());verify(aiReplies,times(10)).reply(anyString(),anyLong(),anyLong(),anyString(),anyString(),anyString());}
    @Test void shopSpecificPolicyPrecedesLongerPlatformKeyword(){publish("PLATFORM",0,List.of(entry("general","平台一般规则","退货问题")));publish("SHOP",101,List.of(entry("specific","本店具体规则","退货")));var s=open().session();assertEquals("本店具体规则",service.send(buyer,text(s,"request01","退货问题"),false).messages().getLast().text());}
    @Test void shopFaqDoesNotLeakToAnotherShopOrTenant(){publish("SHOP",101,List.of(entry("private","101店专有规则","退货")));var s=service.open(buyer,"SHOP",102L).session();assertTrue(service.thread(buyer,s.id(),false).faq().isEmpty());assertEquals("SYSTEM",service.send(buyer,text(s,"request01","退货"),false).messages().getLast().senderRole());TenantContextHolder.setTenantId(2L);var otherTenant=service.open(buyer,"SHOP",201L).session();assertTrue(service.thread(buyer,otherTenant.id(),false).faq().isEmpty());}
    @Test void unpublishedDraftChangeDoesNotAlterActivePublishedReply(){var pub=publish("SHOP",101,List.of(entry("return","已发布原文","退货")));service.saveFaq(operator,new FaqSaveRequest("SHOP",101L,pub.version(),List.of(entry("return","新草稿不能泄露","退货"))));var s=open().session();assertEquals("已发布原文",service.send(buyer,text(s,"request01","退货"),false).messages().getLast().text());}
    @Test void faqConcurrentVersionPreventsOverwritingAnotherEditorsDraft(){var saved=service.saveFaq(operator,new FaqSaveRequest("SHOP",101L,0L,List.of(entry("return","原文","退货"))));assertThrows(ServiceException.class,()->service.saveFaq(operator,new FaqSaveRequest("SHOP",101L,0L,List.of())));assertEquals(saved,service.faq(operator,"SHOP",101L));}
    @Test void faqPublicationAuditFailureRollsBackVersionAndContent(){var saved=service.saveFaq(operator,new FaqSaveRequest("SHOP",101L,0L,List.of(entry("return","原文","退货"))));doThrow(new IllegalStateException("audit unavailable")).when(sessions).audit(anyLong(),anyString(),anyLong(),isNull(),eq("FAQ_PUBLISH"),any(),isNull(),anyLong(),anyString());assertThrows(IllegalStateException.class,()->service.publishFaq(operator,new FaqPublishRequest("SHOP",101L,saved.version(),"发布")));assertEquals(0,service.faq(operator,"SHOP",101L).publishedVersion());}
    @Test void duplicateSendReturnsSameMessageAndDoesNotDuplicateFaq(){publish("SHOP",101,List.of(entry("return","答复","退货")));var s=open().session();var req=text(s,"request01","退货");var first=service.send(buyer,req,false);var retry=service.send(buyer,req,false);assertEquals(first.messages(),retry.messages());assertEquals(2,count());}
    @Test void reusedClientIdWithDifferentTextConflicts(){var s=open().session();service.send(buyer,text(s,"request01","消息一"),false);assertThrows(ServiceException.class,()->service.send(buyer,text(s,"request01","消息二"),false));assertEquals(2,count());}
    @Test void transferToHumanStopsAutomaticAnswersEvenWhileOffline(){publish("SHOP",101,List.of(entry("return","自动答复","退货")));var s=human();var result=service.send(buyer,text(s,"request01","退货"),false);assertEquals(1,result.messages().size());assertEquals("WAITING_HUMAN",result.thread().mode());assertEquals(0,result.thread().onlineAgents());assertTrue(result.thread().availabilityMessage().contains("留言"));}
    @Test void repeatedHandoffDoesNotCreateExtraTransition(){var s=open().session();var req=new ChangeRequest(s.id(),s.version());var first=service.human(buyer,req);var retry=service.human(buyer,req);assertEquals(first.session().version(),retry.session().version());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM promotion_kefu_scope_audit WHERE event_type='HANDOFF'",Integer.class));}
    @Test void handoffAuditFailureRollsBackHumanFlagAndSessionVersion(){var s=open().session();doThrow(new IllegalStateException("audit unavailable")).when(sessions).audit(anyLong(),anyString(),anyLong(),eq(s.id()),eq("HANDOFF"),any(),isNull(),anyLong(),anyString());assertThrows(IllegalStateException.class,()->service.human(buyer,new ChangeRequest(s.id(),s.version())));var after=service.thread(buyer,s.id(),false);assertFalse(after.humanRequested());assertEquals(s.version(),after.session().version());}
    @Test void activeAgentCanReceiveSendAndCloseButOtherAgentCannotRead(){var s=active();grant(other);service.send(agent,text(s,"agentreq01","人工已接待"),true);assertEquals("AGENT",service.messages(buyer,s.id(),false,0L,50).items().getFirst().senderRole());assertThrows(ServiceException.class,()->service.messages(other,s.id(),true,0L,50));assertThrows(ServiceException.class,()->service.send(other,text(s,"agentreq02","抢答"),true));scopes.close(agent,s.id(),s.version());assertThrows(ServiceException.class,()->service.send(agent,text(s,"agentreq03","结束后新消息"),true));}
    @Test void memberAndAdminWithSameNumericIdAreDifferentAgents(){var s=active();grant(admin);assertThrows(ServiceException.class,()->service.messages(admin,s.id(),true,0L,50));assertThrows(ServiceException.class,()->service.send(admin,text(s,"request01","越权"),true));}
    @Test void grantRevocationImmediatelyBlocksReadSendAndPresence(){var s=active();service.heartbeat(agent,new HeartbeatRequest("SHOP",101L,true));assertEquals(1,service.thread(buyer,s.id(),false).onlineAgents());scopes.grant(operator,new GrantRequest("SHOP",101L,agent.type(),agent.id(),false,1L,"撤回"));assertThrows(ServiceException.class,()->service.messages(agent,s.id(),true,0L,50));assertThrows(ServiceException.class,()->service.send(agent,text(s,"agentreq01","越权"),true));assertEquals(0,service.thread(buyer,s.id(),false).onlineAgents());assertEquals("WAITING_HUMAN",service.thread(buyer,s.id(),false).mode());}
    @Test void unauthorizedAgentCannotSeeQueueOrHeartbeat(){assertThrows(ServiceException.class,()->service.workbench(agent,"SHOP",101L,"WAITING",1,20));assertThrows(ServiceException.class,()->service.heartbeat(agent,new HeartbeatRequest("SHOP",101L,true)));}
    @Test void initialSelfServiceDoesNotAppearInHumanQueue(){grant(agent);var s=open().session();assertTrue(service.workbench(agent,"SHOP",101L,"WAITING",1,20).isEmpty());assertThrows(ServiceException.class,()->service.messages(agent,s.id(),true,0L,50));service.human(buyer,new ChangeRequest(s.id(),s.version()));assertEquals(1,service.workbench(agent,"SHOP",101L,"WAITING",1,20).size());}
    @Test void oldScopeQueueAndClaimCannotBypassExplicitHumanRequest(){grant(agent);var s=open().session();assertTrue(scopes.list(agent,"SHOP",101L,"WAITING",1,20).isEmpty());assertThrows(ServiceException.class,()->scopes.claim(agent,s.id(),s.version()));assertEquals("SELF_SERVICE",service.thread(buyer,s.id(),false).mode());}
    @Test void scopeSessionWithoutChatMetaAlsoCannotBeClaimed(){grant(agent);var s=scopes.open(buyer,"SHOP",101L);assertThrows(ServiceException.class,()->scopes.claim(agent,s.id(),s.version()));assertTrue(scopes.list(agent,"SHOP",101L,"WAITING",1,20).isEmpty());}
    @Test void heartbeatExpiresAndOfflineDoesNotFakeOnline(){grant(agent);var s=open().session();var beat=service.heartbeat(agent,new HeartbeatRequest("SHOP",101L,true));assertEquals(now+90_000,beat.expiresAt());assertEquals(1,service.thread(buyer,s.id(),false).onlineAgents());clock(now+90_001);assertEquals(0,service.thread(buyer,s.id(),false).onlineAgents());clock(now);service.heartbeat(agent,new HeartbeatRequest("SHOP",101L,false));assertEquals(0,service.thread(buyer,s.id(),false).onlineAgents());}
    @Test void buyerCannotCountTheirOwnAgentGrantAsAvailableReception(){grant(buyer);service.heartbeat(buyer,new HeartbeatRequest("SHOP",101L,true));assertEquals(0,open().onlineAgents());}
    @Test void disabledShopBlocksNewMessagesButOwnerCanReadHistory(){var s=open().session();service.send(buyer,text(s,"request01","历史"),false);when(shops.getShop(101L)).thenReturn(CommonResult.success(new ProductShopRespDTO().setId(101L).setStatus(1)));assertThrows(ServiceException.class,()->service.send(buyer,text(s,"request02","新消息"),false));assertEquals(2,service.messages(buyer,s.id(),false,0L,50).items().size());}
    @Test void disabledActorCannotReadPrivateMessages(){var s=open().session();when(members.getUser(buyer.id())).thenReturn(CommonResult.success(new MemberUserRespDTO().setId(buyer.id()).setStatus(1)));assertThrows(ServiceException.class,()->service.messages(buyer,s.id(),false,0L,50));}
    @Test void messagesAndReadCursorAreStrictlyScoped(){var s=open().session();var sent=service.send(buyer,text(s,"request01","消息"),false);assertThrows(ServiceException.class,()->service.messages(other,s.id(),false,0L,50));TenantContextHolder.setTenantId(2L);assertThrows(ServiceException.class,()->service.messages(buyer,s.id(),false,0L,50));TenantContextHolder.setTenantId(1L);var second=service.open(other,"SHOP",101L).session();assertThrows(ServiceException.class,()->service.read(other,new ReadRequest(second.id(),sent.messages().getFirst().id()),false));}
    @Test void incrementalCursorReadIsAscendingAndBounded(){var s=open().session();service.send(buyer,text(s,"request01","第一条"),false);service.send(buyer,text(s,"request02","第二条"),false);var first=service.messages(buyer,s.id(),false,0L,2);assertEquals(2,first.items().size());assertTrue(first.hasMore());var second=service.messages(buyer,s.id(),false,first.nextCursor(),2);assertEquals(2,second.items().size());assertFalse(second.hasMore());assertTrue(second.items().getFirst().id()>first.nextCursor());}
    @Test void readCursorIsMonotonicAndDoesNotMarkOtherIdentity(){var s=open().session();var sent=service.send(buyer,text(s,"request01","消息"),false);long first=sent.messages().getFirst().id(),last=sent.messages().getLast().id();assertEquals(1,service.thread(buyer,s.id(),false).unreadCount());service.read(buyer,new ReadRequest(s.id(),last),false);service.read(buyer,new ReadRequest(s.id(),first),false);var result=service.thread(buyer,s.id(),false);assertEquals(last,result.readMessageId());assertEquals(0,result.unreadCount());}
    @Test void humanClaimPreventsLaterStaleFaqSend(){publish("SHOP",101,List.of(entry("return","自动答复","退货")));grant(agent);var old=open().session();var hand=service.human(buyer,new ChangeRequest(old.id(),old.version())).session();scopes.claim(agent,hand.id(),hand.version());assertThrows(ServiceException.class,()->service.send(buyer,text(old,"request01","退货"),false));assertEquals(0,count());}
    @Test void failedAutomaticPersistenceRollsBackUserMessageToo(){var s=open().session();doThrow(new IllegalStateException("store unavailable")).when(repository).insert(any(),eq(new Actor(0,0)),anyString(),anyString(),anyString(),isNull(),isNull(),isNull(),anyLong(),isNull());assertThrows(IllegalStateException.class,()->service.send(buyer,text(s,"request01","问题"),false));assertEquals(0,count());}
    @Test void untrustedAttachmentAndEmptyOversizedRequestsAreRejected(){var s=open().session();for(String text:List.of(" ","x".repeat(2001),"bad\0text"))assertThrows(ServiceException.class,()->service.send(buyer,text(s,"request01",text),false));assertThrows(ServiceException.class,()->service.send(buyer,new SendRequest(s.id(),s.version(),"request01",null,"ATTACHMENT",1L),false));verifyNoInteractions(cards);assertEquals(0,count());}
    @Test void cardIsServerGeneratedAndDuplicateRetainsCardAndAutomaticReply(){var s=open().session();var card=new KeFuChatCardService.Card("PRODUCT",100L,101L,"服务端标题","",100L,null,null,123,1);when(cards.build(s,buyer,"PRODUCT",100L)).thenReturn(card);var req=new SendRequest(s.id(),s.version(),"request01",null,"PRODUCT",100L);var sent=service.send(buyer,req,false);assertEquals(card,sent.messages().getFirst().card());assertEquals("",sent.messages().getFirst().text());assertEquals("FAQ",sent.messages().getLast().senderRole());assertTrue(sent.messages().getLast().text().contains("已收到您发送的商品"));assertEquals(2,count());assertEquals(sent.messages(),service.send(buyer,req,false).messages());verify(cards,times(1)).build(s,buyer,"PRODUCT",100L);}
    @Test void orderCardGetsOrderSpecificAutomaticReply(){var s=open().session();var card=new KeFuChatCardService.Card("ORDER",100L,101L,"订单 O100","",null,100L,null,31000,20);when(cards.build(s,buyer,"ORDER",100L)).thenReturn(card);var sent=service.send(buyer,new SendRequest(s.id(),s.version(),"request02",null,"ORDER",100L),false);assertEquals(2,sent.messages().size());assertEquals("FAQ",sent.messages().getLast().senderRole());assertTrue(sent.messages().getLast().text().contains("已收到您发送的订单"));assertEquals(sent.messages(),service.send(buyer,new SendRequest(s.id(),s.version(),"request02",null,"ORDER",100L),false).messages());assertEquals(2,count());}
    @Test void agentCannotForgeBuyersPrivateOrderCard(){var s=active();assertThrows(ServiceException.class,()->service.send(agent,new SendRequest(s.id(),s.version(),"agentreq01",null,"ORDER",100L),true));verifyNoInteractions(cards);}
    @Test void cardPermissionFailurePersistsNothing(){var s=open().session();when(cards.build(any(),any(),anyString(),anyLong())).thenThrow(new ServiceException(403,"拒绝"));assertThrows(ServiceException.class,()->service.send(buyer,new SendRequest(s.id(),s.version(),"request01",null,"ORDER",100L),false));assertEquals(0,count());}
    @Test void messageRateLimitDoesNotBreakIdempotentRetry(){var s=human();for(int i=0;i<60;i++)service.send(buyer,text(s,"request_"+i,"留言"),false);assertEquals(60,count());assertThrows(ServiceException.class,()->service.send(buyer,text(s,"request_extra","过量"),false));assertDoesNotThrow(()->service.send(buyer,text(s,"request_0","留言"),false));clock(now+60_001);assertDoesNotThrow(()->service.send(buyer,text(s,"request_after","窗口后"),false));}
    @Test void duplicateConcurrentSendHasOnePersistedUserAndOneFaq() throws Exception {
        var s=open().session();var gate=new CyclicBarrier(2);
        try(var executor=Executors.newFixedThreadPool(2)){
            var calls=new ArrayList<Future<SendResult>>();for(int i=0;i<2;i++)calls.add(executor.submit(()->{TenantContextHolder.setTenantId(1L);try{gate.await();return service.send(buyer,text(s,"request01","问题"),false);}finally{TenantContextHolder.clear();}}));
            var a=calls.get(0).get(15,TimeUnit.SECONDS);var b=calls.get(1).get(15,TimeUnit.SECONDS);assertEquals(a.messages(),b.messages());assertEquals(2,count());
        }
    }
    @Test void unrelatedNewConversationsCanSendConcurrentlyWithoutRetryGapDeadlock() throws Exception {
        var first=open().session();
        var second=service.open(other,"SHOP",101L).session();
        var bothChecked=new CyclicBarrier(2);
        doAnswer(call->{
            var result=(Message)call.callRealMethod();
            String key=call.getArgument(2);
            if(result==null && key.startsWith("separate_")) bothChecked.await(10,TimeUnit.SECONDS);
            return result;
        }).when(repository).retry(any(),any(),anyString());
        try(var executor=Executors.newFixedThreadPool(2)) {
            var a=executor.submit(()->{TenantContextHolder.setTenantId(1L);try{return service.send(buyer,text(first,"separate_one","问题"),false);}finally{TenantContextHolder.clear();}});
            var b=executor.submit(()->{TenantContextHolder.setTenantId(1L);try{return service.send(other,text(second,"separate_two","问题"),false);}finally{TenantContextHolder.clear();}});
            assertEquals(2,a.get(15,TimeUnit.SECONDS).messages().size());
            assertEquals(2,b.get(15,TimeUnit.SECONDS).messages().size());
            assertEquals(4,count());
        }
    }
    @Test void faqAndClaimRaceCannotGenerateResponseAfterHumanClaim() throws Exception {
        grant(agent);publish("SHOP",101,List.of(entry("return","自动答复","退货")));var s=human();
        var gate=new CyclicBarrier(2);try(var executor=Executors.newFixedThreadPool(2)){
            var send=executor.submit(()->{TenantContextHolder.setTenantId(1L);try{gate.await();try{service.send(buyer,text(s,"request01","退货"),false);}catch(ServiceException expected){assertEquals(1013016003,expected.getCode());}return true;}finally{TenantContextHolder.clear();}});
            var claim=executor.submit(()->{TenantContextHolder.setTenantId(1L);try{gate.await();return scopes.claim(agent,s.id(),s.version());}finally{TenantContextHolder.clear();}});
            assertTrue(send.get(15,TimeUnit.SECONDS));assertEquals("ACTIVE",claim.get(15,TimeUnit.SECONDS).state());
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM promotion_kefu_chat_message WHERE sender_role IN('FAQ','SYSTEM')",Integer.class));
        }
    }
    @Test void sendAndRevokeRaceLeavesRevokedAgentUnableToReadOrSend() throws Exception {
        var s=active();var gate=new CyclicBarrier(2);try(var executor=Executors.newFixedThreadPool(2)){
            var send=executor.submit(()->{TenantContextHolder.setTenantId(1L);try{gate.await();try{service.send(agent,text(s,"agentreq01","并发回复"),true);return true;}catch(ServiceException denied){assertEquals(1013016002,denied.getCode());return false;}}finally{TenantContextHolder.clear();}});
            var revoke=executor.submit(()->{TenantContextHolder.setTenantId(1L);try{gate.await();scopes.grant(operator,new GrantRequest("SHOP",101L,agent.type(),agent.id(),false,1L,"并发撤权"));return true;}finally{TenantContextHolder.clear();}});
            boolean written=send.get(15,TimeUnit.SECONDS);assertTrue(revoke.get(15,TimeUnit.SECONDS));
            assertEquals(written?1:0,count());assertEquals("WAITING_HUMAN",service.thread(buyer,s.id(),false).mode());
            assertThrows(ServiceException.class,()->service.messages(agent,s.id(),true,0L,50));
        }
    }
    @Test void messageReadFinishesBeforeAnotherAgentCanClaimAndSend() throws Exception {
        agentReadBeforeClaim(false);
    }
    @Test void threadReadFinishesBeforeAnotherAgentCanClaimAndSend() throws Exception {
        agentReadBeforeClaim(true);
    }
    private void agentReadBeforeClaim(boolean threadView) throws Exception {
        grant(agent);grant(other);var session=human();
        var readChecked=new CountDownLatch(1);var releaseRead=new CountDownLatch(1);
        var claimAttempted=new CountDownLatch(1);var claimAndSendDone=new CountDownLatch(1);
        if(threadView)doAnswer(call->{pauseReader(readChecked,releaseRead);return call.callRealMethod();})
                .when(repository).last(any());
        else doAnswer(call->{pauseReader(readChecked,releaseRead);return call.callRealMethod();})
                .when(repository).messages(any(),anyLong(),anyInt());
        doAnswer(call->{
            if(java.lang.Thread.currentThread().getName().equals("claiming-agent"))claimAttempted.countDown();
            return call.callRealMethod();
        }).when(sessions).get(eq(1L),eq(session.id()),eq(true));
        try(var workers=Executors.newFixedThreadPool(2)) {
            try {
                var reading=workers.submit(()->inTenant("reading-agent",()->threadView
                        ?service.thread(agent,session.id(),true):service.messages(agent,session.id(),true,0L,50)));
                assertTrue(readChecked.await(8,TimeUnit.SECONDS));
                var claiming=workers.submit(()->inTenant("claiming-agent",()->{
                    var claimed=scopes.claim(other,session.id(),session.version());
                    service.send(other,text(claimed,"newagent01","接管之后的新正文"),true);
                    claimAndSendDone.countDown();return claimed;
                }));
                assertTrue(claimAttempted.await(8,TimeUnit.SECONDS));
                boolean overtookRead=claimAndSendDone.await(500,TimeUnit.MILLISECONDS);
                releaseRead.countDown();
                Object result=reading.get(12,TimeUnit.SECONDS);
                assertEquals("ACTIVE",claiming.get(12,TimeUnit.SECONDS).state());
                assertFalse(overtookRead,"Another agent must not claim/send between read authorization and its SQL projection");
                if(threadView)assertEquals(0,((KeFuChatModels.Thread)result).lastMessageId());
                else assertTrue(((MessagePage)result).items().isEmpty(),"The waiting agent must not see the new owner's later reply");
                assertThrows(ServiceException.class,()->service.messages(agent,session.id(),true,0L,50));
            } finally {releaseRead.countDown();}
        }
    }
    @Test void claimWinningBeforeMessageReadRejectsTheFormerWaitingAgent() throws Exception {
        claimBeforeAgentRead(false);
    }
    @Test void claimWinningBeforeThreadReadRejectsTheFormerWaitingAgent() throws Exception {
        claimBeforeAgentRead(true);
    }
    private void claimBeforeAgentRead(boolean threadView) throws Exception {
        grant(agent);grant(other);var session=human();
        var hinted=new CountDownLatch(1);var releaseHint=new CountDownLatch(1);
        var first=new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(call->{
            Object value=call.callRealMethod();
            if(java.lang.Thread.currentThread().getName().equals("reading-agent")&&first.compareAndSet(true,false)){
                hinted.countDown();assertTrue(releaseHint.await(8,TimeUnit.SECONDS));
            }
            return value;
        }).when(sessions).get(eq(1L),eq(session.id()),eq(false));
        try(var workers=Executors.newSingleThreadExecutor()) {
            try {
                var reading=workers.submit(()->inTenant("reading-agent",()->assertThrows(ServiceException.class,()->{
                    if(threadView)service.thread(agent,session.id(),true);else service.messages(agent,session.id(),true,0L,50);
                })));
                assertTrue(hinted.await(8,TimeUnit.SECONDS));
                var claimed=scopes.claim(other,session.id(),session.version());
                service.send(other,text(claimed,"newagent01","接管后的正文"),true);
                releaseHint.countDown();
                assertEquals(1013016002,reading.get(12,TimeUnit.SECONDS).getCode());
                assertEquals(1,count());
            } finally {releaseHint.countDown();}
        }
    }
    @Test void agentReadAndRevokeKeepGrantBeforeSessionLockOrder() throws Exception {
        var session=active();service.send(agent,text(session,"agentreq01","已有回复"),true);
        var readChecked=new CountDownLatch(1);var releaseRead=new CountDownLatch(1);
        var revoking=new CountDownLatch(1);var revoked=new CountDownLatch(1);
        doAnswer(call->{pauseReader(readChecked,releaseRead);return call.callRealMethod();})
                .when(repository).messages(any(),anyLong(),anyInt());
        try(var workers=Executors.newFixedThreadPool(2)) {
            try {
                var reading=workers.submit(()->inTenant("reading-agent",()->service.messages(agent,session.id(),true,0L,50)));
                assertTrue(readChecked.await(8,TimeUnit.SECONDS));
                var revocation=workers.submit(()->inTenant("revoking-agent",()->{
                    revoking.countDown();scopes.grant(operator,new GrantRequest("SHOP",101L,agent.type(),agent.id(),false,1L,"隔离并发撤权"));
                    revoked.countDown();return true;
                }));
                assertTrue(revoking.await(8,TimeUnit.SECONDS));
                boolean overtookRead=revoked.await(500,TimeUnit.MILLISECONDS);releaseRead.countDown();
                assertEquals(1,reading.get(12,TimeUnit.SECONDS).items().size());assertTrue(revocation.get(12,TimeUnit.SECONDS));
                assertFalse(overtookRead);assertEquals("WAITING_HUMAN",service.thread(buyer,session.id(),false).mode());
                assertThrows(ServiceException.class,()->service.messages(agent,session.id(),true,0L,50));
            } finally {releaseRead.countDown();}
        }
    }
    @Test void buyerReadsAndQueueMetadataDoNotAcquireSessionWriteLocks() {
        grant(agent);var session=human();clearInvocations(sessions);
        service.messages(buyer,session.id(),false,0L,50);service.thread(buyer,session.id(),false);
        assertEquals(1,service.workbench(agent,"SHOP",101L,"WAITING",1,20).size());
        verify(sessions,never()).get(anyLong(),anyString(),eq(true));
    }
    private static void pauseReader(CountDownLatch reached,CountDownLatch release) throws InterruptedException {
        if(java.lang.Thread.currentThread().getName().equals("reading-agent")){
            reached.countDown();assertTrue(release.await(8,TimeUnit.SECONDS));
        }
    }
    private static <T>T inTenant(String name,Callable<T> action) throws Exception {
        var worker=java.lang.Thread.currentThread();String before=worker.getName();worker.setName(name);TenantContextHolder.setTenantId(1L);
        try{return action.call();}finally{TenantContextHolder.clear();worker.setName(before);}
    }
    @Test void invalidFaqEntriesAndMemberPublicationAreRejected(){assertThrows(ServiceException.class,()->service.saveFaq(operator,new FaqSaveRequest("SHOP",101L,0L,List.of(entry("same","a","退货"),entry("same","b","退货")))));assertThrows(ServiceException.class,()->service.saveFaq(buyer,new FaqSaveRequest("SHOP",101L,0L,List.of(entry("one","a","退货")))));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM promotion_kefu_faq_config",Integer.class));}
}
