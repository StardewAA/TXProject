package cn.iocoder.txgy.module.member.service.platform;

import cn.iocoder.txgy.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.iocoder.txgy.framework.tenant.config.TenantProperties;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.txgy.module.member.controller.app.user.vo.AppMemberUserUpdateMobileReqVO;
import cn.iocoder.txgy.module.member.controller.admin.user.vo.MemberUserUpdateReqVO;
import cn.iocoder.txgy.module.member.dal.mysql.platform.MemberPlatformAccountMapper;
import cn.iocoder.txgy.module.member.dal.mysql.platform.MemberPlatformAddressMapper;
import cn.iocoder.txgy.module.member.dal.mysql.platform.MemberPlatformTenantMembershipMapper;
import cn.iocoder.txgy.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.txgy.module.member.mq.producer.user.MemberUserProducer;
import cn.iocoder.txgy.module.member.service.user.MemberUserServiceImpl;
import cn.iocoder.txgy.module.system.api.sms.SmsCodeApi;
import cn.iocoder.txgy.module.system.api.social.SocialClientApi;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.sql.DataSource;
import java.util.UUID;

import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.PLATFORM_ACCOUNT_IDENTITY_UPDATE_CONFLICT;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.AUTH_LOGIN_BAD_CREDENTIALS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/** Real local transactions, tenant interception and unique indexes on an isolated H2 database. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemberPlatformMobileSyncTransactionTest {

    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private MemberUserServiceImpl users;
    private MemberPlatformAccountServiceImpl platformAccounts;
    private MemberPlatformAccountMapper platformAccountMapper;
    private MemberPlatformAddressMapper platformAddressMapper;
    private SmsCodeApi sms;

    @BeforeAll
    void start() {
        context = new AnnotationConfigApplicationContext(Config.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        users = context.getBean(MemberUserServiceImpl.class);
        platformAccounts = context.getBean(MemberPlatformAccountServiceImpl.class);
        platformAccountMapper = context.getBean(MemberPlatformAccountMapper.class);
        platformAddressMapper = context.getBean(MemberPlatformAddressMapper.class);
        sms = context.getBean(SmsCodeApi.class);
        jdbc.execute("CREATE TABLE member_user (id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT NOT NULL,"
                + "mobile VARCHAR(32),login_name VARCHAR(64),contact_mobile VARCHAR(32),member_identity VARCHAR(32),"
                + "email VARCHAR(100),password VARCHAR(100),status INT,nickname VARCHAR(64),avatar VARCHAR(255),"
                + "register_ip VARCHAR(64),register_terminal INT,login_ip VARCHAR(64),login_date TIMESTAMP,name VARCHAR(100),"
                + "sex INT,birthday TIMESTAMP,area_id INT,mark VARCHAR(200),point INT,tag_ids VARCHAR(200),level_id BIGINT,"
                + "experience INT,group_id BIGINT,creator VARCHAR(64),updater VARCHAR(64),create_time TIMESTAMP,"
                + "update_time TIMESTAMP,deleted BOOLEAN DEFAULT FALSE,UNIQUE(mobile,tenant_id))");
        jdbc.execute("CREATE TABLE member_platform_account (id BIGINT AUTO_INCREMENT PRIMARY KEY,platform_no VARCHAR(32),"
                + "credential_type VARCHAR(16),credential_identifier VARCHAR(64),login_name VARCHAR(64),mobile VARCHAR(32),"
                + "canonical_member_id BIGINT,status INT,creator VARCHAR(64),create_time TIMESTAMP,updater VARCHAR(64),"
                + "update_time TIMESTAMP,deleted BOOLEAN DEFAULT FALSE,UNIQUE(mobile))");
        jdbc.execute("CREATE TABLE member_platform_account_link (id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "platform_account_id BIGINT,legacy_member_id BIGINT,source_tenant_id BIGINT,link_status VARCHAR(32),"
                + "conflict_reason VARCHAR(255),link_origin VARCHAR(32) DEFAULT 'LEGACY_UNVERIFIED' NOT NULL,"
                + "identity_review_no VARCHAR(64),identity_verified_by VARCHAR(64),identity_verified_at TIMESTAMP,"
                + "creator VARCHAR(64),create_time TIMESTAMP,updater VARCHAR(64),"
                + "update_time TIMESTAMP,deleted BOOLEAN DEFAULT FALSE,UNIQUE(source_tenant_id,legacy_member_id,deleted))");
        jdbc.execute("CREATE TABLE member_address (id BIGINT PRIMARY KEY,user_id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,"
                + "name VARCHAR(64),mobile VARCHAR(32),area_id BIGINT,detail_address VARCHAR(255),"
                + "longitude DECIMAL(10,6),latitude DECIMAL(10,6),coord_system VARCHAR(16),deleted BOOLEAN DEFAULT FALSE)");
    }

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM member_platform_account_link");
        jdbc.update("DELETE FROM member_platform_account");
        jdbc.update("DELETE FROM member_address");
        jdbc.update("DELETE FROM member_user");
        reset(sms);
        when(sms.useSmsCode(any())).thenReturn(CommonResult.success(true));
        TenantContextHolder.setTenantId(9001L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @AfterAll
    void close() {
        if (context != null) {
            jdbc.execute("SHUTDOWN");
            context.close();
        }
    }

    @Test
    void updatesMemberAndPlatformPrimaryMobileInOneTransaction() {
        seedReadyMobileAccount();
        assertTrue(AopUtils.isAopProxy(users));

        users.updateUserMobile(81L, mobileRequest("13900009992"));

        assertEquals("13900009992", jdbc.queryForObject("SELECT mobile FROM member_user WHERE id=81", String.class));
        assertEquals("13900009992", jdbc.queryForObject(
                "SELECT mobile FROM member_platform_account WHERE id=9", String.class));
        assertEquals("13900009992", jdbc.queryForObject(
                "SELECT credential_identifier FROM member_platform_account WHERE id=9", String.class));
        assertEquals(81L, platformAccounts.resolveLoginRequired("13900009992").getCanonicalMemberId());
        ServiceException oldAlias = assertThrows(ServiceException.class,
                () -> platformAccounts.resolveLoginRequired("13900009991"));
        assertEquals(AUTH_LOGIN_BAD_CREDENTIALS.getCode(), oldAlias.getCode());
    }

    @Test
    void crossTenantConflictRollsBackMemberAndKeepsPlatformAliasUnchanged() {
        seedReadyMobileAccount();
        jdbc.update("INSERT INTO member_user(id,tenant_id,mobile,password,status,nickname,deleted) "
                + "VALUES(82,9002,'13900009992','hash',0,'other',FALSE)");

        ServiceException error = assertThrows(ServiceException.class,
                () -> users.updateUserMobile(81L, mobileRequest("13900009992")));

        assertEquals(PLATFORM_ACCOUNT_IDENTITY_UPDATE_CONFLICT.getCode(), error.getCode());
        assertEquals("13900009991", jdbc.queryForObject("SELECT mobile FROM member_user WHERE id=81", String.class));
        assertEquals("13900009991", jdbc.queryForObject(
                "SELECT mobile FROM member_platform_account WHERE id=9", String.class));
        assertEquals("13900009991", jdbc.queryForObject(
                "SELECT credential_identifier FROM member_platform_account WHERE id=9", String.class));
    }

    @Test
    void linkedPlatformAccountRejectsClearingIdentityMobileAndRollsBack() {
        seedReadyMobileAccount();
        MemberUserUpdateReqVO request = new MemberUserUpdateReqVO();
        request.setId(81L);
        request.setMobile(" ");
        request.setStatus((byte) 0);
        request.setNickname("buyer");

        ServiceException error = assertThrows(ServiceException.class, () -> users.updateUser(request));

        assertEquals(PLATFORM_ACCOUNT_IDENTITY_UPDATE_CONFLICT.getCode(), error.getCode());
        assertEquals("13900009991", jdbc.queryForObject("SELECT mobile FROM member_user WHERE id=81", String.class));
        assertEquals("13900009991", jdbc.queryForObject(
                "SELECT mobile FROM member_platform_account WHERE id=9", String.class));
        assertEquals("13900009991", jdbc.queryForObject(
                "SELECT credential_identifier FROM member_platform_account WHERE id=9", String.class));
        assertEquals(81L, platformAccounts.resolveLoginRequired("13900009991").getCanonicalMemberId());
    }

    @Test
    void multiMemberIdentityGraphRequiresOneSharedManualReview() {
        jdbc.update("INSERT INTO member_platform_account(id,platform_no,credential_type,credential_identifier,login_name,"
                + "canonical_member_id,status,deleted) VALUES(9,'PM81','LOGIN_NAME','buyer_1','buyer_1',81,0,FALSE)");
        jdbc.update("INSERT INTO member_platform_account_link(platform_account_id,legacy_member_id,source_tenant_id,"
                + "link_status,link_origin,deleted) VALUES(9,81,9001,'READY','MIGRATION_UNIQUE',FALSE)");
        jdbc.update("INSERT INTO member_platform_account_link(platform_account_id,legacy_member_id,source_tenant_id,"
                + "link_status,link_origin,deleted) VALUES(9,82,9002,'READY','MIGRATION_UNIQUE',FALSE)");

        assertEquals(1, platformAccountMapper.countUnsafeReadyIdentityGraph(9L));

        jdbc.update("UPDATE member_platform_account_link SET link_origin='MANUAL_IDENTITY_REVIEW',"
                + "identity_review_no='IR-20260921-001' WHERE platform_account_id=9");
        assertEquals(1, platformAccountMapper.countUnsafeReadyIdentityGraph(9L));

        jdbc.update("UPDATE member_platform_account_link SET identity_verified_by='admin',"
                + "identity_verified_at=CURRENT_TIMESTAMP "
                + "WHERE platform_account_id=9");
        assertEquals(0, platformAccountMapper.countUnsafeReadyIdentityGraph(9L));

        jdbc.update("UPDATE member_platform_account_link SET identity_review_no='IR-20260921-002' "
                + "WHERE legacy_member_id=82 AND source_tenant_id=9002");
        assertEquals(1, platformAccountMapper.countUnsafeReadyIdentityGraph(9L));
    }

    @Test
    void crossTenantAddressRequiresTheSameManualIdentityReview() {
        jdbc.update("INSERT INTO member_user(id,tenant_id,login_name,password,status,nickname,deleted) "
                + "VALUES(81,9001,'buyer_1','hash',0,'requester',FALSE)");
        jdbc.update("INSERT INTO member_user(id,tenant_id,login_name,password,status,nickname,deleted) "
                + "VALUES(82,9002,'buyer_2','hash',0,'owner',FALSE)");
        jdbc.update("INSERT INTO member_platform_account(id,platform_no,credential_type,credential_identifier,login_name,"
                + "canonical_member_id,status,deleted) VALUES(9,'PM81','LOGIN_NAME','buyer_1','buyer_1',81,0,FALSE)");
        jdbc.update("INSERT INTO member_platform_account_link(platform_account_id,legacy_member_id,source_tenant_id,"
                + "link_status,link_origin,deleted) VALUES(9,81,9001,'READY','MIGRATION_UNIQUE',FALSE)");
        jdbc.update("INSERT INTO member_platform_account_link(platform_account_id,legacy_member_id,source_tenant_id,"
                + "link_status,link_origin,deleted) VALUES(9,82,9002,'READY','MIGRATION_UNIQUE',FALSE)");
        jdbc.update("INSERT INTO member_address(id,user_id,tenant_id,name,mobile,area_id,detail_address,deleted) "
                + "VALUES(99,82,9002,'owner','13900009992',610100,'高新区 1 号',FALSE)");

        assertNull(platformAddressMapper.selectLinkedAddress(9L, 81L, 9001L, 99L));

        jdbc.update("UPDATE member_platform_account_link SET link_origin='MANUAL_IDENTITY_REVIEW',"
                + "identity_review_no='IR-20260921-001' WHERE platform_account_id=9");
        assertNull(platformAddressMapper.selectLinkedAddress(9L, 81L, 9001L, 99L));

        jdbc.update("UPDATE member_platform_account_link SET identity_verified_by='admin',"
                + "identity_verified_at=CURRENT_TIMESTAMP "
                + "WHERE platform_account_id=9");
        assertNotNull(platformAddressMapper.selectLinkedAddress(9L, 81L, 9001L, 99L));

        jdbc.update("UPDATE member_platform_account_link SET identity_review_no='IR-20260921-002' "
                + "WHERE legacy_member_id=82 AND source_tenant_id=9002");
        assertNull(platformAddressMapper.selectLinkedAddress(9L, 81L, 9001L, 99L));
    }

    private void seedReadyMobileAccount() {
        jdbc.update("INSERT INTO member_user(id,tenant_id,mobile,password,status,nickname,deleted) "
                + "VALUES(81,9001,'13900009991','hash',0,'buyer',FALSE)");
        jdbc.update("INSERT INTO member_platform_account(id,platform_no,credential_type,credential_identifier,mobile,"
                + "canonical_member_id,status,deleted) VALUES(9,'PM81','MOBILE','13900009991','13900009991',81,0,FALSE)");
        jdbc.update("INSERT INTO member_platform_account_link(platform_account_id,legacy_member_id,source_tenant_id,"
                + "link_status,deleted) VALUES(9,81,9001,'READY',FALSE)");
    }

    private static AppMemberUserUpdateMobileReqVO mobileRequest(String mobile) {
        AppMemberUserUpdateMobileReqVO request = new AppMemberUserUpdateMobileReqVO();
        request.setMobile(mobile);
        request.setCode("123456");
        return request;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean
        DataSource dataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:member-platform-mobile-" + UUID.randomUUID()
                    + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
            return dataSource;
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            GlobalConfig global = new GlobalConfig();
            global.setDbConfig(new GlobalConfig.DbConfig().setIdType(IdType.AUTO));
            global.setMetaObjectHandler(new DefaultDBFieldHandler());
            MybatisPlusInterceptor plugins = new MybatisPlusInterceptor();
            plugins.addInnerInterceptor(new TenantLineInnerInterceptor(
                    new TenantDatabaseInterceptor(new TenantProperties())));
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setConfiguration(configuration);
            factory.setGlobalConfig(global);
            factory.setPlugins(plugins);
            SqlSessionFactory result = factory.getObject();
            result.getConfiguration().addMapper(MemberUserMapper.class);
            result.getConfiguration().addMapper(MemberPlatformAccountMapper.class);
            result.getConfiguration().addMapper(MemberPlatformAddressMapper.class);
            return result;
        }

        @Bean
        MemberUserMapper memberUserMapper(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory).getMapper(MemberUserMapper.class);
        }

        @Bean
        MemberPlatformAccountMapper platformAccountMapper(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory).getMapper(MemberPlatformAccountMapper.class);
        }

        @Bean MemberPlatformTenantMembershipMapper membershipMapper() { return mock(MemberPlatformTenantMembershipMapper.class); }
        @Bean
        MemberPlatformAddressMapper addressMapper(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory).getMapper(MemberPlatformAddressMapper.class);
        }
        @Bean MemberPlatformAccountServiceImpl platformAccounts() { return new MemberPlatformAccountServiceImpl(); }
        @Bean MemberUserServiceImpl users() { return new MemberUserServiceImpl(); }
        @Bean SmsCodeApi sms() { return mock(SmsCodeApi.class); }
        @Bean SocialClientApi social() { return mock(SocialClientApi.class); }
        @Bean OAuth2TokenCommonApi tokens() { return mock(OAuth2TokenCommonApi.class); }
        @Bean MemberUserProducer producer() { return mock(MemberUserProducer.class); }
        @Bean PasswordEncoder passwords() { return new BCryptPasswordEncoder(4); }
    }
}
