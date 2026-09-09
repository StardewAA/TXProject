package cn.iocoder.txgy.module.bidding.service.externalfetch;

// 规范依据：外部信息获取 v1.0 §五/§六（Redis Stream 轻信号消费端执行：收 taskId → 回查 payload → 下载/直采 → ingest 回写）；
//           正文文本/源文件由下载链路 FileApi 承载，AI 结构化复用 noticeexternal.ai（v1.0 §三）。

import cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO;
import cn.iocoder.txgy.module.bidding.service.externalfetch.fetch.UrlHostGuard;
import cn.iocoder.txgy.module.bidding.service.externalfetch.parser.NoticeParseResult;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.ingest.ExternalNoticeIngestDTO;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.ingest.ExternalNoticeIngestService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 信息获取任务执行器（消费端：一条 task = 一次下载/解析/落库工作单元）。
 *
 * <p>输入：task 行（url + payloadJson 快照）。执行：按 payload 是否有全文/文件引用分派：
 * <ul>
 *   <li>payload 带 contentHtml/全文引用（QYZB 类列表直采）→ 组装 DTO 直采落库（无网络）；</li>
 *   <li>payload 仅有 URL/标题（GX/详情类）→ 保留 URL 由后续详情抓取链路（P2）处理，本轮标记 SKIPPED 语义由调用方决定。</li>
 * </ul>
 * 纯同步、无状态：生产端直接跑与消费者异步共用同一执行语义。
 */
