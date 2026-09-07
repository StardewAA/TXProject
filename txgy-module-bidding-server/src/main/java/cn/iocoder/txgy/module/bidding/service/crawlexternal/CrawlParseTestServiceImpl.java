package cn.iocoder.txgy.module.bidding.service.crawlexternal;
// [依据] 解析器测试服务实现：路由 ParserRegistry → 校验载荷 → 执行解析 → VO 化；
//       FILE 按扩展名归一 HTML/JSON（.html/.htm/.json）；错误码 106_041~045。

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.controller.admin.crawlexternal.vo.CrawlParseTestRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.crawlexternal.vo.CrawlParserInfoRespVO;
import cn.iocoder.txgy.module.bidding.enums.CrawlParsePayloadTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.service.crawlexternal.parser.NoticeParseResult;
import cn.iocoder.txgy.module.bidding.service.crawlexternal.parser.NoticeParser;
import cn.iocoder.txgy.module.bidding.service.crawlexternal.parser.ParseInput;
import cn.iocoder.txgy.module.bidding.service.crawlexternal.parser.ParserRegistry;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 爬虫解析器插件服务实现。
 */
@Slf4j
@Service
public class CrawlParseTestServiceImpl implements CrawlParseTestService {

    /** 正文纯文本预览长度 */
    private static final int TEXT_PREVIEW = 3000;

    @Resource
    private ParserRegistry parserRegistry;

    @Override
    public List<CrawlParserInfoRespVO> listParsers() {
        List<CrawlParserInfoRespVO> result = new ArrayList<>();
        for (NoticeParser parser : parserRegistry.all()) {
            CrawlParserInfoRespVO vo = new CrawlParserInfoRespVO();
            vo.setCode(parser.getParserCode());
            vo.setName(parser.getParserName());
            vo.setDescription(parser.getDescription());
            vo.setSupportedTypes(parser.supportedPayloadTypes());
            result.add(vo);
        }
        return result;
    }

