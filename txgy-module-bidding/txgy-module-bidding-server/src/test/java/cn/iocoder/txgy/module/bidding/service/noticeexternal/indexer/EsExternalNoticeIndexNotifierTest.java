package cn.iocoder.txgy.module.bidding.service.noticeexternal.indexer;
// 规范依据：设计文档 v0.6 §7.6/§15.6（审核 ACTIVE → ES 索引；幂等 PUT 支撑补偿重放）；本地 ES 8.10 xpack（Basic Auth）

import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO;
import cn.iocoder.txgy.module.bidding.enums.NoticeExternalStatusEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ES 外部公告索引通知器测试：JDK HttpClient 对本地 RawHttpServer 真发 HTTP，
 * 覆盖 PUT 路径与载荷/Authorization 头、201 成功、500 失败抛错、无密码不带认证头。
 */
class EsExternalNoticeIndexNotifierTest {

    /** 迷你 HTTP 服务：捕获一次请求（请求行/头/体）并按预设码返回，验证真实 HTTP 行为（JDK 基础网络，无模块问题） */
    static class RawHttpServer implements AutoCloseable {

        final int code;
        final String body;
        final ServerSocket server;
        final Thread thread;
        volatile String requestLine = "";
        volatile String headers = "";
        volatile String requestBody = "";
        volatile Exception failure;

        RawHttpServer(int code, String body) throws IOException {
            this.code = code;
            this.body = body;
            this.server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            this.thread = new Thread(this::serve);
            this.thread.setDaemon(true);
            this.thread.start();
        }

        private void serve() {
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(3000); // 读请求兜底：超时即视为请求已到，直接响应（< 客户端 10s 超时）
                java.io.InputStream in = socket.getInputStream();
                // 逐字节读至头部结束 CRLFCRLF（HTTP/1.1）
                java.io.ByteArrayOutputStream headerBuf = new java.io.ByteArrayOutputStream();
                boolean headerDone = false;
                try {
                    while (true) {
                        int b = in.read();
                        if (b < 0) {
                            break;
                        }
                        headerBuf.write(b);
                        byte[] arr = headerBuf.toByteArray();
                        int n = arr.length;
                        if (n >= 4 && arr[n - 4] == '\r' && arr[n - 3] == '\n'
                                && arr[n - 2] == '\r' && arr[n - 1] == '\n') {
                            headerDone = true;
                            break;
                        }
                    }
                } catch (java.net.SocketTimeoutException ignore) {
                    // 读到部分头部即超时：仍按已读内容响应
                }
                String headerStr = headerBuf.toString(StandardCharsets.UTF_8);
                String[] lines = headerStr.split("\r\n");
                requestLine = lines.length > 0 ? lines[0] : "";
                headers = headerStr;
                // 按字节长度读 body（Content-Length 为字节数）；读超时/EOF 容忍，不回写阻塞
                int contentLength = 0;
                for (String l : lines) {
                    if (l.toLowerCase().startsWith("content-length:")) {
                        contentLength = Integer.parseInt(l.substring(l.indexOf(':') + 1).trim());
                    }
                }
                if (contentLength > 0 && headerDone) {
                    byte[] body = new byte[contentLength];
                    int off = 0;
                    try {
                        while (off < contentLength) {
                            int n = in.read(body, off, contentLength - off);
                            if (n < 0) {
                                break;
                            }
                            off += n;
                        }
                    } catch (java.net.SocketTimeoutException ignore) {
                        // body 未读全（超时）仍响应；测试断言遇此场景会自行失败提示
                    }
                    requestBody = new String(body, 0, off, StandardCharsets.UTF_8);
                }
                String reason = code == 201 ? "Created" : "Internal Server Error";
                byte[] resp = ("HTTP/1.1 " + code + " " + reason + "\r\n"
                        + "Content-Type: application/json\r\n"
                        + "Content-Length: " + body.length() + "\r\n"
                        + "Connection: close\r\n\r\n" + body).getBytes(StandardCharsets.UTF_8);
                try (java.io.OutputStream out = socket.getOutputStream()) {
                    out.write(resp);
                }
            } catch (Exception e) {
                this.failure = e;
            }
        }

