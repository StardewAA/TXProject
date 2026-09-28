package cn.iocoder.txgy.module.bidding.service.externalfetch.fetch;
// [依据] 下载器 HTTP 客户端：GET/POST JSON/二进制与错误状态。

import cn.iocoder.txgy.module.bidding.infra.config.CrawlHttpProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpFetchClientTest {

    private static HttpServer server;
    private static String base;

    @BeforeAll
    static void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hello", exchange -> {
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/pdf", exchange -> {
            byte[] body = new byte[]{37, 80, 68, 70, 45, 49, 46, 52}; // %PDF-1.4
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/missing", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    /** 构造走 SSRF 校验的客户端，并把本地 mock server 地址加入白名单（测试自控地址，符合白名单语义）。 */
    private static HttpFetchClient client() {
        HttpFetchClient c = new HttpFetchClient();
        CrawlHttpProperties props = new CrawlHttpProperties();
        props.setSsrfAllowHosts("127.0.0.1");
        ReflectionTestUtils.setField(c, "props", props);
        return c;
    }

    @Test
    void getTextAndPostJson() throws Exception {
        HttpFetchClient client = client();
        String text = client.getText(base + "/hello");
        assertTrue(text.contains("ok"));
        String post = client.postJson(base + "/hello", "{\"a\":1}");
        assertTrue(post.contains("ok"));
    }

    @Test
    void getBytesReturnsPdf() throws Exception {
        HttpFetchClient client = client();
        byte[] bytes = client.getBytes(base + "/pdf");
        assertEquals('%', bytes[0]);
        assertEquals('P', bytes[1]);
    }

    @Test
    void non200Throws() {
        HttpFetchClient client = client();
        assertThrows(IOException.class, () -> client.getText(base + "/missing"));
        assertThrows(IOException.class, () -> client.getBytes(base + "/missing"));
    }

}
