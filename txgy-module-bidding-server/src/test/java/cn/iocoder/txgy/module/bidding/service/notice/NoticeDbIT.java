package cn.iocoder.txgy.module.bidding.service.notice;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticeSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.notice.NoticeContentDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.notice.NoticeDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.notice.NoticeContentMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.notice.NoticeMapper;
import cn.iocoder.txgy.module.bidding.enums.NoticeStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.NoticeTypeEnum;
import cn.iocoder.txgy.module.bidding.service.notice.search.DevDisabledSearchEngine;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据库层集成测试（真 MySQL：127.0.0.1:3306/ruoyi-vue-pro，见 application-local.yaml）。
 *
 * <p>基建说明：独立 Spring 上下文（只装配数据源+MyBatis-Plus+Service，不启动 nacos/redis/安全），
 * 表由 sql/2026-09-03-notice.sql 预先建好；类名以 IT 结尾 → 不随默认 mvn test 执行，
 * 用 {@code mvn -pl txgy-module-bidding-server -am test -Dtest='NoticeDbIT'} 单独跑（依赖本地 MySQL）。
 * 每个用例 @Transactional 自动回滚，不留脏数据。
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = NoticeDbIT.DbConfig.class)
@Transactional
class NoticeDbIT {

    @Autowired
    private NoticeService noticeService;
    @Autowired
    private NoticeMapper noticeMapper;
    @Autowired
    private NoticeContentMapper noticeContentMapper;

    // ========== 用例 ==========

