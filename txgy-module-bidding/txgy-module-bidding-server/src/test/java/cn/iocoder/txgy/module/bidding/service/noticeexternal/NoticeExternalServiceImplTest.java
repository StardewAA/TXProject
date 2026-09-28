package cn.iocoder.txgy.module.bidding.service.noticeexternal;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalDetailRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalManageDetailRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalManagePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalManageRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalAttachmentDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalAttachmentMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.service.externalfetch.fetch.HttpFetchClient;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.search.ExternalNoticeSearchEngine;
import cn.iocoder.txgy.module.infra.api.file.FileApi;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 外部公告公开只读服务测试（Mockito，无 Spring 上下文）：
 * 公开过滤（SOCIAL+ACTIVE）封装、详情三形态字段回填、不存在/不可展示错误码（1-060-106 段）。
 */
class NoticeExternalServiceImplTest {

    private NoticeExternalMapper noticeExternalMapper;
    private NoticeExternalAttachmentMapper noticeExternalAttachmentMapper;
    private ExternalNoticeSearchEngine externalNoticeSearchEngine;
    private HttpFetchClient httpFetchClient;
    private FileApi fileApi;
    private cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlSourceMapper crawlSourceMapper;
    // 2026-09-11 补齐：合规链路依赖（PDF 回填/预览回退都要过下载闸位与 robots 检查），
    //   不注入则单测里为 null → NPE（backfillPdfDownloadUrls_downloadsUploadsAndPatches 变红的原因）
    private cn.iocoder.txgy.module.bidding.service.externalfetch.throttle.CrawlDownloadGate crawlDownloadGate;
    private cn.iocoder.txgy.module.bidding.service.externalfetch.fetch.RobotsGuard robotsGuard;
    private NoticeExternalServiceImpl service;

    @BeforeEach
    void setUp() {
        noticeExternalMapper = mock(NoticeExternalMapper.class);
        noticeExternalAttachmentMapper = mock(NoticeExternalAttachmentMapper.class);
        externalNoticeSearchEngine = mock(ExternalNoticeSearchEngine.class);
        httpFetchClient = mock(HttpFetchClient.class);
        fileApi = mock(FileApi.class);
        crawlSourceMapper = mock(cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlSourceMapper.class);
        // backfill SSRF 守卫用源配置：默认源行白名单=gx.e-bidding.org（base_url），放行外站 PDF 重下载
        cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlSourceDO src =
                new cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlSourceDO();
        src.setSourceCode("GX");
        src.setBaseUrl("https://gx.e-bidding.org");
        when(crawlSourceMapper.selectOne(any())).thenReturn(src);
        // 默认：robots 允许、闸位随时可得（限速语义另有专门用例）
        crawlDownloadGate = mock(cn.iocoder.txgy.module.bidding.service.externalfetch.throttle.CrawlDownloadGate.class);
        when(crawlDownloadGate.tryAcquire(any(), any(), any())).thenReturn(true);
        robotsGuard = mock(cn.iocoder.txgy.module.bidding.service.externalfetch.fetch.RobotsGuard.class);
        when(robotsGuard.isAllowed(any())).thenReturn(true);
        service = new NoticeExternalServiceImpl();
        ReflectionTestUtils.setField(service, "noticeExternalMapper", noticeExternalMapper);
        ReflectionTestUtils.setField(service, "noticeExternalAttachmentMapper", noticeExternalAttachmentMapper);
        ReflectionTestUtils.setField(service, "externalNoticeSearchEngine", externalNoticeSearchEngine);
        ReflectionTestUtils.setField(service, "httpFetchClient", httpFetchClient);
        ReflectionTestUtils.setField(service, "crawlSourceMapper", crawlSourceMapper);
        ReflectionTestUtils.setField(service, "fileApi", fileApi);
        ReflectionTestUtils.setField(service, "crawlDownloadGate", crawlDownloadGate);
        ReflectionTestUtils.setField(service, "robotsGuard", robotsGuard);
    }

