package cn.iocoder.txgy.module.bidding.service.noticeexternal.ai;
// 规范依据：设计文档 v0.6 §7（AI 结构化增强）；错误码 1-060-106-016~019

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.indexer.ExternalNoticeIndexNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 结构化抽取服务测试（Mockito）：状态化成功/失败、未配置、无素材、错误码归类。
 */
class NoticeExternalAiExtractServiceImplTest {

    private NoticeExternalMapper noticeExternalMapper;
    private ExternalNoticeIndexNotifier externalNoticeIndexNotifier;
    private AiNoticeExtractor aiNoticeExtractor;
    private NoticeExternalAiExtractServiceImpl service;

    @BeforeEach
    void setUp() {
        noticeExternalMapper = mock(NoticeExternalMapper.class);
        externalNoticeIndexNotifier = mock(ExternalNoticeIndexNotifier.class);
        aiNoticeExtractor = mock(AiNoticeExtractor.class);
        service = new NoticeExternalAiExtractServiceImpl();
        ReflectionTestUtils.setField(service, "noticeExternalMapper", noticeExternalMapper);
        ReflectionTestUtils.setField(service, "externalNoticeIndexNotifier", externalNoticeIndexNotifier);
        ReflectionTestUtils.setField(service, "aiNoticeExtractor", aiNoticeExtractor);
    }

    private NoticeExternalDO noticeWithText() {
        NoticeExternalDO notice = new NoticeExternalDO();
        notice.setId(7L);
        notice.setTitle("某装置大修消缺公开招标公告");
        notice.setNoticeTypeRaw("招标公告");
        notice.setExtractionText("正文：炼化公司…");
        notice.setContentType("PDF");
        return notice;
    }