    @Override
    public CrawlParseTestRespVO testParse(String parserCode, String payloadType, String content,
                                          String fileName, String url, byte[] fileBytes) {
        long started = System.currentTimeMillis();
        // 1) 解析器路由（未知 code → 041）
        NoticeParser parser;
        try {
            parser = parserRegistry.get(parserCode);
        } catch (IllegalArgumentException e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_PARSER_NOT_FOUND, String.valueOf(parserCode));
        }
        // 2) 载荷类型：FILE 按扩展名归一 HTML/JSON/PDF
        CrawlParsePayloadTypeEnum type = CrawlParsePayloadTypeEnum.of(payloadType);
        String effectiveType;
        byte[] pdfBytes = null;
        String resolvedFileName = fileName;
        if (type == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_PARSER_INPUT_TYPE_UNSUPPORTED,
                    parser.getParserName(), String.valueOf(payloadType));
        }
        if (type == CrawlParsePayloadTypeEnum.FILE) {
            if (fileBytes == null || fileBytes.length == 0) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_PARSER_PAYLOAD_MISSING);
            }
            effectiveType = inferFileType(fileName); // 未知扩展名抛 044（.html/.htm→HTML；.json→JSON；.pdf→PDF）
            if ("PDF".equals(effectiveType)) {
                pdfBytes = fileBytes; // PDF 二进制原文直传解析器（不做 UTF-8 字符串化）
            } else {
                content = new String(fileBytes, StandardCharsets.UTF_8);
            }
        } else {
            effectiveType = type.getType();
            if (StringUtils.hasText(content)) {
                pdfBytes = null;
            } else if (type == CrawlParsePayloadTypeEnum.PDF) {
                // PDF 载荷类型仅经 FILE 归一进入（粘贴文本无意义）
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_PARSER_PAYLOAD_MISSING);
            } else {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_PARSER_PAYLOAD_MISSING);
            }
        }
        // 3) 解析器支持性
        if (!parser.supportedPayloadTypes().contains(effectiveType)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_PARSER_INPUT_TYPE_UNSUPPORTED,
                    parser.getParserName(), effectiveType);
        }
        // 3.5) PDF 文本（payloadType=PDF 时由解析器落盘临时文件抽取）
        // 4) 执行解析（解析器内部不抛业务异常；兜底 045）
        NoticeParseResult result;
        try {
            ParseInput input = new ParseInput();
            input.setPayloadType(effectiveType);
            input.setContent(content);
            input.setFileName(resolvedFileName);
            input.setUrl(url);
            input.setFileBytes(pdfBytes);
            result = parser.parse(input);
        } catch (Exception e) {
            log.error("[testParse][parserCode={}] 解析器执行异常", parserCode, e);
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_PARSER_EXECUTE_FAILED,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        return toResp(parser, result, effectiveType, resolvedFileName, url, content, started);
    }

    /** 文件扩展名 → 载荷类型（.html/.htm/.json；其它 → 044）。 */
    private String inferFileType(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_PARSER_FILE_INVALID);
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return "HTML";
        }
        if (lower.endsWith(".json")) {
            return "JSON";
        }
        if (lower.endsWith(".pdf")) {
            return "PDF";
        }
        throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_PARSER_FILE_INVALID);
    }

    private CrawlParseTestRespVO toResp(NoticeParser parser, NoticeParseResult result, String payloadType,
                                        String fileName, String url, String content, long started) {
        CrawlParseTestRespVO vo = new CrawlParseTestRespVO();
        vo.setParserCode(parser.getParserCode());
        vo.setParserName(parser.getParserName());
        vo.setPayloadType(payloadType);
        vo.setFileName(fileName);
        vo.setUrl(url);
        vo.setContentLength(content == null ? 0 : content.length());
        vo.setDurationMs(System.currentTimeMillis() - started);
        vo.setStatus(result.getStatus() == null ? null : result.getStatus().name());
        vo.setError(result.getError());
        vo.setTitle(result.getTitle());
        vo.setOrigUrl(result.getOrigUrl());
        vo.setContentType(result.getContentType());
        vo.setNoticeTypeRaw(result.getNoticeTypeRaw());
        vo.setLegalFlag(result.getLegalFlag());
        vo.setProjectKey(result.getProjectCode());
        vo.setProjectName(result.getProjectName());
        vo.setTenderProjectKey(result.getTenderProjectCode());
        vo.setBidSectionCodes(result.getBidSectionCodes());
        vo.setIssuerName(result.getIssuerName());
        vo.setPublishTime(result.getPublishTime());
        vo.setEffectiveFrom(result.getEffectiveFrom());
        vo.setEffectiveTo(result.getEffectiveTo());
        String text = result.getContentText();
        if (StringUtils.hasText(text)) {
            vo.setContentTextPreview(text.length() > TEXT_PREVIEW ? text.substring(0, TEXT_PREVIEW) + "…" : text);
        }
        String html = result.getContentHtml();
        vo.setContentHtmlLength(html == null ? 0 : html.length());
        List<CrawlParseTestRespVO.Attachment> attachments = new ArrayList<>();
        if (result.getAttachments() != null) {
            for (NoticeParseResult.AttachmentCandidate candidate : result.getAttachments()) {
                CrawlParseTestRespVO.Attachment attachment = new CrawlParseTestRespVO.Attachment();
                attachment.setName(candidate.getName());
                attachment.setUrl(candidate.getUrl());
                attachment.setUrlIncomplete(candidate.isUrlIncomplete());
                attachments.add(attachment);
            }
        }
        vo.setAttachments(attachments);
        vo.setPageType(result.getPageType());
        List<CrawlParseTestRespVO.Entry> entries = new ArrayList<>();
        if (result.getEntries() != null) {
            for (NoticeParseResult.ListEntry item : result.getEntries()) {
                CrawlParseTestRespVO.Entry entry = new CrawlParseTestRespVO.Entry();
                entry.setTitle(item.getTitle());
                entry.setUrl(item.getUrl());
                entry.setDate(item.getDate());
                entry.setCategory(item.getCategory());
                entries.add(entry);
            }
        }
        vo.setEntries(entries);
        vo.setMeta(result.getMeta());
        vo.setWarnings(result.getWarnings());
        return vo;
    }

}