    private NoticeExternalDO doActiveSocial(String contentType) {
        NoticeExternalDO notice = new NoticeExternalDO();
        notice.setId(1L);
        notice.setSourceCode("TEST-SRC");
        notice.setSourceName("测试源");
        notice.setTitle("某防腐保温工程招标公告");
        notice.setOrigUrl("https://example.com/notice/1");
        notice.setContentType(contentType);
        notice.setPublicScope("SOCIAL");
        notice.setStatus("ACTIVE");
        notice.setPublishTime(LocalDateTime.of(2026, 9, 3, 10, 0));
        return notice;
    }

    @Test
    void getPage_filtersSocialActive_only() {
        NoticeExternalDO row = doActiveSocial("HTML");
        Page<NoticeExternalDO> page = new Page<>(1, 10);
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(noticeExternalMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        NoticeExternalPageReqVO req = new NoticeExternalPageReqVO();
        PageResult<NoticeExternalRespVO> result = service.getPage(req);

        assertEquals(1, result.getList().size());
        assertEquals("ACTIVE", result.getList().get(0).getStatus());
        assertEquals("某防腐保温工程招标公告", result.getList().get(0).getTitle());
        assertEquals("HTML", result.getList().get(0).getContentType());
    }

    @Test
    void getDetail_html_returnsOriginalHtmlAndOrigUrl() {
        NoticeExternalDO html = doActiveSocial("HTML");
        html.setContentHtml("<html>原文原样</html>");
        html.setLegalFlag("N");
        when(noticeExternalMapper.selectById(1L)).thenReturn(html);
        when(noticeExternalAttachmentMapper.selectList(any())).thenReturn(List.of());

        NoticeExternalDetailRespVO resp = service.getDetail(1L);

        assertEquals(null, resp.getContentHtml(), "公开详情不下发原样 HTML（留档仅存库）");
        assertEquals("https://example.com/notice/1", resp.getOrigUrl());
        assertNotNull(resp.getContentHtmlSafe());
        assertNull(resp.getPdfFileId());
        assertTrue(resp.getAttachments() == null || resp.getAttachments().isEmpty());
    }

    @Test
    void getDetail_pdf_carriesFilePointerOnly() {
        NoticeExternalDO pdf = doActiveSocial("PDF");
        pdf.setPdfFileId(88L);
        pdf.setPdfSha256("abc");
        pdf.setPdfSize(1024L);
        when(noticeExternalMapper.selectById(1L)).thenReturn(pdf);
        when(noticeExternalAttachmentMapper.selectList(any())).thenReturn(List.of());

        NoticeExternalDetailRespVO resp = service.getDetail(1L);

        assertEquals(88L, resp.getPdfFileId());
        assertNull(resp.getContentHtml());
        assertEquals("https://example.com/notice/1", resp.getOrigUrl());
    }

    @Test
    void getDetail_attachments_listed() {
        NoticeExternalAttachmentDO att = new NoticeExternalAttachmentDO();
        att.setId(9L);
        att.setNoticeId(1L);
        att.setFileId(55L);
        att.setName("招标文件.pdf");
        att.setVirusStatus("CLEAN");
        when(noticeExternalMapper.selectById(1L)).thenReturn(doActiveSocial("ATTACHMENT"));
        when(noticeExternalAttachmentMapper.selectList(any())).thenReturn(List.of(att));

        NoticeExternalDetailRespVO resp = service.getDetail(1L);

        assertEquals(1, resp.getAttachments().size());
        assertEquals("招标文件.pdf", resp.getAttachments().get(0).getName());
    }

    @Test
    void getDetail_notExists_throws106() {
        when(noticeExternalMapper.selectById(999L)).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.getDetail(999L));
        assertEquals(ErrorCodeConstants.EXTERNAL_NOTICE_NOT_EXISTS.getCode(), ex.getCode());
    }

