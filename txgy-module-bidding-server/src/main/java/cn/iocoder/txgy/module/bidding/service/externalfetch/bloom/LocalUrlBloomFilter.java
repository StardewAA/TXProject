package cn.iocoder.txgy.module.bidding.service.externalfetch.bloom;
// 规范依据：设计文档 v0.6 §二/§四；评审诉求：布隆过滤（单节点默认实现）
// 局限说明：Guava 位图为进程内实现 —— 重启丢失、多节点不共享；多实例部署请配置 bidding.ext-notice.bloom.type=redis（见 RedisUrlBloomFilter）

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 单节点布隆过滤（Guava）：默认实现。见 {@link UrlBloomFilter} 语义注释。
 */
@Component
@ConditionalOnProperty(prefix = "bidding.ext-notice.bloom", name = "type",
        havingValue = "local", matchIfMissing = true)
public class LocalUrlBloomFilter implements UrlBloomFilter {

    private static final long DEFAULT_EXPECTED_INSERTIONS = 1_000_000L;
    private static final double DEFAULT_FPP = 0.01D;

    private final BloomFilter<String> filter;

    public LocalUrlBloomFilter() {
        this(DEFAULT_EXPECTED_INSERTIONS, DEFAULT_FPP);
    }

    public LocalUrlBloomFilter(long expectedInsertions, double fpp) {
        this.filter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8), expectedInsertions, fpp);
    }

    @Override
    public boolean mightContain(String urlHashHex) {
        return filter.mightContain(urlHashHex);
    }

    @Override
    public void mark(String urlHashHex) {
        filter.put(urlHashHex);
    }

    /** 布隆估算的已插入元素数（观测/监控用，本地实现专有） */
    public long approximateElementCount() {
        return filter.approximateElementCount();
    }

}
