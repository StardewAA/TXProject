package cn.iocoder.txgy.module.bidding.service.externalfetch.validate;

// 规范依据：用户口径 2026-09-10「严格校验下载文件的完整性（如 MD5/SHA 校验）、文件大小、格式合规性，以及存储状态」。
// 业务目标：在"落库之前"拦住三类脏数据——空/截断、格式不符（把错误页当 PDF）、明显异常（验证码页/乱码），
//   并产出 sha256 作为完整性凭据（红线 8 三件套之一）。
// 为什么必须做：采集侧一旦把拦截页/空文件当正文入库，后续 AI 抽取、行业判定、前端展示会全线污染，
//   且原始错误现场已丢失（无法回溯是站点拦截还是解析问题）。

import cn.iocoder.txgy.module.bidding.infra.config.CrawlHttpProperties;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 下载内容完整性校验器（纯函数式判定 + sha256 产出，无副作用便于单测）。
 */
@Slf4j
@Component
public class DownloadIntegrityValidator {

    /** 判定结论（PASS=可入库；FAIL_* 一律不得入库，由调用方按失败处理并落审计） */
    public enum Verdict {
        PASS,
        /** 空内容/0 字节（常见于站点未登录返回 200 空体） */
        FAIL_EMPTY,
        /** 超过单文件上限 */
        FAIL_TOO_LARGE,
        /** 格式不符（如 PDF 无 %PDF 头、图片魔数不匹配） */
        FAIL_FORMAT,
        /** 文本过短（多是壳页/占位页，不是正文） */
        FAIL_TEXT_TOO_SHORT,
        /** 命中错误页特征词（404/系统繁忙/无权访问…） */
        FAIL_ERROR_PAGE,
        /** 乱码（替换字符占比过高，多为编码未处理） */
        FAIL_MOJIBAKE
    }

    /** 错误页特征词（正文命中即判失败；与反爬特征区分：这些是"页面不存在/系统错误"类） */
    private static final String[] ERROR_PAGE_MARKERS = {
            "404 Not Found", "页面不存在", "页面已删除", "系统错误", "系统异常", "无权访问",
            "您访问的页面不存在", "The page you requested was not found", "Service Unavailable"
    };

    /** 文本正文最小字符数（低于此值基本不是公告正文；壳页/跳转页通常 <50） */
    private static final int MIN_TEXT_CHARS = 120;

    /** 乱码判定阈值：替换字符 U+FFFD 占比超过 1% 即视为编码异常 */
    private static final double MOJIBAKE_RATIO = 0.01d;

    @Resource
    private CrawlHttpProperties props;

    private long maxBytes() {
        return props == null ? 50L * 1024 * 1024 : Math.max(1L, props.getMaxDownloadBytes());
    }

    /** 校验结果（含 sha256 与人类可读原因，直接进审计表/任务 message） */
    @Getter
    public static class Result {
        private final Verdict verdict;
        private final String sha256;
        private final long size;
        private final String detail;

        Result(Verdict verdict, String sha256, long size, String detail) {
            this.verdict = verdict;
            this.sha256 = sha256;
            this.size = size;
            this.detail = detail;
        }

        public boolean pass() {
            return verdict == Verdict.PASS;
        }
    }

