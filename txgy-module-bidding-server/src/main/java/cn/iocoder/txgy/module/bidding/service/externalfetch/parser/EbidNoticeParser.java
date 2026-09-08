package cn.iocoder.txgy.module.bidding.service.externalfetch.parser;
// [依据] 秦源电子招标平台（www.ebidding.com.cn）站点专用解析器（EBIDDING）：下载器阶段将按 source 路由到本解析器。
//       站点专用规则（列表页/详情页结构、接口字段）待真实样本校准：当前版本基于通用抽取并输出校准提示，
//       拿到样本后在本类内叠加站点规则（parse 内按 input.url/特征判断页面类型并定向抽取），不影响其它策略。

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * EBIDDING（秦源电子招标平台）专用解析器 —— v0 骨架（通用抽取 + 站点提示，规则待真实样本校准）。
 */
@Component
public class EbidNoticeParser implements NoticeParser {

    private static final String SITE_HOST = "ebidding.com.cn";

    @Override
    public String getParserCode() {
        return "EBIDDING";
    }

    @Override
    public String getParserName() {
        return "秦源电子招标平台解析器（ebidding.com.cn）";
    }

    @Override
    public String getDescription() {
        return "EBIDDING 站点专用解析（v0 骨架：先走通用抽取，站点规则待真实样本校准）。"
                + "采集源 EBIDDING 的 parser_code_default 建议配置为本 code。";
    }

    @Override
    public List<String> supportedPayloadTypes() {
        return List.of("HTML", "JSON");
    }

    @Override
    public NoticeParseResult parse(ParseInput input) {
        NoticeParseResult out = new NoticeParseResult();
        out.setParserCode(getParserCode());
        out.setParserName(getParserName());
        boolean jsonPayload = "JSON".equalsIgnoreCase(input.getPayloadType());
        if (jsonPayload) {
            JsonNoticeExtractor.extract(input.getContent(), input.getUrl(), out);
        } else {
            HtmlNoticeExtractor.extract(input.getContent(), input.getUrl(), out);
        }
        if (out.getOrigUrl() == null && StringUtils.hasText(input.getUrl())) {
            out.setOrigUrl(input.getUrl());
        }
        // 站点上下文核对（测试提示用，不阻断）
        String url = input.getUrl();
        if (!StringUtils.hasText(url) || !url.contains(SITE_HOST)) {
            out.getWarnings().add("输入未提供 " + SITE_HOST + " 来源 URL（或非本站域名）：EBIDDING 专用规则依赖站点上下文，请携带详情页 URL 再测");
        } else {
            out.getMeta().put("siteConfirmed", SITE_HOST);
        }
        out.getWarnings().add("EBIDDING 专用规则为 v0 通用抽取：提供真实公告页 HTML/JSON 样本后在本解析器内校准（列表/详情/接口三态定向）");
        if (out.getStatus() == null || out.getStatus() == NoticeParseResult.Status.SUCCESS
                || out.getStatus() == NoticeParseResult.Status.PARTIAL) {
            out.finalizeStatus();
        }
        return out;
    }

}
