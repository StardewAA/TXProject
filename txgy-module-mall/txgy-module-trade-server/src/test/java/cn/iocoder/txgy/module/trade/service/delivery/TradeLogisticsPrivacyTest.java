package cn.iocoder.txgy.module.trade.service.delivery;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.module.trade.framework.delivery.config.TradeExpressProperties;
import cn.iocoder.txgy.module.trade.framework.delivery.core.client.dto.ExpressSnapshotRespDTO;
import cn.iocoder.txgy.module.trade.framework.delivery.core.client.dto.ExpressTrackQueryReqDTO;
import cn.iocoder.txgy.module.trade.framework.delivery.core.client.impl.kd100.Kd100ExpressClient;
import cn.iocoder.txgy.module.trade.framework.delivery.core.client.impl.kdniao.KdNiaoExpressClient;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Captures both utility parser and provider loggers at DEBUG so malformed sensitive content stays private. */
class TradeLogisticsPrivacyTest {
    private static final String SENTINEL = "PRIVATE-PHONE-AND-ADDRESS-DO-NOT-LOG";
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private final List<Logger> loggers = new ArrayList<>();
    private final List<Level> levels = new ArrayList<>();

    @BeforeEach void startCapture() {
        events.start();
        for (Class<?> type : List.of(JsonUtils.class, Kd100ExpressClient.class, KdNiaoExpressClient.class, TradeLogisticsQueryCache.class)) {
            Logger logger = (Logger) LoggerFactory.getLogger(type);
            loggers.add(logger); levels.add(logger.getLevel()); logger.setLevel(Level.DEBUG); logger.addAppender(events);
        }
    }
    @AfterEach void stopCapture() {
        for (int i = 0; i < loggers.size(); i++) { loggers.get(i).detachAppender(events); loggers.get(i).setLevel(levels.get(i)); }
        events.stop();
    }
    void assertPrivate() {
        assertTrue(events.list.stream().noneMatch(event -> event.getFormattedMessage().contains(SENTINEL)));
        assertTrue(events.list.stream().noneMatch(event -> event.getThrowableProxy() != null
                && event.getThrowableProxy().getMessage() != null && event.getThrowableProxy().getMessage().contains(SENTINEL)));
        assertTrue(events.list.stream().noneMatch(event -> event.getLoggerName().equals(JsonUtils.class.getName())));
    }
    @Test void malformedProviderBodiesDoNotLogCredentialsOrTrackingPayload() {
        RestTemplate rest = new RestTemplate(); MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
        var request = new ExpressTrackQueryReqDTO().setExpressCode("debangwuliu").setLogisticsNo("TEST123456");
        server.expect(anything()).andRespond(withSuccess("{\"private\":\"" + SENTINEL, MediaType.APPLICATION_JSON));
        var kd100 = new Kd100ExpressClient(rest, new TradeExpressProperties.Kd100Config().setCustomer("fixture").setKey("fixture"));
        assertThrows(ServiceException.class, () -> kd100.getSnapshot(request)); server.verify();
        server.reset(); server.expect(anything()).andRespond(withSuccess("{\"private\":\"" + SENTINEL, MediaType.APPLICATION_JSON));
        var kdniao = new KdNiaoExpressClient(rest, new TradeExpressProperties.KdNiaoConfig().setBusinessId("fixture").setApiKey("fixture"));
        assertThrows(ServiceException.class, () -> kdniao.getSnapshot(request)); server.verify(); assertPrivate();
    }
    @Test void corruptCacheNeverLogsPrivateDataOrBypassesIntoExternalQuery() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn("{\"private\":\"" + SENTINEL);
        Supplier<ExpressSnapshotRespDTO> provider = mock(Supplier.class);
        assertTrue(new TradeLogisticsQueryCache(redis, new TradeExpressProperties())
                .query("tenant/order", "kd100", "carrier/no", provider).isFailed());
        verifyNoInteractions(provider); assertPrivate();
    }
}
