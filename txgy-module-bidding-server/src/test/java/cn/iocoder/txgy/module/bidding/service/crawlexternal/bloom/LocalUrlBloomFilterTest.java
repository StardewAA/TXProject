package cn.iocoder.txgy.module.bidding.service.crawlexternal.bloom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 单节点（Guava）布隆过滤测试：无假阴性、误报容错、幂等标记。
 */
class LocalUrlBloomFilterTest {

    @Test
    void unseenHash_neverReported() {
        LocalUrlBloomFilter bloom = new LocalUrlBloomFilter(1000, 0.01);
        for (int i = 0; i < 500; i++) {
            bloom.mark("seen-" + i);
        }
        for (int i = 0; i < 500; i++) {
            assertTrue(bloom.mightContain("seen-" + i));
        }
        assertFalse(bloom.mightContain("never-seen-key-abc"), "布隆无假阴性：未加入的 key 不应判定为见过");
    }

    @Test
    void mark_isIdempotentAndCountsElements() {
        LocalUrlBloomFilter bloom = new LocalUrlBloomFilter(100, 0.01);
        bloom.mark("h1");
        bloom.mark("h1");
        bloom.mark("h2");
        assertTrue(bloom.mightContain("h1"));
        assertTrue(bloom.approximateElementCount() <= 2);
    }

}