    /**
     * 校验二进制原文（PDF/图片/OFD 等）。
     *
     * @param bytes      下载字节
     * @param contentType 期望形态（PDF/JPG/PNG/GIF/OFD/ZIP；空则只做通用校验）
     */
    public Result validateBinary(byte[] bytes, String contentType, String url) {
        String sha = CrawlDigestUtils.sha256Hex(bytes);
        int size = bytes == null ? 0 : bytes.length;
        if (size == 0) {
            return new Result(Verdict.FAIL_EMPTY, sha, 0, "下载内容为空（0 字节） url=" + url);
        }
        if (size > maxBytes()) {
            return new Result(Verdict.FAIL_TOO_LARGE, sha, size, "超过单文件上限 " + maxBytes() + " 字节 url=" + url);
        }
        String expected = contentType == null ? "" : contentType.toUpperCase(Locale.ROOT);
        if (expected.contains("PDF") && !isPdf(bytes)) {
            // 典型场景：站点未登录/被拦截时返回 HTML 错误页，扩展名/声明却是 PDF
            return new Result(Verdict.FAIL_FORMAT, sha, size,
                    "声明为 PDF 但缺少 %PDF- 头或未见 %%EOF 结尾（可能是错误页/截断文件） url=" + url);
        }
        if (expected.contains("JPG") || expected.contains("JPEG")) {
            if (!startsWith(bytes, new int[]{0xFF, 0xD8, 0xFF})) {
                return new Result(Verdict.FAIL_FORMAT, sha, size, "JPEG 魔数不匹配 url=" + url);
            }
        } else if (expected.contains("PNG")) {
            if (!startsWith(bytes, new int[]{0x89, 0x50, 0x4E, 0x47})) {
                return new Result(Verdict.FAIL_FORMAT, sha, size, "PNG 魔数不匹配 url=" + url);
            }
        } else if (expected.contains("GIF")) {
            if (!(size > 6 && new String(bytes, 0, 3, StandardCharsets.US_ASCII).equals("GIF"))) {
                return new Result(Verdict.FAIL_FORMAT, sha, size, "GIF 魔数不匹配 url=" + url);
            }
        } else if (expected.contains("OFD") || expected.contains("ZIP")) {
            if (!startsWith(bytes, new int[]{0x50, 0x4B})) { // OFD/ZIP 均为 PK 开头
                return new Result(Verdict.FAIL_FORMAT, sha, size, "OFD/ZIP 魔数不匹配 url=" + url);
            }
        }
        return new Result(Verdict.PASS, sha, size, "OK");
    }

    /**
     * 校验文本原文（HTML/JSON）：去标签后判长度，并识别错误页与乱码。
     *
     * @param text 下载到的文本（可能为 HTML）
     */
    public Result validateText(String text, String url) {
        byte[] raw = text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
        String sha = CrawlDigestUtils.sha256Hex(raw);
        if (!StringUtils.hasText(text)) {
            return new Result(Verdict.FAIL_EMPTY, sha, raw.length, "文本内容为空 url=" + url);
        }
        String plain = text.replaceAll("<script[\\s\\S]*?</script>", " ")
                .replaceAll("<style[\\s\\S]*?</style>", " ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        for (String marker : ERROR_PAGE_MARKERS) {
            if (plain.contains(marker)) {
                return new Result(Verdict.FAIL_ERROR_PAGE, sha, raw.length, "命中错误页特征[" + marker + "] url=" + url);
            }
        }
        if (plain.length() < MIN_TEXT_CHARS) {
            return new Result(Verdict.FAIL_TEXT_TOO_SHORT, sha, raw.length,
                    "正文仅 " + plain.length() + " 字符（<" + MIN_TEXT_CHARS + "），疑为壳页/占位页 url=" + url);
        }
        long bad = text.chars().filter(c -> c == 0xFFFD).count();
        if (bad > 0 && (double) bad / Math.max(1, text.length()) > MOJIBAKE_RATIO) {
            return new Result(Verdict.FAIL_MOJIBAKE, sha, raw.length,
                    "乱码字符占比过高（" + bad + "/" + text.length() + "），编码处理异常 url=" + url);
        }
        return new Result(Verdict.PASS, sha, raw.length, "OK");
    }

    /** PDF 判定：头部允许 BOM/空白（≤1KB 内出现 %PDF-），且尾部 2KB 内含 %%EOF（防截断） */
    private static boolean isPdf(byte[] bytes) {
        int headLimit = Math.min(bytes.length, 1024);
        String head = new String(bytes, 0, headLimit, StandardCharsets.ISO_8859_1);
        if (!head.contains("%PDF-")) {
            return false;
        }
        int tailStart = Math.max(0, bytes.length - 2048);
        String tail = new String(bytes, tailStart, bytes.length - tailStart, StandardCharsets.ISO_8859_1);
        return tail.contains("%%EOF");
    }

    private static boolean startsWith(byte[] bytes, int[] magic) {
        if (bytes.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if ((bytes[i] & 0xFF) != magic[i]) {
                return false;
            }
        }
        return true;
    }

}
