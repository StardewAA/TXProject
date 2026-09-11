package cn.iocoder.txgy.module.bidding.service.externalfetch;

// 规范依据：外部信息获取 v1.0 §五/§六（Redis Stream 轻信号消费端执行：收 taskId → 回查 payload → 下载/直采 → ingest 回写）；
//           正文文本/源文件由下载链路 FileApi 承载，AI 结构化复用 noticeexternal.ai（v1.0 §三）。

import cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO;
import cn.iocoder.txgy.module.bidding.service.externalfetch.fetch.UrlHostGuard;
import cn.iocoder.txgy.module.bidding.service.externalfetch.parser.NoticeParseResult;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.ingest.ExternalNoticeIngestDTO;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.ingest.ExternalNoticeIngestService;
import cn.iocoder.txgy.module.bidding.service.externalfetch.throttle.CrawlDownloadGate;
import cn.iocoder.txgy.module.bidding.service.externalfetch.throttle.CrawlDownloadNotDueException;
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
    /** 原文下载限速闸：所有"下载公告原文文件"的动作必须经它放行（合规频控唯一入口） */
    @Resource
    private CrawlDownloadGate crawlDownloadGate;
    /** 下载内容完整性校验（空/截断/格式不符/错误页/乱码 → 不入库；并产出 sha256） */
    @Resource
    private cn.iocoder.txgy.module.bidding.service.externalfetch.validate.DownloadIntegrityValidator integrityValidator;
    /** 下载审计与速度监控（量/速/摘要/结论/存储状态落台账） */
    @Resource
    private cn.iocoder.txgy.module.bidding.service.externalfetch.audit.CrawlDownloadAuditor downloadAuditor;
    /** robots.txt 合规检查（抓取前检查 Disallow） */
    @Resource
    private cn.iocoder.txgy.module.bidding.service.externalfetch.fetch.RobotsGuard robotsGuard;

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
        // 2026-09-10 下载器改 AI：详情/落库解析器随 payload 下发（task.parserCode 仍为列表解析器，用于执行路由与去重语义）；
        // 兼容旧任务：payload 无 detailParser 时回退到 task.parserCode（不改变既有行为）
        String detailParser = text(payload, "detailParser");
        String effectiveDetail = StringUtils.hasText(detailParser) ? detailParser : task.getParserCode();

        // ===== 合规限速闸（唯一入口，2026-09-10 重构）=====
        // 规范依据：《外部信息获取 v1.0》§六/§11 + 用户口径「时间间隔必须且只能作用于『下载公告原文文件』这一动作」
        //   「每次下载原文文件之前，必须强制执行休眠等待」。
        // 判定口径：
        //   · payload 已带 contentHtml（列表接口全文直采，如秦源 res.rows.text）→ 纯本地处理，**不下载**，不占闸位；
        //   · 否则本次执行必然要去取原文（平台归档 rawFileUrl / 详情页 HTML / 详情页内 PDF 直链）
        //     → 在**任何 HTTP 下载之前**占一次闸位，未获闸位即抛 NotDue，绝不发起请求。
        // 为什么一次占位而非每个请求各占一次：详情页与其 PDF 直链是"同一份公告原文"的取回过程，
        //   按公告计数才与「每 interval 一条原文」的合规口径一致；每个请求各占会导致间隔被重复消耗。
        if (!StringUtils.hasText(contentHtml)) {
            if (!crawlDownloadGate.tryAcquire(task.getSourceCode(), task.getId(), task.getUrl())) {
                throw new CrawlDownloadNotDueException(crawlDownloadGate.nextDueAt(task.getSourceCode()), task.getUrl());
            }
        }
        // 全文文件化：payload 无内联全文时按 rawFileUrl 从平台文件服务拉取（v1.0 §2.2 原文文件化）
        if (!StringUtils.hasText(contentHtml)) {
            String rawFileUrl = text(payload, "rawFileUrl");
            if (StringUtils.hasText(rawFileUrl)) {
                // 纵深防御：非平台文件端点的 rawFileUrl（理论上自产受控）也过主机白名单（SSRF）
                if (!UrlHostGuard.isInternalFileUrl(rawFileUrl)) {
                    assertUrlAllowed(rawFileUrl, task.getSourceCode());
                }
                // 下载监控 + 完整性校验（2026-09-10）：平台归档原文同样记录量/速/摘要，校验不通过不得入库
                cn.iocoder.txgy.module.bidding.service.externalfetch.fetch.HttpFetchClient.FetchOutcome outcome =
                        httpFetchClient.fetchText(rawFileUrl);
                contentHtml = outcome.getText();
                var verdict = integrityValidator.validateText(contentHtml, rawFileUrl);
                downloadAuditor.record(task.getId(), task.getSourceCode(), rawFileUrl, "ARCHIVE_HTML",
                        outcome.getStatusCode(), (long) outcome.getByteSize(), outcome.getDurationMs(),
                        verdict.getSha256(), verdict.getVerdict().name(),
                        cn.iocoder.txgy.module.bidding.service.externalfetch.audit.CrawlDownloadAuditor.STORAGE_NONE,
                        null, verdict.getDetail());
                if (!verdict.pass()) {
                    throw new IllegalStateException("平台归档原文校验不通过：" + verdict.getDetail());
                }
            }
        }
        if (!StringUtils.hasText(contentHtml)) {
            // GX 类：task.url=详情页 → 详情→PDF 直链→下载→PDFBox→归档→落库（长任务异步化的核心收益）
            if (task.getParserCode() != null && task.getParserCode().startsWith("GX")) {
                return executeGxDetail(task, title, url, date, effectiveDetail);
            }
            // 2026-09-10 全链路补全：非 GX 的 HTML 详情任务（HTML_GENERIC/EBIDDING 等 HTML 列表栏目，async 模式）
            // → 下载详情 HTML → 按任务 parser 解析 → 落库（此前 async 下这类任务全部“素材缺失”死信）
            if (task.getParserCode() == null || !"QYZB_JSON".equals(task.getParserCode())) {
                return executeHtmlDetail(task, title, url, date, effectiveDetail);
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
        // 2026-09-10 下载器改 AI：全文直采（QYZB 类）同样过 AI 结构化与行业判定（detailParser=HTML_AI 时生效）
        applyAiEnrichIfConfigured(dto, effectiveDetail, contentHtml, url);
        return externalNoticeIngestService.ingest(dto);
    }

    /**
     * HTML 详情类任务（HTML_GENERIC/EBIDDING 等 HTML 列表栏目，async 模式全链路补全 2026-09-10）：
     * 下载详情 HTML → 按任务 parserCode 解析（站点/通用）→ 结构化字段落库；下载前过主机白名单（SSRF）。
     */
    private Long executeHtmlDetail(CrawlTaskDO task, String title, String url, String date, String detailParser) throws Exception {
        if (!StringUtils.hasText(url)) {
            throw new IllegalStateException("HTML 详情任务缺详情 URL");
        }
        assertUrlAllowed(url, task.getSourceCode());
        // robots.txt 合规：目标路径被 Disallow 即不抓（2026-09-10 补齐，此前该字段形同虚设）
        if (!robotsGuard.isAllowed(url)) {
            throw new IllegalStateException("目标路径被 robots.txt 禁止抓取（合规跳过） url=" + url);
        }
        // 下载 + 即时校验 + 审计（速度监控与完整性结论一并落台账）
        cn.iocoder.txgy.module.bidding.service.externalfetch.fetch.HttpFetchClient.FetchOutcome outcome =
                httpFetchClient.fetchText(url);
        String html = outcome.getText();
        var verdict = integrityValidator.validateText(html, url);
        downloadAuditor.record(task.getId(), task.getSourceCode(), url, "DETAIL_HTML",
                outcome.getStatusCode(), (long) outcome.getByteSize(), outcome.getDurationMs(),
                verdict.getSha256(), verdict.getVerdict().name(),
                cn.iocoder.txgy.module.bidding.service.externalfetch.audit.CrawlDownloadAuditor.STORAGE_NONE,
                null, verdict.getDetail());
        if (!verdict.pass()) {
            throw new IllegalStateException("详情页完整性校验不通过：" + verdict.getDetail());
        }
        // 2026-09-10 下载器改 AI：详情正文解析器 = detailParser（HTML_AI 时走平台 AI 结构化+行业判定），
        // 缺省回退任务列表解析器（HTML_GENERIC/EBIDDING 等本站规则解析器），保持既有行为
        String parserCode = StringUtils.hasText(detailParser) ? detailParser
                : (task.getParserCode() == null ? "HTML_GENERIC" : task.getParserCode());
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
        // AI 解析器产出：行业判定 + 置信度 + AI meta 原样留痕（v1.0 §三）
        dto.setIndustryFlag(parsed.getIndustryFlag());
        dto.setIndustryConfidence(parseConfidence(parsed.getIndustryConfidence()));
        dto.setAiMetaJson(parsed.getAiMetaJson());
        return externalNoticeIngestService.ingest(dto);
    }

    /** GX（国信）类任务：task.url=详情页 → 详情 HTML → PDF.js 直链 → 下载 PDF → PDFBox 文本抽取 → FileApi 归档 → 落库（PDF 形态）。
     *  长下载（详情+PDF）在消费端线程执行，不占生产端/Redis（v1.0 §6.2）。 */
    private Long executeGxDetail(CrawlTaskDO task, String title, String url, String date, String detailParser) throws Exception {
        if (!StringUtils.hasText(url)) {
            throw new IllegalStateException("GX 任务缺详情 URL");
        }
        assertUrlAllowed(url, task.getSourceCode()); // 详情页下载前：主机白名单（SSRF 防护）
        // robots.txt 合规（2026-09-10 补齐）
        if (!robotsGuard.isAllowed(url)) {
            throw new IllegalStateException("详情页被 robots.txt 禁止抓取（合规跳过） url=" + url);
        }
        var detailOutcome = httpFetchClient.fetchText(url);
        var detailVerdict = integrityValidator.validateText(detailOutcome.getText(), url);
        downloadAuditor.record(task.getId(), task.getSourceCode(), url, "DETAIL_HTML",
                detailOutcome.getStatusCode(), (long) detailOutcome.getByteSize(), detailOutcome.getDurationMs(),
                detailVerdict.getSha256(), detailVerdict.getVerdict().name(),
                cn.iocoder.txgy.module.bidding.service.externalfetch.audit.CrawlDownloadAuditor.STORAGE_NONE,
                null, detailVerdict.getDetail());
        if (!detailVerdict.pass()) {
            throw new IllegalStateException("GX 详情页完整性校验不通过：" + detailVerdict.getDetail());
        }
        String detailHtml = detailOutcome.getText();
        NoticeParseResult detail = parserRegistry.get("GX_E_BIDDING")
                .parse(inputOf("HTML", detailHtml, url));
        String pdfUrl = detail.getMeta() == null ? null : detail.getMeta().get("pdfDownloadUrl");
        if (!StringUtils.hasText(pdfUrl)) {
            throw new IllegalStateException("详情页未解析出 PDF 直链（页面结构变化？）");
        }
        assertUrlAllowed(pdfUrl, task.getSourceCode()); // PDF 直链下载前：主机白名单（SSRF 防护）
        if (!robotsGuard.isAllowed(pdfUrl)) {
            throw new IllegalStateException("PDF 直链被 robots.txt 禁止抓取（合规跳过） url=" + pdfUrl);
        }
        // PDF 下载：限速闸（execute 已占位）→ 单文件上限 → 完整性校验（%PDF 头 + %%EOF 结尾 + sha256）
        var pdfOutcome = httpFetchClient.fetchBytes(pdfUrl);
        byte[] pdf = pdfOutcome.getBytes();
        var pdfVerdict = integrityValidator.validateBinary(pdf, "PDF", pdfUrl);
        if (!pdfVerdict.pass()) {
            downloadAuditor.record(task.getId(), task.getSourceCode(), pdfUrl, "PDF",
                    pdfOutcome.getStatusCode(), (long) pdfOutcome.getByteSize(), pdfOutcome.getDurationMs(),
                    pdfVerdict.getSha256(), pdfVerdict.getVerdict().name(),
                    cn.iocoder.txgy.module.bidding.service.externalfetch.audit.CrawlDownloadAuditor.STORAGE_FAILED,
                    null, pdfVerdict.getDetail());
            throw new IllegalStateException("PDF 完整性校验不通过：" + pdfVerdict.getDetail());
        }
        // 2026-09-10 下载器改 AI：PDF 正文解析器 = detailParser（PDF_AI=PDFBox 抽文本→平台 AI 结构化+行业判定）；
        // 非 AI（或未配置）继续走本地 PDF_TEXT_GENERIC，保持既有行为（AI 未启用不阻断公告入库）
        String pdfParser = isAiParser(detailParser) ? detailParser : "PDF_TEXT_GENERIC";
        NoticeParseResult parsed = "PDF_AI".equals(pdfParser)
                ? parserRegistry.get("PDF_AI").parse(inputOfPdf(pdf)) // AI：PDFBox 抽文本 + 平台 AI 结构化
                : pdfTextNoticeParser.parse(inputOfPdf(pdf));
        ExternalNoticeIngestDTO dto = new ExternalNoticeIngestDTO();
        dto.setSourceCode(task.getSourceCode());
        dto.setOrigUrl(url);
        dto.setTitle(StringUtils.hasText(parsed.getTitle()) ? parsed.getTitle() : title);
        dto.setContentType("PDF");
        // 摘要与审计台账同源（用校验器产出值，避免两处各算一次导致口径不一致）
        dto.setPdfSha256(pdfVerdict.getSha256());
        dto.setPdfSize((long) pdf.length);
        dto.setParserCode(pdfParser); // 实际参与解析的策略码（PDF_AI 或 PDF_TEXT_GENERIC）可追溯
        dto.setIndustryFlag(parsed.getIndustryFlag());
        dto.setIndustryConfidence(parseConfidence(parsed.getIndustryConfidence()));
        dto.setAiMetaJson(parsed.getAiMetaJson());
        dto.setIssuerName(parsed.getIssuerName());
        dto.setProjectCode(parsed.getProjectCode());
        dto.setTenderProjectCode(parsed.getTenderProjectCode());
        dto.setPublishTime(parsed.getPublishTime() != null ? parsed.getPublishTime() : parseDate(date));
        // 归档 + 存储校验：metaJson 的 fileStatus/fileId 反映**真实**存储状态（不再写死 REGISTERED）
        cn.iocoder.txgy.module.bidding.service.externalfetch.audit.StorageOutcome storage =
                uploadPdfArchive(dto, pdf, StringUtils.hasText(parsed.getTitle()) ? parsed.getTitle() : title);
        dto.setMetaJson("{\"pdfDownloadUrl\":\"" + escapeJson(pdfUrl) + "\",\"textChars\":" + (parsed.getContentText() == null ? 0 : parsed.getContentText().length())
                + ",\"fileStatus\":\"" + storage.getStatus() + "\",\"fileId\":" + (storage.getFileId() == null ? "null" : storage.getFileId()) + "}");
        // 审计台账（下载量/速率/sha256/完整性结论/存储状态）：监控"文件完整性与状态"的唯一数据源
        downloadAuditor.record(task.getId(), task.getSourceCode(), pdfUrl, "PDF",
                pdfOutcome.getStatusCode(), (long) pdfOutcome.getByteSize(), pdfOutcome.getDurationMs(),
                pdfVerdict.getSha256(), pdfVerdict.getVerdict().name(), storage.getStatus(), storage.getFileId(),
                storage.getDetail() + " | " + pdfVerdict.getDetail());
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

    /**
     * AI 结构化增强（2026-09-10 下载器改 AI；全文直采路径专用）。
     *
     * <p>规范依据：外部信息获取 v1.0 §三（AI 结构化抽取 + 防腐保温行业判定）；
     * 用户口径：「把现在的三个下载器都改成 AI 的」——秦源（QYZB）全文直采同样须出行业判定。
     * 业务目标：公告落库即带 industryFlag/industryConfidence/aiMetaJson，使「是否属于防腐保温行业」可查、可复核。
     * 原因：AI 仅补字段不改原文（contentHtml 原样留档）；AI 未启用/异常一律降级为不增强，
     * 绝不让模型侧的失败变成公告丢失（宁可少字段，不可丢数据）。
     */
    private void applyAiEnrichIfConfigured(ExternalNoticeIngestDTO dto, String detailParser, String contentHtml, String url) {
        if (dto == null || !isAiParser(detailParser) || !StringUtils.hasText(contentHtml)) {
            return;
        }
        try {
            NoticeParseResult parsed = parserRegistry.get(detailParser).parse(inputOf("HTML", contentHtml, url));
            if (parsed == null) {
                return;
            }
            if (!StringUtils.hasText(dto.getIndustryFlag())) {
                dto.setIndustryFlag(parsed.getIndustryFlag());
            }
            dto.setIndustryConfidence(parseConfidence(parsed.getIndustryConfidence()));
            dto.setAiMetaJson(parsed.getAiMetaJson());
            if (!StringUtils.hasText(dto.getProjectCode())) {
                dto.setProjectCode(parsed.getProjectCode());
            }
            if (!StringUtils.hasText(dto.getProjectName())) {
                dto.setProjectName(parsed.getProjectName());
            }
            if (!StringUtils.hasText(dto.getIssuerName()) && StringUtils.hasText(parsed.getIssuerName())) {
                dto.setIssuerName(parsed.getIssuerName());
            }
            if (dto.getPublishTime() == null && parsed.getPublishTime() != null) {
                dto.setPublishTime(parsed.getPublishTime());
            }
            dto.setParserCode(detailParser); // 实际参与解析的策略码可追溯（列表解析器→AI 解析器）
            if (parsed.getStatus() == NoticeParseResult.Status.FAILED) {
                log.warn("[processor][ai-enrich] AI 未产出（不阻断落库）: parser={} err={}", detailParser, parsed.getError());
            }
        } catch (Exception e) {
            log.warn("[processor][ai-enrich] AI 增强异常（不阻断落库）: parser={} msg={}", detailParser, e.getMessage());
        }
    }

    /** AI 解析器判定：仅 *_AI 策略码走平台 AI（HTML_AI/PDF_AI），其余为本地规则解析 */
    private static boolean isAiParser(String parserCode) {
        return StringUtils.hasText(parserCode) && parserCode.endsWith("_AI");
    }

    /** AI 置信度字符串 → BigDecimal（解析失败返回 null，不阻断落库） */
    private static java.math.BigDecimal parseConfidence(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return new java.math.BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * PDF 归档平台文件服务（失败不阻断落库，metaJson 保留外站直链兜底）。
     *
     * <p>2026-09-10 增强（用户口径「存储状态监测」）：归档后回查元数据核对 file_id 与字节数，
     * 返回真实的存储状态（REGISTERED/FAILED）与 file_id——原实现只要 createFile 返回 URL 就写死
     * metaJson {@code fileStatus=REGISTERED}，存储实际失败（URL 指不到文件）时也被标成"已归档"。
     */
    private cn.iocoder.txgy.module.bidding.service.externalfetch.audit.StorageOutcome uploadPdfArchive(
            ExternalNoticeIngestDTO dto, byte[] pdf, String title) {
        if (pdf == null || pdf.length == 0) {
            return cn.iocoder.txgy.module.bidding.service.externalfetch.audit.StorageOutcome.failed(null, "PDF 字节为空，未归档");
        }
        try {
            String safeName = (title == null ? "notice" : title.replaceAll("[/:*?\"<>|]", "_")).trim();
            if (safeName.length() > 80) {
                safeName = safeName.substring(0, 80);
            }
            String u = fileApi.createFile(pdf, safeName + ".pdf", "bidding/notice-external", "application/pdf");
            if (!StringUtils.hasText(u)) {
                return cn.iocoder.txgy.module.bidding.service.externalfetch.audit.StorageOutcome.failed(null, "文件服务未返回访问地址");
            }
            dto.setPdfDownloadUrl(u);
            // 存储校验：回查元数据拿 file_id（红线 8 三件套）并核对字节数，避免"URL 有了但文件没落下来"
            FileMetadataCheck check = verifyStored(u, pdf.length);
            if (check.fileId != null) {
                dto.setPdfFileId(check.fileId);
            }
            if (!check.ok) {
                log.warn("[processor][pdf-archive] 归档校验未通过（保留外站直链兜底）: {} url={}", check.detail, u);
                return cn.iocoder.txgy.module.bidding.service.externalfetch.audit.StorageOutcome.failed(check.fileId, check.detail);
            }
            return cn.iocoder.txgy.module.bidding.service.externalfetch.audit.StorageOutcome.registered(check.fileId, check.detail);
        } catch (Exception e) {
            log.warn("[processor][pdf-archive] 归档失败（不阻断入库）: {}", e.getMessage());
            return cn.iocoder.txgy.module.bidding.service.externalfetch.audit.StorageOutcome.failed(null, "归档异常: " + e.getMessage());
        }
    }

    /** 归档校验：按访问地址回查元数据，取 file_id 并核对字节数（size 为空则只校验 file_id 存在） */
    private FileMetadataCheck verifyStored(String url, int expectedBytes) {
        try {
            // 适配 infra FileApi 契约变更（返回 CommonResult）：错误信封时 getCheckedData 抛 ServiceException；
            // 同时保留对 null 返回的容忍（原实现允许"查不到元数据"，调用方按 null 分支处理）
            cn.iocoder.txgy.framework.common.pojo.CommonResult<cn.iocoder.txgy.module.infra.api.file.dto.FileMetadataRespDTO> metaResult =
                    fileApi.getMetadataByUrl(url);
            cn.iocoder.txgy.module.infra.api.file.dto.FileMetadataRespDTO meta =
                    metaResult == null ? null : metaResult.getCheckedData();
            if (meta == null || meta.getId() == null) {
                return new FileMetadataCheck(false, null, "回查元数据未取到 file_id");
            }
            if (meta.getSize() != null && meta.getSize() > 0 && meta.getSize() != expectedBytes) {
                return new FileMetadataCheck(false, meta.getId(),
                        "存储字节数不符：期望 " + expectedBytes + " 实际 " + meta.getSize());
            }
            return new FileMetadataCheck(true, meta.getId(), "归档校验通过 fileId=" + meta.getId());
        } catch (Exception e) {
            return new FileMetadataCheck(false, null, "归档校验异常: " + e.getMessage());
        }
    }

    /** 存储校验结果（file_id + 结论 + 说明） */
    private static class FileMetadataCheck {
        final boolean ok;
        final Long fileId;
        final String detail;

        FileMetadataCheck(boolean ok, Long fileId, String detail) {
            this.ok = ok;
            this.fileId = fileId;
            this.detail = detail;
        }
    }



    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // 注：原 sha256Hex(byte[]) 已随"PDF 摘要统一由 DownloadIntegrityValidator 产出"移除（2026-09-10），
    // 避免同一份字节在两处各算一次摘要导致口径不一致（审计台账与落库 sha256 必须同源）。

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