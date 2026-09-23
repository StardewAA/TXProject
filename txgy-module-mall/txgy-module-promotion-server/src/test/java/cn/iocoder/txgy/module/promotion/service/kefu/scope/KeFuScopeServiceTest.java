package cn.iocoder.txgy.module.promotion.service.kefu.scope;

import cn.iocoder.txgy.framework.common.exception.ErrorCode;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.member.api.user.MemberUserApi;
import cn.iocoder.txgy.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.txgy.module.product.api.shop.ProductShopApi;
import cn.iocoder.txgy.module.product.api.shop.dto.ProductShopRespDTO;
import cn.iocoder.txgy.module.promotion.dal.mysql.kefu.KeFuScopeRepository;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

import static cn.iocoder.txgy.module.promotion.service.kefu.scope.KeFuScopeModels.*;
import static cn.iocoder.txgy.module.promotion.service.kefu.scope.KeFuScopeService.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real SQL + real Spring transaction proxy, fake RPCs; optionally rerun on an explicitly isolated MySQL DB. */
class KeFuScopeServiceTest {
    private JdbcTemplate jdbc;
    private KeFuScopeRepository repository;
    private KeFuScopeService target;
    private KeFuScopeService service;
    private MemberUserApi members;
    private AdminUserApi admins;
    private ProductShopApi shops;
    private ResourceDatabasePopulator schema;
    private DriverManagerDataSource source;
    private final Actor buyer = new Actor(11, 1);
    private final Actor agent = new Actor(22, 1);
    private final Actor other = new Actor(33, 1);
    private final Actor adminAgent = new Actor(22, 2);
    private final Actor operator = new Actor(99, 2);

