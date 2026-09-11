package cn.iocoder.txgy.module.bidding.service.externalfetch.schema;

// 规范依据：用户反馈 2026-09-11「表 bidding_crawl_download_gate 不存在 → 任务反复重试 → 无限循环」；
//   《招投标模块开发规范》§4.4（故障可见、不许静默降级）。
// 业务目标：锁住「缺爬虫关键表 → 停调度；缺无关表 / 表齐备 → 照常」这一行为，
//   防止以后有人把熔断逻辑改掉，或改了表清单忘了同步。

import cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.BiddingSchemaMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link BiddingSchemaHealthCheck} 单测：用 mock 的库表盘点结果驱动，不依赖真实数据库。
 */
class BiddingSchemaHealthCheckTest {

    @AfterEach
    void tearDown() {
        BiddingSchemaGuard.resetForTest(); // 静态状态，用例间必须复位
    }

    /** 构造自检组件：库表盘点返回给定清单 */
    private BiddingSchemaHealthCheck withExistingTables(List<String> existing) {
        BiddingSchemaMapper mapper = mock(BiddingSchemaMapper.class);
        when(mapper.selectExistingTables(anyCollection())).thenReturn(existing);
        BiddingSchemaHealthCheck check = new BiddingSchemaHealthCheck();
        ReflectionTestUtils.setField(check, "biddingSchemaMapper", mapper);
        return check;
    }

    /** 表清单去掉若干张 */
    private List<String> allTablesExcept(String... missing) {
        List<String> tables = new ArrayList<>(BiddingSchemaHealthCheck.ALL_TABLES);
        for (String m : missing) {
            tables.remove(m);
        }
        return tables;
    }

    @Test
    @DisplayName("业务表齐备 → 爬虫可用（不熔断）")
    void run_allTablesPresent_keepsCrawlUsable() {
        withExistingTables(BiddingSchemaHealthCheck.ALL_TABLES).run(null);

        assertTrue(BiddingSchemaGuard.isCrawlSchemaReady(), "表齐备不应熔断爬虫");
    }

    @Test
    @DisplayName("缺限速闸表 → 停爬虫，且原因点名缺哪张表（照抄即可修复）")
    void run_missingGateTable_stopsCrawl() {
        withExistingTables(allTablesExcept("bidding_crawl_download_gate")).run(null);

        assertFalse(BiddingSchemaGuard.isCrawlSchemaReady(), "缺限速闸表必须熔断爬虫");
        String reason = BiddingSchemaGuard.crawlUnavailableReason();
        assertNotNull(reason);
        assertTrue(reason.contains("bidding_crawl_download_gate"), "原因里必须点名缺哪张表：" + reason);
    }

    @Test
    @DisplayName("缺与爬虫无关的表（如备案表）→ 报错但不熔断爬虫")
    void run_missingNonCrawlTable_keepsCrawlUsable() {
        withExistingTables(allTablesExcept("bidding_filing")).run(null);

        assertTrue(BiddingSchemaGuard.isCrawlSchemaReady(), "缺无关表不应停爬虫（其它业务域照常）");
    }

    @Test
    @DisplayName("数据库不可用/权限不足 → 视为不可用，避免带伤空跑")
    void run_schemaQueryFails_stopsCrawl() {
        BiddingSchemaMapper mapper = mock(BiddingSchemaMapper.class);
        when(mapper.selectExistingTables(anyCollection())).thenThrow(new IllegalStateException("Access denied"));
        BiddingSchemaHealthCheck check = new BiddingSchemaHealthCheck();
        ReflectionTestUtils.setField(check, "biddingSchemaMapper", mapper);

        check.run(null);

        assertFalse(BiddingSchemaGuard.isCrawlSchemaReady());
        assertTrue(BiddingSchemaGuard.crawlUnavailableReason().contains("Access denied"));
    }

}
