package cn.iocoder.txgy.module.bidding.service.crawlexternal.fetch;
// [依据] 下载器执行器 v1（2026-09-07，两站实测机制落地）：配置驱动（crawl_source.entry_urls_json）→
//   拉列表（秦源 POST queryContentPage / 国信 GET bulletinListNew）→ 解析器 → 条目 → 全文/详情 → ingest 落库待审。
//   频控：源轮询间隔 ≥ fetch_interval_seconds（保存校验 ≥120s）；源内条目抓取间隔 ENTRY_DELAY_MS。
//   去重：本轮内存 seen(URL hash) + 跨轮 ingest origUrlHash 幂等。
//   P2 待办：FileApi 回填 PDF fileId（PDF 形态以 sha256+size+metaJson{pdfDownloadUrl} 落库，fileId 留空）。

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.dal.dataobject.crawlexternal.CrawlSourceDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.crawlexternal.CrawlTaskDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.crawlexternal.CrawlSourceMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.crawlexternal.CrawlTaskMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.service.crawlexternal.parser.NoticeParseResult;
import cn.iocoder.txgy.module.bidding.service.crawlexternal.parser.NoticeParser;
import cn.iocoder.txgy.module.bidding.service.crawlexternal.parser.ParserRegistry;
import cn.iocoder.txgy.module.bidding.service.crawlexternal.parser.PdfTextNoticeParser;
import cn.iocoder.txgy.module.bidding.service.crawlexternal.parser.ParseInput;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.ingest.ExternalNoticeIngestDTO;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.ingest.ExternalNoticeIngestService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class CrawlSourceRunner {

    /** 秦源栏目 JSON 接口（页面 JS 实测） */
    private static final String QYZB_API = "https://qyzb.shccmg.com/cms/api/dynamicData/queryContentPage";
    /** 源内条目抓取间隔（毫秒，防封） */
    private static final long ENTRY_DELAY_MS = 1500L;
    /** 页间抓取间隔（毫秒，防封：多页回溯时每页之间再等一拍，避免突发高频请求） */
    private static final long PAGE_DELAY_MS = 1500L;
    /** 启用增量回溯（lookbackDays>0）且未显式配置 maxPages 时的页上限（单栏目单轮，防失控） */
    private static final int DEFAULT_LOOKBACK_MAX_PAGES = 10;
    /** 未启用回溯：保持 v1 行为——每栏目每轮仅取第一页（最新 pageSize 条）。增量回溯机制见文档 §11 */
    private static final int SINGLE_PAGE_LIMIT = 1;

    private final ObjectMapper mapper = new ObjectMapper();

    @jakarta.annotation.Resource private HttpFetchClient httpFetchClient;
    @jakarta.annotation.Resource private ParserRegistry parserRegistry;
    @jakarta.annotation.Resource private ExternalNoticeIngestService externalNoticeIngestService;
    @jakarta.annotation.Resource private CrawlSourceMapper crawlSourceMapper;
    @jakarta.annotation.Resource private CrawlTaskMapper crawlTaskMapper;
    @jakarta.annotation.Resource private PdfTextNoticeParser pdfTextNoticeParser;

    /** 执行一个采集源的一轮采集（同步；人工触发/调度调用）。 */
    public RunSummary runSource(String sourceCode) {
        CrawlSourceDO source = crawlSourceMapper.selectOne(new LambdaQueryWrapper<CrawlSourceDO>()
                .eq(CrawlSourceDO::getSourceCode, sourceCode).last("LIMIT 1"));
        if (source == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_SOURCE_NOT_EXISTS);
        }
        RunSummary summary = new RunSummary();
        summary.setSourceCode(sourceCode);
        summary.setStartedAt(LocalDateTime.now());
        Set<String> seenUrlHashes = new HashSet<>(); // 本轮去重；跨轮由 ingest origUrlHash 幂等
        List<CategorySummary> categories = new ArrayList<>();
        summary.setCategories(categories);
        List<CategoryConfig> configs = parseConfigs(source.getEntryUrlsJson());
        for (CategoryConfig config : configs) {
            CategorySummary cs = new CategorySummary();
            cs.setName(config.getName());
            try {
                if ("QYZB_JSON".equals(config.getParser())) {
                    runQyzCategory(source, config, cs, seenUrlHashes);
                } else {
                    runHtmlCategory(source, config, cs, seenUrlHashes);
                }
            } catch (Exception e) {
                cs.setFailed(cs.getFailed() + 1);
                cs.setError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                log.warn("[crawl][source={}][{}] 栏目采集失败", sourceCode, config.getName(), e);
            }
            categories.add(cs);
        }
        for (CategorySummary cs : categories) {
            summary.setTotalIngested(summary.getTotalIngested() + cs.getIngested());
            summary.setTotalFailed(summary.getTotalFailed() + cs.getFailed());
        }
        summary.setFinishedAt(LocalDateTime.now());
        return summary;
    }

    // ==================== 秦源：栏目 JSON 接口（全文直采） ====================
    /**
     * 秦源栏目 JSON 翻页采集（增量回溯见文档 §11）：
     * 启用 lookbackDays 时按发布时间窗口逐页回溯——页内出现早于窗口起点的条目即停（列表倒序，其后页必然更老）；
     * 未启用时保持 v1 单页行为。窗口外条目不采集（视为已被历史轮次覆盖）。
     */
    private void runQyzCategory(CrawlSourceDO source, CategoryConfig config, CategorySummary cs, Set<String> seen) throws Exception {
        int maxPages = effectiveMaxPages(config);
        LocalDate cutoff = lookbackCutoff(config);
        for (int pageNo = 1; pageNo <= maxPages; pageNo++) {
            String body = mapper.writeValueAsString(Map.of(
                    "pageNo", pageNo,
                    "pageSize", config.getPageSize() == null ? 10 : config.getPageSize(),
                    "dto", Map.of("siteId", config.getSiteId() == null ? "725" : config.getSiteId(),
                            "categoryId", config.getCategoryId())));
            String json = httpFetchClient.postJson(QYZB_API, body);
            NoticeParseResult result = parserRegistry.get("QYZB_JSON").parse(inputOf("JSON", json, null));
            List<NoticeParseResult.ListEntry> entries = result.getEntries();
            if (entries == null || entries.isEmpty()) {
                break; // 空页：没有更多
            }
            boolean stopAfterPage = false;
            for (NoticeParseResult.ListEntry entry : entries) {
                if (outOfWindow(cutoff, entry.getDate())) {
                    stopAfterPage = true; // 页内出现早于窗口的条目 → 其后（更老）页无需再翻
                    break;
                }
                cs.setFetched(cs.getFetched() + 1);
                try {
                    if (ingestQyzEntry(source.getSourceCode(), entry, seen)) {
                        cs.setIngested(cs.getIngested() + 1);
                    }
                } catch (Exception e) {
                    cs.setFailed(cs.getFailed() + 1);
                    recordTask(source, config, entry, "FAILED", e.getMessage());
                    log.warn("[crawl][source={}] 条目落库失败 url={} err={}", source.getSourceCode(), entry.getUrl(), e.getMessage());
                }
                sleep(ENTRY_DELAY_MS);
            }
            if (stopAfterPage || pageNo >= maxPages) {
                break;
            }
            sleep(PAGE_DELAY_MS); // 页间防封
        }
    }

    /** 秦源条目：rows.text 全文直采（本轮 seen 去重；跨轮 ingest urlHash 幂等） */
    private boolean ingestQyzEntry(String sourceCode, NoticeParseResult.ListEntry entry, Set<String> seen) throws Exception {
        if (!StringUtils.hasText(entry.getUrl()) || !StringUtils.hasText(entry.getContentHtml())) {
            throw new IllegalStateException("条目缺 url/contentHtml（接口结构变化？）");
        }
        String urlHash = sha256Hex(entry.getUrl());
        if (!seen.add(urlHash)) {
            return false; // 本轮已见（列表重复/翻页重叠）
        }
        ExternalNoticeIngestDTO dto = new ExternalNoticeIngestDTO();
        dto.setSourceCode(sourceCode);
        dto.setOrigUrl(entry.getUrl());
        dto.setTitle(entry.getTitle());
        dto.setContentType("HTML");
        dto.setContentHtml(entry.getContentHtml());
        dto.setNoticeTypeRaw(entry.getCategory());
        LocalDateTime publish = parseDate(entry.getDate());
        dto.setPublishTime(publish == null ? LocalDateTime.now() : publish);
        dto.setIssuerName(extractIssuer(entry.getContentHtml()));
        dto.setParserCode("QYZB_JSON");
        externalNoticeIngestService.ingest(dto);
        return true;
    }

    // ==================== 国信（HTML 列表 → 详情 PDF） ====================
    /**
     * 国信（HTML 列表 → 详情 PDF）翻页采集（增量回溯见文档 §11）：
     * 列表 URL 为 page=N 服务端分页，翻页=改写 page 参数；停页/窗口语义同秦源 JSON 驱动。
     */
    private void runHtmlCategory(CrawlSourceDO source, CategoryConfig config, CategorySummary cs, Set<String> seen) throws Exception {
        String listUrl = config.getListUrl();
        if (!StringUtils.hasText(listUrl)) {
            throw new IllegalStateException("栏目配置缺 listUrl");
        }
        int maxPages = effectiveMaxPages(config);
        LocalDate cutoff = lookbackCutoff(config);
        for (int pageNo = 1; pageNo <= maxPages; pageNo++) {
            String pageUrl = pageNo == 1 ? listUrl : bumpPageParam(listUrl, pageNo);
            String html = httpFetchClient.getText(pageUrl);
            NoticeParseResult result = parserRegistry.get("GX_E_BIDDING").parse(inputOf("HTML", html, pageUrl));
            List<NoticeParseResult.ListEntry> entries = result.getEntries();
            if (entries == null || entries.isEmpty()) {
                if (pageNo == 1) {
                    cs.setError("列表无条目（可能为栏目壳页，检查 listUrl）");
                }
                break;
            }
            boolean stopAfterPage = false;
            for (NoticeParseResult.ListEntry entry : entries) {
                if (outOfWindow(cutoff, entry.getDate())) {
                    stopAfterPage = true;
                    break;
                }
                cs.setFetched(cs.getFetched() + 1);
                try {
                    if (ingestGxEntry(source.getSourceCode(), entry, seen)) {
                        cs.setIngested(cs.getIngested() + 1);
                    }
                } catch (Exception e) {
                    cs.setFailed(cs.getFailed() + 1);
                    recordTask(source, config, entry, "FAILED", e.getMessage());
                    log.warn("[crawl][source={}] 国信条目失败 url={} err={}", source.getSourceCode(), entry.getUrl(), e.getMessage());
                }
                sleep(ENTRY_DELAY_MS);
            }
            if (stopAfterPage || pageNo >= maxPages) {
                break;
            }
            sleep(PAGE_DELAY_MS); // 页间防封
        }
    }

    /** 翻页：把 listUrl 中 page=N 替换为 page=pageNo（未带 page 参数则原样返回，仅单页）。 */
    private static String bumpPageParam(String listUrl, int pageNo) {
        if (pageNo <= 1 || !StringUtils.hasText(listUrl)) {
            return listUrl;
        }
        return listUrl.replaceAll("([?&]page=)\\d+", "$1" + pageNo);
    }

    /** 国信条目：详情页 → pdf.js 直链 → 下载 PDF → 文本解析 → 落库（PDF 形态；fileId 待 FileApi P2 回填） */
    private boolean ingestGxEntry(String sourceCode, NoticeParseResult.ListEntry entry, Set<String> seen) throws Exception {
        String detailUrl = entry.getUrl();
        if (!StringUtils.hasText(detailUrl)) {
            throw new IllegalStateException("条目缺详情 URL");
        }
        String urlHash = sha256Hex(detailUrl);
        if (!seen.add(urlHash)) {
            return false; // 本轮已见
        }
        String detailHtml = httpFetchClient.getText(detailUrl);
        NoticeParseResult detail = parserRegistry.get("GX_E_BIDDING").parse(inputOf("HTML", detailHtml, detailUrl));
        String pdfUrl = detail.getMeta() == null ? null : detail.getMeta().get("pdfDownloadUrl");
        if (!StringUtils.hasText(pdfUrl)) {
            throw new IllegalStateException("详情页未解析出 PDF 直链（页面结构变化？）");
        }
        byte[] pdf = httpFetchClient.getBytes(pdfUrl);
        if (pdf == null || pdf.length < 8) {
            throw new IllegalStateException("PDF 下载为空");
        }
        ParseInput pdfInput = new ParseInput();
        pdfInput.setPayloadType("PDF");
        pdfInput.setFileName("notice.pdf");
        pdfInput.setFileBytes(pdf);
        NoticeParseResult parsed = pdfTextNoticeParser.parse(pdfInput);
        ExternalNoticeIngestDTO dto = new ExternalNoticeIngestDTO();
        dto.setSourceCode(sourceCode);
        dto.setOrigUrl(detailUrl);
        dto.setTitle(StringUtils.hasText(parsed.getTitle()) ? parsed.getTitle() : entry.getTitle());
        dto.setContentType("PDF");
        dto.setPdfSha256(sha256HexBytes(pdf));
        dto.setPdfSize((long) pdf.length);
        dto.setParserCode("GX_E_BIDDING");
        dto.setIssuerName(parsed.getIssuerName());
        dto.setProjectCode(parsed.getProjectCode());
        dto.setTenderProjectCode(parsed.getTenderProjectCode());
        dto.setPublishTime(parsed.getPublishTime() != null ? parsed.getPublishTime() : parseDate(entry.getDate()));
        dto.setMetaJson("{\"pdfDownloadUrl\":\"" + escapeJson(pdfUrl) + "\",\"textChars\":"
                + (parsed.getContentText() == null ? 0 : parsed.getContentText().length())
                + ",\"fileStatus\":\"UNREGISTERED_FILE\"}");
        externalNoticeIngestService.ingest(dto);
        return true;
    }

    // ==================== 配置解析与工具 ====================

    @Data
    public static class RunSummary {
        private String sourceCode;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private int totalIngested;
        private int totalFailed;
        private List<CategorySummary> categories = new ArrayList<>();
    }

    @Data
    public static class CategorySummary {
        private String name;
        private int fetched;
        private int ingested;
        private int failed;
        private String error;
    }

    @Data
    public static class CategoryConfig {
        private String name;
        private String parser;
        private String categoryId;
        private String siteId;
        private Integer pageSize;
        private String listUrl;
        /** 增量回溯窗口（天）：>0 时按发布时间窗口翻页补齐窗口内公告（文档 §11）；缺省不启用 */
        private Integer lookbackDays;
        /** 本栏目单轮页上限（防封；启用回溯且未配时默认 10，未启用默认 1） */
        private Integer maxPages;
    }

    private List<CategoryConfig> parseConfigs(String entryUrlsJson) {
        List<CategoryConfig> configs = new ArrayList<>();
        if (!StringUtils.hasText(entryUrlsJson)) {
            return configs;
        }
        try {
            JsonNode node = mapper.readTree(entryUrlsJson);
            if (node.isArray()) {
                for (JsonNode item : node) {
                    CategoryConfig config = new CategoryConfig();
                    config.setName(textOf(item, "name"));
                    config.setParser(textOf(item, "parser"));
                    config.setCategoryId(textOf(item, "categoryId"));
                    config.setSiteId(textOf(item, "siteId"));
                    config.setPageSize(item.path("pageSize").isNumber() ? item.path("pageSize").asInt() : null);
                    config.setListUrl(textOf(item, "listUrl"));
                    config.setLookbackDays(item.path("lookbackDays").isNumber() ? item.path("lookbackDays").asInt() : null);
                    config.setMaxPages(item.path("maxPages").isNumber() ? item.path("maxPages").asInt() : null);
                    if (config.getName() != null && (config.getCategoryId() != null || config.getListUrl() != null)) {
                        configs.add(config);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[crawl] entry_urls_json 解析失败: {}", e.getMessage());
        }
        return configs;
    }

    private static String textOf(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static ParseInput inputOf(String type, String content, String url) {
        ParseInput input = new ParseInput();
        input.setPayloadType(type);
        input.setContent(content);
        input.setUrl(url);
        return input;
    }

    private void recordTask(CrawlSourceDO source, CategoryConfig config, NoticeParseResult.ListEntry entry, String status, String message) {
        try {
            CrawlTaskDO task = new CrawlTaskDO();
            task.setSourceCode(source.getSourceCode());
            task.setUrl(entry.getUrl());
            task.setUrlHash(entry.getUrl() == null ? null : sha256Hex(entry.getUrl()));
            task.setChannelCode("GENERIC");
            task.setParserCode(config.getParser());
            task.setStatus(status);
            task.setAttempt(1);
            task.setFinishedAt(LocalDateTime.now());
            task.setMessage(message == null ? "" : (message.length() > 500 ? message.substring(0, 500) : message));
            crawlTaskMapper.insert(task);
        } catch (Exception ignore) {
            // 任务留痕失败不阻断主流程
        }
    }

    private static String extractIssuer(String contentHtml) {
        if (!StringUtils.hasText(contentHtml)) {
            return null;
        }
        String bodyText = org.jsoup.Jsoup.parse(contentHtml).body() == null ? "" : org.jsoup.Jsoup.parse(contentHtml).body().text();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(招标人|采购人)\s*[:：]\s*([^\\s]{2,60})").matcher(bodyText);
        if (matcher.find()) {
            String name = matcher.group(2).trim();
            int cut = name.length();
            for (String marker : new String[]{"地", "联", "电", "，", ",", "。"}) {
                int at = name.indexOf(marker);
                if (at >= 0 && at < cut) {
                    cut = at;
                }
            }
            return name.substring(0, Math.max(2, cut)).trim();
        }
        return null;
    }

    /** 单栏目单轮页上限：显式 maxPages 优先；启用回溯且未配 → 默认 10；未启用回溯 → 1（v1 行为） */
    private int effectiveMaxPages(CategoryConfig config) {
        if (config.getMaxPages() != null) {
            return Math.max(1, config.getMaxPages());
        }
        boolean lookback = config.getLookbackDays() != null && config.getLookbackDays() > 0;
        return lookback ? DEFAULT_LOOKBACK_MAX_PAGES : SINGLE_PAGE_LIMIT;
    }

    /** 窗口起点（含当天向前 lookbackDays 天）；未启用回溯返回 null（不过滤） */
    private static LocalDate lookbackCutoff(CategoryConfig config) {
        if (config.getLookbackDays() == null || config.getLookbackDays() <= 0) {
            return null;
        }
        return LocalDate.now().minusDays(config.getLookbackDays());
    }

    /**
     * 条目是否早于窗口起点：早于 → 视为已被历史轮次覆盖（不采集，且其后页必然更老 → 停）；
     * 无日期/日期不可解析 → 保守视为窗口内（宁多采靠 ingest origUrlHash 幂等去重，不丢数据）。
     */
    private static boolean outOfWindow(LocalDate cutoff, String date) {
        if (cutoff == null || !StringUtils.hasText(date)) {
            return false;
        }
        try {
            LocalDate parsed = LocalDate.parse(date.trim().substring(0, 10));
            return parsed.isBefore(cutoff);
        } catch (RuntimeException e) {
            return false; // 不可解析：不拦截
        }
    }

    private static LocalDateTime parseDate(String date) {
        if (!StringUtils.hasText(date)) {
            return null;
        }
        try {
            return LocalDate.parse(date.trim().substring(0, 10)).atStartOfDay();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String sha256Hex(String text) {
        return sha256HexBytes(text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256HexBytes(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
