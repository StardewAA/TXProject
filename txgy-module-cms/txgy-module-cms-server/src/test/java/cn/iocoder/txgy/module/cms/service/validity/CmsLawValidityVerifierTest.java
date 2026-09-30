package cn.iocoder.txgy.module.cms.service.validity;

import cn.iocoder.txgy.module.cms.config.CmsLawValidityCheckProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CmsLawValidityVerifierTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void reportsEffectiveNationalLawAsHealthy() throws Exception {
        CmsLawValidityVerifier verifier = verifierWithResponse(3, "中华人民共和国测试法");

        CmsLawValidityVerifier.Verification result = verifier.verify(
                "https://flk.npc.gov.cn/detail?id=test-law");

        assertThat(result.healthy()).isTrue();
        assertThat(result.remoteStatus()).isEqualTo("3");
        assertThat(result.remoteTitle()).isEqualTo("中华人民共和国测试法");
    }

    @Test
    void createsManualReviewSignalWhenRemoteStatusIsNotEffective() throws Exception {
        CmsLawValidityVerifier verifier = verifierWithResponse(1, "中华人民共和国测试法");

        CmsLawValidityVerifier.Verification result = verifier.verify(
                "https://flk.npc.gov.cn/detail?id=test-law");

        assertThat(result.healthy()).isFalse();
        assertThat(result.issueType()).isEqualTo(CmsLawValidityVerifier.IssueType.REMOTE_STATUS_REVIEW);
        assertThat(result.remoteStatus()).isEqualTo("1");
        assertThat(result.message()).contains("人工核验");
    }

    private CmsLawValidityVerifier verifierWithResponse(int status, String title) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/law-search/search/flfgDetails", exchange -> {
            byte[] body = ("{\"code\":200,\"data\":{\"title\":\"" + title
                    + "\",\"sxx\":" + status + "}}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        CmsLawValidityCheckProperties properties = new CmsLawValidityCheckProperties();
        properties.setNationalLawBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setRequestTimeoutSeconds(3);
        return new CmsLawValidityVerifier(properties);
    }
}
