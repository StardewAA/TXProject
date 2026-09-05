package cn.iocoder.txgy.module.bidding.service.noticeexternal;

import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalDetailRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalAttachmentDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalAttachmentMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalMapper;
import cn.iocoder.txgy.module.bidding.enums.NoticeExternalStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 公开详情安全输出测试（安全评审意见 1/2）：
 * contentHtml 留档原样 + contentHtmlSafe 净化版并存；附件 INFECTED 不下发。
 */
class NoticeExternalDetailSecurityTest {

    private NoticeExternalMapper noticeExternalMapper;
    private NoticeExternalAttachmentMapper attachmentMapper;
    private NoticeExternalServiceImpl service;

    @BeforeEach
    void setUp() {
        noticeExternalMapper = mock(NoticeExternalMapper.class);
        attachmentMapper = mock(NoticeExternalAttachmentMapper.class);
        service = new NoticeExternalServiceImpl();
        ReflectionTestUtils.setField(service, "noticeExternalMapper", noticeExternalMapper);
        ReflectionTestUtils.setField(service, "noticeExternalAttachmentMapper", attachmentMapper);
    }

    private NoticeExternalDO activeHtml(String raw) {
        NoticeExternalDO notice = new NoticeExternalDO();
        notice.setId(100L);
        notice.setTitle("某外部公告");
        notice.setOrigUrl("https://src.example.gov.cn/n/100");
        notice.setContentType("HTML");
        notice.setContentHtml(raw);
        notice.setPublicScope("SOCIAL");
        notice.setStatus(NoticeExternalStatusEnum.ACTIVE.getStatus());
        return notice;
    }

    @Test
    void detail_html_keepsRawAndAddsSanitizedSafe() {
        String raw = "<p>正文</p><script>alert(1)</script><a href=\"javascript:x\" onclick=\"y()\">坏</a>";
        when(noticeExternalMapper.selectById(100L)).thenReturn(activeHtml(raw));

        NoticeExternalDetailRespVO resp = service.getDetail(100L);

        assertEquals(raw, resp.getContentHtml(), "留档原样不裁剪（权威要求）");
        assertNotNull(resp.getContentHtmlSafe());
        assertFalse(resp.getContentHtmlSafe().contains("<script"));
        assertFalse(resp.getContentHtmlSafe().contains("javascript:"));
        assertFalse(resp.getContentHtmlSafe().contains("onclick"));
        assertTrue(resp.getContentHtmlSafe().contains("<p>正文</p>"));
    }

    @Test
    void detail_nonHtml_hasNoHtmlFields() {
        NoticeExternalDO pdf = new NoticeExternalDO();
        pdf.setId(100L);
        pdf.setTitle("PDF 公告");
        pdf.setOrigUrl("https://src.example.gov.cn/n/100");
        pdf.setPublicScope("SOCIAL");
        pdf.setStatus(NoticeExternalStatusEnum.ACTIVE.getStatus());
        pdf.setContentType("PDF");
        pdf.setPdfFileId(1L);
        pdf.setPdfSha256("a".repeat(64));
        when(noticeExternalMapper.selectById(100L)).thenReturn(pdf);

        NoticeExternalDetailRespVO resp = service.getDetail(100L);

        assertNull(resp.getContentHtml());
        assertNull(resp.getContentHtmlSafe());
        assertEquals(1L, resp.getPdfFileId());
    }

    @Test
    void detail_attachments_infectedNotExposed() {
        when(noticeExternalMapper.selectById(100L)).thenReturn(activeHtml("<p>x</p>"));
        NoticeExternalAttachmentDO clean = attachment(1L, "CLEAN");
        NoticeExternalAttachmentDO infected = attachment(2L, "INFECTED");
        when(attachmentMapper.selectList(any())).thenReturn(List.of(clean, infected));

        NoticeExternalDetailRespVO resp = service.getDetail(100L);

        assertEquals(1, resp.getAttachments().size(), "INFECTED 附件不下发（病毒扫描闭环）");
        assertEquals(1L, resp.getAttachments().get(0).getId());
    }

    private NoticeExternalAttachmentDO attachment(Long id, String virusStatus) {
        NoticeExternalAttachmentDO a = new NoticeExternalAttachmentDO();
        a.setId(id);
        a.setName("文件" + id);
        a.setVirusStatus(virusStatus);
        a.setOrigDownloadUrl("https://src.example.gov.cn/files/" + id);
        return a;
    }

}