    @BeforeEach
    void setup() {
        source = new DriverManagerDataSource();
        String mysql = System.getenv("MALL_KEFU_TEST_JDBC");
        if (mysql != null) {
            assertTrue(mysql.matches("jdbc:mysql://127\\.0\\.0\\.1:3306/mall_kefu_(?:scope|chat)_test_20260917(?:\\?.*)?"));
            source.setUrl(mysql);
            source.setUsername(System.getenv("MALL_KEFU_TEST_USER"));
            source.setPassword(System.getenv("MALL_KEFU_TEST_PASSWORD"));
        } else {
            source.setUrl("jdbc:h2:mem:kefu-scope-" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        }
        jdbc = new JdbcTemplate(source);
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        String ddl = "deploy/mysql/repair/2026-09-17-promotion-kefu-scope.sql";
        while (root != null && !Files.exists(root.resolve(ddl))) root = root.getParent();
        assertNotNull(root);
        schema = new ResourceDatabasePopulator(new FileSystemResource(root.resolve(ddl)),
                new FileSystemResource(root.resolve("deploy/mysql/repair/2026-09-17-promotion-kefu-chat.sql")));
        schema.execute(source);
        clearTables();
        repository = spy(new KeFuScopeRepository(jdbc));
        shops = mock(ProductShopApi.class);
        members = mock(MemberUserApi.class);
        admins = mock(AdminUserApi.class);
        when(shops.getShop(anyLong())).thenAnswer(call -> {
            Long id = call.getArgument(0);
            boolean valid = Objects.equals(TenantContextHolder.getTenantId(), 1L) ? id == 101 || id == 102 : id == 201;
            return CommonResult.success(valid ? new ProductShopRespDTO().setId(id).setStatus(0) : null);
        });
        when(members.getUser(anyLong())).thenAnswer(call -> CommonResult.success(
                new MemberUserRespDTO().setId(call.getArgument(0)).setStatus(0)));
        when(admins.getUser(anyLong())).thenAnswer(call -> CommonResult.success(
                new AdminUserRespDTO().setId(call.getArgument(0)).setStatus(0)));
        target = new KeFuScopeService(repository, shops, members, admins);
        ReflectionTestUtils.setField(target, "enabled", true);
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),
                new AnnotationTransactionAttributeSource()));
        service = (KeFuScopeService) proxy.getProxy();
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
        clearTables();
    }

    private void clearTables() {
        jdbc.update("DELETE FROM promotion_kefu_chat_read");
        jdbc.update("DELETE FROM promotion_kefu_chat_message");
        jdbc.update("DELETE FROM promotion_kefu_chat_presence");
        jdbc.update("DELETE FROM promotion_kefu_faq_config");
        jdbc.update("DELETE FROM promotion_kefu_chat_meta");
        jdbc.update("DELETE FROM promotion_kefu_scope_audit");
        jdbc.update("DELETE FROM promotion_kefu_platform_session");
        jdbc.update("DELETE FROM promotion_kefu_session");
        jdbc.update("DELETE FROM promotion_kefu_agent");
    }

    @Test void migrationIsRerunnableWithoutChangingExistingSessions() {
        var first = open();
        schema.execute(source);
        assertEquals(first, service.own(buyer, first.id()));
        assertEquals(1, count("promotion_kefu_session"));
    }

    @Test void disabledByDefaultAndDoesNotReadRpcOrWriteData() {
        var original = new KeFuScopeService(repository, shops, members, admins);
        error(DISABLED, () -> original.open(buyer, "SHOP", 101L));
        verifyNoInteractions(shops, members, admins);
        assertEquals(0, count("promotion_kefu_session"));
    }

    @Test void scopeMustBeExplicitAndPlatformCannotUseShopId() {
        error(INVALID, () -> service.open(buyer, null, 101L));
        error(INVALID, () -> service.open(buyer, "PLATFORM", 101L));
        error(INVALID, () -> service.open(buyer, "SHOP", 0L));
        error(INVALID, () -> service.open(buyer, "SHOP", null));
        error(INVALID, () -> service.open(buyer, "shop", 101L));
        error(INVALID, () -> service.open(buyer, "SHOP", -1L));
        assertEquals(0, count("promotion_kefu_session"));
    }

    @Test void sameOwnerIsIdempotentButPlatformAndDifferentShopsAreIndependent() {
        var a = open();
        assertEquals(a.id(), open().id());
        var b = service.open(buyer, "SHOP", 102L);
        var c = service.open(buyer, "PLATFORM", 0L);
        assertEquals(3, Set.of(a.id(), b.id(), c.id()).size());
        assertEquals(3, audit("OPEN"));
    }

    @Test void tenantAndBuyerIsolationCannotBeBypassedWithKnownSessionId() {
        var session = open();
        error(FORBIDDEN, () -> service.own(other, session.id()));
        TenantContextHolder.setTenantId(2L);
        error(FORBIDDEN, () -> service.own(buyer, session.id()));
        error(FORBIDDEN, () -> service.open(buyer, "SHOP", 101L));
        var foreign = service.open(buyer, "SHOP", 201L);
        assertNotEquals(session.id(), foreign.id());
        TenantContextHolder.setTenantId(1L);
        error(FORBIDDEN, () -> service.own(buyer, foreign.id()));
    }

    @Test void platformConversationUsesResolvedTargetTenantButOnlyAccountOwnerCanReadIt() {
        var session = service.openPlatform(buyer, 2L, 201L, 9001L);
        assertEquals(2L, session.tenantId());
        assertEquals(1L, TenantContextHolder.getTenantId());
        assertEquals(session, service.chatAccess(buyer, session.id(), false, false));
        error(FORBIDDEN, () -> service.chatAccess(other, session.id(), false, false));

        // Even a target-tenant account with the same legacy numeric member id must not inherit it.
        TenantContextHolder.setTenantId(2L);
        error(FORBIDDEN, () -> service.chatAccess(buyer, session.id(), false, false));
        TenantContextHolder.setTenantId(1L);
        assertEquals(1, count("promotion_kefu_platform_session"));
    }

    @Test void targetTenantRpcDropsAccountPrincipalAndRestoresItAfterFailure() {
        login(11, 1, 1L, null);
        var originalAuthentication = SecurityContextHolder.getContext().getAuthentication();
        when(shops.getShop(201L)).thenAnswer(call -> {
            assertEquals(2L, TenantContextHolder.getTenantId());
            assertNull(SecurityFrameworkUtils.getLoginUser());
            throw new IllegalStateException("target shop unavailable");
        });

        assertThrows(IllegalStateException.class,
                () -> service.openPlatform(buyer, 2L, 201L, 9001L));

        assertEquals(1L, TenantContextHolder.getTenantId());
        assertSame(originalAuthentication, SecurityContextHolder.getContext().getAuthentication());
        assertEquals(11L, SecurityFrameworkUtils.getLoginUserId());
        assertEquals(0, count("promotion_kefu_session"));
    }

    @Test void missingOrIgnoredTenantIsDenied() {
        TenantContextHolder.clear();
        error(FORBIDDEN, this::open);
        TenantContextHolder.setTenantId(0L);
        error(FORBIDDEN, this::open);
        TenantContextHolder.setTenantId(1L);
        TenantContextHolder.setIgnore(true);
        error(FORBIDDEN, this::open);
        assertEquals(0, count("promotion_kefu_session"));
    }

    @Test void missingDisabledShopOrDisabledMemberDoesNotCreateConversation() {
        error(FORBIDDEN, () -> service.open(buyer, "SHOP", 999L));
        when(shops.getShop(101L)).thenReturn(CommonResult.success(new ProductShopRespDTO().setId(101L).setStatus(1)));
        error(FORBIDDEN, this::open);
        when(members.getUser(11L)).thenReturn(CommonResult.success(new MemberUserRespDTO().setId(11L).setStatus(1)));
        error(FORBIDDEN, () -> service.open(buyer, "PLATFORM", 0L));
    }

    @Test void malformedOrWrongActorCannotUseBuyerEndpoints() {
        error(FORBIDDEN, () -> service.open(adminAgent, "SHOP", 101L));
        error(FORBIDDEN, () -> service.open(new Actor(0, 1), "SHOP", 101L));
        error(FORBIDDEN, () -> service.open(new Actor(22, 3), "SHOP", 101L));
        error(FORBIDDEN, () -> service.open(null, "SHOP", 101L));
        error(FORBIDDEN, () -> service.own(adminAgent, open().id()));
    }

    @Test void supplierAndAdminWithoutIndependentGrantCannotAccessQueue() {
        var session = open();
        for (Actor actor : List.of(agent, adminAgent, operator)) {
            error(FORBIDDEN, () -> service.list(actor, "SHOP", 101L, "WAITING", 1, 20));
            error(FORBIDDEN, () -> service.claim(actor, session.id(), 0L));
        }
    }

    @Test void grantsAreScopedByTenantShopAndActorType() {
        grant(agent, true);
        open();
        assertEquals(1, service.list(agent, "SHOP", 101L, "WAITING", 1, 20).size());
        error(FORBIDDEN, () -> service.list(adminAgent, "SHOP", 101L, "WAITING", 1, 20));
        error(FORBIDDEN, () -> service.list(agent, "SHOP", 102L, "WAITING", 1, 20));
        error(FORBIDDEN, () -> service.list(agent, "PLATFORM", 0L, "WAITING", 1, 20));
        TenantContextHolder.setTenantId(2L);
        error(FORBIDDEN, () -> service.list(agent, "SHOP", 201L, "WAITING", 1, 20));
    }

    @Test void memberCannotGrantPrivilegesToSelf() {
        error(FORBIDDEN, () -> service.grant(agent, request(agent, true)));
        error(FORBIDDEN, () -> service.agents(agent, "SHOP", 101L, 1, 20));
        assertEquals(0, count("promotion_kefu_agent"));
    }

    @Test void grantRequiresValidTargetAndReasonAndIsIdempotent() {
        error(INVALID, () -> service.grant(operator, new GrantRequest("SHOP", 101L, 1, 22L, true, 0L, " ")));
        when(members.getUser(22L)).thenReturn(CommonResult.success(null));
        error(FORBIDDEN, () -> grant(agent, true));
        when(members.getUser(22L)).thenReturn(CommonResult.success(new MemberUserRespDTO().setId(22L).setStatus(0)));
        grant(agent, true);
        grant(agent, true);
        assertEquals(1, audit("GRANT"));
        assertEquals(1, service.agents(operator, "SHOP", 101L, 1, 20).size());
    }

    @Test void agentCanDiscoverOnlyOwnEnabledWorkbenchScopes() {
        grant(adminAgent, true);
        grant(agent, true);
        assertEquals(List.of(new Agent("SHOP", 101L, 2, 22L, true, 1L)), service.myScopes(adminAgent));
        grant(adminAgent, false);
        assertTrue(service.myScopes(adminAgent).isEmpty());
        assertEquals(1, service.myScopes(agent).size());
    }

    @Test void claimAndCloseRequireCurrentVersionAndAssignedTypedActor() {
        grant(agent, true);
        grant(other, true);
        var session = open();
        var claimed = service.claim(agent, session.id(), 0L);
        assertEquals("ACTIVE", claimed.state());
        assertEquals(claimed, service.claim(agent, session.id(), 0L));
        error(CONFLICT, () -> service.claim(other, session.id(), 0L));
        error(CONFLICT, () -> service.close(other, session.id(), 1L));
        error(CONFLICT, () -> service.close(agent, session.id(), 0L));
        var closed = service.close(agent, session.id(), 1L);
        assertEquals("CLOSED", closed.state());
        assertEquals(closed, service.close(agent, session.id(), 1L));
        assertEquals(1, audit("CLAIM"));
        assertEquals(1, audit("CLOSE"));
    }

    @Test void reopenInvalidatesOldClaimAndCloseRetries() {
        grant(agent, true);
        var session = open();
        service.claim(agent, session.id(), 0L);
        service.close(agent, session.id(), 1L);
        var reopened = open();
        assertEquals(session.id(), reopened.id());
        assertEquals(3, reopened.version());
        assertNull(reopened.agentId());
        error(CONFLICT, () -> service.close(agent, session.id(), 1L));
        error(CONFLICT, () -> service.claim(agent, session.id(), 0L));
        assertEquals(4, service.claim(agent, session.id(), 3L).version());
        assertEquals(1, audit("REOPEN"));
    }

    @Test void memberCannotReceiveOwnConversationButSameNumberAdminIsDifferentIdentity() {
        grant(buyer, true);
        var session = open();
        error(FORBIDDEN, () -> service.claim(buyer, session.id(), 0L));
        Actor admin = new Actor(11, 2);
        grant(admin, true);
        assertEquals(2, service.claim(admin, session.id(), 0L).agentType());
    }

    @Test void lateGrantRetryCannotUndoRevocation() {
        var initial = request(agent, true);
        service.grant(operator, initial);
        service.grant(operator, initial);
        assertEquals(1, audit("GRANT"));
        grant(agent, false);
        error(CONFLICT, () -> service.grant(operator, initial));
        error(FORBIDDEN, () -> service.list(agent, "SHOP", 101L, "WAITING", 1, 20));
        assertEquals(1, audit("REVOKE"));
    }

    @Test void grantAuditFailureRollsBackNewConversationAsWell() {
        doThrow(new IllegalStateException("audit unavailable")).when(repository).audit(anyLong(), anyString(),
                anyLong(), any(), eq("OPEN"), any(), any(), anyLong(), anyString());
        assertThrows(IllegalStateException.class, this::open);
        assertEquals(0, count("promotion_kefu_session"));
    }

    @Test void revocationRequeuesOwnedSessionsAndImmediatelyBlocksOldAgent() {
        grant(agent, true);
        var session = open();
        service.claim(agent, session.id(), 0L);
        grant(agent, false);
        var waiting = service.own(buyer, session.id());
        assertEquals("WAITING", waiting.state());
        assertEquals(2, waiting.version());
        assertNull(waiting.agentId());
        error(FORBIDDEN, () -> service.close(agent, session.id(), 1L));
        error(FORBIDDEN, () -> service.claim(agent, session.id(), 2L));
        error(FORBIDDEN, () -> service.list(agent, "SHOP", 101L, "WAITING", 1, 20));
        grant(agent, false);
        assertEquals(1, audit("REVOKE"));
        assertEquals(1, audit("REQUEUE"));
    }

    @Test void revokingOneTypeCannotRequeueSameNumberOtherType() {
        grant(agent, true);
        grant(adminAgent, true);
        var session = open();
        service.claim(adminAgent, session.id(), 0L);
        grant(agent, false);
        assertEquals("ACTIVE", service.own(buyer, session.id()).state());
        assertEquals(0, audit("REQUEUE"));
    }

    @Test void disabledShopOrAccountCanStillHaveGrantRevoked() {
        grant(agent, true);
        var session = open();
        service.claim(agent, session.id(), 0L);
        when(shops.getShop(101L)).thenReturn(CommonResult.success(null));
        when(members.getUser(22L)).thenReturn(CommonResult.success(null));
        grant(agent, false);
        assertEquals("WAITING", service.own(buyer, session.id()).state());
    }

    @Test void actorStatusIsRecheckedOnEveryOperation() {
        grant(agent, true);
        var session = open();
        when(members.getUser(22L)).thenReturn(CommonResult.success(new MemberUserRespDTO().setId(22L).setStatus(1)));
        error(FORBIDDEN, () -> service.claim(agent, session.id(), 0L));
        error(FORBIDDEN, () -> service.list(agent, "SHOP", 101L, "WAITING", 1, 20));
    }

    @Test void queueHasValidatedPaginationAndState() {
        grant(agent, true);
        open();
        markHumanRequested(service.open(other, "SHOP", 101L));
        assertEquals(1, service.list(agent, "SHOP", 101L, "WAITING", 1, 1).size());
        assertEquals(1, service.list(agent, "SHOP", 101L, "WAITING", 2, 1).size());
        assertTrue(service.list(agent, "SHOP", 101L, "ACTIVE", 1, 20).isEmpty());
        error(INVALID, () -> service.list(agent, "SHOP", 101L, "WAITING", 0, 20));
        error(INVALID, () -> service.list(agent, "SHOP", 101L, "WAITING", 1, 101));
        error(INVALID, () -> service.list(agent, "SHOP", 101L, "ONLINE", 1, 20));
    }

    @Test void auditFailureRollsBackClaimAndGrant() {
        grant(agent, true);
        var session = open();
        doThrow(new IllegalStateException("audit unavailable")).when(repository).audit(anyLong(), anyString(),
                anyLong(), any(), anyString(), any(), any(), anyLong(), anyString());
        assertThrows(IllegalStateException.class, () -> service.claim(agent, session.id(), 0L));
        assertEquals("WAITING", service.own(buyer, session.id()).state());
        assertThrows(IllegalStateException.class, () -> grant(other, true));
        assertEquals(1, count("promotion_kefu_agent"));
    }

    @Test void auditFailureRollsBackRevocationAndRequeue() {
        grant(agent, true);
        var session = open();
        service.claim(agent, session.id(), 0L);
        doThrow(new IllegalStateException("audit unavailable")).when(repository).audit(anyLong(), anyString(),
                anyLong(), any(), eq("REQUEUE"), any(), any(), anyLong(), anyString());
        assertThrows(IllegalStateException.class, () -> grant(agent, false));
        assertEquals("ACTIVE", service.own(buyer, session.id()).state());
        assertEquals(1, service.list(agent, "SHOP", 101L, "ACTIVE", 1, 20).size());
        assertEquals(0, audit("REVOKE"));
    }

    @Test void concurrentOpenCreatesOneSessionAndOneAudit() throws Exception {
        var results = concurrently(this::open, this::open);
        assertEquals(results.get(0).id(), results.get(1).id());
        assertEquals(1, count("promotion_kefu_session"));
        assertEquals(1, audit("OPEN"));
    }

    @Test void concurrentAgentsProduceOnlyOneSuccessfulClaim() throws Exception {
        grant(agent, true);
        grant(other, true);
        var session = open();
        var results = concurrently(() -> tryClaim(agent, session), () -> tryClaim(other, session));
        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, audit("CLAIM"));
    }

    @Test void concurrentRevokeAndClaimAlwaysLeavesNoActiveRevokedAgent() throws Exception {
        grant(agent, true);
        var session = open();
        concurrently(() -> tryClaim(agent, session), () -> { grant(agent, false); return false; });
        assertEquals("WAITING", service.own(buyer, session.id()).state());
        error(FORBIDDEN, () -> service.claim(agent, session.id(), 0L));
    }

    @Test void directClaimReadsCommittedHandoffInsteadOfEarlierRepeatableReadSnapshot() throws Exception {
        grant(agent, true);
        var session = service.open(buyer, "SHOP", 101L);
        jdbc.update("INSERT INTO promotion_kefu_chat_meta(tenant_id,session_id,human_requested) VALUES(?,?,FALSE)",
                session.tenantId(), session.id());
        var transaction = new org.springframework.transaction.support.TransactionTemplate(
                new DataSourceTransactionManager(source));
        var snapshotReady = new CountDownLatch(1);
        var handoffCommitted = new CountDownLatch(1);
        try (var worker = Executors.newSingleThreadExecutor()) {
            var claim = worker.submit(() -> {
                TenantContextHolder.setTenantId(1L);
                try {
                    return transaction.execute(status -> {
                        // Establish a genuine MySQL RR snapshot before another transaction hands off.
                        assertEquals(0L, repository.get(1, session.id(), false).version());
                        snapshotReady.countDown();
                        try { assertTrue(handoffCommitted.await(8, TimeUnit.SECONDS)); }
                        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
                        // The session current-read sees v1; the human flag must use a current-read too.
                        return service.claim(agent, session.id(), 1L);
                    });
                } finally { TenantContextHolder.clear(); }
            });
            assertTrue(snapshotReady.await(8, TimeUnit.SECONDS));
            transaction.executeWithoutResult(status -> {
                var locked = repository.get(1, session.id(), true);
                jdbc.update("UPDATE promotion_kefu_chat_meta SET human_requested=TRUE WHERE tenant_id=1 AND session_id=?", session.id());
                repository.transition(locked, "WAITING", null);
            });
            handoffCommitted.countDown();
            assertEquals("ACTIVE", claim.get(12, TimeUnit.SECONDS).state());
            assertEquals(2L, service.own(buyer, session.id()).version());
        } finally { handoffCommitted.countDown(); }
    }

    @Test void identityMustComeFromMatchingTokenTenantAndType() {
        error(FORBIDDEN, () -> KeFuScopeIdentity.require(1));
        login(22, 1, 1, null);
        assertEquals(agent, KeFuScopeIdentity.require(1));
        error(FORBIDDEN, () -> KeFuScopeIdentity.require(2));
        TenantContextHolder.setTenantId(2L);
        error(FORBIDDEN, () -> KeFuScopeIdentity.require(1));
        login(22, 2, 1, 2L);
        error(FORBIDDEN, () -> KeFuScopeIdentity.require(2));
        TenantContextHolder.setTenantId(1L);
        error(FORBIDDEN, () -> KeFuScopeIdentity.require(2));
        login(22, 2, 1, 1L);
        assertEquals(adminAgent, KeFuScopeIdentity.require(2));
    }

    private void login(long id, int type, long tenant, Long visit) {
        var user = new LoginUser().setId(id).setUserType(type).setTenantId(tenant).setVisitTenantId(visit);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    /** Scope state tests start after an explicit handoff; chat tests exercise the actual handoff endpoint. */
    private Session open() { return markHumanRequested(service.open(buyer, "SHOP", 101L)); }
    private Session markHumanRequested(Session session) {
        jdbc.update("INSERT INTO promotion_kefu_chat_meta(tenant_id,session_id,human_requested) VALUES(?,?,TRUE) ON DUPLICATE KEY UPDATE human_requested=TRUE",
                session.tenantId(),session.id());
        return session;
    }
    private void grant(Actor actor, boolean enable) { service.grant(operator, request(actor, enable)); }
    private GrantRequest request(Actor actor, boolean enable) {
        var versions = jdbc.queryForList("SELECT version FROM promotion_kefu_agent WHERE tenant_id=1 AND scope='SHOP' "
                + "AND shop_id=101 AND actor_type=? AND actor_id=?", Long.class, actor.type(), actor.id());
        return new GrantRequest("SHOP", 101L, actor.type(), actor.id(), enable,
                versions.isEmpty() ? 0L : versions.getFirst(), "隔离自动测试");
    }
    private boolean tryClaim(Actor actor, Session session) {
        try { service.claim(actor, session.id(), 0L); return true; }
        catch (ServiceException e) {
            assertTrue(Objects.equals(e.getCode(), CONFLICT.getCode()) || Objects.equals(e.getCode(), FORBIDDEN.getCode()));
            return false;
        }
    }
    private <T> List<T> concurrently(Callable<T> first, Callable<T> second) throws Exception {
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> inTenant(gate, first));
            var b = executor.submit(() -> inTenant(gate, second));
            gate.countDown();
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        }
    }
    private <T> T inTenant(CountDownLatch gate, Callable<T> action) throws Exception {
        TenantContextHolder.setTenantId(1L);
        try { assertTrue(gate.await(5, TimeUnit.SECONDS)); return action.call(); }
        finally { TenantContextHolder.clear(); }
    }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    private int audit(String event) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM promotion_kefu_scope_audit WHERE event_type=?", Integer.class, event);
    }
    private void error(ErrorCode code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code.getCode(), assertThrows(ServiceException.class, action).getCode());
    }
}
