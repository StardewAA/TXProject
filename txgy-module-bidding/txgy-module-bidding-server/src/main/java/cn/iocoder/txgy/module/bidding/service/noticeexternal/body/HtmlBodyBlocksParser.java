package cn.iocoder.txgy.module.bidding.service.noticeexternal.body;
// 规范依据：设计文档 v0.6 §7（结构化展示）；联调清单 R12② 策略链第一档：常规 HTML 结构解析
// 参考 HtmlSanitizer 白名单口径（净化后的 DOM 等价输入，但直接吃原文并自行忽略 script/style 亦安全）

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 常规 HTML 正文结构化解析器（策略码 HTML_STRUCTURE）：把正文 HTML 按文档顺序转成结构化块。
 *
 * <p>能力边界（诚实声明）：真 &lt;table&gt; / 列表 / 标题 / 段落结构能可靠块化；
 * <b>对"拍平粘连文本"（原表格被压成一段无分隔文字，无 table 标签）无能为力</b>——
 * 该情形交给 {@link AiBodyStructurer} 兜底（策略链 AI_FALLBACK，人工触发）。
 *
 * <p>文本节点缓冲顺序与块元素（标题/表格/列表/段落）交错时保持文档序：游离文本并入前后段落。
 */
public final class HtmlBodyBlocksParser {

    /** 需要递归展开的纯容器（自身不出块，子块保序） */
    private static final Set<String> CONTAINERS = Set.of("div", "section", "article", "main", "body");
    /** 内联文本标签：并入文本缓冲，不单独成块 */
    private static final Set<String> INLINE = Set.of("span", "a", "strong", "b", "em", "i", "u",
            "small", "sub", "sup", "code", "q", "font", "abbr", "cite", "br", "label");
    /** 展示无意义标签：直接跳过（脚本/样式/无内容装饰） */
    private static final Set<String> SKIP = Set.of("script", "style", "link", "meta", "base",
            "iframe", "object", "embed", "form", "input", "button", "textarea", "select", "option");

    private HtmlBodyBlocksParser() {
    }

    /** 解析一段 HTML 正文为结构化块；html 为空返回空表。 */
    public static List<BodyBlock> parse(String html) {
        if (!StringUtils.hasText(html)) {
            return new ArrayList<>();
        }
        List<BodyBlock> blocks = new ArrayList<>();
        StringBuilder pending = new StringBuilder();
        walk(Jsoup.parse(html).body(), blocks, pending);
        flush(blocks, pending);
        return blocks;
    }

    private static void walk(Node parent, List<BodyBlock> blocks, StringBuilder pending) {
        for (Node child : parent.childNodes()) {
            if (child instanceof TextNode text) {
                appendText(pending, text.getWholeText());
                continue;
            }
            if (!(child instanceof Element element)) {
                continue; // Comment/DataNode 等忽略
            }
            String tag = element.normalName();
            if (SKIP.contains(tag)) {
                continue;
            }
            if (tag.matches("h[1-6]")) {
                flush(blocks, pending);
                blocks.add(BodyBlock.heading(Integer.parseInt(tag.substring(1)), element.text()));
            } else if (tag.equals("table")) {
                flush(blocks, pending);
                blocks.add(tableBlock(element));
            } else if (tag.equals("ul") || tag.equals("ol")) {
                flush(blocks, pending);
                blocks.add(listBlock(element));
            } else if (tag.equals("p")) {
                flush(blocks, pending);
                String text = element.text();
                if (StringUtils.hasText(text)) {
                    blocks.add(BodyBlock.paragraph(text));
                }
            } else if (CONTAINERS.contains(tag)) {
                walk(element, blocks, pending); // 容器递归，子块保序
            } else if (INLINE.contains(tag)) {
                appendText(pending, element.wholeText());
            } else {
                // 未登记标签（dl/dd/center/marquee 等罕见）：文本并入缓冲，不丢内容
                appendText(pending, element.wholeText());
            }
        }
    }

    /** 表格块：thead（或全 th 首行）→ headers；tbody/其余行 → rows */
    private static BodyBlock tableBlock(Element table) {
        List<String> headers = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        Element thead = table.selectFirst("thead");
        if (thead != null) {
            for (Element th : thead.select("tr").first() == null ? List.<Element>of()
                    : thead.select("tr").first().select("th,td")) {
                headers.add(th.text());
            }
        }
        for (Element tr : table.select("tbody tr, > tr")) {
            List<String> cells = new ArrayList<>();
            for (Element td : tr.select("th,td")) {
                cells.add(td.text());
            }
            if (headers.isEmpty() && tr.children().stream().allMatch(c -> c.normalName().equals("th"))) {
                headers.addAll(cells); // 无 thead 且首行全表头 → 作 headers
            } else {
                rows.add(cells);
            }
        }
        BodyBlock block = new BodyBlock();
        block.setType("table");
        block.setHeaders(headers.isEmpty() ? null : headers);
        block.setRows(rows);
        return block;
    }

    /** 列表块（li 项文本；嵌套列表拍平成平铺条目） */
    private static BodyBlock listBlock(Element list) {
        boolean ordered = list.normalName().equals("ol");
        List<String> items = new ArrayList<>();
        for (Element li : list.select("> li")) {
            String text = li.text();
            if (StringUtils.hasText(text)) {
                items.add(text);
            }
        }
        return BodyBlock.list(ordered, items);
    }

    private static void appendText(StringBuilder pending, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String normalized = text.replace('\u00a0', ' ').trim();
        if (normalized.isEmpty()) {
            return;
        }
        if (pending.length() > 0 && !Character.isWhitespace(pending.charAt(pending.length() - 1))) {
            pending.append(' ');
        }
        pending.append(normalized);
    }

    private static void flush(List<BodyBlock> blocks, StringBuilder pending) {
        String text = pending.toString().trim();
        pending.setLength(0);
        if (StringUtils.hasText(text)) {
            blocks.add(BodyBlock.paragraph(text));
        }
    }

}
