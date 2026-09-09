package cn.iocoder.txgy.module.bidding.service.noticeexternal.ingest;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalAttachmentDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalAttachmentMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.NoticeExternalAuditStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.NoticeExternalStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 采集落库单测（Mockito，无 Spring 上下文）：NEW 入库/附件子表/PDF 红线8/URL 哈希幂等去重/必填校验。
 */
class ExternalNoticeIngestServiceImplTest {

    private NoticeExternalMapper noticeExternalMapper;
    private NoticeExternalAttachmentMapper attachmentMapper;
    private cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlSourceMapper crawlSourceMapper;
    private ExternalNoticeIngestServiceImpl service;

    @BeforeEach
    void setUp() {
        noticeExternalMapper = mock(NoticeExternalMapper.class);
        attachmentMapper = mock(NoticeExternalAttachmentMapper.class);
        crawlSourceMapper = mock(cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlSourceMapper.class);
        when(crawlSourceMapper.selectOne(any())).thenReturn(null); // sourceName 兜底：源缺失不补名
        service = new ExternalNoticeIngestServiceImpl();
        ReflectionTestUtils.setField(service, "noticeExternalMapper", noticeExternalMapper);
        ReflectionTestUtils.setField(service, "noticeExternalAttachmentMapper", attachmentMapper);
        ReflectionTestUtils.setField(service, "crawlSourceMapper", crawlSourceMapper);
    }

    private void stubSourceName(String name) {
        cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlSourceDO src =
                new cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlSourceDO();
        src.setSourceCode("S1");
        src.setSourceName(name);
        when(crawlSourceMapper.selectOne(any())).thenReturn(src);
    }

    private ExternalNoticeIngestDTO htmlDto() {
        ExternalNoticeIngestDTO dto = new ExternalNoticeIngestDTO();
        dto.setSourceCode("S1");
        dto.setTitle("某项目招标公告");
        dto.setOrigUrl("https://example.com/n/1");
        dto.setContentType("HTML");
        dto.setContentHtml("<html>原文</html>");
        dto.setMetaJson("{\"x\":1}");
        return dto;
    }

    private void assignId(Long id) {
        doAnswer(inv -> {
            NoticeExternalDO t = inv.getArgument(0);
            t.setId(id);
            return 1;
        }).when(noticeExternalMapper).insert(any(NoticeExternalDO.class));
    }

    @Test
    void ingest_sourceNameFallsBackToSourceConfig() {
        when(noticeExternalMapper.selectOne(any())).thenReturn(null);
        stubSourceName("测试采集源");
        assignId(300L);

        ExternalNoticeIngestDTO dto = htmlDto();
        dto.setSourceName(null); // 上游未传 → 按 source_code 兜底补源名快照（P2-3）

        Long id = service.ingest(dto);

        assertEquals(300L, id);
        ArgumentCaptor<NoticeExternalDO> cap = ArgumentCaptor.forClass(NoticeExternalDO.class);
        verify(noticeExternalMapper).insert(cap.capture());
        assertEquals("测试采集源", cap.getValue().getSourceName());
    }

    @Test
    void ingest_html_newNoticePersistedAsNew() {
        when(noticeExternalMapper.selectOne(any())).thenReturn(null);
        assignId(100L);

        Long id = service.ingest(htmlDto());

        assertEquals(100L, id);
        ArgumentCaptor<NoticeExternalDO> cap = ArgumentCaptor.forClass(NoticeExternalDO.class);
        verify(noticeExternalMapper).insert(cap.capture());
        NoticeExternalDO saved = cap.getValue();
        assertEquals("S1", saved.getSourceCode());
        assertEquals(NoticeExternalStatusEnum.NEW.getStatus(), saved.getStatus());
        assertEquals(NoticeExternalAuditStatusEnum.UNREVIEWED.getStatus(), saved.getAuditStatus());
        assertEquals("SOCIAL", saved.getPublicScope());
        assertEquals("<html>原文</html>", saved.getContentHtml());
        assertEquals("NONE", saved.getExtractionStatus());
        assertNull(saved.getPdfFileId(), "HTML 形态不落 pdf 字段");
        assertTrue(saved.getOrigUrlHash().matches("[0-9a-f]{64}"), "缺省 urlHash 自动 SHA-256");
    }

