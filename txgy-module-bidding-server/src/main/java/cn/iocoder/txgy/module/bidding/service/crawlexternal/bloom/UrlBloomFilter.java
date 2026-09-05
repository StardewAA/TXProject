package cn.iocoder.txgy.module.bidding.service.crawlexternal.bloom;
// 规范依据：设计文档 v0.6 §二/§四（增量/去重判重）；安全评审/性能诉求：布隆过滤（本仓此前无布隆实现，classpath 已有 guava）
// 局限说明：Guava 位图为进程内实现 —— 重启丢失、多节点不共享；多实例部署请替换为 Redis Bit/Bloom（starter-redis 已含 redisson/jedis，可平滑替换）

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * URL 指纹（SHA-256 hex）布隆过滤器：采集判重前置缓存。
 *
 * <p>语义（正确性由 DB 唯一键兜底，本组件只做加速）：
 * <ul>
 *   <li>{@link #mightContain} 返回 false ⇒ 一定未见过（布隆无假阴性）→ 可免 DB 查询直插；</li>
 *   <li>返回 true ⇒ “可能”见过（有假阳性）→ 仍走 DB 复核，不直接跳过（避免误丢公告）；</li>
 *   <li>插入撞唯一键（跨进程/重启窗口竞态）→ 由调用方回退 DB 复用，见 CrawlTaskService.createTaskIfAbsent。</li>
 * </ul>
 */
@Component
public class UrlBloomFilter {

    /** 默认预期容量与误报率（可随 yml 配置注入后调整） */
    private static final long DEFAULT_EXPECTED_INSERTIONS = 1_000_000L;
    private static final double DEFAULT_FPP = 0.01D;

    private final BloomFilter<String> filter;

    public UrlBloomFilter() {
        this(DEFAULT_EXPECTED_INSERTIONS, DEFAULT_FPP);
    }

    public UrlBloomFilter(long expectedInsertions, double fpp) {
        this.filter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8), expectedInsertions, fpp);
    }

    /** 可能见过（false=一定未见；true=需 DB 复核） */
    public boolean mightContain(String urlHashHex) {
        return filter.mightContain(urlHashHex);
    }

    /** 标记已见（成功入库/复用后调用） */
    public void mark(String urlHashHex) {
        filter.put(urlHashHex);
    }

    /** 布隆估算的已插入元素数（观测/监控用） */
    public long approximateElementCount() {
        return filter.approximateElementCount();
    }

}
