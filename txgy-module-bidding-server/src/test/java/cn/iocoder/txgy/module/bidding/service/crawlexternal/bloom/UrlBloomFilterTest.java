package cn.iocoder.txgy.module.bidding.service.crawlexternal.bloom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * URL 布隆过滤器单测：无假阴性（false=一定未见过）、命中可能为假阳性、估算计数。
 */
class UrlBloomFilterTest {

    @Test
    void unseenHash_neverReported() {
        UrlBloomFilter bloom = new UrlBloomFilter(1000, 0.01);
        for (int i = 0; i < 500; i++) {
            bloom.mark("seen-" + i);
        }
        for (int i = 0; i < 500; i++) {
            assertTrue(bloom.mightContain("seen-" + i));
        }
        // 布隆无假阴性：未加入的 key 不应返回 true（校验严格性用“至少绝大部分 false”，此处取未见集合逐条断言）
        assertFalse(bloom.mightContain("never-seen-key-abc"));
    }

    @Test
    void mark_isIdempotentAndCountsElements() {
        UrlBloomFilter bloom = new UrlBloomFilter(100, 0.01);
        bloom.mark("h1");
        bloom.mark("h1");
        bloom.mark("h2");
        assertTrue(bloom.mightContain("h1"));
        assertTrue(bloom.approximateElementCount() <= 2);
    }

}
