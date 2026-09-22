package cn.iocoder.txgy.module.member.service.tenant;

import cn.iocoder.txgy.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.txgy.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCreateReqDTO;
import cn.iocoder.txgy.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.member.api.user.MemberUserApiImpl;
import cn.iocoder.txgy.module.member.controller.app.tenant.vo.AppMallImageAuthReqVO;
import cn.iocoder.txgy.module.member.convert.user.MemberUserConvert;
import cn.iocoder.txgy.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.txgy.module.member.dal.mysql.tenant.MallCustomerAffiliationMapper;
import cn.iocoder.txgy.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.txgy.module.member.framework.tenant.config.MallTenantAuthProperties;
import cn.iocoder.txgy.module.member.framework.tenant.config.MallTenantProperties;
import cn.iocoder.txgy.module.member.mq.producer.user.MemberUserProducer;
import cn.iocoder.txgy.module.member.service.auth.MemberAuthServiceImpl;
import cn.iocoder.txgy.module.member.service.user.MemberUserServiceImpl;
import cn.iocoder.txgy.module.member.service.platform.MemberPlatformAccountService;
import cn.iocoder.txgy.module.product.api.enterprise.ProductEnterpriseCustomerApi;
import cn.iocoder.txgy.module.system.api.logger.LoginLogApi;
import cn.iocoder.txgy.module.system.api.sms.SmsCodeApi;
import cn.iocoder.txgy.module.system.api.social.SocialClientApi;
import cn.iocoder.txgy.module.system.api.social.SocialUserApi;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual member/auth services, BCrypt, MyBatis tenant isolation, unique constraints and commit callbacks.
 * Image verification is mocked here; its atomic consumption has separate isolated Redis regression tests.
 * Every database row and all remote collaborators belong to this in-memory test context. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MallImageRegistrationTransactionTest {
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private MallImageAuthService service;
    private MemberAuthServiceImpl auth;
    private MemberUserServiceImpl userService;
    private MemberUserMapper users;
    private MallAccountPolicy policy;
    private MallImageChallengeService challenges;
    private MemberUserProducer producer;
    private ProductEnterpriseCustomerApi enterprises;
    private OAuth2TokenCommonApi tokens;
    private LoginLogApi logs;
    private PasswordEncoder encoder;

    @BeforeAll void start() {
        context = new AnnotationConfigApplicationContext(Config.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        service = context.getBean(MallImageAuthService.class);
        auth = context.getBean(MemberAuthServiceImpl.class);
        userService = context.getBean(MemberUserServiceImpl.class);
        users = context.getBean(MemberUserMapper.class);
        policy = context.getBean(MallAccountPolicy.class);
        challenges = context.getBean(MallImageChallengeService.class);
        producer = context.getBean(MemberUserProducer.class);
        enterprises = context.getBean(ProductEnterpriseCustomerApi.class);
        tokens = context.getBean(OAuth2TokenCommonApi.class);
        logs = context.getBean(LoginLogApi.class);
        encoder = context.getBean(PasswordEncoder.class);
        jdbc.execute("CREATE TABLE member_user (id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT NOT NULL,"
                + "mobile VARCHAR(11),login_name VARCHAR(20),contact_mobile VARCHAR(11),member_identity VARCHAR(20),"
                + "email VARCHAR(100),password VARCHAR(100) NOT NULL,status INT NOT NULL,nickname VARCHAR(30) NOT NULL,"
                + "avatar VARCHAR(255),register_ip VARCHAR(64),register_terminal INT,login_ip VARCHAR(64),login_date TIMESTAMP,"
                + "name VARCHAR(100),sex INT,birthday TIMESTAMP,area_id INT,mark VARCHAR(200),point INT DEFAULT 0,tag_ids VARCHAR(200),"
                + "level_id BIGINT,experience INT DEFAULT 0,group_id BIGINT,creator VARCHAR(64),updater VARCHAR(64),"
                + "create_time TIMESTAMP,update_time TIMESTAMP,deleted BOOLEAN DEFAULT FALSE,"
                + "CONSTRAINT image_test_unique_mobile UNIQUE(mobile,tenant_id),"
                + "CONSTRAINT image_test_unique_login_name UNIQUE(tenant_id,login_name))");
        jdbc.execute("CREATE TABLE member_mall_customer_affiliation (id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT NOT NULL,"
                + "member_id BIGINT NOT NULL,service_enterprise_id BIGINT NOT NULL,source VARCHAR(32) NOT NULL,"
                + "creator VARCHAR(64),updater VARCHAR(64),create_time TIMESTAMP,update_time TIMESTAMP,deleted BOOLEAN DEFAULT FALSE,"
                + "UNIQUE(tenant_id,member_id))");
    }

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM member_mall_customer_affiliation");
        jdbc.update("DELETE FROM member_user");
        reset(policy, challenges, producer, enterprises, tokens, logs);
        when(policy.isEnabled()).thenReturn(true);
        when(policy.resolve(any(), anyBoolean())).thenAnswer(call -> call.<Long>getArgument(0));
        var properties = context.getBean(MallTenantProperties.class);
        properties.setEnabled(true).setPublicTenantId(9001L).setPlatformEnterpriseId(101L);
        context.getBean(MallTenantAuthProperties.class).setEnabled(true);
        when(enterprises.getCustomerOwner(anyLong())).thenAnswer(call -> CommonResult.success(
                new ProductEnterpriseCustomerApi.Owner(call.getArgument(0), TenantContextHolder.getRequiredTenantId(), "测试平台", true)));
        when(logs.createLoginLog(any())).thenReturn(CommonResult.success(true));
        when(tokens.createAccessToken(any())).thenAnswer(call -> {
            OAuth2AccessTokenCreateReqDTO request = call.getArgument(0);
            assertEquals(1, request.getUserType());
            assertFalse(TenantContextHolder.isIgnore());
            assertNotNull(users.selectById(request.getUserId()));
            return CommonResult.success(new OAuth2AccessTokenRespDTO().setUserId(request.getUserId()).setUserType(1)
                    .setAccessToken("isolated-access-" + request.getUserId()).setRefreshToken("isolated-refresh-" + request.getUserId())
                    .setExpiresTime(LocalDateTime.now().plusMinutes(5)));
        });
        // Simulate the controller's ignored context. The orchestration must enter and then restore the target tenant.
        TenantContextHolder.setTenantId(9002L);
        TenantContextHolder.setIgnore(true);
        requestContext();
    }

    @AfterEach void clear() { TenantContextHolder.clear(); RequestContextHolder.resetRequestAttributes(); }
    @AfterAll void close() { if (context != null) { jdbc.execute("SHUTDOWN"); context.close(); } }

    private static void requestContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1"); request.addHeader("User-Agent", "isolated-image-registration-test");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
    private AppMallImageAuthReqVO.Register request(String name, long tenant) {
        var request = new AppMallImageAuthReqVO.Register();
        request.setTenantId(tenant); request.setLoginName(name); request.setPassword("Test123456");
        request.setConfirmPassword("Test123456"); request.setMemberIdentity("supplier"); request.setAgree(true);
        request.setCaptchaSession("a".repeat(32)); request.setImageChallengeId("b".repeat(32)); request.setImageCode("ABC23");
        return request;
    }
    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
    private static <T> T inTenant(long tenant, Supplier<T> task) {
        AtomicReference<T> result = new AtomicReference<>();
        TenantUtils.execute(tenant, (Runnable) () -> result.set(task.get()));
        return result.get();
    }
    private MemberUserDO user(long tenant, long id) { return inTenant(tenant, () -> users.selectById(id)); }
    private TransactionTemplate transaction() { return new TransactionTemplate(context.getBean(PlatformTransactionManager.class)); }

    @Test void realRegistrationNormalizesStoresIndependentContactAndHashesPassword() {
        assertTrue(AopUtils.isAopProxy(auth)); assertTrue(AopUtils.isAopProxy(userService));
        var request = request(" Account_One ", 9001); request.setContactMobile("13900000001");
        var result = service.register(request, "peer");
        var row = user(9001, result.getUserId());
        assertEquals(9001L, result.getTenantId()); assertEquals(9001L, row.getTenantId());
        assertEquals("account_one", row.getLoginName()); assertEquals("account_one", row.getNickname());
        assertNull(row.getMobile()); assertEquals("13900000001", row.getContactMobile());
        assertEquals("supplier", row.getMemberIdentity()); assertEquals(0, row.getStatus());
        assertNotEquals(request.getPassword(), row.getPassword()); assertTrue(encoder.matches(request.getPassword(), row.getPassword()));
        assertNotNull(row.getLoginDate()); assertEquals(1, count("member_mall_customer_affiliation"));
        verify(producer).sendUserCreateMessage(result.getUserId());
        verify(challenges).consume(9001L, "account_one", "a".repeat(32), "b".repeat(32), "ABC23", "peer");
        verify(logs).createLoginLog(argThat(log -> "account_one".equals(log.getUsername()) && result.getUserId().equals(log.getUserId())));
        assertEquals(9002L, TenantContextHolder.getTenantId()); assertTrue(TenantContextHolder.isIgnore());
    }

    @Test void optionalPhoneRemainsNullAndPublicAndRpcProfilesDoNotInventIdentityPhone() {
        var request = request("profile_one", 9001); request.setContactMobile("");
        var result = service.register(request, "peer");
        var row = user(9001, result.getUserId());
        assertNull(row.getContactMobile()); assertNull(row.getMobile());
        var profile = MemberUserConvert.INSTANCE.convert(row, null);
        assertEquals(result.getUserId(), profile.getId()); assertEquals("profile_one", profile.getLoginName());
        assertNull(profile.getMobile()); assertNull(profile.getContactMobile()); assertEquals("supplier", profile.getMemberIdentity());
        var rpc = inTenant(9001, () -> context.getBean(MemberUserApiImpl.class).getUser(result.getUserId()).getCheckedData());
        assertEquals(result.getUserId(), rpc.getId()); assertEquals("profile_one", rpc.getNickname()); assertNull(rpc.getMobile());
        assertEquals("supplier", rpc.getMemberIdentity());
    }

    @Test void userServiceNormalizesWithoutOrchestrationAndCaseSensitiveIndexRejectsCanonicalDuplicate() {
        // Bypass MallImageAuthService entirely: the persistence service must enforce normalization itself.
        var created = inTenant(9001, () -> userService.registerAccountUser(" Direct_Name ", "Test123456", null,
                null, "buyer", "127.0.0.1", 20));
        var stored = user(9001, created.getId());
        assertEquals("direct_name", stored.getLoginName());
        assertEquals("direct_name", stored.getNickname());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM member_user WHERE login_name='direct_name'", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM member_user WHERE login_name='Direct_Name'", Long.class));
        assertThrows(org.springframework.dao.DuplicateKeyException.class,
                () -> inTenant(9001, () -> userService.registerAccountUser("DIRECT_NAME", "Test123456", null,
                        null, "buyer", "127.0.0.1", 20)));
        assertEquals(1, count("member_user"));
        verify(producer, times(1)).sendUserCreateMessage(created.getId());
        verifyNoInteractions(challenges, tokens, logs);
    }

    @Test void contactPhoneCanRepeatAndNeverClaimsOrMergesAnExistingIdentityPhone() {
        jdbc.update("INSERT INTO member_user(tenant_id,mobile,password,status,nickname) VALUES(9001,?,?,0,'legacy')",
                "13900000002", encoder.encode("Legacy123"));
        long legacy = jdbc.queryForObject("SELECT id FROM member_user WHERE mobile=?", Long.class, "13900000002");
        var first = request("contact_one", 9001); first.setContactMobile("13900000002");
        var second = request("contact_two", 9001); second.setContactMobile("13900000002");
        long firstId = service.register(first, "peer").getUserId();
        long secondId = service.register(second, "peer").getUserId();
        assertEquals(3, count("member_user")); assertNotEquals(legacy, firstId); assertNotEquals(firstId, secondId);
        assertEquals(legacy, inTenant(9001, () -> users.selectByMobile("13900000002")).getId());
        assertNull(user(9001, firstId).getMobile()); assertNull(user(9001, secondId).getMobile());
        assertEquals("13900000002", MemberUserConvert.INSTANCE.convert(user(9001, firstId)).getContactMobile());
    }

    @Test void creationMessageIsDeferredUntilOuterTransactionCommits() {
        AtomicReference<Long> id = new AtomicReference<>();
        transaction().executeWithoutResult(status -> {
            id.set(service.register(request("commit_one", 9001), "peer").getUserId());
            assertEquals(1, count("member_user")); assertEquals(1, count("member_mall_customer_affiliation"));
            verifyNoInteractions(producer);
        });
        verify(producer, times(1)).sendUserCreateMessage(id.get());
        assertEquals(1, count("member_user"));
    }

    @Test void outerRollbackDropsMemberAndAffiliationAndNeverPublishesCreationMessage() {
        transaction().executeWithoutResult(status -> {
            service.register(request("rollback_outer", 9001), "peer");
            verifyNoInteractions(producer); status.setRollbackOnly();
        });
        assertEquals(0, count("member_user")); assertEquals(0, count("member_mall_customer_affiliation"));
        verifyNoInteractions(producer);
        verify(challenges, times(1)).consume(anyLong(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test void tokenRpcFailureRollsBackActualUserInsertAndDoesNotSendMq() {
        doThrow(new IllegalStateException("isolated token RPC failure")).when(tokens).createAccessToken(any());
        assertThrows(IllegalStateException.class, () -> service.register(request("rollback_token", 9001), "peer"));
        assertEquals(0, count("member_user")); assertEquals(0, count("member_mall_customer_affiliation"));
        verifyNoInteractions(producer);
        verify(challenges, times(1)).consume(anyLong(), anyString(), anyString(), anyString(), anyString(), anyString());
        assertEquals(9002L, TenantContextHolder.getTenantId()); assertTrue(TenantContextHolder.isIgnore());
    }

    @Test void invalidPlatformOwnerRollsBackActualUserBeforeTokenOrLogRpc() {
        when(enterprises.getCustomerOwner(101L)).thenReturn(CommonResult.success(null));
        assertThrows(ServiceException.class, () -> service.register(request("rollback_owner", 9001), "peer"));
        assertEquals(0, count("member_user")); assertEquals(0, count("member_mall_customer_affiliation"));
        verifyNoInteractions(tokens, logs, producer);
    }

    @Test void sameLoginNameInTwoTenantsHasDifferentMembersPasswordsAndIsolatedQueries() {
        var first = service.register(request("shared_name", 9001), "peer");
        var secondRequest = request(" SHARED_NAME ", 9002);
        secondRequest.setPassword("Other123456"); secondRequest.setConfirmPassword("Other123456");
        var second = service.register(secondRequest, "peer");
        assertNotEquals(first.getUserId(), second.getUserId()); assertEquals(2, count("member_user"));
        assertEquals(1, count("member_mall_customer_affiliation"));
        assertEquals(first.getUserId(), inTenant(9001, () -> users.selectByLoginName("shared_name")).getId());
        assertEquals(second.getUserId(), inTenant(9002, () -> users.selectByLoginName("shared_name")).getId());
        assertNull(user(9001, second.getUserId())); assertNull(user(9002, first.getUserId()));
        assertEquals(first.getUserId(), inTenant(9001, () -> auth.loginWithAccountPassword("SHARED_NAME", "Test123456")).getUserId());
        assertEquals(second.getUserId(), inTenant(9002, () -> auth.loginWithAccountPassword("shared_name", "Other123456")).getUserId());
        assertThrows(ServiceException.class, () -> inTenant(9002, () -> auth.loginWithAccountPassword("shared_name", "Test123456")));
    }

    @Test void databaseUniqueIndexReservesSoftDeletedLoginNamesWithoutRecreatingThem() {
        var first = service.register(request("reserved_name", 9001), "peer");
        jdbc.update("UPDATE member_user SET deleted=TRUE WHERE id=?", first.getUserId());
        clearInvocations(producer, tokens, logs);
        var error = assertThrows(ServiceException.class, () -> service.register(request(" RESERVED_NAME ", 9001), "peer"));
        assertEquals(MallAccountPolicy.ACCOUNT_USED.getCode(), error.getCode()); assertEquals(1, count("member_user"));
        verifyNoInteractions(producer, tokens, logs);
    }

    @Test void concurrentCaseVariantsProduceOneAccountOneAffiliationAndOneCommitMessage() throws Exception {
        CountDownLatch ready = new CountDownLatch(6), start = new CountDownLatch(1);
        ArrayList<Future<Boolean>> results = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(6)) {
            for (int i = 0; i < 6; i++) {
                final String name = i % 2 == 0 ? "parallel_name" : " PARALLEL_NAME ";
                results.add(pool.submit(() -> {
                    TenantContextHolder.setTenantId(9001L); TenantContextHolder.setIgnore(false); requestContext();
                    try {
                        ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS));
                        try { service.register(request(name, 9001), "parallel-peer"); return true; }
                        catch (ServiceException error) { assertEquals(MallAccountPolicy.ACCOUNT_USED.getCode(), error.getCode()); return false; }
                    } finally { TenantContextHolder.clear(); RequestContextHolder.resetRequestAttributes(); }
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            int successes = 0;
            for (var result : results) if (result.get(20, TimeUnit.SECONDS)) successes++;
            assertEquals(1, successes);
        } finally { start.countDown(); }
        assertEquals(1, count("member_user")); assertEquals(1, count("member_mall_customer_affiliation"));
        verify(producer, times(1)).sendUserCreateMessage(anyLong()); verify(tokens, times(1)).createAccessToken(any());
        verify(challenges, times(6)).consume(anyLong(), eq("parallel_name"), anyString(), anyString(), anyString(), anyString());
    }

    @Test void disabledImagePolicyCannotCreateUserThroughUnderlyingAuthService() {
        when(policy.isEnabled()).thenReturn(false);
        assertThrows(ServiceException.class, () -> inTenant(9001, () -> auth.registerWithVerifiedImage(request("disabled_name", 9001))));
        assertEquals(0, count("member_user")); verifyNoInteractions(enterprises, tokens, logs, producer);
    }

    @Configuration(proxyBeanMethods = false) @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean DataSource dataSource() {
            var ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:mall-image-register-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
            return ds;
        }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            var factory = new MallAuthTransactionTest.Config().sqlSessionFactory(ds);
            factory.getConfiguration().addMapper(MallCustomerAffiliationMapper.class); return factory;
        }
        @Bean MemberUserMapper users(SqlSessionFactory factory) { return new SqlSessionTemplate(factory).getMapper(MemberUserMapper.class); }
        @Bean MallCustomerAffiliationMapper affiliations(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory).getMapper(MallCustomerAffiliationMapper.class);
        }
        @Bean MallTenantProperties tenants() { return new MallTenantProperties(); }
        @Bean MallTenantAuthProperties flags() { return new MallTenantAuthProperties(); }
        @Bean ProductEnterpriseCustomerApi enterprises() { return mock(ProductEnterpriseCustomerApi.class); }
        @Bean MallCustomerAffiliationService customers(MallTenantProperties tenants, MallTenantAuthProperties auth,
                ProductEnterpriseCustomerApi enterprises, MallCustomerAffiliationMapper affiliations) {
            return new MallCustomerAffiliationService(tenants, auth, enterprises, affiliations);
        }
        @Bean MemberAuthServiceImpl auth() { return new MemberAuthServiceImpl(); }
        @Bean MemberUserServiceImpl userService() { return new MemberUserServiceImpl(); }
        @Bean MemberUserApiImpl memberApi() { return new MemberUserApiImpl(); }
        @Bean PasswordEncoder passwords() { return new BCryptPasswordEncoder(4); }
        @Bean MemberUserProducer producer() { return mock(MemberUserProducer.class); }
        @Bean OAuth2TokenCommonApi tokens() { return mock(OAuth2TokenCommonApi.class); }
        @Bean LoginLogApi logs() { return mock(LoginLogApi.class); }
        @Bean SmsCodeApi sms() { return mock(SmsCodeApi.class); }
        @Bean SocialUserApi socialUser() { return mock(SocialUserApi.class); }
        @Bean SocialClientApi socialClient() { return mock(SocialClientApi.class); }
        @Bean MallAccountPolicy policy() { return mock(MallAccountPolicy.class); }
        @Bean MallImageChallengeService challenges() { return mock(MallImageChallengeService.class); }
        @Bean MemberPlatformAccountService platformAccounts() { return mock(MemberPlatformAccountService.class); }
        @Bean MallImageAuthService service(MallAccountPolicy policy, MallImageChallengeService challenges,
                MemberAuthServiceImpl auth, MemberPlatformAccountService platformAccounts) {
            return new MallImageAuthService(policy, challenges, auth, platformAccounts);
        }
    }
}
