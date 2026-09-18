package cn.iocoder.txgy.module.crm.util;

import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.crm.enums.ErrorCodeConstants.CUSTOMER_FETCH_WEB_PAGE_FAIL;

/**
 * 网页抓取工具类
 *
 * 用途：「一键识别」支持粘贴企业官网 / 工商信息页面链接，由后端代理抓取网页正文，
 * 再交给大模型提取工商信息（公司名称、联系人、电话、地址等），
 * 从而绕开浏览器直接抓取时的跨域限制。
 *
 * 抓取链路中需要处理的坑：
 * 1. 响应可能是 gzip / deflate 压缩的，必须先解压，否则拿到的是二进制乱码；
 * 2. 网页编码可能是 UTF-8 也可能是 GBK，且部分站点声明与实际不符，需要按字节探测；
 * 3. HTML 里的转义实体（例如 &amp;lt;div&amp;gt;）还原后会重新出现标签，需要二次清理；
 * 4. 只取 body 内容，避免 head 里的 meta / link 混进正文干扰大模型。
 *
 * 说明：只做基础正文提取，不执行 JS；纯前端渲染（SPA）的页面可能拿不到内容，
 * 此时返回空内容，由前端提示用户改用手动粘贴。
 */
@Slf4j
public class CrmWebPageUtils {

