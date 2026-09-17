package cn.iocoder.txgy.module.trade.service.delivery;

import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.module.trade.framework.delivery.config.TradeExpressProperties;
import cn.iocoder.txgy.module.trade.framework.delivery.core.client.dto.ExpressSnapshotRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TradeLogisticsQueryCacheTest {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    ValueOperations<String, String> values = mock(ValueOperations.class);
    TradeExpressProperties properties = new TradeExpressProperties();
    TradeLogisticsQueryCache cache = new TradeLogisticsQueryCache(redis, properties);
    Supplier<ExpressSnapshotRespDTO> loader = mock(Supplier.class);

    @BeforeEach void setup() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(redis.execute(any(RedisScript.class), anyList(), any(String.class))).thenReturn(1L);
        when(loader.get()).thenReturn(new ExpressSnapshotRespDTO().setRawStatus("3"));
    }
    @Test void successfulLookupCachedForThirtyMinutes() {
        var result = cache.query("tenant1/order2/carrier/no/mobile", "kd100", "carrier/no", loader);
        assertFalse(result.isFailed()); assertEquals(1800, result.getRefreshAfterSeconds()); assertNotNull(result.getQueriedTime());
        verify(loader).get(); verify(values).set(startsWith("mall:logistics:query:"), anyString(), eq(Duration.ofSeconds(1800)));
    }
    @Test void cacheHitNeverContactsProviderOrUsesBudget() {
        when(values.get(anyString())).thenReturn(JsonUtils.toJsonString(new TradeLogisticsQueryCache.Result()
                .setSnapshot(new ExpressSnapshotRespDTO().setRawStatus("3")).setQueriedTime(LocalDateTime.now())));
        when(redis.getExpire(anyString())).thenReturn(120L);
        var result = cache.query("identity", "kd100", "carrier/no", loader);
        assertTrue(result.isCached()); assertEquals(120, result.getRefreshAfterSeconds()); verifyNoInteractions(loader);
        verify(values, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }
    @Test void failureIsCachedAndNoProviderExceptionLeaks() {
        when(loader.get()).thenThrow(new IllegalStateException("secret-key private mobile"));
        var result = cache.query("identity", "kd100", "carrier/no", loader);
        assertTrue(result.isFailed()); assertNull(result.getSnapshot()); assertNull(result.getQueriedTime());
        verify(values).set(anyString(), argThat(text -> !text.contains("secret") && !text.contains("mobile")), eq(Duration.ofSeconds(1800)));
    }
    @Test void redisUnavailablePreventsProviderCall() {
        when(values.get(anyString())).thenThrow(new IllegalStateException("redis offline"));
        assertTrue(cache.query("identity", "kd100", "carrier/no", loader).isFailed()); verifyNoInteractions(loader);
    }
    @Test void missingLeasePreventsConcurrentProviderCall() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        assertTrue(cache.query("identity", "kd100", "carrier/no", loader).isFailed()); verifyNoInteractions(loader);
    }
    @Test void exceededBudgetPreventsProviderCall() {
        when(redis.execute(any(RedisScript.class), anyList(), any(String.class))).thenReturn(0L);
        assertEquals(60, cache.query("identity", "kd100", "carrier/no", loader).getRefreshAfterSeconds()); verifyNoInteractions(loader);
    }
    @Test void zeroBudgetDisablesProvider() {
        properties.setMaxQueriesPerMinute(0);
        assertTrue(cache.query("identity", "kd100", "carrier/no", loader).isFailed()); verifyNoInteractions(loader);
    }
    @Test void nullBudgetResultFailsClosed() {
        when(redis.execute(any(RedisScript.class), anyList(), any(String.class))).thenReturn(null);
        assertTrue(cache.query("identity", "kd100", "carrier/no", loader).isFailed()); verifyNoInteractions(loader);
    }
    @Test void nullProviderBodyBecomesCooldownFailure() {
        when(loader.get()).thenReturn(null);
        assertTrue(cache.query("identity", "kd100", "carrier/no", loader).isFailed());
        verify(values).set(anyString(), anyString(), eq(Duration.ofSeconds(1800)));
    }
    @Test void sameWaybillAcrossTenantAndOrderSharesOnlyCooldownNotPayload() {
        when(values.setIfAbsent(startsWith("mall:logistics:cooldown:"), eq("1"), eq(Duration.ofSeconds(1800)))).thenReturn(false);
        when(redis.getExpire(startsWith("mall:logistics:cooldown:"))).thenReturn(1700L);
        assertEquals(1700, cache.query("tenant2/order9", "kd100", "carrier/no", loader).getRefreshAfterSeconds());
        verifyNoInteractions(loader);
    }
    @Test void cacheIdentityIsNotPlaintextAndChangesAcrossTenantOrWaybill() {
        String a = TradeLogisticsQueryCache.digest("1|2|13800000000|TRACK-1");
        assertEquals(64, a.length()); assertFalse(a.contains("13800000000"));
        assertNotEquals(a, TradeLogisticsQueryCache.digest("2|2|13800000000|TRACK-1"));
        assertNotEquals(a, TradeLogisticsQueryCache.digest("1|2|13800000000|TRACK-2"));
    }
}