@Slf4j
@Component
public class CrawlNoticeProcessor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private ExternalNoticeIngestService externalNoticeIngestService;
    @Resource
    private cn.iocoder.txgy.module.bidding.service.externalfetch.fetch.HttpFetchClient httpFetchClient;
    @Resource
    private cn.iocoder.txgy.module.bidding.service.externalfetch.parser.ParserRegistry parserRegistry;
    @Resource
    private cn.iocoder.txgy.module.bidding.service.externalfetch.parser.PdfTextNoticeParser pdfTextNoticeParser;
    @Resource
    private cn.iocoder.txgy.module.infra.api.file.FileApi fileApi;
    @Resource
    private cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlSourceMapper crawlSourceMapper;

    /**
     * 执行一条信息获取任务（按 payload 快照落库）。
     *
     * @return 成功落库返回公告 id；缺执行素材（无全文且无详情 URL）抛 IllegalStateException 由调用方计失败
     */
    public Long execute(CrawlTaskDO task) throws Exception {
        if (task == null) {
            throw new IllegalStateException("任务不存在");
        }
        JsonNode payload = parsePayload(task.getPayloadJson());
        String title = text(payload, "title");
        String url = text(payload, "url");
        String date = text(payload, "date");
        String category = text(payload, "category");
        String contentHtml = text(payload, "contentHtml");
        if (!StringUtils.hasText(url)) {
            url = task.getUrl();
        }
        // 全文文件化：payload 无内联全文时按 rawFileUrl 从平台文件服务拉取（v1.0 §2.2 原文文件化）
        if (!StringUtils.hasText(contentHtml)) {
            String rawFileUrl = text(payload, "rawFileUrl");
            if (StringUtils.hasText(rawFileUrl)) {
                // 纵深防御：非平台文件端点的 rawFileUrl（理论上自产受控）也过主机白名单（SSRF）
                if (!UrlHostGuard.isInternalFileUrl(rawFileUrl)) {
                    assertUrlAllowed(rawFileUrl, task.getSourceCode());
                }
                contentHtml = httpFetchClient.getText(rawFileUrl);
            }
        }
        if (!StringUtils.hasText(contentHtml)) {
            // GX 类：task.url=详情页 → 详情→PDF 直链→下载→PDFBox→归档→落库（长任务异步化的核心收益）
            if (task.getParserCode() != null && task.getParserCode().startsWith("GX")) {
                return executeGxDetail(task, title, url, date);
            }
            // 2026-09-10 全链路补全：非 GX 的 HTML 详情任务（HTML_GENERIC/EBIDDING 等 HTML 列表栏目，async 模式）
            // → 下载详情 HTML → 按任务 parser 解析 → 落库（此前 async 下这类任务全部“素材缺失”死信）
            if (task.getParserCode() == null || !"QYZB_JSON".equals(task.getParserCode())) {
                return executeHtmlDetail(task, title, url, date);
            }
            // QYZB_JSON 直采任务 payload 必有全文/文件引用，走到这里属异常
            throw new IllegalStateException("任务无正文素材（全文直采缺失/详情链路未接线），url=" + url);
        }
        ExternalNoticeIngestDTO dto = new ExternalNoticeIngestDTO();
        dto.setSourceCode(task.getSourceCode());
        dto.setOrigUrl(url);
        dto.setTitle(StringUtils.hasText(title) ? title : null);
        dto.setContentType("HTML");
        dto.setContentHtml(contentHtml);
        dto.setNoticeTypeRaw(category);
        LocalDateTime publish = parseDate(date);
        dto.setPublishTime(publish); // 2026-09-10：解析不出不再用抓取时间冒充发布时间（宁缺毋假，可经审核侧 AI 抽取回填）
        dto.setIssuerName(extractIssuer(contentHtml));
        dto.setParserCode(task.getParserCode() == null ? task.getChannelCode() : task.getParserCode());
        return externalNoticeIngestService.ingest(dto);
    }

    /**
     * HTML 详情类任务（HTML_GENERIC/EBIDDING 等 HTML 列表栏目，async 模式全链路补全 2026-09-10）：
     * 下载详情 HTML → 按任务 parserCode 解析（站点/通用）→ 结构化字段落库；下载前过主机白名单（SSRF）。
     */
    private Long executeHtmlDetail(CrawlTaskDO task, String title, String url, String date) throws Exception {
        if (!StringUtils.hasText(url)) {
            throw new IllegalStateException("HTML 详情任务缺详情 URL");
        }
        assertUrlAllowed(url, task.getSourceCode());
        String html = httpFetchClient.getText(url);
        if (!StringUtils.hasText(html)) {
            throw new IllegalStateException("详情页下载为空 url=" + url);
        }
        String parserCode = task.getParserCode() == null ? "HTML_GENERIC" : task.getParserCode();
        NoticeParseResult parsed = parserRegistry.get(parserCode).parse(inputOf("HTML", html, url));
        if (parsed.getStatus() == NoticeParseResult.Status.FAILED
                && !StringUtils.hasText(parsed.getTitle()) && !StringUtils.hasText(parsed.getContentText())) {
            throw new IllegalStateException("详情页解析无产出（parser=" + parserCode + "）: "
                    + (parsed.getError() == null ? "未知" : parsed.getError()));
        }
        ExternalNoticeIngestDTO dto = new ExternalNoticeIngestDTO();
        dto.setSourceCode(task.getSourceCode());
        dto.setOrigUrl(url);
        dto.setTitle(StringUtils.hasText(parsed.getTitle()) ? parsed.getTitle() : title);
        dto.setContentType("HTML");
        dto.setContentHtml(html); // 原文留档（展示侧 sanitize）
        dto.setIssuerName(parsed.getIssuerName());
        dto.setProjectCode(parsed.getProjectCode());
        dto.setTenderProjectCode(parsed.getTenderProjectCode());
        dto.setProjectName(parsed.getProjectName());
        dto.setPublishTime(parsed.getPublishTime() != null ? parsed.getPublishTime() : parseDate(date));
        dto.setParserCode(parserCode);
        return externalNoticeIngestService.ingest(dto);
    }

    /** GX（国信）类任务：task.url=详情页 → 详情 HTML → PDF.js 直链 → 下载 PDF → PDFBox 文本抽取 → FileApi 归档 → 落库（PDF 形态）。
     *  长下载（详情+PDF）在消费端线程执行，不占生产端/Redis（v1.0 §6.2）。 */
    private Long executeGxDetail(CrawlTaskDO task, String title, String url, String date) throws Exception {
        if (!StringUtils.hasText(url)) {
            throw new IllegalStateException("GX 任务缺详情 URL");
        }
        assertUrlAllowed(url, task.getSourceCode()); // 详情页下载前：主机白名单（SSRF 防护）
        String detailHtml = httpFetchClient.getText(url);
        NoticeParseResult detail = parserRegistry.get("GX_E_BIDDING")
                .parse(inputOf("HTML", detailHtml, url));
        String pdfUrl = detail.getMeta() == null ? null : detail.getMeta().get("pdfDownloadUrl");
        if (!StringUtils.hasText(pdfUrl)) {
            throw new IllegalStateException("详情页未解析出 PDF 直链（页面结构变化？）");
        }
        assertUrlAllowed(pdfUrl, task.getSourceCode()); // PDF 直链下载前：主机白名单（SSRF 防护）
        byte[] pdf = httpFetchClient.getBytes(pdfUrl);
        if (pdf == null || pdf.length < 8) {
            throw new IllegalStateException("PDF 下载为空");
        }
        NoticeParseResult parsed = pdfTextNoticeParser.parse(inputOfPdf(pdf));
        ExternalNoticeIngestDTO dto = new ExternalNoticeIngestDTO();
        dto.setSourceCode(task.getSourceCode());
        dto.setOrigUrl(url);
        dto.setTitle(StringUtils.hasText(parsed.getTitle()) ? parsed.getTitle() : title);
        dto.setContentType("PDF");
        dto.setPdfSha256(sha256Hex(pdf));
        dto.setPdfSize((long) pdf.length);
        dto.setParserCode("GX_E_BIDDING");
        dto.setIssuerName(parsed.getIssuerName());
        dto.setProjectCode(parsed.getProjectCode());
        dto.setTenderProjectCode(parsed.getTenderProjectCode());
        dto.setPublishTime(parsed.getPublishTime() != null ? parsed.getPublishTime() : parseDate(date));
        dto.setMetaJson("{\"pdfDownloadUrl\":\"" + escapeJson(pdfUrl) + "\",\"textChars\":" + (parsed.getContentText() == null ? 0 : parsed.getContentText().length())
                + ",\"fileStatus\":\"REGISTERED\"}");
        uploadPdfArchive(dto, pdf, StringUtils.hasText(parsed.getTitle()) ? parsed.getTitle() : title);
        return externalNoticeIngestService.ingest(dto);
    }

    /**
     * 下载目标主机白名单校验（SSRF 防护）：源存在则强校验（白名单=base_url ∪ allowed_hosts 及子域）；
     * 源缺失放行（手工任务/单测等无源上下文不阻断）。
     */
    private void assertUrlAllowed(String url, String sourceCode) {
        if (!StringUtils.hasText(url) || !StringUtils.hasText(sourceCode)) {
            return;
        }
        cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlSourceDO source = crawlSourceMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlSourceDO>()
                        .eq(cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlSourceDO::getSourceCode, sourceCode)
                        .last("LIMIT 1"));
        if (source == null) {
            return; // 无源上下文：不阻断
        }
        if (!UrlHostGuard.isExternalAllowed(url, source.getBaseUrl(), source.getAllowedHosts())) {
            throw new IllegalStateException("下载目标不在采集源白名单（SSRF 防护），url=" + url);
        }
    }

    private static cn.iocoder.txgy.module.bidding.service.externalfetch.parser.ParseInput inputOfPdf(byte[] pdf) {
        cn.iocoder.txgy.module.bidding.service.externalfetch.parser.ParseInput in = new cn.iocoder.txgy.module.bidding.service.externalfetch.parser.ParseInput();
        in.setPayloadType("PDF");
        in.setFileName("notice.pdf");
        in.setFileBytes(pdf);
        return in;
    }

    private static cn.iocoder.txgy.module.bidding.service.externalfetch.parser.ParseInput inputOf(String type, String content, String url) {
        cn.iocoder.txgy.module.bidding.service.externalfetch.parser.ParseInput in = new cn.iocoder.txgy.module.bidding.service.externalfetch.parser.ParseInput();
        in.setPayloadType(type);
        in.setContent(content);
        in.setUrl(url);
        return in;
    }

    /** PDF 归档平台文件服务（失败不阻断落库，metaJson 保留外站直链兜底） */
    private void uploadPdfArchive(ExternalNoticeIngestDTO dto, byte[] pdf, String title) {
        if (pdf == null || pdf.length == 0) {
            return;
        }
        try {
            String safeName = (title == null ? "notice" : title.replaceAll("[/:*?\"<>|]", "_")).trim();
            if (safeName.length() > 80) {
                safeName = safeName.substring(0, 80);
            }
            String u = fileApi.createFile(pdf, safeName + ".pdf", "bidding/notice-external", "application/pdf");
            if (StringUtils.hasText(u)) {
                dto.setPdfDownloadUrl(u);
            }
        } catch (Exception e) {
            log.warn("[processor][pdf-archive] 归档失败（不阻断入库）: {}", e.getMessage());
        }
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String sha256Hex(byte[] data) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 计算失败", e);
        }
    }

    private JsonNode parsePayload(String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return null;
        }
        try {
            return MAPPER.readTree(payloadJson);
        } catch (Exception e) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull() || field == null) {
            return null;
        }
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || v.isMissingNode()) {
            return null;
        }
        String s = v.isTextual() ? v.asText() : v.toString();
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** 宽松解析外站日期（多格式兜底；解析失败返回 null 由调用方取抓取时间） */
    private static LocalDateTime parseDate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String s = raw.trim();
        // 去尾毫秒/时区偏移（yyyy-MM-ddTHH:mm:ss.SSS+0800 → 截断到秒）
        int cut = s.length();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '+' || ch == '-' || ch == 'Z') {
                if (i > 8) {
                    cut = i;
                    break;
                }
            }
        }
        s = s.substring(0, cut).replace('T', ' ');
        DateTimeFormatter[] formatters = new DateTimeFormatter[]{
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT),
                DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT),
                DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ROOT)
        };
        for (DateTimeFormatter fmt : formatters) {
            try {
                return LocalDateTime.parse(s, fmt);
            } catch (Exception ignore) {
                // 尝试下一格式
            }
        }
        try {
            return LocalDate.parse(s.substring(0, Math.min(10, s.length()))).atStartOfDay();
        } catch (Exception e) {
            return null;
        }
    }

    /** 招标人/采购人启发式抽取（正文前 800 字符内的 “招标人|采购人：XXX”） */
    private static String extractIssuer(String contentHtml) {
        if (!StringUtils.hasText(contentHtml)) {
            return null;
        }
        try {
            String bodyText = org.jsoup.Jsoup.parse(contentHtml).body() == null ? "" : org.jsoup.Jsoup.parse(contentHtml).body().text();
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(招标人|采购人)\\s*[:：]\\s*([^\\s]{2,60})").matcher(bodyText);
            if (matcher.find()) {
                String name = matcher.group(2).trim();
                int c = name.length();
                for (String marker : new String[]{"地", "联", "电", "，", ",", "。"}) {
                    int at = name.indexOf(marker);
                    if (at >= 0 && at < c) {
                        c = at;
                    }
                }
                return name.substring(0, Math.max(2, c)).trim();
            }
        } catch (Exception ignore) {
            // 抽取失败返回 null（不阻断）
        }
        return null;
    }
}