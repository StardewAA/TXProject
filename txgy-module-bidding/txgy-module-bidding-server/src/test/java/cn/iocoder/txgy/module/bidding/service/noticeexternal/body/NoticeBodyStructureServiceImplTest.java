package cn.iocoder.txgy.module.bidding.service.noticeexternal.body;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 正文结构化服务测试（策略链：常规解析 / AI 兜底 / 失败归类）。
 */
class NoticeBodyStructureServiceImplTest {

    private NoticeExternalMapper noticeExternalMapper;
    private AiBodyStructurer aiBodyStructurer;
    private NoticeBodyStructureServiceImpl service;

    @BeforeEach
    void setUp() {
        noticeExternalMapper = mock(NoticeExternalMapper.class);
        aiBodyStructurer = mock(AiBodyStructurer.class);
        service = new NoticeBodyStructureServiceImpl();
        ReflectionTestUtils.setField(service, "noticeExternalMapper", noticeExternalMapper);
        ReflectionTestUtils.setField(service, "aiBodyStructurer", aiBodyStructurer);
    }

    private NoticeExternalDO htmlNotice() {
        NoticeExternalDO notice = new NoticeExternalDO();
        notice.setId(7L);
        notice.setTitle("某候选人公示");
        notice.setContentHtml("<h1>公示</h1><table><thead><tr><th>排名</th></tr></thead>"
                + "<tbody><tr><td>1</td></tr></tbody></table>");
        return notice;
    }

    @Test
    void structure_notExists_throws106_001() {
        when(noticeExternalMapper.selectById(9L)).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.structure(9L, false));
        assertEquals(ErrorCodeConstants.EXTERNAL_NOTICE_NOT_EXISTS.getCode(), ex.getCode());
    }

    @Test
    void structure_htmlParser_successWritesBlocks() {
        when(noticeExternalMapper.selectById(7L)).thenReturn(htmlNotice());
        NoticeBodyStructureSnapshot snapshot = service.structure(7L, false);

        assertEquals("SUCCESS", snapshot.getBodyBlocksStatus());
        assertEquals("HTML_STRUCTURE", snapshot.getBodyParserCode());
        assertEquals(2, snapshot.getBlockCount());
        assertEquals(1, snapshot.getTableCount());
        assertEquals(false, snapshot.getAiRecommended());
        ArgumentCaptor<NoticeExternalDO> captor = ArgumentCaptor.forClass(NoticeExternalDO.class);
        verify(noticeExternalMapper).updateById(captor.capture());
        NoticeExternalDO saved = captor.getValue();
        assertEquals("HTML_STRUCTURE", saved.getBodyParserCode());
        assertEquals("SUCCESS", saved.getBodyBlocksStatus());
        assertTrue(saved.getBodyBlocksJson().contains("table"));
    }

    @Test
    void structure_stickyParagraph_recommendsAi() {
        NoticeExternalDO notice = htmlNotice();
        notice.setContentHtml("<p>" + "候选供应商名称金额".repeat(50) + "</p>");
        when(noticeExternalMapper.selectById(7L)).thenReturn(notice);
        NoticeBodyStructureSnapshot snapshot = service.structure(7L, false);
        assertEquals(Boolean.TRUE, snapshot.getAiRecommended());
    }

    @Test
    void structure_aiFallback_success() throws Exception {
        when(noticeExternalMapper.selectById(7L)).thenReturn(htmlNotice());
        when(aiBodyStructurer.structure(any(), any(), any()))
                .thenReturn(List.of(BodyBlock.paragraph("恢复段"),
                        BodyBlock.table(List.of("排名"), List.of(List.of("1")))));
        NoticeBodyStructureSnapshot snapshot = service.structure(7L, true);
        assertEquals("AI_FALLBACK", snapshot.getBodyParserCode());
        assertEquals(2, snapshot.getBlockCount());
    }

    @Test
    void structure_aiNotConfigured_throws106_016() {
        ReflectionTestUtils.setField(service, "aiBodyStructurer", null);
        when(noticeExternalMapper.selectById(7L)).thenReturn(htmlNotice());
        ServiceException ex = assertThrows(ServiceException.class, () -> service.structure(7L, true));
        assertEquals(ErrorCodeConstants.EXTERNAL_NOTICE_AI_NOT_CONFIGURED.getCode(), ex.getCode());
        verify(noticeExternalMapper, never()).updateById(any(NoticeExternalDO.class));
    }

    @Test
    void structure_noMaterial_marksFailedThrows106_019() {
        NoticeExternalDO empty = new NoticeExternalDO();
        empty.setId(8L);
        when(noticeExternalMapper.selectById(8L)).thenReturn(empty);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.structure(8L, false));
        assertEquals(ErrorCodeConstants.EXTERNAL_NOTICE_AI_NO_MATERIAL.getCode(), ex.getCode());
        ArgumentCaptor<NoticeExternalDO> captor = ArgumentCaptor.forClass(NoticeExternalDO.class);
        verify(noticeExternalMapper).updateById(captor.capture());
        assertEquals("FAILED", captor.getValue().getBodyBlocksStatus());
    }

    @Test
    void structure_aiFailure_marksFailedThrows106_020() throws Exception {
        when(noticeExternalMapper.selectById(7L)).thenReturn(htmlNotice());
        when(aiBodyStructurer.structure(any(), any(), any()))
                .thenThrow(new IllegalStateException("AI 排版调用失败"));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.structure(7L, true));
        assertEquals(ErrorCodeConstants.EXTERNAL_NOTICE_BODY_STRUCTURE_FAILED.getCode(), ex.getCode());
        ArgumentCaptor<NoticeExternalDO> captor = ArgumentCaptor.forClass(NoticeExternalDO.class);
        verify(noticeExternalMapper).updateById(captor.capture());
        assertEquals("FAILED", captor.getValue().getBodyBlocksStatus());
        assertNull(captor.getValue().getBodyParserCode());
    }

}
