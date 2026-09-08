package cn.iocoder.txgy.module.bidding.service.crawlexternal.parser;
// [依据] 秦源等站点样本经浏览器"查看源代码"保存后，源码被行高亮包装（td.line-content + 实体转义）。
//       解析前自动检测并还原：含 .line-content 结构 → 抽行文本反转义 → 得到原始 HTML。

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * view-source 保存物还原（浏览器"查看源代码"另存文件结构解码）。
 */
public final class HtmlViewSourceDecoder {

    private static final Pattern LINE_CONTENT = Pattern.compile("<td class=\"line-content\">([\\s\\S]*?)</td>");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern ENTITY_NUM = Pattern.compile("&#(\\d+);");

    private HtmlViewSourceDecoder() {
    }

    /**
     * 若为 view-source 包装结构则还原原始 HTML，否则原样返回。
     */
    public static String decodeIfNeeded(String html) {
        if (html == null || !html.contains("line-content")) {
            return html;
        }
        StringBuilder raw = new StringBuilder();
        Matcher matcher = LINE_CONTENT.matcher(html);
        while (matcher.find()) {
            raw.append(matcher.group(1));
        }
        if (raw.length() == 0) {
            return html;
        }
        String line = raw.toString();
        // 拼合高亮 span（属性名/值被拆开渲染）
        line = line.replaceAll("</span><span class=\"html-attribute-name\">", " ");
        line = TAG.matcher(line).replaceAll("");
        line = unescape(line);
        return line;
    }

    private static String unescape(String text) {
        text = text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&amp;", "&");
        Matcher matcher = ENTITY_NUM.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            try {
                matcher.appendReplacement(buffer, String.valueOf((char) Integer.parseInt(matcher.group(1))));
            } catch (RuntimeException ignored) {
                // 忽略无法解析的实体
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

}
