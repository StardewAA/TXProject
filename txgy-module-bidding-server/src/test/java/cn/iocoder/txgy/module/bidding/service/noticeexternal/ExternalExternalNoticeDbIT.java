package cn.iocoder.txgy.module.bidding.service.noticeexternal;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalDetailRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalAttachmentDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticefollow.FollowItemEventDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticefollow.SubscriptionEventDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalAttachmentMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticefollow.FollowItemEventMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticefollow.SubscriptionEventMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.NoticeExternalAuditStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.NoticeExternalStatusEnum;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.ingest.ExternalNoticeIngestDTO;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.ingest.ExternalNoticeIngestService;
import cn.iocoder.txgy.module.bidding.service.noticefollow.FollowService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 外部公告域 数据库集成测试（真 MySQL，独立库 bidding_it_external 自举建表，事务回滚）。
 *
 * <p>基建说明：DbConfig 在创建 DataSource 前先连 127.0.0.1:3306 创建独立库并执行
 * {@code sql/2026-09-03-external-notice.sql}（v0.6 DDL，全部 CREATE TABLE IF NOT EXISTS），
 * 不污染业务库；类名以 IT 结尾 → 不随默认 mvn test 执行，用
 * {@code mvn -pl txgy-module-bidding-server test -Dtest='ExternalExternalNoticeDbIT'} 单独跑（依赖本地 MySQL）。
 * 每个用例 @Transactional 自动回滚。
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ExternalExternalNoticeDbIT.DbConfig.class)
@Transactional
class ExternalExternalNoticeDbIT {

    private static final String TEST_DB = "bidding_it_external";

    @Autowired
    private ExternalNoticeIngestService ingestService;
    @Autowired
    private NoticeExternalService noticeExternalService;
    @Autowired
    private NoticeExternalMapper noticeExternalMapper;
    @Autowired
    private NoticeExternalAttachmentMapper attachmentMapper;
    @Autowired
    private FollowService followService;
    @Autowired
    private FollowItemEventMapper followItemEventMapper;
    @Autowired
    private SubscriptionEventMapper subscriptionEventMapper;

    private ExternalNoticeIngestDTO htmlDto(String tag) {
        ExternalNoticeIngestDTO dto = new ExternalNoticeIngestDTO();
        dto.setSourceCode("IT-SRC");
        dto.setSourceName("测试源");
        dto.setTitle("IT 招标公告 " + tag);
        dto.setOrigUrl("https://it.example.com/" + tag + "/" + UUID.randomUUID());
        dto.setContentType("HTML");
        dto.setContentHtml("<html><body>原文原样 " + tag + "</body></html>");
        dto.setPublishTime(LocalDateTime.now());
        dto.setIssuerName("测试单位");
        dto.setMetaJson("{\"raw\":\"" + tag + "\"}");
        return dto;
    }

