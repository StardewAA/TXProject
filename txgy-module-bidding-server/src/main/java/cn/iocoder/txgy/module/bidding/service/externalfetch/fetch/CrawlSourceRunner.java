package cn.iocoder.txgy.module.bidding.service.externalfetch.fetch;
// [依据] 下载器执行器 v1（2026-09-07，两站实测机制落地）：配置驱动（crawl_source.entry_urls_json）→
//   拉列表（秦源 POST queryContentPage / 国信 GET bulletinListNew）→ 解析器 → 条目 → 全文/详情 → ingest 落库待审。
//   频控：源轮询间隔 ≥ fetch_interval_seconds（保存校验 ≥120s）；源内条目抓取间隔 ENTRY_DELAY_MS。
//   去重：本轮内存 seen(URL hash) + 跨轮 ingest origUrlHash 幂等。
//   P2 待办：FileApi 回填 PDF fileId（PDF 形态以 sha256+size+metaJson{pdfDownloadUrl} 落库，fileId 留空）。

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlSourceDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlSourceMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlTaskMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.service.externalfetch.parser.NoticeParseResult;
import cn.iocoder.txgy.module.bidding.service.externalfetch.parser.NoticeParser;
import cn.iocoder.txgy.module.bidding.service.externalfetch.parser.ParserRegistry;
import cn.iocoder.txgy.module.bidding.service.externalfetch.parser.PdfTextNoticeParser;
import cn.iocoder.txgy.module.bidding.service.externalfetch.parser.ParseInput;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.ingest.ExternalNoticeIngestDTO;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.ingest.ExternalNoticeIngestService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    /** 页内条目处理间隔（毫秒）。注意：频率主控在 application.yaml bidding.crawl.runner.*，此默认值仅兜底（配置缺失时保守取值） */
    @Value("${bidding.crawl.runner.entry-delay-ms:1500}")
    private long entryDelayMs;
    /** 翻页间隔（毫秒）：多页回溯每页之间等一拍再翻下一页。频率主控在 application.yaml（page-delay-ms），此默认值仅兜底 */
    @Value("${bidding.crawl.runner.page-delay-ms:60000}")
    private long pageDelayMs;
    /** 启用增量回溯（lookbackDays>0）且未显式配置 maxPages 时的页上限（单栏目单轮，防失控） */
    private static final int DEFAULT_LOOKBACK_MAX_PAGES = 10;
    /** 首次全量默认窗口（天）：栏目未配 fullLookbackDays 时使用；配置文件 bidding.crawl.runner.full-lookback-days 可覆盖（2026-09-09 用户口径 60 天） */
    @Value("${bidding.crawl.runner.full-lookback-days:60}")
    private int fullLookbackDaysDefault;
    /** 首次全量单栏目单轮页上限（防失控；bidding.crawl.runner.full-max-pages 配置，默认 300；停页由窗口外判定负责） */
    @Value("${bidding.crawl.runner.full-max-pages:300}")
    private int fullMaxPagesDefault;
    /** 未启用回溯：保持 v1 行为——每栏目每轮仅取第一页（最新 pageSize 条）。增量回溯机制见文档 §11 */
    private static final int SINGLE_PAGE_LIMIT = 1;

    private final ObjectMapper mapper = new ObjectMapper();

    @jakarta.annotation.Resource private HttpFetchClient httpFetchClient;
    @jakarta.annotation.Resource private cn.iocoder.txgy.module.infra.api.file.FileApi fileApi;
    @jakarta.annotation.Resource private ParserRegistry parserRegistry;
    @jakarta.annotation.Resource private ExternalNoticeIngestService externalNoticeIngestService;
    @jakarta.annotation.Resource private CrawlSourceMapper crawlSourceMapper;
    @jakarta.annotation.Resource private CrawlTaskMapper crawlTaskMapper;
    @jakarta.annotation.Resource private PdfTextNoticeParser pdfTextNoticeParser;
    @jakarta.annotation.Resource private ApiFetchDriver apiFetchDriver;
    @jakarta.annotation.Resource private cn.iocoder.txgy.module.bidding.service.externalfetch.CrawlTaskDispatchService crawlTaskDispatchService;
    /** 异步投递模式（bidding.crawl.runner.async-mode=true 启用：列表发现→落 task+发轻信号，下载/落库由消费者执行）；默认 false=同步直抓（现状） */
    @Value("${bidding.crawl.runner.async-mode:false}")
    private boolean asyncMode;

    /** 执行一个采集源的一轮采集（同步；人工触发/调度调用；无批次上下文，任务不挂 jobId）。 */
    public RunSummary runSource(String sourceCode) {
        return runSource(sourceCode, null, false);
    }

    /** 执行一个采集源的一轮采集，并把本轮失败任务挂到指定批次（jobId 可为 null，如单测/无批次场景）。 */
    public RunSummary runSource(String sourceCode, Long jobId) {
        return runSource(sourceCode, jobId, false);
    }

    /** 执行一个采集源的一轮采集。full=true 时按栏目 fullLookbackDays（可配置首次全量窗口）回溯补历史，
     * 用于「首次全量爬取」：增量调度/人工 run 用 lookbackDays 小窗口，全量 run-full 用 fullLookbackDays（默认 60 天）。 */
    public RunSummary runSource(String sourceCode, Long jobId, boolean full) {
        CrawlSourceDO source = crawlSourceMapper.selectOne(new LambdaQueryWrapper<CrawlSourceDO>()
                .eq(CrawlSourceDO::getSourceCode, sourceCode).last("LIMIT 1"));
        if (source == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_SOURCE_NOT_EXISTS);
        }
        RunSummary summary = new RunSummary();
        summary.setSourceCode(sourceCode);
        summary.setAsync(this.asyncMode);
        summary.setStartedAt(LocalDateTime.now());
        Set<String> seenUrlHashes = new HashSet<>(); // 本轮去重；跨轮由 ingest origUrlHash 幂等
        List<CategorySummary> categories = new ArrayList<>();
        summary.setCategories(categories);
        List<CategoryConfig> configs = parseConfigs(source.getEntryUrlsJson());
        if (configs.isEmpty() && StringUtils.hasText(source.getApiConfigJson())) {
            // 审查 A2 修复：源级 api_config_json（结构化 API 源/前端表单直填）→ 兜底为一个 API 栏目，避免保存即校验、执行空跑
            CategoryConfig cfg = new CategoryConfig();
            cfg.setName(source.getSourceName() == null ? source.getSourceCode() : source.getSourceName());
            cfg.setParser(source.getParserCodeDefault() == null ? "API" : source.getParserCodeDefault());
            cfg.setApiConfig(source.getApiConfigJson());
            configs.add(cfg);
        }
        if (configs.isEmpty()) {
            // 审查 A9：空配置/空跑不能静默 SUCCESS——汇总打 error 标记，runNow 据此置 PARTIAL 前端可提示
            CrawlSourceRunner.CategorySummary empty = new CrawlSourceRunner.CategorySummary();
            empty.setName("__empty__");
            empty.setError("源无有效栏目配置（entryUrlsJson 为空且无源级 apiConfig），本轮无抓取——请检查源配置");
            categories.add(empty);
            summary.setTotalFailed(1); // 借 failed 触发 PARTIAL/FAILED，避免“成功 0 条”误导
        }
        if (full) {
            // 首次全量（无历史断点）：窗口按栏目 fullLookbackDays（缺省回退 lookbackDays，再缺省全局 full-lookback-days=60）；
            // 页上限放开到 full-max-pages（默认 300，覆盖 60 天窗口；真正停页靠 outOfWindow 窗口外即停）
            for (CategoryConfig config : configs) {
                if (config.getFullLookbackDays() == null || config.getFullLookbackDays() <= 0) {
                    config.setFullLookbackDays(config.getLookbackDays());
                }
                if (config.getFullLookbackDays() == null || config.getFullLookbackDays() <= 0) {
                    config.setFullLookbackDays(fullLookbackDaysDefault);
                }
                config.setLookbackDays(config.getFullLookbackDays());
                config.setMaxPages(fullMaxPagesDefault);
            }
        }
        for (CategoryConfig config : configs) {
            CategorySummary cs = new CategorySummary();
            cs.setName(config.getName());
            try {
                if (config.getApiConfig() != null) {
                    runApiCategory(source, config, cs, seenUrlHashes, jobId);
                } else if ("QYZB_JSON".equals(config.getParser())) {
                    runQyzCategory(source, config, cs, seenUrlHashes, jobId);
                } else {
                    runHtmlCategory(source, config, cs, seenUrlHashes, jobId);
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

    // ==================== API 源栏目：配置驱动列表直取（ApiFetchDriver） ====================
    /**
     * API 源栏目（entry 栏目对象带 apiConfig）：按 api_config_json 分页直取 JSON 列表，
     * 窗口/停页/去重/频控语义与 QYZB/HTML 源一致；行自带全文（contentHtml 映射）→ 直采入库，
     * 不带全文 → 仅统计 fetched 不 ingest（详情抓取由后续异步链路落任务承担）。
     * 支持公共服务平台通行形态：GET/POST + 静态鉴权头 + 分页参数 + rowsPath/字段映射（设计 v1.0 §四）。
     */
    private void runApiCategory(CrawlSourceDO source, CategoryConfig config, CategorySummary cs, Set<String> seen, Long jobId) throws Exception {
        ApiFetchConfig api = ApiFetchConfig.parse(config.getApiConfig());
        if (api == null || api.getUrl() == null) {
            throw new IllegalStateException("栏目 apiConfig 非法（缺 url）");
        }
        int maxPages = effectiveMaxPages(config);
        LocalDate cutoff = lookbackCutoff(config);
        for (int pageNo = 1; pageNo <= maxPages; pageNo++) {
            List<NoticeParseResult.ListEntry> entries;
            try {
                java.util.Map<String, String> vars = new java.util.HashMap<>();
                if (config.getCategoryId() != null) { vars.put("categoryId", config.getCategoryId()); }
                if (config.getSiteId() != null) { vars.put("siteId", config.getSiteId()); }
                entries = apiFetchDriver.fetchPage(api, pageNo, vars);
            } catch (Exception e) {
                cs.setError("API 拉取失败 page=" + pageNo + ": " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                throw e;
            }
            if (entries == null || entries.isEmpty()) {
                break; // 空页：没有更多
            }
            boolean stopAfterPage = false;
            for (NoticeParseResult.ListEntry entry : entries) {
                if (outOfWindow(cutoff, entry.getDate())) {
                    stopAfterPage = true;
                    break;
                }
                cs.setFetched(cs.getFetched() + 1);
                String urlHash = sha256Hex(entry.getUrl());
                if (!seen.add(urlHash)) {
                    continue; // 本轮已见
                }
                try {
                    if (asyncMode) {
                        // 异步投递：全文生产端归档 FileApi + 落 PENDING task + 发轻信号（消费端执行落库）；ingested 语义=已入队
                        Long taskId = crawlTaskDispatchService.enqueue(jobId, source.getSourceCode(), config.getParser(), entry);
                        if (taskId != null) {
                            cs.setIngested(cs.getIngested() + 1);
                        }
                    } else if (!StringUtils.hasText(entry.getContentHtml())) {
                        // 审查 B7：配置缺陷（缺全文映射）≠ 业务失败——记 SKIPPED 不记 FAILED（避免污染失败率触发调度退避）
                        cs.setFailed(cs.getFailed() + 0);
                        recordTask(source, config, entry, "SKIPPED", "列表行无全文映射（API 源配置缺 contentHtml 字段），跳过", jobId);
                        log.warn("[crawl][source={}] API 条目缺全文 url={}", source.getSourceCode(), entry.getUrl());
                    } else {
                        ExternalNoticeIngestDTO dto = new ExternalNoticeIngestDTO();
                        dto.setSourceCode(source.getSourceCode());
                        dto.setOrigUrl(entry.getUrl());
                        dto.setTitle(entry.getTitle());
                        dto.setContentType("HTML");
                        dto.setContentHtml(entry.getContentHtml());
                        dto.setNoticeTypeRaw(entry.getCategory());
                        LocalDateTime publish = parseDate(entry.getDate());
                        dto.setPublishTime(publish); // A5：解析失败传 null，日期回填决策交还 ingest（BR-5 原文日期优先）
                        dto.setIssuerName(extractIssuer(entry.getContentHtml()));
                        dto.setParserCode(config.getParser());
                        externalNoticeIngestService.ingest(dto);
                        cs.setIngested(cs.getIngested() + 1);
                    }
                } catch (Exception e) {
                    cs.setFailed(cs.getFailed() + 1);
                    recordTask(source, config, entry, "FAILED", e.getMessage(), jobId);
                    log.warn("[crawl][source={}] API 条目失败 url={} err={}", source.getSourceCode(), entry.getUrl(), e.getMessage());
                }
                sleep(entryDelayMs);
            }
            if (stopAfterPage || pageNo >= maxPages) {
                break;
            }
            sleep(pageDelayMs); // 页间防封（5s）
        }
    }
    // ==================== 秦源：栏目 JSON 接口（全文直采） ====================
    /**
     * 秦源栏目 JSON 翻页采集（增量回溯见文档 §11）：
     * 启用 lookbackDays 时按发布时间窗口逐页回溯——页内出现早于窗口起点的条目即停（列表倒序，其后页必然更老）；
     * 未启用时保持 v1 单页行为。窗口外条目不采集（视为已被历史轮次覆盖）。
     */
    private void runQyzCategory(CrawlSourceDO source, CategoryConfig config, CategorySummary cs, Set<String> seen, Long jobId) throws Exception {
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
                    if (asyncMode) {
                        Long taskId = crawlTaskDispatchService.enqueue(jobId, source.getSourceCode(), config.getParser(), entry);
                        if (taskId != null) {
                            cs.setIngested(cs.getIngested() + 1); // 投递模式：ingested=已入队
                        }
                    } else if (ingestQyzEntry(source.getSourceCode(), entry, seen)) {
                        cs.setIngested(cs.getIngested() + 1);
                    }
                } catch (Exception e) {
                    cs.setFailed(cs.getFailed() + 1);
                    recordTask(source, config, entry, "FAILED", e.getMessage(), jobId);
                    log.warn("[crawl][source={}] 条目落库失败 url={} err={}", source.getSourceCode(), entry.getUrl(), e.getMessage());
                }
                sleep(entryDelayMs);
            }
            if (stopAfterPage || pageNo >= maxPages) {
                break;
            }
            sleep(pageDelayMs); // 页间防封（5s）
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
        dto.setPublishTime(publish); // A5：解析失败传 null，日期回填决策交还 ingest（BR-5 原文日期优先）
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
    private void runHtmlCategory(CrawlSourceDO source, CategoryConfig config, CategorySummary cs, Set<String> seen, Long jobId) throws Exception {
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
                    if (asyncMode) {
                        // GX：enqueue 详情 URL，消费端抓详情→PDF→落库（长下载异步化，生产端不阻塞）
                        Long taskId = crawlTaskDispatchService.enqueue(jobId, source.getSourceCode(), config.getParser(), entry);
                        if (taskId != null) {
                            cs.setIngested(cs.getIngested() + 1); // 投递模式：ingested=已入队
                        }
                    } else if (ingestGxEntry(source.getSourceCode(), entry, seen)) {
                        cs.setIngested(cs.getIngested() + 1);
                    }
                } catch (Exception e) {
                    cs.setFailed(cs.getFailed() + 1);
                    recordTask(source, config, entry, "FAILED", e.getMessage(), jobId);
                    log.warn("[crawl][source={}] 国信条目失败 url={} err={}", source.getSourceCode(), entry.getUrl(), e.getMessage());
                }
                sleep(entryDelayMs);
            }
            if (stopAfterPage || pageNo >= maxPages) {
                break;
            }
            sleep(pageDelayMs); // 页间防封（5s）
        }
    }

    /** 翻页：把 listUrl 中 page=N 替换为 page=pageNo（未带 page 参数则原样返回，仅单页）。
     * 注：国信 iframe 实测每页返回最近窗口内全部条目（page 参数不影响内容，仅站点壳 UI），
     * 其真正全量受 dates= 窗口控制；60 天内最近仅 1 条，单页即覆盖，无需 searchDate 分段（2026-09-09 校准）。 */
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
                + ",\"fileStatus\":\"REGISTERED\"}");
        uploadPdfArchive(dto, pdf, parsed.getTitle() != null ? parsed.getTitle() : entry.getTitle());
        externalNoticeIngestService.ingest(dto);
        return true;
    }

    /** 把 PDF 归档到平台文件服务（FileApi.createFile → 访问 URL 回填 dto.pdfDownloadUrl）。
     *  上传失败不阻断入库（metaJson 保留外站 pdfDownloadUrl 直链兜底，可后续补传）。
     *  2026-09-10：平台 RPC 上传不返回 Long fileId（fileId 仅前端直传登记模式有），文件访问由 pdfDownloadUrl 承担。 */
    private void uploadPdfArchive(ExternalNoticeIngestDTO dto, byte[] pdf, String title) {
        if (pdf == null || pdf.length == 0) {
            return;
        }
        try {
            String safeName = (title == null ? "notice" : title.replaceAll("[\\/:*?\"<>|]", "_")).trim();
            if (safeName.length() > 80) {
                safeName = safeName.substring(0, 80);
            }
            String url = fileApi.createFile(pdf, safeName + ".pdf", "bidding/notice-external", "application/pdf");
            if (org.springframework.util.StringUtils.hasText(url)) {
                dto.setPdfDownloadUrl(url);
                log.info("[crawl][pdf-archive] 归档成功 file={} url={}", safeName + ".pdf", url);
            }
        } catch (Exception e) {
            log.warn("[crawl][pdf-archive] 归档失败（不阻断入库，meta pdfDownloadUrl 兜底）: {}", e.getMessage());
        }
    }

    // ==================== 配置解析与工具 ====================

    @Data
    public static class RunSummary {
        private String sourceCode;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private int totalIngested;
        private int totalFailed;
        /** 投递模式标志：true=本轮回合并未落库而是投递 task（ingested 语义=入队数），job 由消费端 tryFinalizeJob 收尾 */
        private boolean async;
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
        /** 首次全量回溯窗口（天）：首次运行（源无历史成功批次）时回溯补历史用；缺省回退 lookbackDays/全局 full-lookback-days */
        private Integer fullLookbackDays;
        /** 本栏目单轮页上限（防封；启用回溯且未配时默认 10，未启用默认 1） */
        private Integer maxPages;
        /** API 源栏目访问配置（JSON，可选：存在时按 ApiFetchDriver 直取列表，不再走 HTML/JSON parser 路径） */
        private String apiConfig;
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
                    config.setFullLookbackDays(item.path("fullLookbackDays").isNumber() ? item.path("fullLookbackDays").asInt() : null);
                    config.setMaxPages(item.path("maxPages").isNumber() ? item.path("maxPages").asInt() : null);
                    JsonNode apiCfg = item.get("apiConfig");
                    config.setApiConfig(apiCfg != null && !apiCfg.isNull() ? apiCfg.toString() : null);
                    if (config.getName() != null && (config.getCategoryId() != null || config.getListUrl() != null || config.getApiConfig() != null)) {
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

    private void recordTask(CrawlSourceDO source, CategoryConfig config, NoticeParseResult.ListEntry entry, String status, String message, Long jobId) {
        try {
            CrawlTaskDO task = new CrawlTaskDO();
            if (jobId != null) {
                task.setJobId(jobId);
            }
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
