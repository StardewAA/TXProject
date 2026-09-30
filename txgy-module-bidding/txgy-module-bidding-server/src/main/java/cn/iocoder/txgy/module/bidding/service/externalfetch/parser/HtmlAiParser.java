package cn.iocoder.txgy.module.bidding.service.externalfetch.parser;

// [依据] 用户口径 2026-09-09：新增 AI 解析器——正文截取后交平台 AI 输出结构化（含行业相关判定），
//           覆盖本地规则解析。流程：本地截正文→contentText→AI→结构化字段回填。

import cn.iocoder.txgy.module.bidding.service.noticeexternal.ai.AiNoticeExtractRequest;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.ai.AiNoticeExtractResult;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.ai.AiNoticeExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * AI 解析器：HTML 正文 → 平台 AI 结构化抽取（复用 AiChatNoticeExtractor，bidding.ai.enabled=true 生效）。
 * 输出：标题/项目/发布单位/时间 + 防腐保温行业判定（industryFlag/Confidence）+ AI 原始 meta 留痕。
 */
@Component
public class HtmlAiParser implements NoticeParser {

    /** AI 抽取器：bidding.ai.enabled=true 时注入；未启用则解析 FAILED 提示 */
    @Autowired(required = false)
    protected AiNoticeExtractor aiNoticeExtractor;

    @Override public String getParserCode() { return "HTML_AI"; }
    @Override public String getParserName() { return "HTML + AI 结构化解析（正文→平台 AI，含行业相关判定）"; }
    @Override public String getDescription() { return "先用本地规则截 HTML 正文纯文本，再送平台 AI 结构化抽取与防腐保温行业判定（需 bidding.ai.enabled=true）；输出 title/projectName/issuer/publishTime + industryFlag/Confidence。"; }
    @Override public List<String> supportedPayloadTypes() { return List.of("HTML"); }

    @Override
    public NoticeParseResult parse(ParseInput input) {
        NoticeParseResult out = new NoticeParseResult();
        out.setParserCode(getParserCode());
        out.setParserName(getParserName());
        if (aiNoticeExtractor == null) {
            out.setError("AI 未启用：bidding.ai.enabled=true 且配置 ai-server 后可用（HTML_AI 解析器）");
            out.setStatus(NoticeParseResult.Status.FAILED);
            return out;
        }
        if (input == null || !StringUtils.hasText(input.getContent())) {
            out.setError("HTML 内容为空");
            out.setStatus(NoticeParseResult.Status.FAILED);
            return out;
        }
        // 1) 本地截正文纯文本（复用通用 HTML 抽取的正文处理）
        NoticeParseResult local = new NoticeParseResult();
        HtmlNoticeExtractor.extract(input.getContent(), input.getUrl(), local);
        String text = local.getContentText();
        if (!StringUtils.hasText(text)) {
            text = org.jsoup.Jsoup.parse(input.getContent()).text();
        }
        // 2) 送 AI
        try {
            AiNoticeExtractRequest req = new AiNoticeExtractRequest();
            req.setTitle(local.getTitle());
            req.setNoticeTypeRaw(local.getNoticeTypeRaw());
            req.setContentText(text);
            AiNoticeExtractResult result = aiNoticeExtractor.extract(req);
            applyAiFields(out, local, result);
        } catch (Exception e) {
            out.setError("AI 解析失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            out.setStatus(NoticeParseResult.Status.FAILED);
            return out;
        }
        out.finalizeStatus();
        return out;
    }

    /** 本地正文 + AI 结构化字段合并到结果 */
    protected void applyAiFields(NoticeParseResult out, NoticeParseResult local, AiNoticeExtractResult result) {
        if (result == null || result.getFields() == null) {
            out.setError("AI 无结构化输出");
            out.setStatus(NoticeParseResult.Status.FAILED);
            return;
        }
        Map<String, Object> f = result.getFields();
        // 保留本地正文（AI 只出字段，不取正文）
        out.setContentHtml(local.getContentHtml());
        out.setContentText(local.getContentText());
        out.setContentType("HTML");
        out.setOrigUrl(local.getOrigUrl());
        out.setTitle(str(f.get("title")) != null ? str(f.get("title")) : local.getTitle());
        out.setProjectName(str(f.get("projectName")));
        out.setProjectCode(str(f.get("projectCode")));
        out.setTenderProjectCode(str(f.get("tenderProjectCode")));
        out.setIssuerName(str(f.get("issuer")));
        out.setNoticeTypeRaw(local.getNoticeTypeRaw());
        out.setPublishTime(parseLdt(f.get("publishTime")));
        out.setIndustryFlag(str(f.get("industryFlag")));
        out.setIndustryConfidence(str(f.get("industryConfidence")));
        try {
            out.setAiMetaJson(cn.iocoder.txgy.framework.common.util.json.JsonUtils.toJsonString(f));
        } catch (Exception ignore) {
            out.setAiMetaJson(String.valueOf(f));
        }
        out.setStatus(NoticeParseResult.Status.SUCCESS);
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o).trim(); }

    private static LocalDateTime parseLdt(Object raw) {
        if (raw == null) { return null; }
        String s = String.valueOf(raw).trim();
        if (s.length() >= 19) { s = s.substring(0, 19).replace("T", " "); }
        try { return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); }
        catch (Exception e) {
            try { return LocalDateTime.parse(s.substring(0, Math.min(10, s.length())), DateTimeFormatter.ofPattern("yyyy-MM-dd")); }
            catch (Exception e2) { return null; }
        }
    }
}
