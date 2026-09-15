package cn.iocoder.txgy.module.bidding.service.externalfetch.fetch;

// [依据] 安全评审（下载目标 http(s)+主机白名单）：采集/回填下载的一切"外站 URL"（详情页/PDF 直链/附件溯源）
//       在发起 HTTP 前做主机白名单校验——白名单=采集源 base_url 主机 ∪ allowed_hosts 域名（含各自子域），
//       防目标站响应内容被污染后诱导服务端请求内网/任意主机（SSRF）。平台文件服务 URL（/admin-api/infra/file/）为
//       本服务受控自产目标，显式豁免；解析失败/无主机一律拒绝（fail-closed）。

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 下载目标主机守卫（纯静态，可单测）。
 *
 * <p>匹配规则：目标 host 与白名单域名做"相等或子域"匹配（如 baseUrl 主机 qyzb.shccmg.com
 * 放行 qyzb.shccmg.com 与其子域 a.qyzb.shccmg.com；allowed_hosts 支持裸域名或完整 URL 两种填写形态）。
 */
public final class UrlHostGuard {

    private UrlHostGuard() {
    }

    /**
     * 校验外站下载目标是否放行。
     *
     * @param rawUrl          待下载 URL（http/https）
     * @param baseUrl         采集源 base_url（可为完整 URL 或裸域名）
     * @param allowedHostsCsv 采集源 allowed_hosts（半角逗号分隔；可为 null/空）
     * @return true=放行
     */
    public static boolean isExternalAllowed(String rawUrl, String baseUrl, String allowedHostsCsv) {
        URI uri = parse(rawUrl);
        if (uri == null) {
            return false; // 解析失败拒绝
        }
        String scheme = uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            return false; // 协议白名单：拒绝 javascript:/file:/data: 等
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            return false;
        }
        String hostLower = host.toLowerCase(Locale.ROOT);
        return matchesAny(hostLower, allowDomains(baseUrl, allowedHostsCsv));
    }

    /** 平台文件服务目标（自产受控）：/admin-api/infra/file/ 前缀 + 容器本机回环主机 */
    public static boolean isInternalFileUrl(String rawUrl) {
        URI uri = parse(rawUrl);
        if (uri == null) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        if (!("127.0.0.1".equals(h) || "localhost".equals(h) || "host.docker.internal".equals(h))) {
            return false;
        }
        String path = uri.getPath();
        return path != null && path.startsWith("/admin-api/infra/file/");
    }

    /** 解析 base_url/allowed_hosts 为白名单域名集合（兼容"完整 URL"与"裸域名"两种填写） */
    public static Set<String> allowDomains(String baseUrl, String allowedHostsCsv) {
        Set<String> domains = new HashSet<>();
        addDomain(domains, baseUrl);
        if (allowedHostsCsv != null) {
            for (String item : allowedHostsCsv.split(",")) {
                addDomain(domains, item.trim());
            }
        }
        return domains;
    }

    private static void addDomain(Set<String> domains, String urlOrHost) {
        if (urlOrHost == null || urlOrHost.trim().isEmpty()) {
            return;
        }
        String v = urlOrHost.trim();
        URI uri = parse(v);
        if (uri != null && uri.getHost() != null) {
            domains.add(uri.getHost().toLowerCase(Locale.ROOT));
            return;
        }
        // 裸域名（无 scheme）：剥路径/用户信息/端口
        int slash = v.indexOf('/');
        if (slash >= 0) {
            v = v.substring(0, slash);
        }
        int at = v.lastIndexOf('@');
        if (at >= 0) {
            v = v.substring(at + 1);
        }
        int colon = v.lastIndexOf(':');
        if (colon >= 0 && v.indexOf(']') < colon) {
            v = v.substring(0, colon);
        }
        if (!v.isEmpty() && !v.contains(" ")) {
            domains.add(v.toLowerCase(Locale.ROOT));
        }
    }

    private static boolean matchesAny(String hostLower, Set<String> domains) {
        for (String domain : domains) {
            if (hostLower.equals(domain) || hostLower.endsWith("." + domain)) {
                return true;
            }
        }
        return false;
    }

    /** 中文/未编码 URL 转义后重解析（与中转守卫同策略，仅作解析用） */
    private static URI parse(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty()) {
            return null;
        }
        try {
            return new URI(rawUrl);
        } catch (URISyntaxException e) {
            try {
                return new URI(asciiEscape(rawUrl));
            } catch (URISyntaxException ex) {
                return null;
            }
        }
    }

    private static String asciiEscape(String url) {
        StringBuilder sb = new StringBuilder(url.length());
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c < 0x80 && c != ' ' && c != 0x7f) {
                sb.append(c);
            } else {
                for (byte b : String.valueOf(c).getBytes(StandardCharsets.UTF_8)) {
                    sb.append('%').append(String.format("%02X", b & 0xFF));
                }
            }
        }
        return sb.toString();
    }
}
