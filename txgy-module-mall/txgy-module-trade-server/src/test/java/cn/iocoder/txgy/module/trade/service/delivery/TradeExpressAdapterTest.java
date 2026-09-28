package cn.iocoder.txgy.module.trade.service.delivery;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.trade.framework.delivery.config.TradeExpressProperties;
import cn.iocoder.txgy.module.trade.framework.delivery.core.client.dto.ExpressTrackQueryReqDTO;
import cn.iocoder.txgy.module.trade.framework.delivery.core.client.impl.kd100.Kd100ExpressClient;
import cn.iocoder.txgy.module.trade.framework.delivery.core.client.impl.kdniao.KdNiaoExpressClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** All provider HTTP bodies here are isolated fixtures, never real carrier requests. */
class TradeExpressAdapterTest {
    RestTemplate rest = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
    Kd100ExpressClient kd100 = new Kd100ExpressClient(rest,
            new TradeExpressProperties.Kd100Config().setCustomer("test-customer").setKey("test-secret"));
    KdNiaoExpressClient kdniao = new KdNiaoExpressClient(rest,
            new TradeExpressProperties.KdNiaoConfig().setBusinessId("test-business").setApiKey("test-secret"));
    ExpressTrackQueryReqDTO request() {
        return new ExpressTrackQueryReqDTO().setExpressCode("debangwuliu").setLogisticsNo("TEST123456").setPhone("13800000000");
    }
    void response(String body) {
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }
    String success100(String company, String waybill) {
        return """
                {"status":"200","state":"3","com":"%s","nu":"%s","data":[
                  {"time":"2026-09-17 12:00:00","context":"isolated signed fixture"}]}
                """.formatted(company, waybill);
    }
    @Test void kd100PreservesProviderSummaryAndEventWithoutSendingOptionalPhone() {
        server.expect(request -> {
            String body = ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString(StandardCharsets.UTF_8);
            assertFalse(body.contains("13800000000")); assertFalse(body.contains("test-secret"));
        }).andRespond(withSuccess(success100("debangwuliu", "TEST123456"), MediaType.APPLICATION_JSON));
        var result = kd100.getSnapshot(request());
        assertEquals("3", result.getRawStatus()); assertEquals(1, result.getEvents().size());
        assertEquals(12, result.getEvents().getFirst().getTime().getHour()); server.verify();
    }
    @Test void kd100LegacyTrackShapeContinuesWorking() {
        response(success100("debangwuliu", "TEST123456"));
        assertEquals("isolated signed fixture", kd100.getExpressTrackList(request()).getFirst().getContent()); server.verify();
    }
    @Test void kd100DifferentWaybillRejected() {
        response(success100("debangwuliu", "OTHER123456"));
        assertThrows(ServiceException.class, () -> kd100.getSnapshot(request()));
    }
    @Test void kd100DifferentCarrierRejected() {
        response(success100("other", "TEST123456"));
        assertThrows(ServiceException.class, () -> kd100.getSnapshot(request()));
    }
    @Test void kd100NoResultIsNotReportedAsProviderFailureOrPickup() {
        response("{\"status\":\"500\",\"result\":\"false\",\"message\":\"no result\"}");
        var result = kd100.getSnapshot(request()); assertTrue(result.getEvents().isEmpty()); assertNull(result.getRawStatus());
    }
    @Test void kd100ProviderMessageNeverReflected() {
        response("{\"status\":\"503\",\"result\":\"false\",\"message\":\"secret and private phone\"}");
        var error = assertThrows(ServiceException.class, () -> kd100.getSnapshot(request()));
        assertFalse(error.getMessage().contains("secret")); assertFalse(error.getMessage().contains("phone"));
    }
    @Test void kd100MalformedNullAndOversizedBodyFailSafely() {
        response("null"); assertThrows(RuntimeException.class, () -> kd100.getSnapshot(request()));
        server.reset(); response("x".repeat(1_000_001)); assertThrows(ServiceException.class, () -> kd100.getSnapshot(request()));
    }
    @Test void kdniaoPreservesCodeAndRejectsMismatchedWaybill() {
        response("""
                {"Success":true,"ShipperCode":"DEBANGWULIU","LogisticCode":"TEST123456","State":"2", "Traces":[]}
                """);
        assertEquals("2", kdniao.getSnapshot(request()).getRawStatus());
        server.reset(); response("""
                {"Success":true,"ShipperCode":"DEBANGWULIU","LogisticCode":"OTHER123456","State":"2", "Traces":[]}
                """);
        assertThrows(ServiceException.class, () -> kdniao.getSnapshot(request()));
    }
    @Test void kdniaoMissingSuccessAndProviderMessageFailSanitized() {
        response("{\"Reason\":\"private-secret\"}");
        assertFalse(assertThrows(ServiceException.class, () -> kdniao.getSnapshot(request())).getMessage().contains("private-secret"));
    }
}
