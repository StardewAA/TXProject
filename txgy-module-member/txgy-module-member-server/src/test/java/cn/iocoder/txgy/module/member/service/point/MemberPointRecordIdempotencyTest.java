package cn.iocoder.txgy.module.member.service.point;

import cn.iocoder.txgy.framework.common.exception.ErrorCode;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.iocoder.txgy.framework.tenant.config.TenantProperties;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.txgy.module.member.api.point.MemberPointApiImpl;
import cn.iocoder.txgy.module.member.dal.mysql.point.MemberPointRecordMapper;
import cn.iocoder.txgy.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.txgy.module.member.enums.point.MemberPointBizTypeEnum;
import cn.iocoder.txgy.module.member.service.user.MemberUserService;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.*;
import static cn.iocoder.txgy.module.member.enums.point.MemberPointBizTypeEnum.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 真实 Spring 事务代理 + MyBatis 映射 + 隔离 H2 数据库，不调用任何现有数据库/Redis/RPC。
 * 覆盖不同 Service 实例、并发请求与跨事务重试；不以 JVM 锁或 mock 流水证明数据库幂等。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemberPointRecordIdempotencyTest {

    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private MemberPointRecordService service;
    private MemberPointRecordService anotherService;
    private TransactionTemplate tx;

    @BeforeAll
    void startIsolatedDatabase() {
        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        service = context.getBean("memberPointRecordService", MemberPointRecordService.class);
        anotherService = context.getBean("anotherPointRecordService", MemberPointRecordService.class);
        tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        jdbc.execute("CREATE TABLE member_user (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL,"
                + " point INT, deleted BOOLEAN NOT NULL DEFAULT FALSE)");
        jdbc.execute("CREATE TABLE member_point_record (id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + " user_id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, biz_id VARCHAR(255) NOT NULL,"
                + " biz_type INT NOT NULL, point INT NOT NULL, total_point INT NOT NULL,"
                + " title VARCHAR(255) NOT NULL, description VARCHAR(5000),"
                + " creator VARCHAR(64), updater VARCHAR(64), create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                + " update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, deleted BOOLEAN NOT NULL DEFAULT FALSE)");
        jdbc.execute("CREATE INDEX index_userId ON member_point_record(user_id)");
    }

    @BeforeEach
    void resetFixtures() {
        jdbc.update("DELETE FROM member_point_record");
        jdbc.update("DELETE FROM member_user");
        jdbc.update("INSERT INTO member_user(id,tenant_id,point) VALUES(1,1,100),(2,1,100),(3,3000,100)");
        TenantContextHolder.setTenantId(1L);
        TenantContextHolder.setIgnore(false);
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @AfterAll
    void stopIsolatedDatabase() {
        if (context != null) {
            jdbc.execute("SHUTDOWN");
            context.close();
        }
    }

    @ParameterizedTest
    @EnumSource(value = MemberPointBizTypeEnum.class, names = {"ORDER_USE", "ORDER_USE_CANCEL",
            "ORDER_USE_CANCEL_ITEM", "ORDER_GIVE", "ORDER_GIVE_CANCEL", "ORDER_GIVE_CANCEL_ITEM"})
    void everyOrderBusinessIsAppliedOnlyOnceAcrossInstancesAndCommittedTransactions(MemberPointBizTypeEnum type) {
        int delta = type.isAdd() ? 3 : -3;
        assertTrue(AopUtils.isAopProxy(service));
        service.createPointRecord(1L, delta, type, "280");
        // 新 Service 实例也只查询持久化记录，不依赖内存中的防重状态。
        anotherService.createPointRecord(1L, delta, type, "280");
        assertEquals(100 + delta, balance(1));
        assertEquals(1, count());
        assertEquals(100 + delta, jdbc.queryForObject("SELECT total_point FROM member_point_record", Integer.class));
    }

    @Test
    void simultaneousRetriesAcrossInstancesCreditExactlyOnce() throws Exception {
        concurrently(16, index -> (index % 2 == 0 ? service : anotherService)
                .createPointRecord(1L, 3, ORDER_USE_CANCEL, "280"));
        assertEquals(103, balance(1));
        assertEquals(1, count());
    }

    @Test
    void differentBusinessKeysAndNaturalEventsStillSerializeTheirBalanceSnapshots() throws Exception {
        concurrently(16, index -> service.createPointRecord(1L, 1,
                index % 2 == 0 ? ADMIN : SIGN, "same-natural-event-source"));
        assertEquals(116, balance(1));
        assertEquals(16, count());
        assertEquals(16, jdbc.queryForObject("SELECT COUNT(DISTINCT total_point) FROM member_point_record", Integer.class));
        assertEquals(101, jdbc.queryForObject("SELECT MIN(total_point) FROM member_point_record", Integer.class));
        assertEquals(116, jdbc.queryForObject("SELECT MAX(total_point) FROM member_point_record", Integer.class));
    }

    @Test
    void concurrentDifferentOrdersCannotOverdrawBalance() throws Exception {
        jdbc.update("UPDATE member_user SET point=3 WHERE id=1");
        AtomicInteger insufficient = new AtomicInteger();
        concurrently(2, index -> {
            try {
                service.createPointRecord(1L, -3, ORDER_USE, String.valueOf(280 + index));
            } catch (ServiceException error) {
                assertEquals(USER_POINT_NOT_ENOUGH.getCode(), error.getCode());
                insufficient.incrementAndGet();
            }
        });
        assertEquals(0, balance(1));
        assertEquals(1, insufficient.get());
        assertEquals(1, count());
    }

    @Test
    void duplicateDebitDoesNotFailBecauseFirstDebitUsedLastPoints() {
        jdbc.update("UPDATE member_user SET point=3 WHERE id=1");
        service.createPointRecord(1L, -3, ORDER_USE, "280");
        assertDoesNotThrow(() -> anotherService.createPointRecord(1L, -3, ORDER_USE, "280"));
        assertEquals(0, balance(1));
        assertEquals(1, count());
    }

    @Test
    void tenantUserBusinessTypeAndBusinessIdAllBelongToIdempotencyKey() {
        service.createPointRecord(1L, 3, ORDER_USE_CANCEL, "280");
        service.createPointRecord(2L, 3, ORDER_USE_CANCEL, "280");
        service.createPointRecord(1L, 3, ORDER_USE_CANCEL_ITEM, "280");
        service.createPointRecord(1L, 3, ORDER_USE_CANCEL, "281");
        TenantContextHolder.setTenantId(3000L);
        service.createPointRecord(3L, 3, ORDER_USE_CANCEL, "280");
        assertEquals(109, balance(1));
        assertEquals(103, balance(2));
        assertEquals(103, balance(3));
        assertEquals(5, count());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM member_point_record WHERE tenant_id=3000", Integer.class));
    }

    @Test
    void wrongTenantAndDeletedUserCannotChangeBalanceEvenWhenTenantInterceptorIsIgnored() {
        TenantContextHolder.setIgnore(true);
        TenantContextHolder.setTenantId(3000L);
        expect(USER_NOT_EXISTS, () -> service.createPointRecord(1L, 3, ORDER_USE_CANCEL, "280"));
        TenantContextHolder.setTenantId(1L);
        jdbc.update("UPDATE member_user SET deleted=TRUE WHERE id=1");
        expect(USER_NOT_EXISTS, () -> service.createPointRecord(1L, 3, ORDER_USE_CANCEL, "280"));
        expect(USER_NOT_EXISTS, () -> service.createPointRecord(999L, 3, ORDER_USE_CANCEL, "280"));
        assertEquals(100, balance(1));
        assertEquals(0, count());
    }

    @Test
    void explicitTenantIsPersistedAndUsedWhenTenantInterceptorIsIgnored() {
        TenantContextHolder.setIgnore(true);
        TenantContextHolder.setTenantId(3000L);
        service.createPointRecord(3L, 3, ORDER_USE_CANCEL, "280");
        anotherService.createPointRecord(3L, 3, ORDER_USE_CANCEL, "280");
        assertEquals(103, balance(3));
        assertEquals(1, count());
        assertEquals(3000L, jdbc.queryForObject("SELECT tenant_id FROM member_point_record", Long.class));
    }

    @Test
    void legacyRecordsFromAnotherTenantAreNotTreatedAsCurrentTenantFact() {
        legacyRecord(3000, 1, 12, "280", 3, false);
        service.createPointRecord(1L, 3, ORDER_USE_CANCEL, "280");
        assertEquals(103, balance(1));
        assertEquals(2, count());
    }

    @Test
    void fiveHistoricalIdenticalRefundsStayUntouchedWithoutSixthCredit() {
        jdbc.update("UPDATE member_user SET point=115 WHERE id=1");
        for (int i = 0; i < 5; i++) legacyRecord(1, 1, 12, "280", 3, false);
        assertDoesNotThrow(() -> service.createPointRecord(1L, 3, ORDER_USE_CANCEL, "280"));
        assertEquals(115, balance(1));
        assertEquals(5, count());
        assertEquals(15, jdbc.queryForObject("SELECT SUM(point) FROM member_point_record", Integer.class));
    }

    @Test
    void logicallyDeletedHistoryStillPreventsAnotherCredit() {
        legacyRecord(1, 1, 12, "280", 3, true);
        service.createPointRecord(1L, 3, ORDER_USE_CANCEL, "280");
        assertEquals(100, balance(1));
        assertEquals(1, count());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM member_point_record WHERE deleted=TRUE", Integer.class));
    }

    @Test
    void anyHistoricalAmountConflictRejectsEvenIfAnotherRecordMatches() {
        legacyRecord(1, 1, 12, "280", 3, false);
        legacyRecord(1, 1, 12, "280", 5, true);
        expect(POINT_RECORD_BIZ_CONFLICT, () -> service.createPointRecord(1L, 3, ORDER_USE_CANCEL, "280"));
        expect(POINT_RECORD_BIZ_CONFLICT, () -> service.createPointRecord(1L, 5, ORDER_USE_CANCEL, "280"));
        assertEquals(100, balance(1));
        assertEquals(2, count());
    }

    @Test
    void sameKeyDifferentAmountIsRejectedWithoutAdditionalBalanceChange() {
        service.createPointRecord(1L, 3, ORDER_USE_CANCEL, "280");
        expect(POINT_RECORD_BIZ_CONFLICT, () -> anotherService.createPointRecord(1L, 4, ORDER_USE_CANCEL, "280"));
        assertEquals(103, balance(1));
        assertEquals(1, count());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "null", "abc", "-1", "0", "01", "+1", "1.0", "1 ", "9223372036854775808"})
    void missingOrNonCanonicalOrderIdentifierIsRejected(String bizId) {
        expect(POINT_RECORD_REQUEST_INVALID, () -> service.createPointRecord(1L, 3, ORDER_USE_CANCEL, bizId));
        assertEquals(100, balance(1));
        assertEquals(0, count());
    }

    @ParameterizedTest
    @EnumSource(value = MemberPointBizTypeEnum.class, names = {"ORDER_USE", "ORDER_USE_CANCEL",
            "ORDER_USE_CANCEL_ITEM", "ORDER_GIVE", "ORDER_GIVE_CANCEL", "ORDER_GIVE_CANCEL_ITEM"})
    void orderBusinessRejectsWrongSignAndZero(MemberPointBizTypeEnum type) {
        int wrongSign = type.isAdd() ? -3 : 3;
        expect(POINT_RECORD_REQUEST_INVALID, () -> service.createPointRecord(1L, wrongSign, type, "280"));
        expect(POINT_RECORD_REQUEST_INVALID, () -> service.createPointRecord(1L, 0, type, "280"));
        assertEquals(100, balance(1));
        assertEquals(0, count());
    }

    @Test
    void missingTenantUserAmountOrTypeIsRejectedBeforeAnyMutation() {
        expect(POINT_RECORD_REQUEST_INVALID, () -> service.createPointRecord(null, 3, ORDER_USE_CANCEL, "280"));
        expect(POINT_RECORD_REQUEST_INVALID, () -> service.createPointRecord(0L, 3, ORDER_USE_CANCEL, "280"));
        expect(POINT_RECORD_REQUEST_INVALID, () -> service.createPointRecord(1L, null, ORDER_USE_CANCEL, "280"));
        expect(POINT_RECORD_BIZ_NOT_SUPPORT, () -> service.createPointRecord(1L, 3, null, "280"));
        TenantContextHolder.clear();
        expect(POINT_RECORD_REQUEST_INVALID, () -> service.createPointRecord(1L, 3, ORDER_USE_CANCEL, "280"));
        assertEquals(100, balance(1));
        assertEquals(0, count());
    }

    @Test
    void insufficientBalanceThrowsAndRetryAfterFundingAppliesOnce() {
        jdbc.update("UPDATE member_user SET point=1 WHERE id=1");
        expect(USER_POINT_NOT_ENOUGH, () -> service.createPointRecord(1L, -3, ORDER_USE, "280"));
        assertEquals(1, balance(1));
        assertEquals(0, count());
        service.createPointRecord(1L, 10, ADMIN, "admin-id");
        service.createPointRecord(1L, -3, ORDER_USE, "280");
        anotherService.createPointRecord(1L, -3, ORDER_USE, "280");
        assertEquals(8, balance(1));
        assertEquals(2, count());
    }

    @Test
    void pointApiPropagatesInsufficientBalanceInsteadOfReturningSuccessfulResult() {
        var api = new MemberPointApiImpl();
        ReflectionTestUtils.setField(api, "memberPointRecordService", service);
        jdbc.update("UPDATE member_user SET point=1 WHERE id=1");
        expect(USER_POINT_NOT_ENOUGH, () -> api.reducePoint(1L, 3, ORDER_USE.getType(), "280"));
        assertEquals(1, balance(1));
        assertEquals(0, count());
    }

    @Test
    void refundApiRetriesReturnSuccessOnlyForSamePersistentBusinessAmount() {
        var api = new MemberPointApiImpl();
        ReflectionTestUtils.setField(api, "memberPointRecordService", service);
        assertTrue(api.addPoint(1L, 3, ORDER_USE_CANCEL.getType(), "280").getCheckedData());
        assertTrue(api.addPoint(1L, 3, ORDER_USE_CANCEL.getType(), "280").getCheckedData());
        expect(POINT_RECORD_BIZ_CONFLICT, () -> api.addPoint(1L, 5, ORDER_USE_CANCEL.getType(), "280"));
        assertEquals(103, balance(1));
        assertEquals(1, count());
    }

    @Test
    void adminCanRepeatSameSourceIncludingDebitAndZeroRemainsNoop() {
        service.createPointRecord(1L, -3, ADMIN, "1");
        service.createPointRecord(1L, -3, ADMIN, "1");
        service.createPointRecord(1L, 0, ADMIN, "1");
        assertEquals(94, balance(1));
        assertEquals(2, count());
    }

    @Test
    void nullBalanceCanBeCreditedAndIntegerOverflowIsRejected() {
        jdbc.update("UPDATE member_user SET point=NULL WHERE id=1");
        service.createPointRecord(1L, 3, ORDER_USE_CANCEL, "280");
        assertEquals(3, balance(1));
        jdbc.update("UPDATE member_user SET point=? WHERE id=1", Integer.MAX_VALUE);
        expect(POINT_RECORD_BALANCE_OVERFLOW, () -> service.createPointRecord(1L, 3, ORDER_USE_CANCEL, "281"));
        assertEquals(Integer.MAX_VALUE, balance(1));
        assertEquals(1, count());
    }

    @Test
    void ledgerInsertFailureRollsBackBalanceAndDoesNotPoisonRetry() {
        jdbc.execute("ALTER TABLE member_point_record ADD CONSTRAINT reject_fixture CHECK (biz_id <> '999999')");
        try {
            assertThrows(RuntimeException.class, () -> service.createPointRecord(1L, 3, ORDER_USE_CANCEL, "999999"));
            assertEquals(100, balance(1));
            assertEquals(0, count());
        } finally {
            jdbc.execute("ALTER TABLE member_point_record DROP CONSTRAINT reject_fixture");
        }
        service.createPointRecord(1L, 3, ORDER_USE_CANCEL, "999999");
        assertEquals(103, balance(1));
        assertEquals(1, count());
    }

    @Test
    void outerMemberTransactionRollbackRollsBackBothBalanceAndBusinessFact() {
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
            service.createPointRecord(1L, 3, ORDER_USE_CANCEL, "280");
            throw new IllegalStateException("isolated fixture failure after credit");
        }));
        assertEquals(100, balance(1));
        assertEquals(0, count());
        anotherService.createPointRecord(1L, 3, ORDER_USE_CANCEL, "280");
        assertEquals(103, balance(1));
        assertEquals(1, count());
    }

    @Test
    void memberRowLockLivesUntilCommitThenWaitingRequestSeesBusinessFact() throws Exception {
        var credited = new CountDownLatch(1);
        var releaseCommit = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = workers.submit(() -> inTenant(() -> tx.executeWithoutResult(status -> {
                service.createPointRecord(1L, 3, ORDER_USE_CANCEL, "280");
                credited.countDown();
                await(releaseCommit);
            })));
            assertTrue(credited.await(5, TimeUnit.SECONDS));
            Future<?> second = workers.submit(() -> inTenant(() -> {
                secondStarted.countDown();
                anotherService.createPointRecord(1L, 3, ORDER_USE_CANCEL, "280");
            }));
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> second.get(150, TimeUnit.MILLISECONDS));
            releaseCommit.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertEquals(103, balance(1));
            assertEquals(1, count());
        } finally {
            releaseCommit.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private void concurrently(int requests, java.util.function.IntConsumer request) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(Math.min(8, requests));
        var start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < requests; i++) {
                int index = i;
                futures.add(workers.submit(() -> inTenant(() -> {
                    await(start);
                    request.accept(index);
                })));
            }
            start.countDown();
            for (Future<?> future : futures) future.get(10, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static void inTenant(Runnable action) {
        TenantContextHolder.setTenantId(1L);
        TenantContextHolder.setIgnore(false);
        try { action.run(); } finally { TenantContextHolder.clear(); }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("fixture synchronization timeout");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }

    private int balance(long userId) {
        return jdbc.queryForObject("SELECT point FROM member_user WHERE id=?", Integer.class, userId);
    }

    private int count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM member_point_record", Integer.class);
    }

    private void legacyRecord(long tenantId, long userId, int type, String bizId, int point, boolean deleted) {
        jdbc.update("INSERT INTO member_point_record(tenant_id,user_id,biz_type,biz_id,point,total_point,title,deleted)"
                + " VALUES(?,?,?,?,?,100,'isolated legacy fixture',?)", tenantId, userId, type, bizId, point, deleted);
    }

    private static void expect(ErrorCode expected, Executable action) {
        assertEquals(expected.getCode(), assertThrows(ServiceException.class, action).getCode());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfiguration {
        @Bean
        DataSource dataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:point-idempotency-" + UUID.randomUUID()
                    + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
            return dataSource;
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            var configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            var global = new GlobalConfig();
            global.setDbConfig(new GlobalConfig.DbConfig().setIdType(IdType.AUTO));
            global.setMetaObjectHandler(new DefaultDBFieldHandler());
            var plugins = new MybatisPlusInterceptor();
            plugins.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
            var factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setConfiguration(configuration);
            factory.setGlobalConfig(global);
            factory.setPlugins(plugins);
            SqlSessionFactory sessionFactory = factory.getObject();
            sessionFactory.getConfiguration().addMapper(MemberUserMapper.class);
            sessionFactory.getConfiguration().addMapper(MemberPointRecordMapper.class);
            return sessionFactory;
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory);
        }

        @Bean
        MemberUserMapper memberUserMapper(SqlSessionTemplate session) {
            return session.getMapper(MemberUserMapper.class);
        }

        @Bean
        MemberPointRecordMapper memberPointRecordMapper(SqlSessionTemplate session) {
            return session.getMapper(MemberPointRecordMapper.class);
        }

        @Bean
        MemberUserService memberUserService() { return mock(MemberUserService.class); }

        @Bean
        MemberPointRecordService memberPointRecordService() { return new MemberPointRecordServiceImpl(); }

        @Bean
        MemberPointRecordService anotherPointRecordService() { return new MemberPointRecordServiceImpl(); }
    }
}
