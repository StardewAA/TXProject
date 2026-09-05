package cn.iocoder.txgy.module.bidding.service.crawlexternal.bloom;
// 规范依据：设计文档 v0.6 §二/§四；评审诉求：布隆过滤多节点（Redis 位图，各采集节点共享同一判定空间）
// 部署注意：
//   1) 需要 Redis（starter-redis 已由 starter-protection 传递进 classpath，key/value 均 String 序列化，
//      SETBIT/GETBIT 位操作不经过 value 序列化器）；连接参数走 spring.data.redis.* 标准配置；
//   2) 位图预算：M=2^24 位（Redis 惰性扩展，最大约 2MB/实例级 key），K=10 个哈希 —— 容量约 100 万 URL，
//      误报率 ~10^-3 量级；URL 规模再大请增大 bidding.ext-notice.bloom.bits（保持 2 的幂）并同步 K；
//   3) Redis 不可用时操作抛异常（fail-fast，避免各节点判定不一致）；正确性始终由 DB 唯一键兜底。

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 多节点布隆过滤（Redis SETBIT/GETBIT）：各采集/调度节点共享同一判定空间。
 *
 * <p>哈希：以 Guava murmur3_128 对 (urlHashHex + "|" + seed) 计算，取低 64 位 & (M-1) 得到位偏移；
 * 同一 key 计算 k 次独立偏移，无假阴性性质与单机一致。
 */
@Component
@ConditionalOnProperty(prefix = "bidding.ext-notice.bloom", name = "type",
        havingValue = "redis", matchIfMissing = false)
public class RedisUrlBloomFilter implements UrlBloomFilter {

    /** 位图默认规模（位）。2^24 ≈ 1600 万位；按 m/n=16、k=10 估算约支持 100 万 URL */
    private static final long DEFAULT_BITS = 1L << 24;
    /** 哈希函数个数（默认） */
    private static final int DEFAULT_K = 10;

    private final RedisTemplate<String, String> redisTemplate;
    private final String key;
    private final long bitsMask;
    private final int hashFunctions;

    public RedisUrlBloomFilter(RedisTemplate<String, String> redisTemplate,
                               @Value("${bidding.ext-notice.bloom.key:bidding:ext-notice:url-bloom}") String key,
                               @Value("${bidding.ext-notice.bloom.bits:16777216}") long bits,
                               @Value("${bidding.ext-notice.bloom.hash-functions:10}") int hashFunctions) {
        if (bits <= 0 || (bits & (bits - 1)) != 0) {
            throw new IllegalArgumentException("bidding.ext-notice.bloom.bits 必须是 2 的幂: " + bits);
        }
        this.redisTemplate = redisTemplate;
        this.key = key;
        this.bitsMask = bits - 1;
        this.hashFunctions = hashFunctions;
    }

    @Override
    public boolean mightContain(String urlHashHex) {
        for (int i = 0; i < hashFunctions; i++) {
            long offset = offset(urlHashHex, i);
            if (!Boolean.TRUE.equals(redisTemplate.opsForValue().getBit(key, offset))) {
                return false; // 任一位未置位 ⇒ 一定未见（无假阴性）
            }
        }
        return true; // 全部命中 ⇒ 可能见过（假阳性需 DB 复核）
    }

    @Override
    public void mark(String urlHashHex) {
        for (int i = 0; i < hashFunctions; i++) {
            redisTemplate.opsForValue().setBit(key, offset(urlHashHex, i), true);
        }
    }

    private long offset(String urlHashHex, int seed) {
        HashFunction hf = Hashing.murmur3_128(seed); // 不同 seed 派生独立哈希（Guava 支持 int seed）
        return hf.hashString(urlHashHex + "|" + seed, StandardCharsets.UTF_8).asLong() & bitsMask;
    }

}
