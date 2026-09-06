package cn.iocoder.txgy.module.bidding.service.noticeexternal;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalDetailRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalAttachmentDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalAttachmentMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.search.ExternalNoticeSearchEngine;
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
import static org.mockito.Mockito.when;

/**
 * 外部公告公开只读服务测试（Mockito，无 Spring 上下文）：
 * 公开过滤（SOCIAL+ACTIVE）封装、详情三形态字段回填、不存在/不可展示错误码（1-060-106 段）。
 */
class NoticeExternalServiceImplTest {

    private NoticeExternalMapper noticeExternalMapper;
    private NoticeExternalAttachmentMapper noticeExternalAttachmentMapper;
    private ExternalNoticeSearchEngine externalNoticeSearchEngine;
    private NoticeExternalServiceImpl service;

    @BeforeEach
    void setUp() {
        noticeExternalMapper = mock(NoticeExternalMapper.class);
        noticeExternalAttachmentMapper = mock(NoticeExternalAttachmentMapper.class);
        externalNoticeSearchEngine = mock(ExternalNoticeSearchEngine.class);
        service = new NoticeExternalServiceImpl();
        ReflectionTestUtils.setField(service, "noticeExternalMapper", noticeExternalMapper);
        ReflectionTestUtils.setField(service, "noticeExternalAttachmentMapper", noticeExternalAttachmentMapper);
        ReflectionTestUtils.setField(service, "externalNoticeSearchEngine", externalNoticeSearchEngine);
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
        when(externalNoticeSearchEngine.matchNoticeIds("炼化", 200)).thenReturn(List.of(11L, 12L));
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
        when(externalNoticeSearchEngine.matchNoticeIds("不存在词", 200)).thenReturn(new ArrayList<>());

        NoticeExternalPageReqVO req = new NoticeExternalPageReqVO();
        req.setKeyword("不存在词");
        PageResult<NoticeExternalRespVO> result = service.getPage(req);

        assertTrue(result.getList().isEmpty());
        org.mockito.Mockito.verify(noticeExternalMapper, org.mockito.Mockito.never())
                .selectPage(any(Page.class), any(LambdaQueryWrapper.class));
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

}
