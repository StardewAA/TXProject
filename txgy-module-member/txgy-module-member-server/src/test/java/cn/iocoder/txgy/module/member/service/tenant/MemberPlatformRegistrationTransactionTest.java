package cn.iocoder.txgy.module.member.service.tenant;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.txgy.module.member.dal.mysql.platform.MemberPlatformAccountMapper;
import cn.iocoder.txgy.module.member.dal.mysql.platform.MemberPlatformAddressMapper;
import cn.iocoder.txgy.module.member.dal.mysql.platform.MemberPlatformTenantMembershipMapper;
import cn.iocoder.txgy.module.member.service.platform.MemberPlatformAccountService;
import cn.iocoder.txgy.module.member.service.platform.MemberPlatformAccountServiceImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.PLATFORM_ACCOUNT_IDENTITY_CONFLICT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemberPlatformRegistrationTransactionTest {

    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private MemberPlatformAccountService service;
    private TransactionTemplate transactions;

    @BeforeAll
    void start() {
        context = new AnnotationConfigApplicationContext(Config.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        service = context.getBean(MemberPlatformAccountService.class);
        transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        jdbc.execute("""
            CREATE TABLE member_user (
              id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, mobile VARCHAR(30), login_name VARCHAR(20),
              password VARCHAR(100), status INT NOT NULL, deleted BOOLEAN DEFAULT FALSE)
            """);
        jdbc.execute("""
            CREATE TABLE member_platform_account (
              id BIGINT AUTO_INCREMENT PRIMARY KEY, platform_no VARCHAR(32) NOT NULL,
              credential_type VARCHAR(16) NOT NULL, credential_identifier VARCHAR(64) NOT NULL,
              login_name VARCHAR(64), mobile VARCHAR(32),
              canonical_member_id BIGINT NOT NULL, status INT NOT NULL,
              creator VARCHAR(64), create_time TIMESTAMP, updater VARCHAR(64), update_time TIMESTAMP,
              deleted BOOLEAN DEFAULT FALSE,
              UNIQUE(platform_no), UNIQUE(credential_type,credential_identifier,deleted),
              UNIQUE(login_name), UNIQUE(mobile),
              UNIQUE(canonical_member_id,deleted))
            """);
        jdbc.execute("""
            CREATE TABLE member_platform_account_link (
              id BIGINT AUTO_INCREMENT PRIMARY KEY, platform_account_id BIGINT,
              legacy_member_id BIGINT NOT NULL, source_tenant_id BIGINT NOT NULL,
              link_status VARCHAR(32) NOT NULL, conflict_reason VARCHAR(512),
              link_origin VARCHAR(32) NOT NULL DEFAULT 'LEGACY_UNVERIFIED', identity_review_no VARCHAR(64),
              identity_verified_by VARCHAR(64), identity_verified_at TIMESTAMP,
              creator VARCHAR(64), create_time TIMESTAMP, updater VARCHAR(64), update_time TIMESTAMP,
              deleted BOOLEAN DEFAULT FALSE,
              UNIQUE(source_tenant_id,legacy_member_id,deleted))
            """);
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM member_platform_account_link");
        jdbc.update("DELETE FROM member_platform_account");
        jdbc.update("DELETE FROM member_user");
    }

    @AfterAll
    void close() {
        if (context != null) {
            jdbc.execute("SHUTDOWN");
            context.close();
        }
    }

    @Test
    void registrationAccountAndLinkCommitTogether() {
        AtomicReference<Long> accountId = new AtomicReference<>();
        transactions.executeWithoutResult(status -> {
            insertMember(81L, 9001L, "buyer_1", null, "hash-a");
            accountId.set(service.createForRegistration(member(81L, "buyer_1", null), 9001L)
                    .getPlatformAccountId());
        });

        assertNotNull(accountId.get());
        assertEquals(1L, count("member_user"));
        assertEquals(1L, count("member_platform_account"));
        assertEquals(1L, count("member_platform_account_link"));
        assertEquals("READY", jdbc.queryForObject(
                "SELECT link_status FROM member_platform_account_link", String.class));
        assertEquals("SELF_REGISTRATION", jdbc.queryForObject(
                "SELECT link_origin FROM member_platform_account_link", String.class));
    }

    @Test
    void downstreamFailureRollsBackMemberAccountAndLinkTogether() {
        assertThrows(IllegalStateException.class, () -> transactions.executeWithoutResult(status -> {
            insertMember(81L, 9001L, "buyer_1", null, "hash-a");
            service.createForRegistration(member(81L, "buyer_1", null), 9001L);
            throw new IllegalStateException("later registration step failed");
        }));

        assertEquals(0L, count("member_user"));
        assertEquals(0L, count("member_platform_account"));
        assertEquals(0L, count("member_platform_account_link"));
    }

    @Test
    void conflictingIdentifierRollsBackOnlyTheNewRegistration() {
        insertMember(80L, 9002L, "buyer_1", null, "other-hash");

        ServiceException error = assertThrows(ServiceException.class,
                () -> transactions.executeWithoutResult(status -> {
                    insertMember(81L, 9001L, "buyer_1", null, "hash-a");
                    service.createForRegistration(member(81L, "buyer_1", null), 9001L);
                }));

        assertEquals(PLATFORM_ACCOUNT_IDENTITY_CONFLICT.getCode(), error.getCode());
        assertEquals(1L, count("member_user"));
        assertEquals(0L, count("member_platform_account"));
        assertEquals(0L, count("member_platform_account_link"));
    }

    @Test
    void secondaryMobileConflictRollsBackTheWholeRegistration() {
        insertMember(80L, 9002L, "other_buyer", "13900009991", "other-hash");

        ServiceException error = assertThrows(ServiceException.class,
                () -> transactions.executeWithoutResult(status -> {
                    insertMember(81L, 9001L, "buyer_1", "13900009991", "hash-a");
                    service.createForRegistration(member(81L, "buyer_1", "13900009991"), 9001L);
                }));

        assertEquals(PLATFORM_ACCOUNT_IDENTITY_CONFLICT.getCode(), error.getCode());
        assertEquals(1L, count("member_user"));
        assertEquals(0L, count("member_platform_account"));
        assertEquals(0L, count("member_platform_account_link"));
    }

    @Test
    void publicTenantAndCallsWithoutRegistrationTransactionAreRejected() {
        MemberUserDO member = member(81L, "buyer_1", null);
        assertThrows(IllegalTransactionStateException.class,
                () -> service.createForRegistration(member, 9001L));
        ServiceException publicTenant = assertThrows(ServiceException.class,
                () -> transactions.executeWithoutResult(status -> {
                    insertMember(81L, 3000L, "buyer_1", null, "hash-a");
                    service.createForRegistration(member, 3000L);
                }));
        assertEquals(PLATFORM_ACCOUNT_IDENTITY_CONFLICT.getCode(), publicTenant.getCode());
        assertEquals(0L, count("member_user"));
    }

    private void insertMember(long id, long tenantId, String loginName, String mobile, String password) {
        jdbc.update("INSERT INTO member_user(id,tenant_id,mobile,login_name,password,status,deleted) "
                        + "VALUES(?,?,?,?,?,0,FALSE)", id, tenantId, mobile, loginName, password);
    }

    private static MemberUserDO member(long id, String loginName, String mobile) {
        return new MemberUserDO().setId(id).setLoginName(loginName).setMobile(mobile)
                .setPassword("hash-a").setStatus(0);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean
        DataSource dataSource() {
            JdbcDataSource source = new JdbcDataSource();
            source.setURL("jdbc:h2:mem:platform-registration-" + UUID.randomUUID()
                    + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
            return source;
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactory factory = new MallAuthTransactionTest.Config().sqlSessionFactory(dataSource);
            factory.getConfiguration().addMapper(MemberPlatformAccountMapper.class);
            return factory;
        }

        @Bean
        MemberPlatformAccountMapper accountMapper(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory).getMapper(MemberPlatformAccountMapper.class);
        }

        @Bean
        MemberPlatformTenantMembershipMapper membershipMapper() {
            return mock(MemberPlatformTenantMembershipMapper.class);
        }

        @Bean
        MemberPlatformAddressMapper addressMapper() {
            return mock(MemberPlatformAddressMapper.class);
        }

        @Bean
        MemberPlatformAccountService platformAccountService() {
            return new MemberPlatformAccountServiceImpl();
        }
    }
}