    @Test
    void extract_notExists_throws106_001() {
        when(noticeExternalMapper.selectById(9L)).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.extract(9L));
        assertEquals(ErrorCodeConstants.EXTERNAL_NOTICE_NOT_EXISTS.getCode(), ex.getCode());
    }

    @Test
    void extract_notConfigured_throws106_016() {
        ReflectionTestUtils.setField(service, "aiNoticeExtractor", null);
        when(noticeExternalMapper.selectById(7L)).thenReturn(noticeWithText());
        ServiceException ex = assertThrows(ServiceException.class, () -> service.extract(7L));
        assertEquals(ErrorCodeConstants.EXTERNAL_NOTICE_AI_NOT_CONFIGURED.getCode(), ex.getCode());
        verify(noticeExternalMapper, never()).updateById(any(NoticeExternalDO.class));
    }

    @Test
    void extract_noMaterial_marksFailedAndThrows106_019() {
        NoticeExternalDO empty = new NoticeExternalDO();
        empty.setId(8L);
        empty.setTitle("扫描件公告");
        when(noticeExternalMapper.selectById(8L)).thenReturn(empty);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.extract(8L));
        assertEquals(ErrorCodeConstants.EXTERNAL_NOTICE_AI_NO_MATERIAL.getCode(), ex.getCode());
        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper> uwCap =
                org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        verify(noticeExternalMapper).update(isNull(), uwCap.capture());
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper uwX = uwCap.getValue();
        assertTrue(uwX.getSqlSet().contains("ai_extraction_status=") && String.valueOf(uwX.getParamNameValuePairs()).contains("FAILED"));
    }

    @Test
    void extract_success_writesFieldsFullTextAndReindexes() throws Exception {
        NoticeExternalDO notice = noticeWithText();
        when(noticeExternalMapper.selectById(7L)).thenReturn(notice);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("projectName", "装置大修项目");
        fields.put("budgetAmount", "5000000");
        when(aiNoticeExtractor.extract(any(AiNoticeExtractRequest.class)))
                .thenReturn(new AiNoticeExtractResult(fields, "AI 转写全文…"));

        AiExtractSnapshot snapshot = service.extract(7L);

        assertEquals("SUCCESS", snapshot.getAiExtractionStatus());
        assertEquals(2, snapshot.getMetaFieldCount());
        assertEquals("AI 转写全文…".length(), snapshot.getFullTextLength());
        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper> uwCap =
                org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        verify(noticeExternalMapper).update(isNull(), uwCap.capture());
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper uw = uwCap.getValue();
        String sqlSet = uw.getSqlSet();
        String params = String.valueOf(uw.getParamNameValuePairs());
        assertTrue(sqlSet.contains("ai_extraction_status=") && params.contains("SUCCESS"), params);
        assertTrue(sqlSet.contains("extraction_text="), sqlSet);
        assertTrue(sqlSet.contains("extraction_status=") && params.contains("SUCCESS"), params);
        assertTrue(sqlSet.contains("ai_meta_json="), sqlSet);
        // 全文回写后重放索引（检索可见）
        verify(externalNoticeIndexNotifier).indexNotice(any(NoticeExternalDO.class));
    }

    @Test
    void extract_invalidOutput_marksFailedAndThrows106_017() {
        when(noticeExternalMapper.selectById(7L)).thenReturn(noticeWithText());
        when(aiNoticeExtractor.extract(any(AiNoticeExtractRequest.class)))
                .thenReturn(new AiNoticeExtractResult(new LinkedHashMap<>(), null));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.extract(7L));
        assertEquals(ErrorCodeConstants.EXTERNAL_NOTICE_AI_OUTPUT_INVALID.getCode(), ex.getCode());
        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper> uwCap =
                org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        verify(noticeExternalMapper).update(isNull(), uwCap.capture());
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper uwX = uwCap.getValue();
        assertTrue(uwX.getSqlSet().contains("ai_extraction_status=") && String.valueOf(uwX.getParamNameValuePairs()).contains("FAILED"));
    }

    @Test
    void extract_industryVerdict_writesFlagAndConfidence() throws Exception {
        NoticeExternalDO notice = noticeWithText();
        when(noticeExternalMapper.selectById(7L)).thenReturn(notice);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("projectName", "装置大修项目");
        fields.put("industryFlag", "BELONGS");
        fields.put("industryConfidence", 0.95);
        fields.put("industryReason", "涉及装置防腐保温施工");
        when(aiNoticeExtractor.extract(any(AiNoticeExtractRequest.class)))
                .thenReturn(new AiNoticeExtractResult(fields, null));

        AiExtractSnapshot snapshot = service.extract(7L);

        assertEquals("SUCCESS", snapshot.getAiExtractionStatus());
        assertEquals("BELONGS", snapshot.getIndustryFlag());
        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper> uwCap =
                org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        verify(noticeExternalMapper).update(isNull(), uwCap.capture());
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper uw3 = uwCap.getValue();
        String sqlSet3 = uw3.getSqlSet();
        String params3 = String.valueOf(uw3.getParamNameValuePairs());
        assertTrue(sqlSet3.contains("industry_flag=") && params3.contains("BELONGS"), params3);
        assertTrue(sqlSet3.contains("industry_confidence=") && params3.contains("0.95"), params3);
        assertTrue(sqlSet3.contains("ai_meta_json="), sqlSet3);
        // 抽取不涉全文时不覆盖 extraction 状态
        // 注意：ai_extraction_status 含子串 extraction_status，须排除——检查是否有独立 extraction_status 列（=#{ 前缀前无 ai_）
        String plainExtraction = sqlSet3.replace("ai_extraction_status", "");
        assertTrue(!plainExtraction.contains("extraction_status="), sqlSet3);
    }

    @Test
    void extract_aiException_marksFailedAndThrows106_018() throws Exception {
        when(noticeExternalMapper.selectById(7L)).thenReturn(noticeWithText());
        when(aiNoticeExtractor.extract(any(AiNoticeExtractRequest.class)))
                .thenThrow(new IllegalStateException("AI 服务调用失败: 连接超时"));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.extract(7L));
        assertEquals(ErrorCodeConstants.EXTERNAL_NOTICE_AI_FAILED.getCode(), ex.getCode());
        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper> uwCap =
                org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        verify(noticeExternalMapper).update(isNull(), uwCap.capture());
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper uwX = uwCap.getValue();
        assertTrue(uwX.getSqlSet().contains("ai_extraction_status=") && String.valueOf(uwX.getParamNameValuePairs()).contains("FAILED"));
        assertTrue(String.valueOf(uwX.getParamNameValuePairs()).contains("连接超时")); // 值在 params（ai_meta_json）
    }

}