    /** 连接超时时间 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /** 读取超时时间 */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(12);
    /** 网页最大读取字节数：2MB，避免超大页面拖垮服务 */
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    /** 返回给大模型的正文最大长度：8000 字符，太长的导航噪声反而会干扰提取 */
    private static final int MAX_CONTENT_LENGTH = 8000;
    /** 浏览器 UA，避免部分站点直接拒绝抓取 */
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/122.0 Safari/537.36";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Pattern CHARSET_PATTERN = Pattern.compile("charset=[\"']?([\\w-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_PATTERN = Pattern.compile("<title[^>]*>([\\s\\S]*?)</title>", Pattern.CASE_INSENSITIVE);
    private static final Pattern BODY_PATTERN = Pattern.compile("<body[^>]*>([\\s\\S]*)</body\\s*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCRIPT_STYLE_PATTERN = Pattern.compile(
            "<(script|style|noscript|svg|iframe)[^>]*>[\\s\\S]*?</\\1\\s*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMENT_PATTERN = Pattern.compile("<!--[\\s\\S]*?-->");
    private static final Pattern BLOCK_TAG_PATTERN = Pattern.compile(
            "</?(?:p|div|br|li|tr|table|ul|ol|h[1-6]|section|article|dd|dt|blockquote)[^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern NUMERIC_ENTITY_PATTERN = Pattern.compile("&#(x?)([0-9a-fA-F]+);");

    private CrmWebPageUtils() {
    }

    /**
     * 校验是否为合法的 http/https 链接
     *
     * @param url 链接
     * @return 是否合法
     */
    public static boolean isValidUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return false;
        }
        try {
            URI uri = URI.create(url.trim());
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && StrUtil.isNotBlank(uri.getHost());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 抓取网页，并提取标题与正文文本
     *
     * @param url 网页地址
     * @return 网页内容（标题 + 正文）
     */
    public static WebPage fetch(String url) {
        if (!isValidUrl(url)) {
            throw exception(CUSTOMER_FETCH_WEB_PAGE_FAIL, "链接格式不正确，请填写以 http:// 或 https:// 开头的完整链接");
        }
        url = url.trim();
        String html = request(url);
        return new WebPage(extractTitle(html), extractText(html));
    }

    /**
     * 发起 HTTP 请求，获得网页 HTML（自动处理 gzip / deflate 压缩与中文编码）
     */
    private static String request(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(READ_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                // 明确声明可接受的压缩方式，便于按 Content-Encoding 对应解压
                .header("Accept-Encoding", "gzip, deflate")
                .GET()
                .build();
        HttpResponse<byte[]> response;
        try {
            response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (Exception e) {
            log.warn("[request][抓取网页({}) 发生异常]", url, e);
            throw exception(CUSTOMER_FETCH_WEB_PAGE_FAIL, "无法访问该链接，请确认链接能正常打开");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 400) {
            throw exception(CUSTOMER_FETCH_WEB_PAGE_FAIL, "页面返回状态码 " + response.statusCode());
        }
        byte[] body = uncompress(response);
        if (body == null || body.length == 0) {
            throw exception(CUSTOMER_FETCH_WEB_PAGE_FAIL, "页面内容为空");
        }
        if (body.length > MAX_BYTES) {
            body = Arrays.copyOf(body, MAX_BYTES);
        }
        return decodeBody(body, response);
    }

    /**
     * 按 Content-Encoding 解压响应体：不解压会拿到二进制乱码，是大模型识别失败的常见原因
     */
    private static byte[] uncompress(HttpResponse<byte[]> response) {
        byte[] body = response.body();
        if (body == null || body.length == 0) {
            return body;
        }
        String encoding = response.headers().firstValue("Content-Encoding").orElse("").toLowerCase();
        try {
            if (encoding.contains("gzip")) {
                return readLimited(new GZIPInputStream(new ByteArrayInputStream(body)));
            }
            if (encoding.contains("deflate")) {
                return readLimited(new InflaterInputStream(new ByteArrayInputStream(body)));
            }
        } catch (Exception e) {
            log.warn("[uncompress][解压网页内容({}) 失败，按原样解析]", encoding, e);
        }
        return body;
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        try (InputStream input = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) > 0) {
                total += read;
                if (total > MAX_BYTES) {
                    out.write(buffer, 0, read - (total - MAX_BYTES));
                    break;
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    /**
     * 解码网页字节：优先用页面声明的编码；
     * 声明缺失或与实际不符时，按「严格 UTF-8 → GBK」的顺序探测，避免中文乱码。
     */
    private static String decodeBody(byte[] body, HttpResponse<?> response) {
        // 1. 页面声明的编码
        String declared = detectDeclaredCharset(body, response);
        if (StrUtil.isNotBlank(declared)) {
            Charset charset = safeCharset(declared);
            if (charset != null) {
                String text = new String(body, charset);
                if (countReplacement(text) == 0) {
                    return text;
                }
            }
        }
        // 2. 严格按 UTF-8 解码，成功则说明是 UTF-8 页面
        if (isValidUtf8(body)) {
            return new String(body, StandardCharsets.UTF_8);
        }
        // 3. 兜底按 GBK 解码（国内老站点常见）
        return new String(body, safeCharset("GBK"));
    }

    private static String detectDeclaredCharset(byte[] body, HttpResponse<?> response) {
        String charset = match(CHARSET_PATTERN, response.headers().firstValue("Content-Type").orElse(""));
        if (StrUtil.isNotBlank(charset)) {
            return charset;
        }
        // 用 ISO-8859-1 粗读头部，避免中文乱码影响 meta 匹配
        String head = new String(body, 0, Math.min(body.length, 8192), StandardCharsets.ISO_8859_1);
        return match(CHARSET_PATTERN, head);
    }

    private static Charset safeCharset(String name) {
        if (StrUtil.isBlank(name)) {
            return StandardCharsets.UTF_8;
        }
        // 兼容部分站点声明的非标准编码名
        if ("gb2312".equalsIgnoreCase(name) || "gbk".equalsIgnoreCase(name) || "gb18030".equalsIgnoreCase(name)) {
            return Charset.forName("GBK");
        }
        try {
            return Charset.forName(name);
        } catch (Exception e) {
            return null;
        }
    }

    /** 严格校验字节流是否为合法 UTF-8 */
    private static boolean isValidUtf8(byte[] body) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(body));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    /** 统计解码后的替换字符数量，用于判断编码是否选错 */
    private static long countReplacement(String text) {
        return text.chars().filter(c -> c == 0xFFFD).count();
    }

    /**
     * 提取网页标题
     */
    private static String extractTitle(String html) {
        String title = match(TITLE_PATTERN, html);
        return StrUtil.sub(unescapeHtml(title).trim(), 0, 200);
    }

    /**
     * 提取网页正文文本：只取 body、去掉脚本样式注释，再剥离标签并压缩空白
     */
    private static String extractText(String html) {
        // 只保留 body 内容，避免 head 里的 meta / link 混入正文
        Matcher bodyMatcher = BODY_PATTERN.matcher(html);
        if (bodyMatcher.find()) {
            html = bodyMatcher.group(1);
        }
        String text = SCRIPT_STYLE_PATTERN.matcher(html).replaceAll(" ");
        text = COMMENT_PATTERN.matcher(text).replaceAll(" ");
        text = BLOCK_TAG_PATTERN.matcher(text).replaceAll("\n");
        text = TAG_PATTERN.matcher(text).replaceAll(" ");
        // 还原实体后可能重新出现标签（如 &lt;div&gt;），再清一轮，避免 HTML 混进正文
        text = unescapeHtml(text);
        text = TAG_PATTERN.matcher(text).replaceAll(" ");
        text = unescapeHtml(text);
        // 压缩空白：连续空白压成一个空格，连续空行压成一个换行
        text = text.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
        text = text.replaceAll("\\n[ \\t]*\\n+", "\n");
        text = text.trim();
        return text.length() > MAX_CONTENT_LENGTH ? text.substring(0, MAX_CONTENT_LENGTH) : text;
    }

    /**
     * 还原常见的 HTML 实体
     */
    private static String unescapeHtml(String text) {
        if (StrUtil.isEmpty(text)) {
            return StrUtil.EMPTY;
        }
        text = text.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&#39;", "'");
        // 还原数字实体，例如 &#20013;
        Matcher matcher = NUMERIC_ENTITY_PATTERN.matcher(text);
        StringBuilder builder = new StringBuilder(text.length());
        while (matcher.find()) {
            try {
                int codePoint = Integer.parseInt(matcher.group(2), "x".equalsIgnoreCase(matcher.group(1)) ? 16 : 10);
                matcher.appendReplacement(builder, Matcher.quoteReplacement(new String(Character.toChars(codePoint))));
            } catch (Exception e) {
                matcher.appendReplacement(builder, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private static String match(Pattern pattern, String content) {
        if (StrUtil.isEmpty(content)) {
            return StrUtil.EMPTY;
        }
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(1) : StrUtil.EMPTY;
    }

    /**
     * 网页内容
     */
    @Data
    @AllArgsConstructor
    public static class WebPage {

        /**
         * 网页标题
         */
        private String title;

        /**
         * 网页正文文本
         */
        private String content;

    }

}