        String url() {
            return "http://127.0.0.1:" + server.getLocalPort();
        }

        @Override
        public void close() throws Exception {
            server.close();
            thread.join(2000);
        }
    }

    private EsExternalNoticeIndexNotifier newNotifier(String uris, String username, String password, boolean debug) {
        EsExternalNoticeIndexNotifier notifier = new EsExternalNoticeIndexNotifier();
        ReflectionTestUtils.setField(notifier, "uris", uris);
        ReflectionTestUtils.setField(notifier, "username", username);
        ReflectionTestUtils.setField(notifier, "password", password);
        ReflectionTestUtils.setField(notifier, "debug", debug);
        return notifier;
    }

    private NoticeExternalDO notice(Long id) {
        NoticeExternalDO n = new NoticeExternalDO();
        n.setId(id);
        n.setTitle("炼化公司 2026 年装置大修消缺公开招标");
        n.setSourceCode("SAMPLE");
        n.setContentType("HTML");
        n.setOrigUrl("https://src.example.gov.cn/trade/" + id);
        n.setContentHtml("<p>正文含招标范围</p><script>alert(1)</script>");
        n.setBidSectionCodes("SEC-A,SEC-B");
        n.setStatus(NoticeExternalStatusEnum.ACTIVE.getStatus());
        n.setPublicScope("SOCIAL");
        n.setPublishTime(LocalDateTime.of(2026, 8, 30, 12, 0));
        return n;
    }

    @AfterEach
    void noServerLeft() {
        // each test closes its own server in finally/close(); 仅保证不残留
    }

    @Test
    void indexNotice_success_putDocWithAuthAndText() throws Exception {
        try (RawHttpServer server = new RawHttpServer(201, "{\"result\":\"created\"}")) {
            EsExternalNoticeIndexNotifier notifier =
                    newNotifier(server.url(), "elastic", "secret123", false);
            notifier.indexNotice(notice(100L));

            assertTrue(server.requestLine.startsWith(
                            "PUT /bidding-notice-external/_doc/100 HTTP/1.1"),
                    "请求行应为 PUT 索引路径: " + server.requestLine);
            String expect = "Basic " + Base64.getEncoder().encodeToString(
                    "elastic:secret123".getBytes(StandardCharsets.UTF_8));
            assertTrue(server.headers.contains("Authorization: " + expect),
                    "应携带 Basic 认证头");
            assertTrue(server.requestBody.contains("炼化公司 2026 年装置大修消缺公开招标"));
            assertTrue(server.requestBody.contains("正文含招标范围"), "HTML 正文经 jsoup 去标签入库");
            assertTrue(!server.requestBody.contains("<script>"), "正文不得带原始标签");
            assertTrue(server.requestBody.contains("SEC-A,SEC-B"));
            assertTrue(server.requestBody.contains("2026-08-30T12:00:00"));
        }
    }

    @Test
    void indexNotice_server500_throwsIllegalState() throws Exception {
        try (RawHttpServer server = new RawHttpServer(500, "{\"error\":\"boom\"}")) {
            EsExternalNoticeIndexNotifier notifier =
                    newNotifier(server.url(), "elastic", "secret123", false);
            assertThrows(IllegalStateException.class, () -> notifier.indexNotice(notice(101L)));
        }
    }

    @Test
    void indexNotice_noPassword_noAuthHeader() throws Exception {
        try (RawHttpServer server = new RawHttpServer(201, "{\"result\":\"created\"}")) {
            EsExternalNoticeIndexNotifier notifier =
                    newNotifier(server.url(), "elastic", "", false);
            notifier.indexNotice(notice(102L));
            assertTrue(!server.headers.toLowerCase().contains("authorization:"),
                    "密码留空时不带认证头");
        }
    }

}
