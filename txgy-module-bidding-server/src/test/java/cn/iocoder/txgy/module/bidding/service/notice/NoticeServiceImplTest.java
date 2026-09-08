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
import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.service.notice.search.NoticeSearchEngine;
import cn.iocoder.txgy.module.bidding.service.noticefollow.FollowService;
import cn.iocoder.txgy.module.bidding.service.subject.SubjectEligibilityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
    private SubjectEligibilityService subjectEligibilityService;
    private FollowService followService;
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
        subjectEligibilityService = mock(SubjectEligibilityService.class);
        ReflectionTestUtils.setField(service, "subjectEligibilityService", subjectEligibilityService);
        followService = mock(FollowService.class);
        ReflectionTestUtils.setField(service, "followService", followService);
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
        // 缺失必填已由 checkRequired 拦截；此处验证“非必填的可选字段”补 null
        String nullableField = java.util.Arrays.stream(
                        NoticeTypeEnum.TENDER_NOTICE.getDatasetClass().getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .filter(name -> !"NOTICE_NAME".equals(name))
                .filter(name -> !NoticePayloadUtils.requiredFieldNames(NoticeTypeEnum.TENDER_NOTICE).contains(name))
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
        reqVO.setPayload(payload("E-CODE", "某项目招标公告"));
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

    // ========== 删除守卫（2026-09-09：仅草稿/已撤下可删；已发布先撤下；已归档留痕禁删） ==========

    @Test
    void delete_rejectsPublished_mustUnpublishFirst() {
        NoticeDO notice = noticeOf(1L, NoticeStatusEnum.PUBLISHED, 1001L);
        when(noticeMapper.selectById(1L)).thenReturn(notice);

        ServiceException e = assertThrows(ServiceException.class, () -> service.deleteNotice(1L));
        assertEquals(Integer.valueOf(1_060_101_009), e.getCode(), "已发布直接删除应报 NOTICE_DELETE_STATUS_INVALID");
        verify(noticeMapper, never()).deleteById(1L);
    }

    @Test
    void delete_rejectsArchived_keepReadonlyTrail() {
        NoticeDO notice = noticeOf(1L, NoticeStatusEnum.ARCHIVED, 1001L);
        when(noticeMapper.selectById(1L)).thenReturn(notice);

        ServiceException e = assertThrows(ServiceException.class, () -> service.deleteNotice(1L));
        assertEquals(Integer.valueOf(1_060_101_009), e.getCode(), "归档终态禁止删除（留痕）");
        verify(noticeMapper, never()).deleteById(1L);
    }

    @Test
    void delete_allowsDraft() {
        NoticeDO notice = noticeOf(1L, NoticeStatusEnum.DRAFT, null);
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

    // ========== 截止时间查询（BID-ENT-NOTICE-02） ==========

    @Test
    void deadline_mapsTenderNoticeTimes() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("BID_SECTION_CODES", "S1,S2");
        payload.put("NOTICE_SEND_TIME", "20260901090000");
        payload.put("BID_DOC_REFER_END_TIME", "20260915170000");
        payload.put("SUBMIT_TIMESTAMP", "20260930090000");
        payload.put("BID_OPEN_TIME", "20260930090001");
        NoticeDO notice = noticeOf(1L, NoticeStatusEnum.PUBLISHED, null);
        notice.setNoticeCode("BN-A");
        notice.setNoticeType(NoticeTypeEnum.TENDER_NOTICE.name());
        notice.setFieldsJson(MAPPER.writeValueAsString(payload));
        when(noticeMapper.selectList(any())).thenReturn(List.of(notice));

        var resp = service.getBidDeadline("S2");

        assertEquals("S2", resp.getBidSectionCode());
        assertEquals("20260901090000", resp.getFileIssueTime());
        assertEquals("20260915170000", resp.getClarifyDeadline());
        assertEquals("20260930090000", resp.getSubmitDeadline());
        assertEquals("20260930090001", resp.getBidOpenTime());
    }

    @Test
    void deadline_changeNoticeOverridesSubmitTime() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("BID_SECTION_CODES", "S1");
        payload.put("BID_END_TIME", "20261010100000");
        NoticeDO notice = noticeOf(2L, NoticeStatusEnum.PUBLISHED, null);
        notice.setNoticeCode("BN-C");
        notice.setNoticeType(NoticeTypeEnum.CHANGE_NOTICE.name());
        notice.setFieldsJson(MAPPER.writeValueAsString(payload));
        when(noticeMapper.selectList(any())).thenReturn(List.of(notice));

        var resp = service.getBidDeadline("S1");

        assertEquals("20261010100000", resp.getSubmitDeadline(), "变更公告 BID_END_TIME 应覆盖递交截止");
    }


    // ========== 招标人发布门（授权+完善） ==========

    @Test
    void create_blocksWhenTendererNotAuthorized() {
        // 招标投标法第8/16条：发布主体须为已授权招标人；平台未授权 → 106_031，且不落库
        NoticeSaveReqVO reqVO = baseReq();
        reqVO.setPayload(payload("E4400002026GD0002", "未授权招标人公告"));
        ServiceException ex = ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_NOT_AUTHORIZED);
        org.mockito.Mockito.doThrow(ex).when(subjectEligibilityService).assertTendererIssuanceAllowed();

        assertThrows(ServiceException.class, () -> service.createNotice(reqVO));
        verify(noticeMapper, never()).insert(any(NoticeDO.class));
    }

    @Test
    void publish_blocksWhenTendererProfileIncomplete() {
        // 仅授权未完善：主体信息必填不全 → 106_034（需求口径：被授权投标人仍须完善信息）
        NoticeDO notice = noticeOf(10L, NoticeStatusEnum.DRAFT, null);
        when(noticeMapper.selectById(10L)).thenReturn(notice);
        ServiceException ex = ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_PROFILE_INCOMPLETE);
        org.mockito.Mockito.doThrow(ex).when(subjectEligibilityService).assertTendererIssuanceAllowed();

        assertThrows(ServiceException.class, () -> service.publishNotice(10L));
        verify(searchEngine, never()).index(any());
    }

    // ========== 内部公告关注扇出（业务核查补全 2026-09-07：发布/更新触发关注动态；草稿不打扰订阅者） ==========

    @Test
    void publish_triggersInternalFanoutWithLatestContentVersion() {
        NoticeDO notice = noticeOf(1L, NoticeStatusEnum.DRAFT, 1001L);
        when(noticeMapper.selectById(1L)).thenReturn(notice);
        when(contentMapper.selectOne(any())).thenReturn(contentOf("正文已就绪")); // v1

        service.publishNotice(1L);

        verify(followService).handleInternalNoticeChanged(eq("PUBLISHED"), eq(1L), eq("某项目施工招标公告"),
                eq((String) null), eq((String) null), eq((String) null), eq(1));
    }

    @Test
    void update_contentChangedOnPublishedNotice_fansOutUpdatedWithNewVersion() {
        NoticeDO notice = noticeOf(1L, NoticeStatusEnum.PUBLISHED, 1001L);
        when(noticeMapper.selectById(1L)).thenReturn(notice);
        when(contentMapper.selectOne(any())).thenReturn(contentOf("旧版正文")); // v1

        NoticeSaveReqVO changed = baseReq();
        changed.setId(1L);
        changed.setContent("新版正文");
        service.updateNotice(changed);

        // 原状态已发布 + 正文产生新版本 v2 → UPDATED 扇出（publisherName 等展示字段由载荷规整，无载荷时为 null）
        verify(followService).handleInternalNoticeChanged(eq("UPDATED"), eq(1L), eq("某项目施工招标公告"),
                eq((String) null), eq((String) null), eq((String) null), eq(2));
    }

    @Test
    void update_partialFieldsOnly_preservesUnsubmittedValuesAndFansOutWithProjectCode() {
        // 部分更新实测修复（2026-09-07）：仅提交标题+正文时，不得翻转 internalFlag / 清空项目归属，
        // 且 UPDATED 扇出必须携带库中项目 code（否则 PROJECT 维度订阅漏提醒）
        NoticeDO notice = noticeOf(1L, NoticeStatusEnum.PUBLISHED, 1001L);
        notice.setProjectCode("P-1");
        notice.setProjectName("某项目");
        notice.setPublisherName("某集团");
        notice.setInternalFlag(true);
        when(noticeMapper.selectById(1L)).thenReturn(notice);
        when(contentMapper.selectOne(any())).thenReturn(contentOf("旧版正文")); // v1

        NoticeSaveReqVO changed = baseReq();
        changed.setId(1L);
        changed.setContent("新版正文");
        service.updateNotice(changed);

        ArgumentCaptor<NoticeDO> saved = ArgumentCaptor.forClass(NoticeDO.class);
        verify(noticeMapper).updateById(saved.capture());
        assertEquals(Boolean.TRUE, saved.getValue().getInternalFlag(), "未提交 internalFlag 不得翻回 false");
        assertEquals("P-1", saved.getValue().getProjectCode(), "未提交 projectCode 保留库中原值");
        assertEquals("某集团", saved.getValue().getPublisherName());
        verify(followService).handleInternalNoticeChanged(eq("UPDATED"), eq(1L), eq("某项目施工招标公告"),
                eq("某集团"), eq("某项目"), eq("P-1"), eq(2));
    }

    @Test
    void update_withExplicitInternalFlagChange_overridesValue() {
        // 显式提交 internalFlag=false（如把公告从企业内部改为公开）仍生效
        NoticeDO notice = noticeOf(1L, NoticeStatusEnum.PUBLISHED, 1001L);
        notice.setInternalFlag(true);
        when(noticeMapper.selectById(1L)).thenReturn(notice);
        when(contentMapper.selectOne(any())).thenReturn(contentOf("旧版正文"));

        NoticeSaveReqVO changed = baseReq();
        changed.setId(1L);
        changed.setInternalFlag(false);
        changed.setContent("与旧版不同的正文");
        service.updateNotice(changed);

        ArgumentCaptor<NoticeDO> saved = ArgumentCaptor.forClass(NoticeDO.class);
        verify(noticeMapper).updateById(saved.capture());
        assertEquals(Boolean.FALSE, saved.getValue().getInternalFlag());
    }

    @Test
    void update_contentUnchanged_noInternalFanout() {
        NoticeDO notice = noticeOf(1L, NoticeStatusEnum.PUBLISHED, 1001L);
        when(noticeMapper.selectById(1L)).thenReturn(notice);
        when(contentMapper.selectOne(any())).thenReturn(contentOf("旧版正文"));

        NoticeSaveReqVO same = baseReq();
        same.setId(1L);
        same.setContent("  旧版正文  "); // 仅空白差异：不产生版本 → 不打扰订阅者
        service.updateNotice(same);

        verifyNoInteractions(followService);
    }

    @Test
    void update_contentChangedOnDraftNotice_noInternalFanout() {
        // 草稿期编辑虽追加版本（发布前的版本演进），但不扇出（订阅者只会看到最终发布内容）
        NoticeDO notice = noticeOf(1L, NoticeStatusEnum.DRAFT, 1001L);
        when(noticeMapper.selectById(1L)).thenReturn(notice);
        when(contentMapper.selectOne(any())).thenReturn(contentOf("旧版正文"));

        NoticeSaveReqVO changed = baseReq();
        changed.setId(1L);
        changed.setContent("新版正文");
        service.updateNotice(changed);

        verifyNoInteractions(followService);
    }

    @Test
    void create_draftNotice_noInternalFanout() {
        // 新建（DRAFT）不扇出：动态只发生在 PUBLISHED 之后（发布/已发布内容更新）
        NoticeSaveReqVO reqVO = baseReq();
        reqVO.setPayload(payload("E-CODE", "某项目招标公告"));
        reqVO.setContent("招标公告正文第一版");
        when(noticeMapper.insert(any(NoticeDO.class))).thenAnswer(inv -> {
            NoticeDO notice = inv.getArgument(0);
            notice.setId(1L);
            return 1;
        });

        service.createNotice(reqVO);

        verifyNoInteractions(followService);
    }

    // ========== 测试工具 ==========

    private NoticeSaveReqVO baseReq() {
        NoticeSaveReqVO reqVO = new NoticeSaveReqVO();
        reqVO.setNoticeType(NoticeTypeEnum.TENDER_NOTICE.name());
        reqVO.setTitle("某项目施工招标公告");
        return reqVO;
    }

    private Map<String, Object> payload(String dealCode, String noticeName) {
        Map<String, Object> raw = new LinkedHashMap<>();
        // 权威必填字段全部置值（VO数据规范 必填=是），否则新建会被必填校验拦截
        for (String field : NoticePayloadUtils.requiredFieldNames(NoticeTypeEnum.TENDER_NOTICE)) {
            raw.put(field, "REQ");
        }
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
