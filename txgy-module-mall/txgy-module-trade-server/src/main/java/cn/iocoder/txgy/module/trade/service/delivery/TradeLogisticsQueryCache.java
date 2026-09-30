package cn.iocoder.txgy.module.trade.service.delivery;

import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.module.trade.framework.delivery.config.TradeExpressProperties;
import cn.iocoder.txgy.module.trade.framework.delivery.core.client.dto.ExpressSnapshotRespDTO;
import lombok.Data;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;

/** Shared cache and quota; Redis failure must not create uncontrolled paid provider calls. */
@Service
public class TradeLogisticsQueryCache {
    private final StringRedisTemplate redis;
    private final TradeExpressProperties properties;
    private static final DefaultRedisScript<Long> BUDGET = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], 60) end
            if count > tonumber(ARGV[1]) then return 0 end
            return 1
            """, Long.class);

    public TradeLogisticsQueryCache(StringRedisTemplate redis, TradeExpressProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Data
    public static class Result {
        private ExpressSnapshotRespDTO snapshot;
        private LocalDateTime queriedTime;
        private boolean failed;
        private boolean cached;
        private int refreshAfterSeconds;
    }

    public Result query(String identity, String provider, String waybillIdentity, Supplier<ExpressSnapshotRespDTO> loader) {
        String key = "mall:logistics:query:" + digest(identity);
        try {
            String value = redis.opsForValue().get(key);
            if (value != null) {
                Result result = JsonUtils.parseObjectQuietly(value, Result.class);
                if (result != null) {
                    Long ttl = redis.getExpire(key);
                    return result.setCached(true).setRefreshAfterSeconds((int) Math.max(1L, ttl == null ? 30 : ttl));
                }
                return failure(); // Corrupt private cache: fail closed, never log raw content or call the provider.
            }
            // Retain this short lease rather than unconditional delete (which could delete a new owner's lease).
            if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key + ":lease", "1", Duration.ofSeconds(30)))) {
                return failure().setRefreshAfterSeconds(30);
            }
            int limit = Math.max(0, Math.min(properties.getMaxQueriesPerMinute(), 300));
            if (limit == 0 || !Long.valueOf(1).equals(redis.execute(BUDGET,
                    List.of("mall:logistics:budget:" + provider), Integer.toString(limit)))) {
                return failure().setRefreshAfterSeconds(60);
            }
            // Provider contract: >= 30 min per physical waybill, even across two orders/tenants.
            // Share only the cooldown, never one buyer's cached tracking payload with another buyer.
            String throttleKey = "mall:logistics:cooldown:" + digest(provider + "|" + waybillIdentity);
            if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(throttleKey, "1", Duration.ofSeconds(1800)))) {
                Long ttl = redis.getExpire(throttleKey);
                return failure().setRefreshAfterSeconds((int) Math.max(30L, ttl == null ? 1800 : ttl));
            }
            Result result;
            try {
                ExpressSnapshotRespDTO snapshot = loader.get();
                result = snapshot == null ? failure() : new Result().setSnapshot(snapshot)
                        .setQueriedTime(LocalDateTime.now()).setRefreshAfterSeconds(1800);
            } catch (RuntimeException ignored) {
                // Never reflect provider bodies, signatures, mobile numbers or credentials to logs/UI.
                result = failure();
            }
            result.setRefreshAfterSeconds(1800);
            redis.opsForValue().set(key, JsonUtils.toJsonString(result), Duration.ofSeconds(result.getRefreshAfterSeconds()));
            return result;
        } catch (RuntimeException unavailable) {
            return failure();
        }
    }

    private Result failure() {
        return new Result().setFailed(true).setRefreshAfterSeconds(30);
    }

    static String digest(String identity) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
