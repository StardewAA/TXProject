package cn.iocoder.txgy.module.bidding.service.crawlexternal;

import cn.iocoder.txgy.module.bidding.dal.dataobject.crawlexternal.CrawlTaskDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.crawlexternal.CrawlTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.dao.DuplicateKeyException;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 采集任务判重/二轮调度语义单测（Mockito，无 Spring 上下文）。
 *
 * <p>审查修复高1b：任务表唯一键扩为 (job_id, url_hash, source_code, deleted) 后——\n * 同一 URL 跨批次（job 不同）落新任务行（二轮调度可行）；同批次重复 URL 仍去重（复用既有行，不抛裸异常）。
 */
class CrawlTaskServiceImplTest {

    private CrawlTaskMapper crawlTaskMapper;
    private CrawlTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        crawlTaskMapper = mock(CrawlTaskMapper.class);
        service = new CrawlTaskServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "crawlTaskMapper", crawlTaskMapper);
        // urlBloomFilter 不注入（null）→ 走 DB 复核路径，语义等价布隆未命中
    }

    /** mock insert：按调用序分配自增 id；可切换为抛重复键（撞 uk 模拟） */
    private void stubInsert(AtomicLong ids, boolean dupOnSecondCall) {
        AtomicLong calls = new AtomicLong();
        doAnswer(inv -> {
            if (dupOnSecondCall && calls.getAndIncrement() == 1) {
                throw new DuplicateKeyException("dup uk_task_job_url");
            }
            CrawlTaskDO task = inv.getArgument(0);
            task.setId(ids.incrementAndGet());
            return 1;
        }).when(crawlTaskMapper).insert(any(CrawlTaskDO.class));
    }

    @Test
    void sameUrl_crossJob_createsSeparateTaskPerJob() {
        // 历史 job=100 已产出任务（假设已 SUCCESS/FAILED 终态，PENDING/RUNNING 查询为空）→ 新 job=200 二轮调度可再建任务
        when(crawlTaskMapper.selectOne(ArgumentMatchers.any())).thenReturn(null);
        stubInsert(new AtomicLong(), false);

        Long first = service.createTaskIfAbsent(100L, "SRC", "https://src.example.gov.cn/trade/1",
                "hash-1", "CH", "P", 1);
        Long second = service.createTaskIfAbsent(200L, "SRC", "https://src.example.gov.cn/trade/1",
                "hash-1", "CH", "P", 1);

        assertNotEquals(first, second, "跨批次同 URL 应落新任务行（二轮调度不被历史行阻断）");
        verify(crawlTaskMapper, times(2)).insert(any(CrawlTaskDO.class));
    }

    @Test
    void sameUrl_sameJob_reusesExistingTaskInsteadOfDup() {
        // 同批次重复投递：第二次 create 撞 uk_task_job_url → 回查同 job+url 行复用，不抛裸异常（DB 兜底）
        CrawlTaskDO existing = new CrawlTaskDO();
        existing.setId(9L);
        AtomicLong q = new AtomicLong();
        when(crawlTaskMapper.selectOne(ArgumentMatchers.any())).thenAnswer(inv -> {
            // 调用序：①首投 PENDING/RUNNING 复核(null) → ②次投 PENDING/RUNNING 复核(null)
            //        → ③次投 insert 撞键后同 job+url 回查(existing)
            return q.getAndIncrement() == 2 ? existing : null;
        });
        // insert 调用序：首投成功（id=1）；次投撞唯一键抛 DuplicateKeyException
        AtomicLong inserts = new AtomicLong();
        doAnswer(inv -> {
            if (inserts.getAndIncrement() == 1) {
                throw new DuplicateKeyException("dup uk_task_job_url");
            }
            CrawlTaskDO task = inv.getArgument(0);
            task.setId(1L);
            return 1;
        }).when(crawlTaskMapper).insert(any(CrawlTaskDO.class));

        Long first = service.createTaskIfAbsent(100L, "SRC", "https://src.example.gov.cn/trade/2",
                "hash-2", "CH", "P", 1);
        Long again = service.createTaskIfAbsent(100L, "SRC", "https://src.example.gov.cn/trade/2",
                "hash-2", "CH", "P", 1);

        assertEquals(1L, first);
        assertEquals(9L, again, "同 job 重复 URL 复用既有行");
        verify(crawlTaskMapper, times(2)).insert(any(CrawlTaskDO.class));
    }

    @Test
    void nullJobId_normalizedToZero_avoidsUniqueKeyNullGap() {
        // 人工无批次抓取：jobId=null 归一 0L，唯一键全维度生效（MySQL 唯一键对 NULL 不拦截）
        when(crawlTaskMapper.selectOne(ArgumentMatchers.any())).thenReturn(null);
        stubInsert(new AtomicLong(), false);

        Long id = service.createTaskIfAbsent(null, "SRC", "https://src.example.gov.cn/trade/3",
                "hash-3", "CH", "P", 1);

        assertEquals(1L, id);
        verify(crawlTaskMapper, times(1)).insert(any(CrawlTaskDO.class));
    }

}