    @Test
    void getDetail_notSocialOrActive_throwsTakenDown() {
        NoticeExternalDO internal = doActiveSocial("HTML");
        internal.setPublicScope("GOVERNMENT");
        when(noticeExternalMapper.selectById(2L)).thenReturn(internal);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.getDetail(2L));
        assertEquals(ErrorCodeConstants.EXTERNAL_NOTICE_TAKEN_DOWN.getCode(), ex.getCode());
    }

    @Test
    void getPage_withKeyword_hitsEngineThenFiltersByIds() {
        NoticeExternalDO row1 = doActiveSocial("HTML");
        row1.setId(11L);
        NoticeExternalDO row2 = doActiveSocial("HTML");
        row2.setId(12L);
        Page<NoticeExternalDO> page = new Page<>(1, 10);
        page.setRecords(List.of(row1, row2));
        page.setTotal(2);
        when(externalNoticeSearchEngine.matchNoticeIds("炼化", 200))
                .thenReturn(new ExternalNoticeSearchEngine.MatchResult(List.of(11L, 12L), 2L));
        when(noticeExternalMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        NoticeExternalPageReqVO req = new NoticeExternalPageReqVO();
        req.setKeyword("炼化");
        PageResult<NoticeExternalRespVO> result = service.getPage(req);

        assertEquals(2, result.getList().size());
        assertEquals(11L, result.getList().get(0).getId());
        org.mockito.Mockito.verify(externalNoticeSearchEngine).matchNoticeIds("炼化", 200);
        org.mockito.Mockito.verify(noticeExternalMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    void getPage_withKeyword_noHit_returnsEmptyWithoutDbQuery() {
        when(externalNoticeSearchEngine.matchNoticeIds("不存在词", 200))
                .thenReturn(ExternalNoticeSearchEngine.MatchResult.empty());

        NoticeExternalPageReqVO req = new NoticeExternalPageReqVO();
        req.setKeyword("不存在词");
        PageResult<NoticeExternalRespVO> result = service.getPage(req);

        assertTrue(result.getList().isEmpty());
        org.mockito.Mockito.verify(noticeExternalMapper, org.mockito.Mockito.never())
                .selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    // ========== 契约 B5：total 口径 & B2：相关度排序保持（2026-09-22 ES 中文精确检索改造） ==========

    @Test
    void getPage_withKeyword_totalUsesRealEsHitCount_notTruncationLimit() {
        // 旧行为：total 取自主表 COUNT（受 id IN 命中集约束，上限 200）→ 前端永远"共 200 条"。
        // 新行为：无结构化收窄条件时，total = ES 真实命中数（这里 1326 > 取回上限 200）。
        NoticeExternalDO row = doActiveSocial("HTML");
        row.setId(11L);
        Page<NoticeExternalDO> page = new Page<>(1, 10);
        page.setRecords(List.of(row));
        page.setTotal(200); // 主表 COUNT 只能数到命中集里的 200 条
        when(externalNoticeSearchEngine.matchNoticeIds("采购", 200))
                .thenReturn(new ExternalNoticeSearchEngine.MatchResult(List.of(11L), 1326L));
        when(noticeExternalMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        NoticeExternalPageReqVO req = new NoticeExternalPageReqVO();
        req.setKeyword("采购");
        PageResult<NoticeExternalRespVO> result = service.getPage(req);

        assertEquals(1326L, result.getTotal(), "total 应为 ES 真实命中数，而非取回上限 200");
    }

    @Test
    void getPage_withKeywordAndStructuredFilter_totalFallsBackToDbCount() {
        // 附带结构化过滤（项目编号等）时，过滤只在命中集内求交集，ES 真实命中数不再等于结果数
        // → 此时 total 用主表 COUNT（反映"当前过滤条件下真实可展示条数"），不能虚报成 ES 全量。
        NoticeExternalDO row = doActiveSocial("HTML");
        row.setId(11L);
        Page<NoticeExternalDO> page = new Page<>(1, 10);
        page.setRecords(List.of(row));
        page.setTotal(3); // 交集内真实 3 条
        when(externalNoticeSearchEngine.matchNoticeIds("采购", 200))
                .thenReturn(new ExternalNoticeSearchEngine.MatchResult(List.of(11L), 1326L));
        when(noticeExternalMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        NoticeExternalPageReqVO req = new NoticeExternalPageReqVO();
        req.setKeyword("采购");
        req.setProjectCode("0866-26D2SXQY0628"); // 结构化条件
        PageResult<NoticeExternalRespVO> result = service.getPage(req);

        assertEquals(3L, result.getTotal(), "有结构化过滤时 total 应取交集真实条数");
    }

    @Test
    void getPage_withoutKeyword_totalUnaffected() {
        NoticeExternalDO row = doActiveSocial("HTML");
        Page<NoticeExternalDO> page = new Page<>(1, 10);
        page.setRecords(List.of(row));
        page.setTotal(1637);
        when(noticeExternalMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<NoticeExternalRespVO> result = service.getPage(new NoticeExternalPageReqVO());

        assertEquals(1637L, result.getTotal(), "无关键词时 total 语义不变（主表 COUNT）");
    }

    @Test
    void getPage_keyword_deepPage_expandsMatchLimitSoPagesAreNotEmpty() {
        // 2026-09-22 修复：total 报 1326 却只能翻出 200 条 → 第 3 页起空白。
        // 现在按 pageNo*pageSize 扩大取回量，深翻页也能取到数据。
        NoticeExternalDO row = doActiveSocial("HTML");
        row.setId(11L);
        Page<NoticeExternalDO> page = new Page<>(3, 100);
        page.setRecords(List.of(row));
        page.setTotal(300);
        when(externalNoticeSearchEngine.matchNoticeIds(eq("采购"), eq(300)))
                .thenReturn(new ExternalNoticeSearchEngine.MatchResult(List.of(11L), 1326L));
        when(noticeExternalMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        NoticeExternalPageReqVO req = new NoticeExternalPageReqVO();
        req.setKeyword("采购");
        req.setPageNo(3);
        req.setPageSize(100);
        service.getPage(req);

        // pageNo=3 pageSize=100 → 需覆盖 300 条，取回量应扩到 300（而非固定 200）
        org.mockito.Mockito.verify(externalNoticeSearchEngine).matchNoticeIds("采购", 300);
    }

    @Test
    void getPage_keyword_firstPage_keepsBaseLimit() {
        // 首屏仍取基础量 200：不因分页改造放大 IN 条件
        NoticeExternalDO row = doActiveSocial("HTML");
        row.setId(11L);
        Page<NoticeExternalDO> page = new Page<>(1, 20);
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(externalNoticeSearchEngine.matchNoticeIds(eq("采购"), eq(200)))
                .thenReturn(new ExternalNoticeSearchEngine.MatchResult(List.of(11L), 1326L));
        when(noticeExternalMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        NoticeExternalPageReqVO req = new NoticeExternalPageReqVO();
        req.setKeyword("采购");
        req.setPageNo(1);
        req.setPageSize(20);
        service.getPage(req);

        org.mockito.Mockito.verify(externalNoticeSearchEngine).matchNoticeIds("采购", 200);
    }

    @Test
    void getPage_keyword_hugePage_cappedByHardLimit() {
        // 安全阀：pageNo 极大时取回量封顶 MAX_KEYWORD_MATCH_LIMIT(10000)，避免 IN 条件爆炸
        NoticeExternalDO row = doActiveSocial("HTML");
        row.setId(11L);
        Page<NoticeExternalDO> page = new Page<>(9999, 1000);
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(externalNoticeSearchEngine.matchNoticeIds(eq("采购"), eq(10000)))
                .thenReturn(new ExternalNoticeSearchEngine.MatchResult(List.of(11L), 1326L));
        when(noticeExternalMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        NoticeExternalPageReqVO req = new NoticeExternalPageReqVO();
        req.setKeyword("采购");
        req.setPageNo(9999);
        req.setPageSize(1000);
        service.getPage(req);

        org.mockito.Mockito.verify(externalNoticeSearchEngine).matchNoticeIds("采购", 10000);
    }

    @Test
    void getPage_withKeyword_keepsEsRelevanceOrderViaField() {
        // B2 配套：ES 命中集已按 _score 排好，SQL 侧必须用 FIELD(id,...) 保住该顺序，
        // 否则 orderByDesc(publishTime) 会覆盖相关度顺序（最相关的旧公告又被挤出上限）。
        NoticeExternalDO row = doActiveSocial("HTML");
        row.setId(11L);
        Page<NoticeExternalDO> page = new Page<>(1, 10);
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(externalNoticeSearchEngine.matchNoticeIds("炼化", 200))
                .thenReturn(new ExternalNoticeSearchEngine.MatchResult(List.of(11L, 12L), 2L));
        when(noticeExternalMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        NoticeExternalPageReqVO req = new NoticeExternalPageReqVO();
        req.setKeyword("炼化");
        service.getPage(req);

        // 断言方式说明：不能在单测里调 getSqlSegment()/getTargetSql()——
        // LambdaQueryWrapper 的列解析依赖 MyBatis-Plus 的 lambda 缓存，无 Spring/MyBatis 运行时
        // 会抛 "MybatisPlus can not find lambda cache for this entity"。
        // 因此改为直接断言**调用行为**：ORDER BY FIELD(id, 11,12) 由 wrapper.last(..) 注入。
        org.mockito.ArgumentCaptor<LambdaQueryWrapper> captor =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        org.mockito.Mockito.verify(noticeExternalMapper).selectPage(any(Page.class), captor.capture());
        // lastSql 是 wrapper 保存的"末尾拼接 SQL"，不经过 lambda 列解析，可安全读取
        String lastSql = String.valueOf(ReflectionTestUtils.getField(captor.getValue(), "lastSql"));
        assertTrue(lastSql.contains("FIELD(id"), "应按 FIELD(id,...) 保持 ES 相关度序，实际: " + lastSql);
        assertTrue(lastSql.indexOf("11") < lastSql.indexOf("12"), "id 顺序应与 ES 命中顺序一致，实际: " + lastSql);
    }

    @Test
    void getPage_withoutKeyword_doesNotTouchSearchEngine() {
        NoticeExternalDO row = doActiveSocial("HTML");
        Page<NoticeExternalDO> page = new Page<>(1, 10);
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(noticeExternalMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        NoticeExternalPageReqVO req = new NoticeExternalPageReqVO();
        PageResult<NoticeExternalRespVO> result = service.getPage(req);

        assertEquals(1, result.getList().size());
        org.mockito.Mockito.verify(externalNoticeSearchEngine, org.mockito.Mockito.never())
                .matchNoticeIds(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void getManagePage_mapsRowAndKeepsStates() {
        // N1 管理分页：无 SOCIAL+ACTIVE 硬过滤，行映射含审核/抽取/AI/结构化状态
        NoticeExternalDO row = doActiveSocial("HTML");
        row.setAuditStatus("UNREVIEWED");
        row.setExtractionStatus("SUCCESS");
        row.setAiExtractionStatus("SUCCESS");
        row.setBodyBlocksStatus("FAILED");
        row.setBodyParserCode("AI_FALLBACK");
        row.setIssuerName("某发布单位");
        row.setProjectName("防腐保温管道工程");
        row.setFetchCount(3);
        Page<NoticeExternalDO> page = new Page<>(1, 10);
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(noticeExternalMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        NoticeExternalManagePageReqVO req = new NoticeExternalManagePageReqVO();
        req.setAuditStatus("UNREVIEWED");
        PageResult<NoticeExternalManageRespVO> result = service.getManagePage(req);

        assertEquals(1, result.getList().size());
        NoticeExternalManageRespVO vo = result.getList().get(0);
        assertEquals("UNREVIEWED", vo.getAuditStatus());
        assertEquals("SUCCESS", vo.getExtractionStatus());
        assertEquals("FAILED", vo.getBodyBlocksStatus());
        assertEquals("AI_FALLBACK", vo.getBodyParserCode());
        assertEquals("某发布单位", vo.getIssuerName());
        assertEquals(3, vo.getFetchCount().intValue());
        assertEquals("HTML", vo.getContentType());
    }

    // ===== BR-1 跟踪维度键下发（detail / page 行）=====
    @Test
    void getDetail_carriesTrackKeys() {
        NoticeExternalDO notice = doActiveSocial("HTML");
        notice.setProjectCode("P-2026-001");
        notice.setTenderProjectCode("TP-2026-001");
        notice.setBidSectionCodes("SEC-1,SEC-2;SEC-3");
        when(noticeExternalMapper.selectById(1L)).thenReturn(notice);
        when(noticeExternalAttachmentMapper.selectList(any())).thenReturn(List.of());

        NoticeExternalDetailRespVO vo = service.getDetail(1L);

        assertEquals("P-2026-001", vo.getProjectKey());
        assertEquals("TP-2026-001", vo.getTenderProjectKey());
        assertNotNull(vo.getBidSectionKeys());
        assertEquals(3, vo.getBidSectionKeys().size());
        assertTrue(vo.getBidSectionKeys().contains("SEC-2"));
    }

    @Test
    void pageRow_carriesTrackKeys() {
        NoticeExternalDO row = doActiveSocial("HTML");
        row.setProjectCode("P-2026-001");
        row.setTenderProjectCode("TP-2026-001");
        row.setBidSectionCodes("SEC-1");
        Page<NoticeExternalDO> page = new Page<>(1, 10);
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(noticeExternalMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<NoticeExternalRespVO> result = service.getPage(new NoticeExternalPageReqVO());

        assertEquals(1, result.getList().size());
        NoticeExternalRespVO vo = result.getList().get(0);
        assertEquals("P-2026-001", vo.getProjectKey());
        assertEquals("SEC-1", vo.getBidSectionKeys().get(0));
    }

    // ===== BR-7 管理详情（审核预览，全状态）=====
    @Test
    void getManageDetail_newStatus_htmlPreviewWithAttachments() {
        NoticeExternalDO notice = doActiveSocial("HTML");
        notice.setStatus("NEW");
        notice.setAuditStatus("UNREVIEWED");
        notice.setContentHtml("<p>招标公告正文</p><script>alert(1)</script>");
        notice.setExtractionStatus("SUCCESS");
        notice.setExtractionText("抽取正文一");
        when(noticeExternalMapper.selectById(1L)).thenReturn(notice);
        NoticeExternalAttachmentDO clean = new NoticeExternalAttachmentDO();
        clean.setId(10L);
        clean.setName("采购文件.pdf");
        clean.setVirusStatus("CLEAN");
        clean.setFileId(900L);
        NoticeExternalAttachmentDO infected = new NoticeExternalAttachmentDO();
        infected.setId(11L);
        infected.setName("可疑.exe");
        infected.setVirusStatus("INFECTED");
        when(noticeExternalAttachmentMapper.selectList(any())).thenReturn(List.of(clean, infected));

        NoticeExternalManageDetailRespVO vo = service.getManageDetail(1L);

        assertEquals("NEW", vo.getStatus());
        assertEquals("UNREVIEWED", vo.getAuditStatus());
        // 净化正文：script 已去
        assertNotNull(vo.getContentHtmlSafe());
        assertTrue(vo.getContentHtmlSafe().contains("招标公告正文"));
        assertTrue(!vo.getContentHtmlSafe().toLowerCase().contains("<script"));
        assertEquals("抽取正文一", vo.getExtractionSummary());
        // 附件：INFECTED 不下发（downloadUrl 空且列表不含），CLEAN 正常
        assertNotNull(vo.getAttachments());
        assertEquals(1, vo.getAttachments().size());
        assertEquals("采购文件.pdf", vo.getAttachments().get(0).getName());
    }

    @Test
    void getManageDetail_notExists_throws() {
        when(noticeExternalMapper.selectById(99L)).thenReturn(null);
        ServiceException e = assertThrows(ServiceException.class, () -> service.getManageDetail(99L));
        assertEquals(ErrorCodeConstants.EXTERNAL_NOTICE_NOT_EXISTS.getCode(), e.getCode());
    }

    // ===== 2026-09-10 PDF 归档回填 =====

    @Test
    void backfillPdfDownloadUrls_downloadsUploadsAndPatches() throws Exception {
        NoticeExternalDO pdf = doActiveSocial("PDF");
        pdf.setMetaJson("{\"pdfDownloadUrl\":\"https://gx.e-bidding.org/sdny_bulletin/2026-03-04/10185.pdf\"}");
        when(noticeExternalMapper.selectList(any())).thenReturn(List.of(pdf));
        when(httpFetchClient.getBytes("https://gx.e-bidding.org/sdny_bulletin/2026-03-04/10185.pdf")).thenReturn("%PDF-1.4 test".getBytes());
        when(fileApi.createFile(any(), any(), any(), any())).thenReturn("http://127.0.0.1:48080/admin-api/infra/file/4/get/bidding/notice-external/10185.pdf");

        String result = service.backfillPdfDownloadUrls();

        assertEquals("total=1,ok=1,failed=0,skipped=0", result);
        org.mockito.ArgumentCaptor<NoticeExternalDO> cap = org.mockito.ArgumentCaptor.forClass(NoticeExternalDO.class);
        org.mockito.Mockito.verify(noticeExternalMapper).updateById(cap.capture());
        assertEquals("http://127.0.0.1:48080/admin-api/infra/file/4/get/bidding/notice-external/10185.pdf", cap.getValue().getPdfDownloadUrl());
    }

    // ===== 2026-09-11 PDF 在线预览的合规闸门（预览不得成为绕过 2 分钟限速的后门）=====

    @Test
    void pdfContent_externalFallback_gateNotDue_returnsNullWithoutFetching() throws Exception {
        // 平台归档缺失 → 回退外站直链；但未到合规下载间隔时必须"不发请求、直接 404"（返回 null）
        NoticeExternalDO pdf = doActiveSocial("PDF");
        pdf.setMetaJson("{\"pdfDownloadUrl\":\"https://gx.e-bidding.org/sdny_bulletin/2026-03-04/10185.pdf\"}");
        when(noticeExternalMapper.selectById(1L)).thenReturn(pdf);
        when(crawlDownloadGate.tryAcquire(any(), any(), any())).thenReturn(false);

        assertNull(service.pdfContent(1L));
        verify(crawlDownloadGate).tryAcquire(any(), any(), any());
        verify(httpFetchClient, never()).getBytes(any());
    }

    @Test
    void pdfContent_externalFallback_gateGranted_fetchesBytes() throws Exception {
        // 拿到闸位才允许访问外站；下载失败/空体仍按 404（返回 null）处理
        NoticeExternalDO pdf = doActiveSocial("PDF");
        pdf.setMetaJson("{\"pdfDownloadUrl\":\"https://gx.e-bidding.org/sdny_bulletin/2026-03-04/10185.pdf\"}");
        when(noticeExternalMapper.selectById(1L)).thenReturn(pdf);
        when(crawlDownloadGate.tryAcquire(any(), any(), any())).thenReturn(true);
        when(httpFetchClient.getBytes("https://gx.e-bidding.org/sdny_bulletin/2026-03-04/10185.pdf"))
                .thenReturn("%PDF-1.4 test".getBytes());

        byte[] content = service.pdfContent(1L);
        assertNotNull(content);
        assertEquals('%', content[0]);
    }

    @Test
    void pdfContent_robotsDisallowed_returnsNull() throws Exception {
        // robots.txt 禁止抓取该路径：合规优先，直接 404，且不占用闸位
        NoticeExternalDO pdf = doActiveSocial("PDF");
        pdf.setMetaJson("{\"pdfDownloadUrl\":\"https://gx.e-bidding.org/sdny_bulletin/2026-03-04/10185.pdf\"}");
        when(noticeExternalMapper.selectById(1L)).thenReturn(pdf);
        when(robotsGuard.isAllowed(any())).thenReturn(false);

        assertNull(service.pdfContent(1L));
        verify(crawlDownloadGate, never()).tryAcquire(any(), any(), any());
        verify(httpFetchClient, never()).getBytes(any());
    }

    @Test
    void backfillPdfDownloadUrls_metaWithoutUrl_skips() {
        NoticeExternalDO pdf = doActiveSocial("PDF");
        pdf.setMetaJson("{}");
        when(noticeExternalMapper.selectList(any())).thenReturn(List.of(pdf));

        String result = service.backfillPdfDownloadUrls();

        assertEquals("total=1,ok=0,failed=0,skipped=1", result);
        org.mockito.Mockito.verify(noticeExternalMapper, org.mockito.Mockito.never()).updateById(any(NoticeExternalDO.class));
    }

}
