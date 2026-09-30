package cn.iocoder.txgy.module.bidding.service.externalfetch.fetch;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * 出站请求 SSRF 防护（后端审查 B1：HttpFetchClient 全文无 host/内网校验，allowed_hosts 采集执行时从不校验）。
 *
 * <p>为什么必须做：采集目标 URL 来自可配置的 bidding_crawl_source（也受源站跳转/正文链接影响）。
 * 不做校验时，攻击者只要让服务器去请求 http://169.254.169.254/… 或内网地址，
 * 就能读取云元数据凭据、探测内网服务 —— 这是外部可达的服务器端请求伪造。
 *
 * <p>校验口径（全部通过才放行）：
 *   ① 协议仅允许 http / https（禁 file / ftp / gopher / dict 等）；
 *   ② 解析主机名得到**全部** A/AAAA 记录，逐个判定：环回、任意本地、链路本地（含 169.254.169.254 云元数据）、
 *      私有网段（10/8、172.16/12、192.168/16）、组播、IPv6 唯一本地（fc00::/7）、IPv4 映射地址 → 一律拒绝；
 *   ③ 域名解析失败同样拒绝（不给"解析不了就试一下"留口子）。
 *
 * <p>误伤提示（重要）：若确有**内网采集源**（内网 OA / 内网门户），本校验会拒。
 * 届时应加显式白名单配置（例如 bidding.crawl.http.ssrf-allow-hosts）后放行该 host，
 * **不要直接关掉校验** —— 那等于把 B1 原样放回去。
 */
public final class SsrfGuard {

    private SsrfGuard() {
    }

    /** 校验出站 URL（无白名单）；不合规直接抛 IOException（调用方按抓取失败处理，不重试更安全）。 */
    public static void checkOutbound(String url) throws IOException {
        checkOutbound(url, "");
    }

    /**
     * 校验出站 URL，带来源白名单。
     *
     * @param allowHostsCsv 白名单主机（半角逗号分隔，支持 *.suffix 通配）；空=不豁免
     */
    public static void checkOutbound(String url, String allowHostsCsv) throws IOException {
        if (url == null || url.isBlank()) {
            throw new IOException("SSRF 拦截：URL 为空");
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new IOException("SSRF 拦截：URL 非法（" + e.getMessage() + "）");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IOException("SSRF 拦截：仅允许 http/https，当前协议=" + scheme);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IOException("SSRF 拦截：主机名为空");
        }
        // 白名单显式放行：只豁免"地址段"判定（协议已强制 http/https）。
        // 命中即直接返回、**不再解析 DNS** —— 内网域名在公网 DNS 上解析不了，若先解析会误拒。
        if (isAllowlisted(host, allowHostsCsv)) {
            return;
        }
        InetAddress[] addrs;
        try {
            addrs = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IOException("SSRF 拦截：域名无法解析 " + host);
        }
        for (InetAddress addr : addrs) {
            if (isBlockedAddress(addr)) {
                throw new IOException("SSRF 拦截：目标解析到受限地址 " + addr.getHostAddress() + "（host=" + host + "）");
            }
        }
    }

    /**
     * 白名单匹配（纯函数，便于单测）：大小写不敏感的精确匹配，或 {@code *.suffix} 通配匹配。
     *
     * @param host          URL 主机名（不含端口）
     * @param patternsCsv   半角逗号分隔的白名单；空/blank 视为无白名单
     */
    public static boolean isAllowlisted(String host, String patternsCsv) {
        if (host == null || patternsCsv == null || patternsCsv.isBlank()) {
            return false;
        }
        String h = host.trim().toLowerCase();
        for (String raw : patternsCsv.split(",")) {
            String p = raw.trim().toLowerCase();
            if (p.isEmpty()) {
                continue;
            }
            if (p.startsWith("*.")) {
                String suffix = p.substring(1); // ".corp.internal"
                if (h.endsWith(suffix) && h.length() > suffix.length()) {
                    return true;
                }
            } else if (h.equals(p)) {
                return true;
            }
        }
        return false;
    }

    /** 单地址判定（纯函数，便于单测）：是否为必须拒绝的受限地址。 */
    public static boolean isBlockedAddress(InetAddress addr) {
        if (addr == null) {
            return true;
        }
        if (addr.isLoopbackAddress() || addr.isAnyLocalAddress() || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress() || addr.isMulticastAddress()) {
            return true;
        }
        byte[] b = addr.getAddress();
        if (b.length == 16) {
            // IPv6 唯一本地地址 fc00::/7
            if ((b[0] & 0xFE) == 0xFC) {
                return true;
            }
            // IPv4 映射（::ffff:a.b.c.d）与 IPv4 兼容（::a.b.c.d）：取出后 4 字节再判一次
            boolean highZero = true;
            for (int i = 0; i < 10; i++) {
                if (b[i] != 0) {
                    highZero = false;
                    break;
                }
            }
            boolean mapped = highZero && (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF;
            boolean compat = highZero && b[10] == 0 && b[11] == 0;
            if (mapped || compat) {
                try {
                    return isBlockedAddress(InetAddress.getByAddress(new byte[]{b[12], b[13], b[14], b[15]}));
                } catch (UnknownHostException e) {
                    return true;
                }
            }
        }
        return false;
    }
}