package cn.iocoder.txgy.module.bidding.service.crawlexternal.parser;
// [依据] 通用 JSON 公告解析策略（JSON_GENERIC）：站点 JSON API/导出数据；宽容键名映射见 JsonNoticeExtractor。

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 通用 JSON 公告解析器（支持包装字段 data/result/... 与数组取首元素；附件数组识别）。
 */
@Component
public class JsonNoticeParser implements NoticeParser {

    @Override
    public String getParserCode() {
        return "JSON_GENERIC";
    }

    @Override
    public String getParserName() {
        return "通用 JSON 公告解析器";
    }

    @Override
    public String getDescription() {
        return "通用 JSON 解析：兼容常见公告字段键名（中英文/大小写/下划线差异自动归一），"
                + "支持 data/result/content/item/record/rows/list 包装与数组取首元素；未命中键名会进 unmatched.* meta 便于校准。";
    }

    @Override
    public List<String> supportedPayloadTypes() {
        return List.of("JSON");
    }

    @Override
    public NoticeParseResult parse(ParseInput input) {
        NoticeParseResult out = new NoticeParseResult();
        out.setParserCode(getParserCode());
        out.setParserName(getParserName());
        JsonNoticeExtractor.extract(input.getContent(), input.getUrl(), out);
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
