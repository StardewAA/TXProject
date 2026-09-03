package cn.iocoder.txgy.module.bidding.service.notice;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticePublicSyncReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticeSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.notice.NoticeContentDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.notice.NoticeDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.notice.NoticeContentMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.notice.NoticeMapper;
import cn.iocoder.txgy.module.bidding.enums.NoticeStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.NoticeTypeEnum;
import cn.iocoder.txgy.module.bidding.service.notice.search.NoticeSearchEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 公告服务核心规则测试（Mockito，无 Spring 上下文）：
 * 新建载荷规整入库 / 正文 v1 追加 / 发布前置校验（正文+PDF 原文件）/
 * 状态机流转 / 正文实质变更才追加版本 / 删除同步移除检索文档。
 */
class NoticeServiceImplTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private NoticeMapper noticeMapper;
    private NoticeContentMapper contentMapper;
    private NoticeSearchEngine searchEngine;
    private NoticeServiceImpl service;

    @BeforeEach
    void setUp() {
        noticeMapper = mock(NoticeMapper.class);
        contentMapper = mock(NoticeContentMapper.class);
        searchEngine = mock(NoticeSearchEngine.class);
        service = new NoticeServiceImpl();
        ReflectionTestUtils.setField(service, "noticeMapper", noticeMapper);
        ReflectionTestUtils.setField(service, "noticeContentMapper", contentMapper);
        ReflectionTestUtils.setField(service, "noticeSearchEngine", searchEngine);
    }

    // ========== 新建 ==========

    @Test
    void create_savesDraftWithCanonicalPayload() throws Exception {
        NoticeSaveReqVO reqVO = baseReq();
        reqVO.setPayload(payload("E4400002026GD0001", "某项目招标公告"));
        reqVO.getPayload().put("NOT_A_SPEC_FIELD", "随手加的字段");
        when(noticeMapper.insert(any(NoticeDO.class))).thenAnswer(inv -> {
            NoticeDO notice = inv.getArgument(0);
            notice.setId(1L);
            return 1;
        });

        Long id = service.createNotice(reqVO);

        assertEquals(1L, id);
        ArgumentCaptor<NoticeDO> captor = ArgumentCaptor.forClass(NoticeDO.class);
        verify(noticeMapper).insert(captor.capture());
        NoticeDO saved = captor.getValue();
        assertEquals(NoticeStatusEnum.DRAFT.name(), saved.getStatus());
        assertTrue(saved.getNoticeCode().startsWith("BN"), "编号为空应自动生成");
        // 载荷规整：权威字段全集（缺失补 null）、未知字段剔除（字段名以数据集组件为准，不臆测）
        LinkedHashMap<?, ?> fields = MAPPER.readValue(saved.getFieldsJson(), LinkedHashMap.class);
        int componentCount = NoticeTypeEnum.TENDER_NOTICE.getDatasetClass().getRecordComponents().length;
        assertEquals(componentCount, fields.size(), "字段数必须等于权威数据集字段全集");
        assertEquals("某项目招标公告", fields.get("NOTICE_NAME"));
        String nullableField = java.util.Arrays.stream(
                        NoticeTypeEnum.TENDER_NOTICE.getDatasetClass().getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .filter(name -> !"NOTICE_NAME".equals(name))
                .findFirst().orElseThrow();
        assertTrue(fields.containsKey(nullableField) && fields.get(nullableField) == null,
                "缺失字段必须补 null（" + nullableField + "）");
        assertTrue(!fields.containsKey("NOT_A_SPEC_FIELD"), "未知字段必须剔除");
        // 草稿无正文：不产生内容行；同步索引（无正文）不触发
        verifyNoInteractions(searchEngine);
    }

    @Test
    void create_withContent_writesVersionOne() {
        NoticeSaveReqVO reqVO = baseReq();
        reqVO.setContent("招标公告正文第一版");
        when(noticeMapper.insert(any(NoticeDO.class))).thenAnswer(inv -> {
            NoticeDO notice = inv.getArgument(0);
            notice.setId(1L);
            return 1;
        });

        service.createNotice(reqVO);

        ArgumentCaptor<NoticeContentDO> contentCaptor = ArgumentCaptor.forClass(NoticeContentDO.class);
        verify(contentMapper).insert(contentCaptor.capture());
        NoticeContentDO content = contentCaptor.getValue();
        assertEquals(1, content.getVersion());
        assertEquals("招标公告正文第一版", content.getContent());
        assertEquals(64, content.getSha256().length());
    }

    // ========== 发布（前置校验：正文 + PDF 原文件必须已保存） ==========

    @Test
    void publish_rejectsWithoutContent() {
        NoticeDO notice = noticeOf(1L, NoticeStatusEnum.DRAFT, null);
        when(noticeMapper.selectById(1L)).thenReturn(notice);

        ServiceException e = assertThrows(ServiceException.class, () -> service.publishNotice(1L));
        assertEquals(Integer.valueOf(1_060_101_004), e.getCode(), "缺正文应报 NOTICE_CONTENT_REQUIRED");
    }

    @Test
    void publish_rejectsWithoutPdfOriginalFile() {
        NoticeDO notice = noticeOf(1L, NoticeStatusEnum.DRAFT, null);
        when(noticeMapper.selectById(1L)).thenReturn(notice);
        when(contentMapper.selectOne(any())).thenReturn(contentOf("正文已就绪"));

        ServiceException e = assertThrows(ServiceException.class, () -> service.publishNotice(1L));
        assertEquals(Integer.valueOf(1_060_101_006), e.getCode(), "PDF 原文件未保存应报 NOTICE_PDF_REQUIRED");
    }

    @Test
    void publish_success_whenContentAndPdfSaved() {
        NoticeDO notice = noticeOf(1L, NoticeStatusEnum.DRAFT, 1001L);
        when(noticeMapper.selectById(1L)).thenReturn(notice);
        when(contentMapper.selectOne(any())).thenReturn(contentOf("正文已就绪"));

        service.publishNotice(1L);

        ArgumentCaptor<NoticeDO> captor = ArgumentCaptor.forClass(NoticeDO.class);
        verify(noticeMapper).updateById(captor.capture());
        assertEquals(NoticeStatusEnum.PUBLISHED.name(), captor.getValue().getStatus());
        // 事务外同步触发检索文档索引
        verify(searchEngine).index(any());
    }

    @Test
    void unpublish_rejectsFromDraft() {
        NoticeDO notice = noticeOf(1L, NoticeStatusEnum.DRAFT, null);
        when(noticeMapper.selectById(1L)).thenReturn(notice);

        ServiceException e = assertThrows(ServiceException.class, () -> service.unpublishNotice(1L));
        assertEquals(Integer.valueOf(1_060_101_002), e.getCode(), "草稿不允许撤下（状态机约束）");
    }

    // ========== 修改：正文实质变更才追加新版本 ==========

    @Test
    void update_appendsVersionOnlyWhenContentChanged() {
        NoticeDO notice = noticeOf(1L, NoticeStatusEnum.PUBLISHED, 1001L);
        when(noticeMapper.selectById(1L)).thenReturn(notice);
        when(contentMapper.selectOne(any())).thenReturn(contentOf("旧版正文"));

        // 情形一：内容变化 → 追加 v2
        NoticeSaveReqVO changed = baseReq();
        changed.setId(1L);
        changed.setContent("新版正文");
        service.updateNotice(changed);
        ArgumentCaptor<NoticeContentDO> captor = ArgumentCaptor.forClass(NoticeContentDO.class);
        verify(contentMapper).insert(captor.capture());
        assertEquals(2, captor.getValue().getVersion());

        // 情形二：内容未变（含首尾空白差异）→ 不追加（reset 后需重建 selectOne 桩）
        reset(contentMapper);
        when(contentMapper.selectOne(any())).thenReturn(contentOf("旧版正文"));
        NoticeSaveReqVO same = baseReq();
        same.setId(1L);
        same.setContent("  旧版正文  ");
        service.updateNotice(same);
        verify(contentMapper, never()).insert(any(NoticeContentDO.class));
    }

    // ========== 删除：逻辑删除 + 移除检索文档 ==========

    @Test
    void delete_removesNoticeAndSearchDoc() {
        NoticeDO notice = noticeOf(1L, NoticeStatusEnum.UNPUBLISHED, 1001L);
        when(noticeMapper.selectById(1L)).thenReturn(notice);

        service.deleteNotice(1L);

        verify(noticeMapper).deleteById(1L);
        verify(searchEngine).remove(1L);
    }

    // ========== 公开同步（BID-SUP-GATHER-02） ==========

    @Test
    void gatherSync_success_onlyForPublishedSocialPublic() {
        NoticeDO notice = noticeOf(1L, NoticeStatusEnum.PUBLISHED, 1001L);
        when(noticeMapper.selectOne(any())).thenReturn(notice);
        when(noticeMapper.selectById(1L)).thenReturn(notice);
        when(contentMapper.selectOne(any())).thenReturn(contentOf("正文已就绪"));

        NoticePublicSyncReqVO reqVO = new NoticePublicSyncReqVO();
        reqVO.setBizNo("BN123456789012");
        reqVO.setDataTimestamp(LocalDateTime.now());
        var resp = service.syncPublicDocument(reqVO);

        assertEquals("SUCCESS", resp.getStatus());
        assertTrue(resp.getTaskNo().startsWith("G"), "应生成同步任务编号");
        verify(searchEngine).index(any());
    }

    @Test
    void gatherSync_rejectsDraftInternalOrGovernmentNotice() {
        NoticeDO notice = noticeOf(1L, NoticeStatusEnum.PUBLISHED, 1001L);
        NoticePublicSyncReqVO reqVO = new NoticePublicSyncReqVO();
        reqVO.setBizNo("BN123456789012");
        reqVO.setDataTimestamp(LocalDateTime.now());
        // 草稿不可同步
        notice.setStatus(NoticeStatusEnum.DRAFT.name());
        when(noticeMapper.selectOne(any())).thenReturn(notice);
        ServiceException e1 = assertThrows(ServiceException.class, () -> service.syncPublicDocument(reqVO));
        assertEquals(Integer.valueOf(1_060_101_007), e1.getCode());
        // 企业内部公告不可同步
        notice.setStatus(NoticeStatusEnum.PUBLISHED.name());
        notice.setInternalFlag(true);
        ServiceException e2 = assertThrows(ServiceException.class, () -> service.syncPublicDocument(reqVO));
        assertEquals(Integer.valueOf(1_060_101_007), e2.getCode());
        // 政务公开不可对外同步
        notice.setInternalFlag(false);
        notice.setPublicLevel("GOVERNMENT");
        ServiceException e3 = assertThrows(ServiceException.class, () -> service.syncPublicDocument(reqVO));
        assertEquals(Integer.valueOf(1_060_101_007), e3.getCode());
        verifyNoInteractions(searchEngine);
    }

    @Test
    void gatherSync_notFound() {
        when(noticeMapper.selectOne(any())).thenReturn(null);
        NoticePublicSyncReqVO reqVO = new NoticePublicSyncReqVO();
        reqVO.setBizNo("BN-不存在");
        reqVO.setDataTimestamp(LocalDateTime.now());
        ServiceException e = assertThrows(ServiceException.class, () -> service.syncPublicDocument(reqVO));
        assertEquals(Integer.valueOf(1_060_101_000), e.getCode(), "应报 NOTICE_NOT_EXISTS");
    }

    // ========== 工具 ==========

    private NoticeSaveReqVO baseReq() {
        NoticeSaveReqVO reqVO = new NoticeSaveReqVO();
        reqVO.setNoticeType(NoticeTypeEnum.TENDER_NOTICE.name());
        reqVO.setTitle("某安置房项目施工招标公告");
        reqVO.setInternalFlag(false);
        reqVO.setPublicLevel("SOCIAL");
        return reqVO;
    }

    private Map<String, Object> payload(String dealCode, String noticeName) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("UNIFIED_DEAL_CODE", dealCode);
        raw.put("NOTICE_NAME", noticeName);
        return raw;
    }

    private NoticeDO noticeOf(Long id, NoticeStatusEnum status, Long pdfFileId) {
        NoticeDO notice = new NoticeDO();
        notice.setId(id);
        notice.setNoticeType(NoticeTypeEnum.TENDER_NOTICE.name());
        notice.setTitle("某项目施工招标公告");
        notice.setStatus(status.name());
        notice.setInternalFlag(false);
        notice.setPublicLevel("SOCIAL");
        notice.setPdfFileId(pdfFileId);
        return notice;
    }

    private NoticeContentDO contentOf(String content) {
        NoticeContentDO contentDO = new NoticeContentDO();
        contentDO.setId(1L);
        contentDO.setNoticeId(1L);
        contentDO.setContentType("BODY");
        contentDO.setContent(content);
        contentDO.setVersion(1);
        return contentDO;
    }
}