    @Test
    void ingest_newHidden_approveVisibleThenTakenDownHidden() {
        Long id = ingestService.ingest(htmlDto("A1"));

        // 采集入库：NEW + UNREVIEWED，公开页不可见
        NoticeExternalDO saved = noticeExternalMapper.selectById(id);
        assertEquals(NoticeExternalStatusEnum.NEW.getStatus(), saved.getStatus());
        assertEquals(NoticeExternalAuditStatusEnum.UNREVIEWED.getStatus(), saved.getAuditStatus());
        assertTrue(saved.getOrigUrlHash().matches("[0-9a-f]{64}"));
        assertTrue(pageContains("IT 招标公告 A1").isEmpty());

        // 审核通过：ACTIVE + APPROVED，公开页可见且详情回 HTML 原样 + origUrl
        noticeExternalService.approveExternal(id);
        NoticeExternalDO active = noticeExternalMapper.selectById(id);
        assertEquals(NoticeExternalStatusEnum.ACTIVE.getStatus(), active.getStatus());
        assertTrue(pageContains("IT 招标公告 A1").stream().anyMatch(r -> r.getId().equals(id)));
        NoticeExternalDetailRespVO detail = noticeExternalService.getDetail(id);
        assertEquals(null, detail.getContentHtml(), "公开详情不下发原样 HTML");
        assertNotNull(detail.getContentHtmlSafe());
        assertTrue(detail.getContentHtmlSafe().contains("原文原样 A1"));
        assertEquals(saved.getOrigUrl(), detail.getOrigUrl());

        // 下线：展示随即拒绝（TAKEN_DOWN 106_002）
        noticeExternalService.takeDownExternal(id);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> noticeExternalService.getDetail(id));
        assertEquals(ErrorCodeConstants.EXTERNAL_NOTICE_TAKEN_DOWN.getCode(), ex.getCode());
    }

    @Test
    void ingest_sameOrigUrl_twiceDedupeToOneRow() {
        ExternalNoticeIngestDTO dto = htmlDto("D1");
        Long first = ingestService.ingest(dto);
        Long second = ingestService.ingest(dto); // 同 URL 幂等返回既有 ID

        assertEquals(first, second);
        Long count = noticeExternalMapper.selectCount(new LambdaQueryWrapper<NoticeExternalDO>()
                .eq(NoticeExternalDO::getOrigUrlHash, dto.getOrigUrlHash()));
        assertEquals(1L, count);
    }

    @Test
    void ingest_pdf_keepsFileIdAndSha() {
        ExternalNoticeIngestDTO dto = htmlDto("P1");
        dto.setContentType("PDF");
        dto.setContentHtml(null);
        dto.setPdfFileId(777L);
        dto.setPdfSha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        dto.setPdfSize(4096L);

        Long id = ingestService.ingest(dto);

        NoticeExternalDO saved = noticeExternalMapper.selectById(id);
        assertEquals(777L, saved.getPdfFileId());
        assertEquals(64, saved.getPdfSha256().length());
        assertEquals(4096L, saved.getPdfSize());
        assertNull(saved.getContentHtml());
        // PDF 详情形态（审核通过后）
        noticeExternalService.approveExternal(id);
        NoticeExternalDetailRespVO detail = noticeExternalService.getDetail(id);
        assertEquals(777L, detail.getPdfFileId());
        assertNotNull(detail.getOrigUrl());
    }

    @Test
    void ingest_withAttachment_persistsChildRowAndIdsJson() {
        ExternalNoticeIngestDTO dto = htmlDto("AT1");
        ExternalNoticeIngestDTO.Attachment a = new ExternalNoticeIngestDTO.Attachment();
        a.setFileId(55L);
        a.setName("招标文件.pdf");
        a.setExt("pdf");
        a.setSize(1024L);
        a.setOrigDownloadUrl("https://it.example.com/AT1/file.pdf");
        dto.getAttachments().add(a);

        Long id = ingestService.ingest(dto);

        NoticeExternalAttachmentDO row = attachmentMapper.selectOne(new LambdaQueryWrapper<NoticeExternalAttachmentDO>()
                .eq(NoticeExternalAttachmentDO::getNoticeId, id));
        assertNotNull(row);
        assertEquals("招标文件.pdf", row.getName());
        assertEquals("PENDING", row.getVirusStatus());
        NoticeExternalDO main = noticeExternalMapper.selectById(id);
        assertTrue(main.getAttachmentIdsJson().contains(String.valueOf(row.getId())),
                "主档 attachment_ids_json 应回填子表 id");
    }

    @Test
    void getPage_noticeTypeRaw_returnsOnlyMatchingType() {
        ExternalNoticeIngestDTO zb = htmlDto("NTR1");
        zb.setNoticeTypeRaw("招标公告");
        ExternalNoticeIngestDTO hx = htmlDto("NTR2");
        hx.setNoticeTypeRaw("中标候选人公示");
        Long zbId = ingestService.ingest(zb);
        Long hxId = ingestService.ingest(hx);
        noticeExternalService.approveExternal(zbId);
        noticeExternalService.approveExternal(hxId);

        NoticeExternalPageReqVO req = new NoticeExternalPageReqVO();
        req.setPageNo(1);
        req.setPageSize(50);
        req.setNoticeTypeRaw("招标公告");
        PageResult<NoticeExternalRespVO> page = noticeExternalService.getPage(req);

        // 注意：DbIT 上下文未注册分页拦截器，Page.total 恒 0 —— 用 records 断言（list 为全量匹配行）
        assertEquals(1, page.getList().size(), "公告类型过滤应只命中 招标公告 一条");
        assertEquals(zbId, page.getList().get(0).getId());
    }

    /** 驳回后同 URL 重采 = 恢复入口：重置 NEW+UNREVIEWED 重新排队，可再审核（审查修复高1a + 低项恢复入口） */
    @Test
    void ingest_rejectedSameUrl_resubmitRequeuedForReaudit() {
        String fixedUrl = "https://it.example.com/resubmit/rejected-1";
        ExternalNoticeIngestDTO first = htmlDto("RJT-1");
        first.setOrigUrl(fixedUrl);
        Long id = ingestService.ingest(first);
        noticeExternalService.rejectExternal(id);
        assertEquals(NoticeExternalAuditStatusEnum.REJECTED.getStatus(),
                noticeExternalMapper.selectById(id).getAuditStatus());

        // 同 URL 同内容重采：驳回记录不再幂等跳过，重置回 NEW+UNREVIEWED
        ExternalNoticeIngestDTO resubmit = htmlDto("RJT-1");
        resubmit.setOrigUrl(fixedUrl);
        Long again = ingestService.ingest(resubmit);
        assertEquals(id, again, "同 URL 复用主档 id（不新建行）");
        NoticeExternalDO reset = noticeExternalMapper.selectById(id);
        assertEquals(NoticeExternalStatusEnum.NEW.getStatus(), reset.getStatus());
        assertEquals(NoticeExternalAuditStatusEnum.UNREVIEWED.getStatus(), reset.getAuditStatus());

        // 可再次审核通过
        noticeExternalService.approveExternal(id);
        assertEquals(NoticeExternalStatusEnum.ACTIVE.getStatus(),
                noticeExternalMapper.selectById(id).getStatus());
    }

    /** ACTIVE 展示中同 URL 内容更新：覆盖并打回 NEW+UNREVIEWED 重审（防源站投毒直上；审核通过即恢复，审查修复高1a/§12.2） */
    @Test
    void ingest_activeNotice_contentChanged_backToPendingReaudit() {
        String fixedUrl = "https://it.example.com/resubmit/changed-1";
        ExternalNoticeIngestDTO first = htmlDto("CHG-1");
        first.setOrigUrl(fixedUrl);
        Long id = ingestService.ingest(first);
        noticeExternalService.approveExternal(id);
        assertTrue(pageContains("IT 招标公告 CHG-1").stream().anyMatch(r -> r.getId().equals(id)));
        NoticeExternalDO active = noticeExternalMapper.selectById(id);
        String hashBefore = active.getContentHash();
        int fetchAfterFirst = active.getFetchCount();

        // 源站同一 URL 内容更新重采
        ExternalNoticeIngestDTO second = htmlDto("CHG-1-v2");
        second.setOrigUrl(fixedUrl);
        Long again = ingestService.ingest(second);
        assertEquals(id, again);
        NoticeExternalDO updated = noticeExternalMapper.selectById(id);
        assertEquals(NoticeExternalStatusEnum.NEW.getStatus(), updated.getStatus(), "内容变化应打回待审核");
        assertEquals(NoticeExternalAuditStatusEnum.UNREVIEWED.getStatus(), updated.getAuditStatus());
        assertEquals(fetchAfterFirst + 1, updated.getFetchCount());
        assertFalse(hashBefore.equals(updated.getContentHash()), "内容指纹应变更（新版本）");
        // 待审期间公开页不可见
        assertTrue(pageContains("IT 招标公告 CHG-1-v2").isEmpty());

        // 重新审核通过 → 恢复展示
        noticeExternalService.approveExternal(id);
        assertEquals(NoticeExternalStatusEnum.ACTIVE.getStatus(), noticeExternalMapper.selectById(id).getStatus());
        assertTrue(pageContains("IT 招标公告 CHG-1-v2").stream().anyMatch(r -> r.getId().equals(id)));
    }

    /** ACTIVE 展示中同 URL 同内容重采：幂等跳过，状态不受打扰（审查修复高1a） */
    @Test
    void ingest_activeNotice_sameContent_idempotentKeepsActive() {
        String fixedUrl = "https://it.example.com/resubmit/same-1";
        ExternalNoticeIngestDTO first = htmlDto("SAME-1");
        first.setOrigUrl(fixedUrl);
        Long id = ingestService.ingest(first);
        noticeExternalService.approveExternal(id);
        NoticeExternalDO active = noticeExternalMapper.selectById(id);
        String hash = active.getContentHash();
        int fetch = active.getFetchCount();

        ExternalNoticeIngestDTO repeat = htmlDto("SAME-1");
        repeat.setOrigUrl(fixedUrl);
        Long again = ingestService.ingest(repeat);

        assertEquals(id, again);
        NoticeExternalDO after = noticeExternalMapper.selectById(id);
        assertEquals(NoticeExternalStatusEnum.ACTIVE.getStatus(), after.getStatus(), "同内容重采不打扰展示中记录");
        assertEquals(hash, after.getContentHash());
        assertEquals(fetch, after.getFetchCount());
        assertEquals(1L, noticeExternalMapper.selectCount(new LambdaQueryWrapper<NoticeExternalDO>()
                .eq(NoticeExternalDO::getOrigUrlHash, after.getOrigUrlHash())));
    }

    // ========== 关注（T 轨） ==========

    private FollowReqVO sectionFollow(String key) {
        FollowReqVO req = new FollowReqVO();
        req.setTargetType("SECTION");
        req.setTargetKey(key);
        return req;
    }

    @Test
    void follow_activatedNotice_pushesTimelineEventAndInAppDedupe() {
        // 1) 关注标段（幂等建订阅+关注项目行）
        followService.follow(7L, sectionFollow("SEC-IT-1"));
        followService.follow(7L, sectionFollow("SEC-IT-1")); // 幂等不重复
        assertEquals(0L, subscriptionEventMapper.selectCount(new LambdaQueryWrapper<>()));

        // 2) 该标段外部公告审核通过 → 关注扇出：事件+提醒各写一条
        ExternalNoticeIngestDTO dto = htmlDto("FOLLOW-1");
        dto.setBidSectionCodes("SEC-IT-1");
        dto.setProjectName("关注项目名");
        dto.setIssuerName("关注发布单位");
        Long noticeId = ingestService.ingest(dto);
        noticeExternalService.approveExternal(noticeId);

        java.util.List<FollowItemEventDO> events = followItemEventMapper.selectList(null);
        assertEquals(1, events.size(), "同用户同公告同源事件只写一条");
        assertEquals(noticeId, events.get(0).getNoticeId());
        assertEquals(Boolean.FALSE, events.get(0).getReadFlag());
        java.util.List<SubscriptionEventDO> se = subscriptionEventMapper.selectList(null);
        assertEquals(1, se.size(), "IN_APP 提醒留痕一条");
        assertEquals("IN_APP", se.get(0).getChannel());

        // 3) 重复触发同公告（补偿/重放场景）→ 不重复建事件/提醒（event_key 去重）
        followService.handleNoticeActivated(noticeExternalMapper.selectById(noticeId), "EXTERNAL");
        assertEquals(1L, followItemEventMapper.selectCount(new LambdaQueryWrapper<>()));
        assertEquals(1L, subscriptionEventMapper.selectCount(new LambdaQueryWrapper<>()));
    }

    @Test
    void unfollow_clearsList_readFlagMarksItemRead() {
        followService.follow(8L, sectionFollow("SEC-IT-2"));

        // 标段公告激活产生未读
        ExternalNoticeIngestDTO dto = htmlDto("FOLLOW-2");
        dto.setBidSectionCodes("SEC-IT-2");
        Long noticeId = ingestService.ingest(dto);
        noticeExternalService.approveExternal(noticeId);
        java.util.List<FollowItemEventDO> events = followItemEventMapper.selectList(null);
        assertEquals(1, events.size());

        // 我的关注可见该行
        var page = followService.pageMyFollow(8L, new cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowItemPageReqVO());
        assertEquals(1, page.getList().size());

        // 标已读：事件 read_flag=true
        followService.markRead(8L, page.getList().get(0).getId());
        FollowItemEventDO after = followItemEventMapper.selectById(events.get(0).getId());
        assertEquals(Boolean.TRUE, after.getReadFlag());

        // 退订幂等：取消后不再出现在我的关注列表（保留历史）
        followService.unfollow(8L, sectionFollow("SEC-IT-2"));
        var pageAfter = followService.pageMyFollow(8L, new cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowItemPageReqVO());
        assertEquals(0, pageAfter.getList().size(), "UNFOLLOWED 不出现于我的关注");
        followService.unfollow(8L, sectionFollow("SEC-IT-2")); // 幂等不报错
    }

    /** 公告级关注（ANNOUNCEMENT）：建关注行 + 审核生效扇出提醒（审查修复中3/低项） */
    @Test
    void follow_announcement_subscription_pushesTimelineEventAndInApp() {
        Long noticeId = ingestService.ingest(htmlDto("ANN-1"));
        // 关注该公告本身（targetType=ANNOUNCEMENT，targetKey=公告 id）
        FollowReqVO req = new FollowReqVO();
        req.setTargetType("ANNOUNCEMENT");
        req.setTargetKey(String.valueOf(noticeId));
        followService.follow(9L, req);
        followService.follow(9L, req); // 幂等
        // 关注后即建关注项目行（公告级关注也有列表行，标题快照激活时刷新）
        var before = followService.pageMyFollow(9L,
                new cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowItemPageReqVO());
        assertEquals(1, before.getList().size(), "公告级关注应建关注项目行");

        noticeExternalService.approveExternal(noticeId);

        java.util.List<FollowItemEventDO> events = followItemEventMapper.selectList(null);
        assertEquals(1, events.size(), "公告级关注命中：事件一条");
        assertEquals("EXTERNAL_ACTIVE", events.get(0).getEventType(), "外部公告生效事件类型 EXTERNAL_ACTIVE（前端 02 约定）");
        assertEquals(noticeId, events.get(0).getNoticeId());
        assertEquals(1L, subscriptionEventMapper.selectCount(new LambdaQueryWrapper<>()));
        // 重复触发（重放）不重复提醒（event_key 幂等）
        followService.handleNoticeActivated(noticeExternalMapper.selectById(noticeId), "EXTERNAL");
        assertEquals(1L, followItemEventMapper.selectCount(new LambdaQueryWrapper<>()));
    }

    /** 维度匹配兼容半角分号标段（落库口径统一；审查修复中7） */
    @Test
    void follow_sectionMatch_semicolonSeparatedCodes() {
        followService.follow(10L, sectionFollow("SEC-S1"));
        ExternalNoticeIngestDTO dto = htmlDto("SEMI-1");
        dto.setBidSectionCodes("SEC-S1;SEC-S2");
        Long noticeId = ingestService.ingest(dto);

        noticeExternalService.approveExternal(noticeId);

        assertEquals(1L, followItemEventMapper.selectCount(new LambdaQueryWrapper<>()),
                "分号分隔中标段 SEC-S1 应命中订阅");
    }

    // ========== 工具 ==========

    /** 真实样本：炼化公司 2026 大修消缺公告 HTML（docs 提供） */
    @Test
    void realSampleHtml_ingest_approve_detailSafe() throws Exception {
        String raw;
        try (InputStream in = getClass().getResourceAsStream("/samples/lianhua-2026-notice.html")) {
            assertNotNull(in, "真实 HTML 样本应存在于测试资源 samples/");
            raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(raw.contains("消缺") && raw.contains("装置"), "样本应含招标正文");
        assertTrue(raw.length() < 8 * 1024 * 1024, "样本应在入库长度上限内");

        ExternalNoticeIngestDTO dto = htmlDto("REAL-SAMPLE");
        dto.setTitle("炼化公司所属各单位 2026 年装置大修消缺公开招标");
        dto.setContentHtml(raw);
        dto.setMetaJson(null);
        Long id = ingestService.ingest(dto);

        noticeExternalService.approveExternal(id);
        NoticeExternalDetailRespVO detail = noticeExternalService.getDetail(id);

        assertNull(detail.getContentHtml(), "公开详情不下发原样（留档仅存库）");
        assertNotNull(detail.getContentHtmlSafe());
        assertTrue(!detail.getContentHtmlSafe().contains("<script"), "净化版不得残留 script（真实页面常有脚本）");
        assertTrue(detail.getContentHtmlSafe().contains("招标") && detail.getContentHtmlSafe().contains("消缺"), "净化版应保留正文");
    }

    private java.util.List<NoticeExternalRespVO> pageContains(String title) {
        NoticeExternalPageReqVO req = new NoticeExternalPageReqVO();
        req.setPageNo(1);
        req.setPageSize(50);
        req.setTitle(title);
        PageResult<NoticeExternalRespVO> page = noticeExternalService.getPage(req);
        return page.getList().stream().filter(r -> title.equals(r.getTitle())).toList();
    }

    // ========== 独立 Spring 上下文（建库+执行 v0.6 DDL 后直连） ==========

    @Configuration
    @MapperScan("cn.iocoder.txgy.module.bidding.dal.mysql")
    @ComponentScan(basePackages = {
            "cn.iocoder.txgy.module.bidding.service.noticeexternal",
            "cn.iocoder.txgy.module.bidding.service.noticefollow"})
    @EnableAspectJAutoProxy
    @EnableTransactionManagement
    public static class DbConfig {

        @Bean
        public DataSource dataSource() throws Exception {
            String url = "jdbc:mysql://127.0.0.1:3306/ruoyi-vue-pro?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
            try (Connection admin = DriverManager.getConnection(url, "root", "123456");
                 Statement st = admin.createStatement()) {
                st.execute("DROP DATABASE IF EXISTS " + TEST_DB);
                st.execute("CREATE DATABASE " + TEST_DB
                        + " DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }
            try (Connection conn = DriverManager.getConnection(
                    "jdbc:mysql://127.0.0.1:3306/" + TEST_DB
                            + "?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true",
                    "root", "123456");
                 Statement st = conn.createStatement()) {
                // 顺序执行 v0.6 DDL + 后续迁移（与业务库演进一致：09-03 基线 → 09-05 审查修复 → 09-06 AI 列）
                for (File ddl : resolveDdlFiles()) {
                    String sql = Files.readString(ddl.toPath(), StandardCharsets.UTF_8);
                    for (String stmt : sql.split(";")) {
                        String s = stripComments(stmt).trim();
                        if (!s.isEmpty()) {
                            st.execute(s);
                        }
                    }
                }
            }
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl("jdbc:mysql://127.0.0.1:3306/" + TEST_DB
                    + "?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true");
            ds.setUsername("root");
            ds.setPassword("123456");
            ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
            return ds;
        }

        /** 建库 DDL 序列：09-03 基线 + 09-05 审查修复 + 09-06 AI 列 + 09-07 正文结构化列（按文件名升序，保证依赖顺序）。 */
        private List<File> resolveDdlFiles() {
            List<File> files = new ArrayList<>();
            for (String name : new String[]{"2026-09-03-external-notice.sql",
                    "2026-09-05-external-notice-review.sql", "2026-09-06-ai-extract.sql",
                    "2026-09-07-body-blocks.sql"}) {
                File fromModule = new File("..", "sql/" + name);
                if (fromModule.exists()) {
                    files.add(fromModule);
                    continue;
                }
                File fromRoot = new File("sql/" + name);
                if (fromRoot.exists()) {
                    files.add(fromRoot);
                    continue;
                }
                throw new IllegalStateException("找不到 DDL: sql/" + name + "（cwd="
                        + new File(".").getAbsolutePath() + "）");
            }
            return files;
        }

        private String stripComments(String stmt) {
            StringBuilder sb = new StringBuilder();
            for (String line : stmt.split("\\r?\\n")) {
                String t = line.trim();
                if (t.startsWith("--") || t.startsWith("/*") || t.isEmpty()) {
                    continue;
                }
                sb.append(line).append('\n');
            }
            return sb.toString();
        }

        @Bean
        public DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        public org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory(DataSource dataSource,
                                                                               com.baomidou.mybatisplus.core.handlers.MetaObjectHandler metaObjectHandler) throws Exception {
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
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
