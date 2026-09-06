package cn.iocoder.txgy.module.bidding.service.noticeexternal.ai;
// 规范依据：设计文档 v0.6 §7/§15.x（AI 结构化=P2 增强，不阻塞采集/审核主链路）；错误码 1-060-106-016~019

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.indexer.ExternalNoticeIndexNotifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 结构化抽取服务实现（人工触发；失败状态化可重试）。
 */
@Slf4j
@Service
public class NoticeExternalAiExtractServiceImpl implements NoticeExternalAiExtractService {

    private static final String AI_STATUS_NONE = "NONE";
    private static final String AI_STATUS_SUCCESS = "SUCCESS";
    private static final String AI_STATUS_FAILED = "FAILED";
    private static final String EXTRACTION_SUCCESS = "SUCCESS";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private NoticeExternalMapper noticeExternalMapper;
    @Resource
    private ExternalNoticeIndexNotifier externalNoticeIndexNotifier;
    /** AI 抽取器：bidding.ai.enabled=true 时注入；未配置则触发端点直接报"未配置" */
    @Autowired(required = false)
    private AiNoticeExtractor aiNoticeExtractor;

    @Override
    public AiExtractSnapshot extract(Long id) {
        NoticeExternalDO notice = noticeExternalMapper.selectById(id);
        if (notice == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_NOT_EXISTS);
        }
        if (aiNoticeExtractor == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_AI_NOT_CONFIGURED);
        }
        String text = pickText(notice);
        // 素材判定：本地抽取文本 / HTML 去标签文本；扫描件需经 FileApi 注入 PDF 公网 URL（P2，联调时接线）
        if (!StringUtils.hasText(text)) {
            markFailed(notice, "无可用素材：本地无抽取文本（扫描件需先接入 PDF 附件访问）");
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_AI_NO_MATERIAL);
        }
        AiNoticeExtractRequest request = new AiNoticeExtractRequest();
        request.setNoticeId(id);
        request.setTitle(notice.getTitle());
        request.setNoticeTypeRaw(notice.getNoticeTypeRaw());
        request.setContentText(text);
        AiNoticeExtractResult result;
        try {
            result = aiNoticeExtractor.extract(request);
        } catch (Exception e) {
            log.error("[extract][noticeId={}] AI 调用异常", id, e);
            markFailed(notice, "AI 调用异常: " + rootMessage(e));
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_AI_FAILED);
        }
        if (result == null || result.getFields() == null || result.getFields().isEmpty()) {
            markFailed(notice, "AI 输出非法（非 JSON 或空）");
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_AI_OUTPUT_INVALID);
        }
        // SUCCESS：结构化字段 + 可选全文转写（回写 extraction_text，复用 ES contentText 索引源）
        String fullText = result.getFullText();
        if (StringUtils.hasText(fullText)) {
            notice.setExtractionText(fullText);
            notice.setExtractionStatus(EXTRACTION_SUCCESS);
            notice.setExtractionAt(LocalDateTime.now());
        }
        notice.setAiExtractionStatus(AI_STATUS_SUCCESS);
        notice.setAiMetaJson(writeJson(result.getFields()));
        notice.setAiExtractAt(LocalDateTime.now());
        noticeExternalMapper.updateById(notice);
        // 重放 ES 索引：AI 转写/结构化文本入检索（幂等 PUT 覆盖；失败不阻断）
        reindex(notice.getId());
        AiExtractSnapshot snapshot = new AiExtractSnapshot();
        snapshot.setId(notice.getId());
        snapshot.setAiExtractionStatus(AI_STATUS_SUCCESS);
        snapshot.setMetaFieldCount(result.getFields().size());
        snapshot.setFullTextLength(StringUtils.hasText(fullText) ? fullText.length() : 0);
        return snapshot;
    }

    /** 素材文本优先级：本地 PDF 抽取纯文本 > HTML 去标签正文 */
    private String pickText(NoticeExternalDO notice) {
        if (StringUtils.hasText(notice.getExtractionText())) {
            return notice.getExtractionText();
        }
        if (StringUtils.hasText(notice.getContentHtml())) {
            return Jsoup.parse(notice.getContentHtml()).text();
        }
        return null;
    }

    private void markFailed(NoticeExternalDO notice, String reason) {
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("error", reason == null ? "" : reason);
            notice.setAiExtractionStatus(AI_STATUS_FAILED);
            notice.setAiMetaJson(writeJson(meta));
            notice.setAiExtractAt(LocalDateTime.now());
            noticeExternalMapper.updateById(notice);
        } catch (Exception e) {
            log.error("[markFailed][noticeId={}] 落库失败", notice.getId(), e);
        }
    }

    private void reindex(Long noticeId) {
        try {
            NoticeExternalDO latest = noticeExternalMapper.selectById(noticeId);
            if (latest != null) {
                externalNoticeIndexNotifier.indexNotice(latest);
            }
        } catch (Exception e) {
            log.error("[reindex][noticeId={}] AI 抽取后 ES 重放索引失败（不影响结果，可人工复核）", noticeId, e);
        }
    }

    private String writeJson(Map<String, Object> map) {
        try {
            return MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException("AI meta JSON 序列化失败", e);
        }
    }

    private String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
    }

}
