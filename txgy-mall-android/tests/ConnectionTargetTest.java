import cn.txgy.mall.ConnectionTarget;

public final class ConnectionTargetTest {
    public static void main(String[] args) {
        for (String host : new String[] {"10.211.216.238", "172.19.83.237", "192.168.1.2"}) {
            ConnectionTarget target = ConnectionTarget.parse("http://" + host + ":3000");
            check(target.localNetwork, "Expected LAN address");
            check(target.markerUrl.equals("http://" + host + ":3000/static/mall-app.json"), "Wrong marker path");
        }
        ConnectionTarget target = ConnectionTarget.parse("HTTPS://mall.example.com:443/mobile");
        check(target.entryUrl.equals("https://mall.example.com/mobile/"), "Normalization failed");
        check(target.contains("https://mall.example.com:443/pages/product?id=1"), "Same origin with query denied");
        for (String url : new String[] {"https://mall.example.com.evil.test/", "https://mall.example.com:444/", "https://mallXexample.com/", "https://mall.example.com@evil.test/", "https://user@mall.example.com/", "http://mall.example.com/", "javascript:alert(1)"}) {
            check(!target.contains(url), "External navigation was allowed: " + url);
        }
        for (String url : new String[] {"http://localhost:3000", "http://127.0.0.1:3000", "http://0.0.0.0", "http://169.254.1.1", "http://8.8.8.8", "http://example.com", "https://user:pass@example.com", "https://example.com/?token=secret", "https://example.com/#code", "https://127.1", "https://0177.0.0.1", "https://10.0.0.999", "http://192.168.1.1:65536", "http://192.168.1.1:0", "https://example.com/../", "https://example.com/%2fsecret", "https://example.com\\@evil.com", "javascript:alert(1)", "file:///data/test", "https://bad..example.com"}) {
            boolean rejected = false;
            try { ConnectionTarget.parse(url); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "Unsafe connection accepted: " + url);
        }
        System.out.println("Connection validation passed: LAN/HTTPS, normalization, origin isolation and invalid input.");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
