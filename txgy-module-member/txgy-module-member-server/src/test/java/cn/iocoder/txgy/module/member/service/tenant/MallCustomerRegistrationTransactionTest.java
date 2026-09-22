package cn.iocoder.txgy.module.member.service.tenant;

import cn.iocoder.txgy.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.txgy.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.member.controller.app.auth.vo.AppAuthRegisterReqVO;
import cn.iocoder.txgy.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.txgy.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.txgy.module.member.dal.mysql.tenant.MallCustomerAffiliationMapper;
import cn.iocoder.txgy.module.member.framework.tenant.config.*;
import cn.iocoder.txgy.module.member.service.auth.MemberAuthServiceImpl;
import cn.iocoder.txgy.module.member.service.user.MemberUserService;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.*;
import javax.sql.DataSource;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Isolated H2 + real Spring transaction, real member/affiliation mappers and tenant interceptor.
 * External product validation, user creation orchestration and OAuth RPC are test doubles, not live services. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MallCustomerRegistrationTransactionTest {
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private MemberAuthServiceImpl auth;
    private MallCustomerAffiliationService customers;
    private MemberUserMapper users;
    private ProductEnterpriseCustomerApi enterprises;
    private OAuth2TokenCommonApi tokens;
    private MallTenantProperties properties;
    private MallTenantAuthProperties flags;

    @BeforeAll void start() {
        context = new AnnotationConfigApplicationContext(Config.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class)); auth=context.getBean(MemberAuthServiceImpl.class);
        customers=context.getBean(MallCustomerAffiliationService.class); users=context.getBean(MemberUserMapper.class);
        enterprises=context.getBean(ProductEnterpriseCustomerApi.class); tokens=context.getBean(OAuth2TokenCommonApi.class);
        properties=context.getBean(MallTenantProperties.class); flags=context.getBean(MallTenantAuthProperties.class);
        jdbc.execute("CREATE TABLE member_user (id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL,mobile VARCHAR(30),"
                + "login_name VARCHAR(20),contact_mobile VARCHAR(11),member_identity VARCHAR(20),email VARCHAR(100),password VARCHAR(100),status INT,nickname VARCHAR(100),avatar VARCHAR(255),"
                + "register_ip VARCHAR(64),register_terminal INT,login_ip VARCHAR(64),login_date TIMESTAMP,"
                + "name VARCHAR(100),sex INT,birthday TIMESTAMP,area_id INT,mark VARCHAR(200),point INT,tag_ids VARCHAR(200),"
                + "level_id BIGINT,experience INT,group_id BIGINT,creator VARCHAR(64),updater VARCHAR(64),"
                + "create_time TIMESTAMP,update_time TIMESTAMP,deleted BOOLEAN DEFAULT FALSE,UNIQUE(mobile,tenant_id))");
        jdbc.execute("CREATE TABLE member_mall_customer_affiliation (id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT NOT NULL,"
                + "member_id BIGINT NOT NULL,service_enterprise_id BIGINT NOT NULL,source VARCHAR(32) NOT NULL,"
                + "creator VARCHAR(64),updater VARCHAR(64),create_time TIMESTAMP,update_time TIMESTAMP,deleted BOOLEAN DEFAULT FALSE,"
                + "UNIQUE(tenant_id,member_id))");
    }
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(9001L); TenantContextHolder.setIgnore(false);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        jdbc.update("DELETE FROM member_mall_customer_affiliation"); jdbc.update("DELETE FROM member_user");
        reset(enterprises,tokens,context.getBean(MemberUserService.class),context.getBean(LoginLogApi.class));
        properties.setEnabled(true).setPublicTenantId(9001L).setPlatformEnterpriseId(101L); flags.setEnabled(true);
        when(enterprises.getCustomerOwner(anyLong())).thenAnswer(i -> CommonResult.success(
                new ProductEnterpriseCustomerApi.Owner(i.getArgument(0),TenantContextHolder.getTenantId(),"测试平台",true)));
        when(context.getBean(MemberUserService.class).registerUser(any(),any(),any(),any(),any())).thenAnswer(i -> {
            MemberUserDO row=new MemberUserDO().setId(81L).setMobile("13900009991").setStatus(0).setPassword("test-hash").setNickname("test");
            row.setTenantId(TenantContextHolder.getRequiredTenantId()); users.insert(row); return row;
        });
        when(context.getBean(LoginLogApi.class).createLoginLog(any())).thenReturn(CommonResult.success(true));
        when(tokens.createAccessToken(any())).thenReturn(CommonResult.success(new OAuth2AccessTokenRespDTO()
                .setUserId(81L).setUserType(1).setAccessToken("isolated-access").setRefreshToken("isolated-refresh")));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); RequestContextHolder.resetRequestAttributes(); }
    @AfterAll void close() { if(context!=null) { jdbc.execute("SHUTDOWN"); context.close(); } }
    private AppAuthRegisterReqVO request() { return new AppAuthRegisterReqVO().setMobile("13900009991")
            .setPassword("Test123456").setNickname("test").setAgree(true).setMemberIdentity("supplier"); }
    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class); }
    private void tx(Runnable action) { new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(s->action.run()); }

    @Test void actualRegistrationBindsCustomerBeforeTokenAndDoesNotGrantEnterpriseMembership() {
        assertTrue(AopUtils.isAopProxy(auth)); assertTrue(AopUtils.isAopProxy(customers));
        when(tokens.createAccessToken(any())).thenAnswer(i -> {
            assertEquals(1,count("member_mall_customer_affiliation"));
            return CommonResult.success(new OAuth2AccessTokenRespDTO().setUserId(81L).setUserType(1).setAccessToken("local").setRefreshToken("local-r"));
        });
        assertEquals(9001L,auth.registerWithVerifiedMobile(request()).getTenantId());
        assertEquals(1,count("member_user")); assertEquals(1,count("member_mall_customer_affiliation"));
        assertEquals("supplier",users.selectById(81L).getMemberIdentity()); // application direction, no approved enterprise application is created
        assertEquals(101L,jdbc.queryForObject("SELECT service_enterprise_id FROM member_mall_customer_affiliation",Long.class));
    }
    @Test void invalidOwnerRollsBackNewMemberAndIdentityBeforeAnyTokenOrLoginLog() {
        when(enterprises.getCustomerOwner(101L)).thenReturn(CommonResult.success(null));
        assertThrows(RuntimeException.class,()->auth.registerWithVerifiedMobile(request()));
        assertEquals(0,count("member_user")); assertEquals(0,count("member_mall_customer_affiliation"));
        verifyNoInteractions(tokens,context.getBean(LoginLogApi.class));
    }
    @Test void missingSchemaRollsBackMemberAndDoesNotIssueToken() {
        jdbc.execute("ALTER TABLE member_mall_customer_affiliation RENAME TO customer_saved");
        try {
            assertThrows(RuntimeException.class,()->auth.registerWithVerifiedMobile(request()));
            assertEquals(0,count("member_user")); verifyNoInteractions(tokens);
        } finally { jdbc.execute("ALTER TABLE customer_saved RENAME TO member_mall_customer_affiliation"); }
    }
    @Test void tokenRpcFailureRollsBackLocalMemberAndRelationship() {
        when(tokens.createAccessToken(any())).thenThrow(new IllegalStateException("isolated remote failure"));
        assertThrows(RuntimeException.class,()->auth.registerWithVerifiedMobile(request()));
        assertEquals(0,count("member_user")); assertEquals(0,count("member_mall_customer_affiliation"));
    }
    @Test void legacyDisabledRegistrationWorksWithoutNewTableOrEnterpriseRpc() {
        flags.setEnabled(false); jdbc.execute("ALTER TABLE member_mall_customer_affiliation RENAME TO customer_saved");
        try { auth.registerWithVerifiedMobile(request()); assertEquals(1,count("member_user")); verifyNoInteractions(enterprises); }
        finally { jdbc.execute("ALTER TABLE customer_saved RENAME TO member_mall_customer_affiliation"); }
    }
    @Test void otherTenantKeepsItsOwnAccountWithoutPublicAffiliation() {
        TenantContextHolder.setTenantId(9002L); assertEquals(9002L,auth.registerWithVerifiedMobile(request()).getTenantId());
        assertEquals(0,count("member_mall_customer_affiliation")); verifyNoInteractions(enterprises);
    }
    @Test void samePhoneInAnotherTenantIsNeitherMovedNorBound() {
        jdbc.update("INSERT INTO member_user(id,tenant_id,mobile,status) VALUES(82,9002,'13900009991',0)");
        auth.registerWithVerifiedMobile(request());
        assertEquals(2,count("member_user")); assertEquals(1,count("member_mall_customer_affiliation"));
        assertEquals(9002L,jdbc.queryForObject("SELECT tenant_id FROM member_user WHERE id=82",Long.class));
        assertEquals(81L,jdbc.queryForObject("SELECT member_id FROM member_mall_customer_affiliation",Long.class));
    }
    @Test void originalLegacyRegisterJoinsTransactionWithoutNewSchemaAccess() {
        flags.setEnabled(false);
        when(context.getBean(SmsCodeApi.class).useSmsCode(any())).thenReturn(CommonResult.success(true));
        jdbc.execute("ALTER TABLE member_mall_customer_affiliation RENAME TO customer_saved");
        try { auth.register(request().setCode("1234")); assertEquals(1,count("member_user")); verifyNoInteractions(enterprises); }
        finally { jdbc.execute("ALTER TABLE customer_saved RENAME TO member_mall_customer_affiliation"); }
    }
    @Test void concurrentRetriesCreateExactlyOneRelationship() throws Exception {
        jdbc.update("INSERT INTO member_user(id,tenant_id,status) VALUES(81,9001,0)");
        try(var pool=Executors.newFixedThreadPool(4)) {
            var calls=new java.util.ArrayList<Future<?>>();
            for(int i=0;i<8;i++) calls.add(pool.submit(()-> {
                TenantContextHolder.setTenantId(9001L);
                try { tx(()->customers.bindRegistration(81L)); } finally { TenantContextHolder.clear(); }
            }));
            for(var call:calls) call.get(15,TimeUnit.SECONDS);
        }
        assertEquals(1,count("member_mall_customer_affiliation"));
    }
    @Test void wrongTenantAndDeletedMemberCannotBindEvenWithRealTenantPlugin() {
        jdbc.update("INSERT INTO member_user(id,tenant_id,status) VALUES(81,9002,0)");
        assertThrows(RuntimeException.class,()->tx(()->customers.bindRegistration(81L)));
        jdbc.update("UPDATE member_user SET tenant_id=9001,deleted=TRUE WHERE id=81");
        assertThrows(RuntimeException.class,()->tx(()->customers.bindRegistration(81L)));
        assertEquals(0,count("member_mall_customer_affiliation"));
    }
    @Test void deletedRelationshipCannotBeSilentlyRecreated() {
        jdbc.update("INSERT INTO member_user(id,tenant_id,status) VALUES(81,9001,0)");
        jdbc.update("INSERT INTO member_mall_customer_affiliation(tenant_id,member_id,service_enterprise_id,source,deleted) VALUES(9001,81,101,'PUBLIC_REGISTRATION',TRUE)");
        assertThrows(RuntimeException.class,()->tx(()->customers.bindRegistration(81L)));
        assertEquals(1,count("member_mall_customer_affiliation"));
    }
    @Test void changedConfigurationDoesNotRewriteExistingRelation() {
        auth.registerWithVerifiedMobile(request()); properties.setPlatformEnterpriseId(102L);
        assertThrows(RuntimeException.class,()->tx(()->customers.bindRegistration(81L)));
        assertEquals(101L,jdbc.queryForObject("SELECT service_enterprise_id FROM member_mall_customer_affiliation",Long.class));
    }
    @Test void bindRequiresExistingRegistrationTransaction() {
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,()->customers.bindRegistration(81L));
    }

    @Test void realReadMapperSeparatesTenantsMembersAndExcludesDeletedWithoutWrites() {
        jdbc.update("INSERT INTO member_mall_customer_affiliation(tenant_id,member_id,service_enterprise_id,source) VALUES"
                + "(9001,81,101,'PUBLIC_REGISTRATION'),(9001,82,102,'PUBLIC_REGISTRATION'),(9002,81,103,'PUBLIC_REGISTRATION')");
        var before=jdbc.queryForList("SELECT * FROM member_mall_customer_affiliation ORDER BY id");
        assertEquals("测试平台",customers.getForMember(81L).serviceEnterpriseName()); verify(enterprises).getCustomerOwner(101L);
        TenantContextHolder.setTenantId(9002L); customers.getForMember(81L); verify(enterprises).getCustomerOwner(103L);
        assertEquals(cn.iocoder.txgy.module.member.controller.app.tenant.vo.AppMallCustomerAffiliationRespVO.State.NONE,customers.getForMember(82L).state());
        assertEquals(before,jdbc.queryForList("SELECT * FROM member_mall_customer_affiliation ORDER BY id"));
        TenantContextHolder.setTenantId(9001L); jdbc.update("UPDATE member_mall_customer_affiliation SET deleted=TRUE WHERE tenant_id=9001 AND member_id=81");
        assertEquals(cn.iocoder.txgy.module.member.controller.app.tenant.vo.AppMallCustomerAffiliationRespVO.State.NONE,customers.getForMember(81L).state());
        assertEquals(3,count("member_mall_customer_affiliation")); verifyNoInteractions(tokens,context.getBean(LoginLogApi.class));
    }

    @Test void disabledReadDoesNotNeedTheNewTable() {
        flags.setEnabled(false); jdbc.execute("ALTER TABLE member_mall_customer_affiliation RENAME TO customer_saved");
        try {
            assertEquals(cn.iocoder.txgy.module.member.controller.app.tenant.vo.AppMallCustomerAffiliationRespVO.State.DISABLED,customers.getForMember(81L).state());
            verifyNoInteractions(enterprises,tokens);
        } finally { jdbc.execute("ALTER TABLE customer_saved RENAME TO member_mall_customer_affiliation"); }
    }

    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true)
    static class Config {
        @Bean DataSource dataSource() { var ds=new JdbcDataSource(); ds.setURL("jdbc:h2:mem:customer-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"); return ds; }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            var sf=new MallAuthTransactionTest.Config().sqlSessionFactory(ds);
            sf.getConfiguration().addMapper(MallCustomerAffiliationMapper.class); return sf;
        }
        @Bean MemberUserMapper memberUserMapper(SqlSessionFactory sf) { return new SqlSessionTemplate(sf).getMapper(MemberUserMapper.class); }
        @Bean MallCustomerAffiliationMapper affiliations(SqlSessionFactory sf) { return new SqlSessionTemplate(sf).getMapper(MallCustomerAffiliationMapper.class); }
        @Bean MallTenantProperties tenants() { return new MallTenantProperties(); }
        @Bean MallTenantAuthProperties flags() { return new MallTenantAuthProperties(); }
        @Bean ProductEnterpriseCustomerApi enterprises() { return mock(ProductEnterpriseCustomerApi.class); }
        @Bean MallCustomerAffiliationService customers(MallTenantProperties t,MallTenantAuthProperties a,ProductEnterpriseCustomerApi e,MallCustomerAffiliationMapper m) { return new MallCustomerAffiliationService(t,a,e,m); }
        @Bean MemberAuthServiceImpl auth() { return new MemberAuthServiceImpl(); }
        @Bean MallAccountPolicy accountPolicy() { return mock(MallAccountPolicy.class); }
        @Bean MallImageChallengeService imageChallenges() { return mock(MallImageChallengeService.class); }
        @Bean MemberUserService userService() { return mock(MemberUserService.class); }
        @Bean OAuth2TokenCommonApi tokens() { return mock(OAuth2TokenCommonApi.class); }
        @Bean LoginLogApi logs() { return mock(LoginLogApi.class); }
        @Bean SmsCodeApi sms() { return mock(SmsCodeApi.class); }
        @Bean SocialUserApi socialUser() { return mock(SocialUserApi.class); }
        @Bean SocialClientApi socialClient() { return mock(SocialClientApi.class); }
        @Bean MemberPlatformAccountService platformAccounts() { return mock(MemberPlatformAccountService.class); }
    }
}
