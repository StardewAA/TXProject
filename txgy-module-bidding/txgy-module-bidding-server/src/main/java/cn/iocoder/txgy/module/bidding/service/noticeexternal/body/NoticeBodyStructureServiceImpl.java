package cn.iocoder.txgy.module.bidding.service.noticeexternal.body;
// 规范依据：设计文档 v0.6 §7（结构化展示=P2 增强，不阻塞主链路）；联调清单 R12②（正文结构化策略链）

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 正文结构化服务实现（人工触发；失败状态化可重试）。
 *
 * <p>策略链：
 * <ol>
 *   <li>常规解析（HTML_STRUCTURE）：HTML 形态用 {@link HtmlBodyBlocksParser} 把
 *       标题/段落/真表格/列表块化；产物无 table 且存在超长粘连段 → 提示 aiRecommended；</li>
 *   <li>AI 兜底（AI_FALLBACK，useAi=true）：把拍平/脏正文交给 {@link AiBodyStructurer}
 *       恢复为结构化块（运营人工触发，不自动烧 token）。</li>
 * </ol>
 * 素材：HTML 形态取 content_html；PDF 形态无 html 时取 extraction_text（段落文本块化），
 * 均无 → 106_019。AI 失败（调用异常/输出非法）→ 状态 FAILED 并抛 106_020。
 */
@Slf4j
@Service
public class NoticeBodyStructureServiceImpl implements NoticeBodyStructureService {

    private static final String BODY_STATUS_NONE = "NONE";
    private static final String BODY_STATUS_SUCCESS = "SUCCESS";
    private static final String BODY_STATUS_FAILED = "FAILED";
    private static final String PARSER_HTML = "HTML_STRUCTURE";
    private static final String PARSER_AI = "AI_FALLBACK";

    /** 疑似"拍平粘连"启发：常规解析无表格，且存在超长单段文本 */
    private static final int STICKY_PARAGRAPH_CHARS = 400;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private NoticeExternalMapper noticeExternalMapper;

    /** AI 兜底排版器：bidding.ai.enabled=true 时注入；未配置时 useAi=true 直接报"未配置" */
    @Autowired(required = false)
    private AiBodyStructurer aiBodyStructurer;

    @Override
    public NoticeBodyStructureSnapshot structure(Long id, boolean useAi) {
        NoticeExternalDO notice = noticeExternalMapper.selectById(id);
        if (notice == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_NOT_EXISTS);
        }
        if (useAi && aiBodyStructurer == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_AI_NOT_CONFIGURED);
        }
        String material = pickMaterial(notice);
        if (!StringUtils.hasText(material)) {
            markFailed(notice, "无可用素材：HTML 与本地抽取文本均为空");
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_AI_NO_MATERIAL);
        }
        List<BodyBlock> blocks;
        String parserCode;
        boolean aiRecommended = false;
        try {
            if (useAi) {
                blocks = aiBodyStructurer.structure(notice.getTitle(), notice.getNoticeTypeRaw(), material);
                parserCode = PARSER_AI;
            } else {
                blocks = HtmlBodyBlocksParser.parse(material);
                parserCode = PARSER_HTML;
                aiRecommended = isStickyCandidate(blocks);
            }
        } catch (Exception e) {
            log.error("[structure][noticeId={} useAi={}] 正文结构化异常", id, useAi, e);
            markFailed(notice, "正文结构化异常: " + rootMessage(e));
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_BODY_STRUCTURE_FAILED);
        }
        if (blocks.isEmpty()) {
            markFailed(notice, "结构化输出为空（无可用正文块）");
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_BODY_STRUCTURE_FAILED);
        }
        notice.setBodyParserCode(parserCode);
        notice.setBodyBlocksStatus(BODY_STATUS_SUCCESS);
        notice.setBodyBlocksJson(writeJson(blocks));
        notice.setBodyBlocksAt(LocalDateTime.now());
        noticeExternalMapper.updateById(notice);
        NoticeBodyStructureSnapshot snapshot = new NoticeBodyStructureSnapshot();
        snapshot.setId(notice.getId());
        snapshot.setBodyBlocksStatus(BODY_STATUS_SUCCESS);
        snapshot.setBodyParserCode(parserCode);
        snapshot.setBlockCount(blocks.size());
        snapshot.setTableCount((int) blocks.stream().filter(b -> "table".equals(b.getType())).count());
        snapshot.setAiRecommended(aiRecommended);
        return snapshot;
    }

    /** 素材优先级：HTML 原文 > PDF 本地抽取文本 */
    private String pickMaterial(NoticeExternalDO notice) {
        if (StringUtils.hasText(notice.getContentHtml())) {
            return notice.getContentHtml();
        }
        if (StringUtils.hasText(notice.getExtractionText())) {
            return notice.getExtractionText();
        }
        return null;
    }

    /** 疑似拍平粘连：无 table 块，且存在 ≥400 字连续段落（原表格被压平的特征） */
    private boolean isStickyCandidate(List<BodyBlock> blocks) {
        boolean hasTable = blocks.stream().anyMatch(b -> "table".equals(b.getType()));
        if (hasTable) {
            return false;
        }
        return blocks.stream().anyMatch(b -> "paragraph".equals(b.getType())
                && b.getText() != null && b.getText().length() >= STICKY_PARAGRAPH_CHARS);
    }

    private void markFailed(NoticeExternalDO notice, String reason) {
        try {
            notice.setBodyBlocksStatus(BODY_STATUS_FAILED);
            notice.setBodyBlocksAt(LocalDateTime.now());
            notice.setBodyParserCode(null);
            // 保留既有 blocks 不清空：展示仍可用上次成功结果/原文兜底（重试成功再覆盖）
            noticeExternalMapper.updateById(notice);
            log.warn("[markFailed][noticeId={}] {}", notice.getId(), reason);
        } catch (Exception e) {
            log.error("[markFailed][noticeId={}] 落库失败", notice.getId(), e);
        }
    }

    private String writeJson(List<BodyBlock> blocks) {
        try {
            return MAPPER.writeValueAsString(blocks);
        } catch (Exception e) {
            throw new IllegalStateException("正文块 JSON 序列化失败", e);
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