    @Test
    void ingest_sameUrlHash_sameContent_idempotentSkips() {
        // 同 URL 同内容（content_hash 一致）幂等跳过（审查修复高1a：幂等判定需状态×内容指纹共同参与）
        ExternalNoticeIngestDTO dto = htmlDto();
        dto.setContentHash("H-FIXED-1");
        NoticeExternalDO existing = new NoticeExternalDO();
        existing.setId(100L);
        existing.setStatus(NoticeExternalStatusEnum.NEW.getStatus());
        existing.setAuditStatus(NoticeExternalAuditStatusEnum.UNREVIEWED.getStatus());
        existing.setContentHash("H-FIXED-1");
        when(noticeExternalMapper.selectOne(any())).thenReturn(existing);

        Long id = service.ingest(dto);

        assertEquals(100L, id);
        verify(noticeExternalMapper, never()).insert(any(NoticeExternalDO.class));
        verify(noticeExternalMapper, never()).updateById(any(NoticeExternalDO.class));
    }

    @Test
    void ingest_rejectedSameUrl_resetsToUnreviewed() {
        // 驳回记录同 URL 重采：重置 NEW+UNREVIEWED 重新排队（审查修复高1a：恢复入口）
        ExternalNoticeIngestDTO dto = htmlDto();
        dto.setContentHash("H-FIXED-2");
        NoticeExternalDO existing = new NoticeExternalDO();
        existing.setId(100L);
        existing.setStatus(NoticeExternalStatusEnum.NEW.getStatus());
        existing.setAuditStatus(NoticeExternalAuditStatusEnum.REJECTED.getStatus());
        existing.setContentHash("H-FIXED-2");
        existing.setFetchCount(1);
        when(noticeExternalMapper.selectOne(any())).thenReturn(existing);
        when(noticeExternalMapper.selectById(100L)).thenReturn(existing); // rebuildAttachments 回读

        Long id = service.ingest(dto);

        assertEquals(100L, id);
        verify(noticeExternalMapper, never()).insert(any(NoticeExternalDO.class));
        // 覆盖走 update(null, UpdateWrapper)（2026-09-09 审查 A6/S3/C2：显式清场防旧产物残留）
        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper> uwCap =
                org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        verify(noticeExternalMapper).update(isNull(), uwCap.capture());
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper uw = uwCap.getValue();
        String sql = uw.getSqlSet();
        String params = String.valueOf(uw.getParamNameValuePairs());
        assertTrue(sql.contains("status="), "sql=" + sql); // 覆盖为 NEW
        assertTrue(params.contains("NEW"), "params=" + params);
        assertTrue(sql.contains("audit_status="), "sql=" + sql);
        assertTrue(params.contains("UNREVIEWED"), "params=" + params);
        assertTrue(sql.contains("fetch_count="), "sql=" + sql);
        assertTrue(sql.contains("body_blocks_status=") && params.contains("NONE"), "清场 bodyBlocks: sql=" + sql + " params=" + params);
        assertTrue(sql.contains("ai_extraction_status=") && params.contains("NONE"), "清场 AI: sql=" + sql + " params=" + params);
        assertTrue(sql.contains("body_blocks_json=") && sql.contains("ai_meta_json="), "清场 JSON 列: sql=" + sql);
    }

    @Test
    void ingest_activeContentChanged_requeuesForReaudit() {
        // 展示中同 URL 内容变化：覆盖内容并打回 NEW+UNREVIEWED 重审（防源站投毒直上）
        ExternalNoticeIngestDTO dto = htmlDto();
        dto.setContentHash("H-NEW");
        NoticeExternalDO existing = new NoticeExternalDO();
        existing.setId(100L);
        existing.setStatus(NoticeExternalStatusEnum.ACTIVE.getStatus());
        existing.setAuditStatus(NoticeExternalAuditStatusEnum.APPROVED.getStatus());
        existing.setContentHash("H-OLD");
        existing.setFetchCount(3);
        when(noticeExternalMapper.selectOne(any())).thenReturn(existing);
        // overwriteNotice 后 rebuildAttachments 回读主档（mock 返回现有对象即可）
        when(noticeExternalMapper.selectById(100L)).thenReturn(existing);

        Long id = service.ingest(dto);

        assertEquals(100L, id);
        verify(noticeExternalMapper, never()).insert(any(NoticeExternalDO.class));
        assertEquals(NoticeExternalStatusEnum.NEW.getStatus(), existing.getStatus(), "覆盖对象应为 NEW");
        assertEquals(NoticeExternalAuditStatusEnum.UNREVIEWED.getStatus(), existing.getAuditStatus());
        assertEquals(4, existing.getFetchCount());
    }