    @Test
    void create_persistsDraftWithPayloadAndContentV1() {
        Long id = noticeService.createNotice(newReq("BN-IT-0001", "IT 冒烟：招标公告", "正文第一版"));
        NoticeDO saved = noticeMapper.selectById(id);
        assertEquals(NoticeStatusEnum.DRAFT.name(), saved.getStatus());
        assertTrue(saved.getFieldsJson().contains("NOTICE_NAME"), "payload 权威字段应落库");
        assertTrue(!saved.getFieldsJson().contains("NOT_A_FIELD"), "未知字段不得入库");
        // 正文 v1
        NoticeContentDO content = noticeContentMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NoticeContentDO>()
                        .eq(NoticeContentDO::getNoticeId, id)
                        .orderByDesc(NoticeContentDO::getVersion).last("LIMIT 1")).get(0);
        assertEquals(1, content.getVersion());
        assertEquals(64, content.getSha256().length());
    }

    @Test
    void publish_updatesStatusAndRequiresPdf() {
        // 缺 PDF：发布被拒
        Long noPdf = noticeService.createNotice(newReq("BN-IT-0002", "无PDF公告", "正文"));
        ServiceException e = assertThrows(ServiceException.class, () -> noticeService.publishNotice(noPdf));
        assertEquals(Integer.valueOf(1_060_101_006), e.getCode(), "缺 PDF 原文件应被拦截");

        // 齐备后发布成功，并持久化状态与发布时间
        Long ok = noticeService.createNotice(withPdf(newReq("BN-IT-0003", "齐备公告", "正文")));
        noticeService.publishNotice(ok);
        NoticeDO published = noticeMapper.selectById(ok);
        assertEquals(NoticeStatusEnum.PUBLISHED.name(), published.getStatus());
        assertNotNull(published.getPublishTime());
    }

    @Test
    void duplicateNoticeCode_blockedByUniqueKey() {
        noticeService.createNotice(newReq("BN-IT-0004", "唯一编号公告", "正文"));
        assertThrows(DuplicateKeyException.class,
                () -> noticeService.createNotice(newReq("BN-IT-0004", "重复编号公告", "正文")));
    }

    @Test
    void update_contentChanged_appendsVersionNotOverwrite() {
        Long id = noticeService.createNotice(newReq("BN-IT-0005", "版本公告", "第一版正文"));
        NoticeSaveReqVO update = withPdf(newReq("BN-IT-0005", "版本公告", "第二版正文"));
        update.setId(id);
        noticeService.updateNotice(update);
        var contents = noticeContentMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NoticeContentDO>()
                        .eq(NoticeContentDO::getNoticeId, id)
                        .orderByDesc(NoticeContentDO::getVersion));
        assertEquals(2, contents.size(), "正文实质变更应追加 v2");
        assertEquals(2, contents.get(0).getVersion());
        assertEquals("第二版正文", contents.get(0).getContent());
    }

    // ========== 工具 ==========

    private NoticeSaveReqVO newReq(String code, String title, String content) {
        NoticeSaveReqVO reqVO = new NoticeSaveReqVO();
        reqVO.setNoticeCode(code);
        reqVO.setNoticeType(NoticeTypeEnum.TENDER_NOTICE.name());
        reqVO.setTitle(title);
        reqVO.setInternalFlag(false);
        reqVO.setPublicLevel("SOCIAL");
        reqVO.setPublishTime(LocalDateTime.now());
        reqVO.setContent(content);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("NOTICE_NAME", title);
        payload.put("BID_SECTION_CODES", "IT-SEC-001");
        reqVO.setPayload(payload);
        return reqVO;
    }

    private NoticeSaveReqVO withPdf(NoticeSaveReqVO reqVO) {
        reqVO.setPdfFileId(900001L);
        reqVO.setPdfSha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        reqVO.setPdfSize(2048L);
        return reqVO;
    }

    // ========== 独立 Spring 上下文 ==========

    @Configuration
    @MapperScan("cn.iocoder.txgy.module.bidding.dal.mysql")
    @ComponentScan(basePackages = "cn.iocoder.txgy.module.bidding.service.notice")
    @EnableTransactionManagement
    public static class DbConfig {

        @Bean
        public DataSource dataSource() {
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl("jdbc:mysql://127.0.0.1:3306/ruoyi-vue-pro?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true");
            ds.setUsername("root");
            ds.setPassword("123456");
            ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
            return ds;
        }

        @Bean
        public DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        public com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor mybatisPlusInterceptor() {
            com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor interceptor =
                    new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor());
            return interceptor;
        }

        @Bean
        public org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory(DataSource dataSource,
                                                                               com.baomidou.mybatisplus.core.handlers.MetaObjectHandler metaObjectHandler,
                                                                               com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor mybatisPlusInterceptor) throws Exception {
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPlugins(new org.apache.ibatis.plugin.Interceptor[]{mybatisPlusInterceptor});
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            // 审计字段自动填充需经 GlobalConfig 注入 SqlSessionFactory
            com.baomidou.mybatisplus.core.config.GlobalConfig globalConfig = new com.baomidou.mybatisplus.core.config.GlobalConfig();
            globalConfig.setMetaObjectHandler(metaObjectHandler);
            factory.setGlobalConfig(globalConfig);
            return factory.getObject();
        }

        /** 审计字段自动填充（对齐平台 BaseDO 口径：creator/createTime/updater/updateTime） */
        @Bean
        public com.baomidou.mybatisplus.core.handlers.MetaObjectHandler metaObjectHandler() {
            return new com.baomidou.mybatisplus.core.handlers.MetaObjectHandler() {
                @Override
                public void insertFill(org.apache.ibatis.reflection.MetaObject metaObject) {
                    this.strictInsertFill(metaObject, "createTime", java.time.LocalDateTime.class, java.time.LocalDateTime.now());
                    this.strictInsertFill(metaObject, "updateTime", java.time.LocalDateTime.class, java.time.LocalDateTime.now());
                    this.strictInsertFill(metaObject, "creator", String.class, "it");
                    this.strictInsertFill(metaObject, "updater", String.class, "it");
                }

                @Override
                public void updateFill(org.apache.ibatis.reflection.MetaObject metaObject) {
                    this.strictUpdateFill(metaObject, "updateTime", java.time.LocalDateTime.class, java.time.LocalDateTime.now());
                    this.strictUpdateFill(metaObject, "updater", String.class, "it");
                }
            };
        }
    }
}
