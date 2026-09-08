package cn.iocoder.txgy.module.bidding.service.crawlexternal.parser;
// [依据] 通用 HTML 公告解析策略（HTML_GENERIC）：正文页面/HTML 文件均可；启发式抽取见 HtmlNoticeExtractor。

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 通用 HTML 公告解析器（默认策略；采集源 parser_code_default 未指定站点专用规则时使用）。
 */
@Component
public class HtmlNoticeParser implements NoticeParser {

    @Override
    public String getParserCode() {
        return "HTML_GENERIC";
    }

    @Override
    public String getParserName() {
        return "通用 HTML 公告解析器";
    }

    @Override
    public String getDescription() {
        return "通用 HTML 解析：h1/title 标题、正文纯文本、发布日期（原文启发）、附件链接候选、"
                + "“招标人/采购人：xxx”发布单位启发。规则类结果带 warnings，可人工核对后固化为站点专用解析器。";
    }

    @Override
    public List<String> supportedPayloadTypes() {
        return List.of("HTML");
    }

    @Override
    public NoticeParseResult parse(ParseInput input) {
        NoticeParseResult out = new NoticeParseResult();
        out.setParserCode(getParserCode());
        out.setParserName(getParserName());
        HtmlNoticeExtractor.extract(input.getContent(), input.getUrl(), out);
        if (out.getOrigUrl() == null && StringUtils.hasText(input.getUrl())) {
            out.setOrigUrl(input.getUrl());
        }
        if (out.getStatus() == null || out.getStatus() == NoticeParseResult.Status.SUCCESS
                || out.getStatus() == NoticeParseResult.Status.PARTIAL) {
            out.finalizeStatus();
        }
        return out;
    }

}