    @Test
    void ingest_pdf_keepsRedLine8Triple() {
        when(noticeExternalMapper.selectOne(any())).thenReturn(null);
        assignId(200L);
        ExternalNoticeIngestDTO dto = htmlDto();
        dto.setContentType("PDF");
        dto.setContentHtml(null);
        dto.setPdfFileId(88L);
        dto.setPdfSha256("abc".repeat(20));
        dto.setPdfSize(4096L);

        service.ingest(dto);

        ArgumentCaptor<NoticeExternalDO> cap = ArgumentCaptor.forClass(NoticeExternalDO.class);
        verify(noticeExternalMapper).insert(cap.capture());
        assertEquals(88L, cap.getValue().getPdfFileId());
        assertTrue(cap.getValue().getPdfSha256().length() == 60);
        assertEquals(4096L, cap.getValue().getPdfSize());
    }

    @Test
    void ingest_withAttachments_persistsRowsAndIdsJson() {
        when(noticeExternalMapper.selectOne(any())).thenReturn(null);
        assignId(300L);
        doAnswer(inv -> {
            NoticeExternalAttachmentDO a = inv.getArgument(0);
            a.setId(501L);
            return 1;
        }).when(attachmentMapper).insert(any(NoticeExternalAttachmentDO.class));
        ExternalNoticeIngestDTO dto = htmlDto();
        ExternalNoticeIngestDTO.Attachment a = new ExternalNoticeIngestDTO.Attachment();
        a.setFileId(7L);
        a.setName("招标文件.pdf");
        a.setExt("pdf");
        a.setSize(123L);
        a.setOrigDownloadUrl("https://example.com/files/1");
        dto.getAttachments().add(a);

        service.ingest(dto);

        ArgumentCaptor<NoticeExternalAttachmentDO> rowCap = ArgumentCaptor.forClass(NoticeExternalAttachmentDO.class);
        verify(attachmentMapper).insert(rowCap.capture());
        assertEquals(300L, rowCap.getValue().getNoticeId());
        assertEquals("PENDING", rowCap.getValue().getVirusStatus());
        ArgumentCaptor<NoticeExternalDO> updateCap = ArgumentCaptor.forClass(NoticeExternalDO.class);
        verify(noticeExternalMapper).updateById(updateCap.capture()); // 新建路径：附件 ids 回填走 updateById
        assertEquals("[501]", updateCap.getValue().getAttachmentIdsJson());
    }

