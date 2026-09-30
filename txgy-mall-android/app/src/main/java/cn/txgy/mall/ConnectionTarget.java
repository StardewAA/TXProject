package cn.txgy.mall;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Validates deployment addresses before any request or WebView navigation. */
public final class ConnectionTarget {
    public final String entryUrl;
    public final String markerUrl;
    public final String origin;
    public final boolean localNetwork;

    private ConnectionTarget(String origin, String path, boolean localNetwork) {
        this.origin = origin;
        this.entryUrl = origin + path;
        this.markerUrl = entryUrl + "static/mall-app.json";
        this.localNetwork = localNetwork;
    }

    public static ConnectionTarget parse(String input) {
        if (input == null || input.length() > 2048) throw invalid("请填写正确的移动商城地址");
        String value = input.trim();
        if (value.matches(".*[\\s\\\\\\p{Cntrl}].*")) throw invalid("地址中不能包含空格或特殊字符");
        final URI uri;
        try { uri = new URI(value); } catch (URISyntaxException e) { throw invalid("地址格式不正确"); }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if ((!scheme.equals("https") && !scheme.equals("http")) || host.isEmpty() || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null || host.startsWith("[")) {
            throw invalid("请填写完整的 HTTP(S) 商城地址，不要附带账号、参数或登录凭据");
        }
        int[] ip = parseIpv4(host);
        if ((host.matches("[0-9.]+") && ip == null) || !host.matches("[a-z0-9]+(?:[a-z0-9.-]*[a-z0-9])?")) {
            throw invalid("服务器主机名或 IP 地址不正确");
        }
        for (String part : host.split("\\.", -1)) {
            if (!part.matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")) throw invalid("主机名不正确");
        }
        if (host.equals("localhost") || host.endsWith(".localhost") || (ip != null &&
                (ip[0] == 0 || ip[0] == 127 || ip[0] >= 224 || (ip[0] == 169 && ip[1] == 254)))) {
            throw invalid("localhost 指向手机自身，请填写电脑的局域网 IP 或服务器域名");
        }
        int port = uri.getPort();
        if (port == 0 || port > 65535 || uri.getRawAuthority().endsWith(":")) throw invalid("端口号不正确");
        boolean lan = host.endsWith(".local") || (ip != null && (ip[0] == 10 || (ip[0] == 172 && ip[1] >= 16 && ip[1] <= 31)
                || (ip[0] == 192 && ip[1] == 168)));
        if (scheme.equals("http") && !lan) throw invalid("公网商城请使用 HTTPS；HTTP 仅用于同一局域网教学演示");
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        if (!path.matches("/[a-zA-Z0-9/_~.-]*") || path.contains("//")) throw invalid("请填写商城入口，不要填写商品页面链接");
        for (String part : path.split("/")) if (part.equals(".") || part.equals("..")) throw invalid("商城路径不正确");
        if (!path.endsWith("/")) path += "/";
        String origin = scheme + "://" + host;
        if (port != -1 && !(scheme.equals("https") && port == 443) && !(scheme.equals("http") && port == 80)) origin += ":" + port;
        return new ConnectionTarget(origin, path, lan);
    }

    public boolean contains(String candidate) {
        try {
            URI uri = new URI(candidate);
            if (uri.getRawUserInfo() != null || uri.getHost() == null || candidate.indexOf('\\') >= 0) return false;
            String authority = uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getHost().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            if (port != -1 && !(uri.getScheme().equalsIgnoreCase("https") && port == 443)
                    && !(uri.getScheme().equalsIgnoreCase("http") && port == 80)) authority += ":" + port;
            return origin.equals(authority);
        } catch (Exception e) { return false; }
    }

    private static int[] parseIpv4(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return null;
        int[] result = new int[4];
        for (int i = 0; i < 4; i++) {
            if (!parts[i].matches("0|[1-9][0-9]{0,2}")) return null;
            result[i] = Integer.parseInt(parts[i]);
            if (result[i] > 255) return null;
        }
        return result;
    }

    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
