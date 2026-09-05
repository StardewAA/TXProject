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
    private ExternalNoticeIngestServiceImpl service;

    @BeforeEach
    void setUp() {
        noticeExternalMapper = mock(NoticeExternalMapper.class);
        attachmentMapper = mock(NoticeExternalAttachmentMapper.class);
        service = new ExternalNoticeIngestServiceImpl();
        ReflectionTestUtils.setField(service, "noticeExternalMapper", noticeExternalMapper);
        ReflectionTestUtils.setField(service, "noticeExternalAttachmentMapper", attachmentMapper);
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
    void ingest_html_newNoticePersistedAsNew() {
        when(noticeExternalMapper.selectOne(any())).thenReturn(null);
        assignId(100L);

        Long id = service.ingest(htmlDto());

        assertEquals(100L, id);
        ArgumentCaptor<NoticeExternalDO> cap = ArgumentCaptor.forClass(NoticeExternalDO.class);
        verify(noticeExternalMapper).insert(cap.capture());
        NoticeExternalDO saved = cap.getValue();
        assertEquals(NoticeExternalStatusEnum.NEW.getStatus(), saved.getStatus());
        assertEquals(NoticeExternalAuditStatusEnum.UNREVIEWED.getStatus(), saved.getAuditStatus());
        assertEquals("SOCIAL", saved.getPublicScope());
        assertEquals("<html>原文</html>", saved.getContentHtml());
        assertEquals("NONE", saved.getExtractionStatus());
        assertNull(saved.getPdfFileId(), "HTML 形态不落 pdf 字段");
        assertTrue(saved.getOrigUrlHash().matches("[0-9a-f]{64}"), "缺省 urlHash 自动 SHA-256");
    }

    @Test
    void ingest_sameUrlHash_skipsDuplicates() {
        NoticeExternalDO existing = new NoticeExternalDO();
        existing.setId(100L);
        when(noticeExternalMapper.selectOne(any())).thenReturn(existing);

        Long id = service.ingest(htmlDto());

        assertEquals(100L, id);
        verify(noticeExternalMapper, never()).insert(any(NoticeExternalDO.class));
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
        verify(noticeExternalMapper).updateById(updateCap.capture());
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

}
