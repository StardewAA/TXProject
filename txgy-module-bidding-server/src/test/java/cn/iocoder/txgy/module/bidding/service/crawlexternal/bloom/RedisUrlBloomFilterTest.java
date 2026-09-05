package cn.iocoder.txgy.module.bidding.service.crawlexternal.bloom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 多节点（Redis 位图）布隆过滤测试：k 次 SETBIT 标记、任一位缺失即判未见（无假阴性）、
 * 全部命中才判可能见过（假阳性需 DB 复核）、位偏移限定在预算位图内、非法容量拒绝。
 */
@SuppressWarnings("unchecked")
class RedisUrlBloomFilterTest {

    private static final String KEY = "bidding:ext-notice:url-bloom";

    private RedisTemplate<String, String> redisTemplate;
    private ValueOperations<String, String> valueOps;
    private RedisUrlBloomFilter filter;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        filter = new RedisUrlBloomFilter(redisTemplate, KEY, 1L << 16, 7);
    }

    @Test
    void mark_setsKBitsWithinBudget() {
        filter.mark("hash-abc");

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Long> offsets = ArgumentCaptor.forClass(Long.class);
        verify(valueOps, times(7)).setBit(eq(KEY), offsets.capture(), eq(true));
        for (Long off : offsets.getAllValues()) {
            assertTrue(off >= 0 && off < (1L << 16), "位偏移必须落在预算位图内: " + off);
        }
    }

    @Test
    void mightContain_allBitsSet_returnsTrue() {
        when(valueOps.getBit(eq(KEY), anyLong())).thenReturn(Boolean.TRUE);
        assertTrue(filter.mightContain("hash-abc"), "全部位命中 ⇒ 可能见过（DB 复核）");
        verify(valueOps, times(7)).getBit(eq(KEY), anyLong());
    }

    @Test
    void mightContain_anyBitMissing_returnsFalse() {
        // k=1：唯一哈希位未置位 ⇒ 一定未见（无假阴性）
        filter = new RedisUrlBloomFilter(redisTemplate, KEY, 1L << 16, 1);
        when(valueOps.getBit(eq(KEY), anyLong())).thenReturn(Boolean.FALSE);
        assertFalse(filter.mightContain("never"), "任一位未置位 ⇒ 一定未见");
        verify(valueOps, times(1)).getBit(eq(KEY), anyLong());
    }

    @Test
    void mark_thenContains_pathsUseSameOffsets() {
        // k=1 时：mark 的置位偏移与 mightContain 查询的偏移一致（同一 offset 计算）
        filter = new RedisUrlBloomFilter(redisTemplate, KEY, 1L << 16, 1);
        filter.mark("u1");
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Long> setOff = ArgumentCaptor.forClass(Long.class);
        verify(valueOps).setBit(eq(KEY), setOff.capture(), eq(true));

        when(valueOps.getBit(KEY, setOff.getValue())).thenReturn(Boolean.TRUE);
        assertTrue(filter.mightContain("u1"));
        verify(valueOps).getBit(KEY, setOff.getValue());
    }

    @Test
    void nonPowerOfTwoBits_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new RedisUrlBloomFilter(redisTemplate, KEY, 1000, 7));
    }

}