    @Test
    void ingest_missingTitle_throwsInvalid() {
        ExternalNoticeIngestDTO dto = htmlDto();
        dto.setTitle(" ");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.ingest(dto));
        assertEquals(ErrorCodeConstants.EXTERNAL_NOTICE_INGEST_INVALID.getCode(), ex.getCode());
        verify(noticeExternalMapper, never()).insert(any(NoticeExternalDO.class));
    }

    @Test
    void ingest_html_contentHtmlOnlyForHtmlType() {
        when(noticeExternalMapper.selectOne(any())).thenReturn(null);
        assignId(1L);
        ExternalNoticeIngestDTO dto = htmlDto();
        dto.setContentType("ATTACHMENT");

        service.ingest(dto);

        ArgumentCaptor<NoticeExternalDO> cap = ArgumentCaptor.forClass(NoticeExternalDO.class);
        verify(noticeExternalMapper).insert(cap.capture());
        assertNull(cap.getValue().getContentHtml(), "非 HTML 形态不存 content_html");
    }

    // ===== 安全评审加固（objective 3：URL 协议白名单 / JSON 合法性 / 长度上限） =====

    @Test
    void ingest_javascriptUrl_rejectedPayloadInvalid() {
        ExternalNoticeIngestDTO dto = htmlDto();
        dto.setOrigUrl("javascript:alert(1)");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.ingest(dto));
        assertEquals(ErrorCodeConstants.EXTERNAL_NOTICE_INGEST_PAYLOAD_INVALID.getCode(), ex.getCode());
        verify(noticeExternalMapper, never()).insert(any(NoticeExternalDO.class));
    }

    @Test
    void ingest_malformedMetaJson_rejectedPayloadInvalid() {
        when(noticeExternalMapper.selectOne(any())).thenReturn(null);
        ExternalNoticeIngestDTO dto = htmlDto();
        dto.setMetaJson("{not-json");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.ingest(dto));
        assertEquals(ErrorCodeConstants.EXTERNAL_NOTICE_INGEST_PAYLOAD_INVALID.getCode(), ex.getCode());
    }

    @Test
    void ingest_validJsonMetaJson_passes() {
        when(noticeExternalMapper.selectOne(any())).thenReturn(null);
        assignId(321L);
        ExternalNoticeIngestDTO dto = htmlDto();
        dto.setMetaJson("[{\"raw\":\"快照\"}]");

        Long id = service.ingest(dto);

        assertEquals(321L, id);
        ArgumentCaptor<NoticeExternalDO> cap = ArgumentCaptor.forClass(NoticeExternalDO.class);
        verify(noticeExternalMapper).insert(cap.capture());
        assertEquals("[{\"raw\":\"快照\"}]", cap.getValue().getMetaJson());
    }

    @Test
    void ingest_oversizedMetaJson_rejectedPayloadInvalid() {
        when(noticeExternalMapper.selectOne(any())).thenReturn(null);
        ExternalNoticeIngestDTO dto = htmlDto();
        dto.setMetaJson("{\"big\":\"" + "a".repeat(1024 * 1024 + 10) + "\"}");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.ingest(dto));
        assertEquals(ErrorCodeConstants.EXTERNAL_NOTICE_INGEST_PAYLOAD_INVALID.getCode(), ex.getCode());
    }

    // ===== BR-5 发布时间回填 =====
    @Test
    void ingest_publishTime_resolvedFromHtmlWhenMissing() {
        when(noticeExternalMapper.selectOne(any())).thenReturn(null);
        assignId(400L);
        ExternalNoticeIngestDTO dto = htmlDto();
        // 动态“过去”日期（now-5d），避免未来窗口过滤导致解析器跳过
        java.time.LocalDate past = java.time.LocalDate.now().minusDays(5);
        dto.setContentHtml("<html><body><p>发布日期：" + past.getYear() + "年" + past.getMonthValue() + "月"
                + past.getDayOfMonth() + "日</p></body></html>");

        service.ingest(dto);

        ArgumentCaptor<NoticeExternalDO> cap = ArgumentCaptor.forClass(NoticeExternalDO.class);
        verify(noticeExternalMapper).insert(cap.capture());
        assertEquals(past.atStartOfDay(), cap.getValue().getPublishTime());
    }

    @Test
    void ingest_publishTime_explicitValueKept() {
        when(noticeExternalMapper.selectOne(any())).thenReturn(null);
        assignId(401L);
        ExternalNoticeIngestDTO dto = htmlDto();
        dto.setContentHtml("<p>发布日期：2026年9月12日</p>"); // 即使正文另有日期也以采集侧显式值为准
        dto.setPublishTime(java.time.LocalDateTime.of(2026, 9, 10, 8, 0));

        service.ingest(dto);

        ArgumentCaptor<NoticeExternalDO> cap = ArgumentCaptor.forClass(NoticeExternalDO.class);
        verify(noticeExternalMapper).insert(cap.capture());
        assertEquals(java.time.LocalDateTime.of(2026, 9, 10, 8, 0), cap.getValue().getPublishTime());
    }

    @Test
    void ingest_publishTime_noDateFallbackToNow() {
        when(noticeExternalMapper.selectOne(any())).thenReturn(null);
        assignId(402L);
        ExternalNoticeIngestDTO dto = htmlDto();
        dto.setContentHtml("<p>无日期正文</p>");

        service.ingest(dto);

        ArgumentCaptor<NoticeExternalDO> cap = ArgumentCaptor.forClass(NoticeExternalDO.class);
        verify(noticeExternalMapper).insert(cap.capture());
        org.junit.jupiter.api.Assertions.assertNotNull(cap.getValue().getPublishTime()); // 兜底抓取时间，恒非空
    }

}
