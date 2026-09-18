package cn.iocoder.txgy.module.member.service.tenant;

import cn.iocoder.txgy.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.iocoder.txgy.framework.tenant.config.TenantProperties;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.txgy.module.member.controller.app.tenant.vo.AppMallAuthReqVO.ResetPassword;
import cn.iocoder.txgy.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.txgy.module.member.service.auth.MemberAuthService;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real Spring transactions + tenant-intercepted MyBatis + isolated H2; no existing user/database touched. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MallAuthTransactionTest {
    private AnnotationConfigApplicationContext context; private JdbcTemplate jdbc; private MallTenantAuthService service;
    private OAuth2TokenCommonApi tokens; private MallSmsChallengeService challenges; private PasswordEncoder encoder;
    @BeforeAll void start() {
        context = new AnnotationConfigApplicationContext(Config.class); jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        service = context.getBean(MallTenantAuthService.class); tokens = context.getBean(OAuth2TokenCommonApi.class);
        challenges = context.getBean(MallSmsChallengeService.class); encoder = context.getBean(PasswordEncoder.class);
        jdbc.execute("CREATE TABLE member_user (id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT NOT NULL,mobile VARCHAR(30),"
                + "login_name VARCHAR(20),contact_mobile VARCHAR(11),member_identity VARCHAR(20),email VARCHAR(100),password VARCHAR(100),status INT,nickname VARCHAR(100),avatar VARCHAR(255),"
                + "register_ip VARCHAR(64),register_terminal INT,login_ip VARCHAR(64),login_date TIMESTAMP,"
                + "name VARCHAR(100),sex INT,birthday TIMESTAMP,area_id INT,mark VARCHAR(200),point INT,tag_ids VARCHAR(200),"
                + "level_id BIGINT,experience INT,group_id BIGINT,creator VARCHAR(64),updater VARCHAR(64),"
                + "create_time TIMESTAMP,update_time TIMESTAMP,deleted BOOLEAN DEFAULT FALSE,UNIQUE(mobile,tenant_id))");
    }
    @BeforeEach void reset() {
        org.mockito.Mockito.reset(tokens, challenges); jdbc.update("DELETE FROM member_user");
        jdbc.update("INSERT INTO member_user(id,tenant_id,mobile,password,status) VALUES(71,9001,'13900009991','original-a',0),(72,9002,'13900009991','original-b',0)");
        when(challenges.consume(any(), any(), any(), any(), any(), any())).thenReturn(9001L);
        when(tokens.removeAccessToken(71L, 1)).thenReturn(CommonResult.success(true));
        TenantContextHolder.setTenantId(9002L); TenantContextHolder.setIgnore(true);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @AfterAll void close() { if (context != null) { jdbc.execute("SHUTDOWN"); context.close(); } }
    private ResetPassword request() {
        ResetPassword r = new ResetPassword(); r.setMobile("13900009991"); r.setTenantId(9001L);
        r.setChallengeId("a".repeat(32)); r.setCode("123456"); r.setPassword("NewTest123"); return r;
    }
    private String password(long id) { return jdbc.queryForObject("SELECT password FROM member_user WHERE id=?", String.class, id); }
    @Test void samePhoneResetTouchesOnlyTargetTenantWithRealInterceptorAndHashing() {
        assertTrue(AopUtils.isAopProxy(service)); service.resetPassword(request(), "peer");
        assertTrue(encoder.matches("NewTest123", password(71))); assertEquals("original-b", password(72));
        verify(tokens).removeAccessToken(71L, 1); assertEquals(9002L, TenantContextHolder.getTenantId()); assertTrue(TenantContextHolder.isIgnore());
    }
    @Test void tokenRevocationFailureRollsBackPasswordButDoesNotRestoreConsumedOtp() {
        when(tokens.removeAccessToken(71L, 1)).thenThrow(new IllegalStateException("isolated RPC failure"));
        assertThrows(IllegalStateException.class, () -> service.resetPassword(request(), "peer"));
        assertEquals("original-a", password(71)); assertEquals("original-b", password(72));
        verify(challenges).consume(any(), any(), any(), any(), any(), any());
    }
    @Test void softDeletedUserAndDisabledUserCannotReset() {
        jdbc.update("UPDATE member_user SET deleted=TRUE WHERE id=71");
        assertThrows(RuntimeException.class, () -> service.resetPassword(request(), "peer")); verifyNoInteractions(tokens);
        jdbc.update("UPDATE member_user SET deleted=FALSE,status=1 WHERE id=71");
        assertThrows(RuntimeException.class, () -> service.resetPassword(request(), "peer")); assertEquals("original-a", password(71));
    }
    @Test void uniqueMobileIsTenantScopedAndStillReservedAfterSoftDelete() {
        jdbc.update("UPDATE member_user SET deleted=TRUE WHERE id=71");
        assertThrows(org.springframework.dao.DuplicateKeyException.class, () -> jdbc.update(
                "INSERT INTO member_user(tenant_id,mobile) VALUES(9001,'13900009991')"));
        assertEquals(1, jdbc.update("INSERT INTO member_user(tenant_id,mobile) VALUES(9003,'13900009991')"));
    }
    @Configuration(proxyBeanMethods = false) @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean DataSource dataSource() { var ds = new JdbcDataSource(); ds.setURL("jdbc:h2:mem:mall-auth-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1"); return ds; }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
            var global = new GlobalConfig(); global.setDbConfig(new GlobalConfig.DbConfig().setIdType(IdType.AUTO)); global.setMetaObjectHandler(new DefaultDBFieldHandler());
            var plugins = new MybatisPlusInterceptor(); plugins.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
            var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(config); factory.setGlobalConfig(global); factory.setPlugins(plugins);
            var sf = factory.getObject(); sf.getConfiguration().addMapper(MemberUserMapper.class); return sf;
        }
        @Bean MemberUserMapper users(SqlSessionFactory sf) { return new SqlSessionTemplate(sf).getMapper(MemberUserMapper.class); }
        @Bean MallAuthPolicy policy() { return mock(MallAuthPolicy.class); }
        @Bean MallSmsChallengeService challenges() { return mock(MallSmsChallengeService.class); }
        @Bean MemberAuthService auth() { return mock(MemberAuthService.class); }
        @Bean PasswordEncoder passwords() { return new BCryptPasswordEncoder(4); }
        @Bean OAuth2TokenCommonApi tokens() { return mock(OAuth2TokenCommonApi.class); }
        @Bean MallTenantAuthService service(MallAuthPolicy policy, MallSmsChallengeService challenges, MemberAuthService auth,
                MemberUserMapper users, PasswordEncoder passwords, OAuth2TokenCommonApi tokens) {
            return new MallTenantAuthService(policy, challenges, auth, users, passwords, tokens);
        }
    }
}
