package cn.iocoder.txgy.module.bidding.service.crawlexternal;

import cn.iocoder.txgy.module.bidding.dal.dataobject.crawlexternal.CrawlTaskDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.crawlexternal.CrawlTaskMapper;
import cn.iocoder.txgy.module.bidding.enums.CrawlTaskStatusEnum;
import cn.iocoder.txgy.module.bidding.service.crawlexternal.CrawlTaskService.CrawlAttemptOutcome;
import cn.iocoder.txgy.module.bidding.service.crawlexternal.sink.CrawlDeadLetterSink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 采集任务编排单测（Mockito，无 Spring 上下文）：
 * urlHash 幂等去重、成功落 SUCCESS、3 次内指数退避重试、耗尽→死信转人工审核。
 */
class CrawlTaskServiceImplTest {

    private CrawlTaskMapper crawlTaskMapper;
    private CrawlDeadLetterSink sink;
    private CrawlTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        crawlTaskMapper = mock(CrawlTaskMapper.class);
        sink = mock(CrawlDeadLetterSink.class);
        service = new CrawlTaskServiceImpl();
        ReflectionTestUtils.setField(service, "crawlTaskMapper", crawlTaskMapper);
        ReflectionTestUtils.setField(service, "crawlDeadLetterSink", sink);
    }

    private CrawlTaskDO task(int attempt) {
        CrawlTaskDO t = new CrawlTaskDO();
        t.setId(5L);
        t.setUrl("https://example.com/1");
        t.setUrlHash("HASH-1");
        t.setAttempt(attempt);
        t.setStatus(CrawlTaskStatusEnum.PENDING.getStatus());
        return t;
    }

    @Test
    void createTaskIfAbsent_reusesPendingTask() {
        when(crawlTaskMapper.selectOne(any())).thenReturn(task(0));

        Long id = service.createTaskIfAbsent(1L, "S1", "https://example.com/1", "HASH-1", "GENERIC", null, 1);

        assertEquals(5L, id);
        verify(crawlTaskMapper, never()).insert(any(CrawlTaskDO.class));
    }

    @Test
    void createTaskIfAbsent_newUrl_insertsPending() {
        when(crawlTaskMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            CrawlTaskDO t = inv.getArgument(0);
            t.setId(9L);
            return 1;
        }).when(crawlTaskMapper).insert(any(CrawlTaskDO.class));

        Long id = service.createTaskIfAbsent(1L, "S1", "https://example.com/new", "HASH-NEW", "GENERIC", "P1", 1);

        assertEquals(9L, id);
        ArgumentCaptor<CrawlTaskDO> cap = ArgumentCaptor.forClass(CrawlTaskDO.class);
        verify(crawlTaskMapper).insert(cap.capture());
        assertEquals("HASH-NEW", cap.getValue().getUrlHash());
        assertEquals(CrawlTaskStatusEnum.PENDING.getStatus(), cap.getValue().getStatus());
        assertEquals(0, cap.getValue().getAttempt());
    }

    @Test
    void markDownloadSuccess_setsSuccessAndRef() {
        CrawlTaskDO t = task(0);
        when(crawlTaskMapper.selectById(5L)).thenReturn(t);

        service.markDownloadSuccess(5L, "fileId:pdf-1", "PDF");

        assertEquals(CrawlTaskStatusEnum.SUCCESS.getStatus(), t.getStatus());
        assertNotNull(t.getFinishedAt());
        assertTrue(t.getMessage().contains("fileId:pdf-1"));
        verify(crawlTaskMapper).updateById(t);
    }

    @Test
    void handleFailure_firstTwoTimes_scheduleBackoffRetry() {
        CrawlTaskDO t0 = task(0);
        when(crawlTaskMapper.selectById(5L)).thenReturn(t0);

        CrawlAttemptOutcome first = service.handleFailure(5L, "boom1");

        assertEquals(CrawlAttemptOutcome.RETRY_SCHEDULED, first);
        assertEquals(1, t0.getAttempt());
        assertEquals(CrawlTaskStatusEnum.PENDING.getStatus(), t0.getStatus());
        assertNotNull(t0.getNextRetryTime());
        assertTrue(t0.getMessage().contains("attempt=1"));
        verify(sink, never()).audit(any(CrawlTaskDO.class));
    }

    @Test
    void handleFailure_thirdTime_deadLetterAudited() {
        CrawlTaskDO t2 = task(2);
        when(crawlTaskMapper.selectById(5L)).thenReturn(t2);

        CrawlAttemptOutcome outcome = service.handleFailure(5L, "boom3");

        assertEquals(CrawlAttemptOutcome.DEAD_LETTER_AUDITED, outcome);
        assertEquals(3, t2.getAttempt());
        assertEquals(CrawlTaskStatusEnum.FAILED.getStatus(), t2.getStatus());
        assertTrue(t2.getMessage().contains("DLQ>audit"));
        assertNotNull(t2.getFinishedAt());
        verify(crawlTaskMapper).updateById(t2);
        ArgumentCaptor<CrawlTaskDO> auditCap = ArgumentCaptor.forClass(CrawlTaskDO.class);
        verify(sink, times(1)).audit(auditCap.capture());
        assertEquals(CrawlTaskStatusEnum.FAILED.getStatus(), auditCap.getValue().getStatus());
    }

    @Test
    void handleFailure_unknownTask_throws() {
        when(crawlTaskMapper.selectById(5L)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> service.handleFailure(5L, "x"));
    }

    // ===== 布隆判重短路（objective 4） =====

    private cn.iocoder.txgy.module.bidding.service.crawlexternal.bloom.UrlBloomFilter installBloom() {
        cn.iocoder.txgy.module.bidding.service.crawlexternal.bloom.UrlBloomFilter bloom =
                mock(cn.iocoder.txgy.module.bidding.service.crawlexternal.bloom.UrlBloomFilter.class);
        ReflectionTestUtils.setField(service, "urlBloomFilter", bloom);
        return bloom;
    }

    @Test
    void createTask_bloomUnseen_skipsDbAndInserts() {
        cn.iocoder.txgy.module.bidding.service.crawlexternal.bloom.UrlBloomFilter bloom = installBloom();
        when(bloom.mightContain("HASH-NEW")).thenReturn(false);
        doAnswer(inv -> {
            CrawlTaskDO t = inv.getArgument(0);
            t.setId(77L);
            return 1;
        }).when(crawlTaskMapper).insert(any(CrawlTaskDO.class));

        Long id = service.createTaskIfAbsent(1L, "S1", "https://example.com/new", "HASH-NEW", "GENERIC", null, 1);

        assertEquals(77L, id);
        verify(crawlTaskMapper, never()).selectOne(any());
        verify(bloom).mark("HASH-NEW");
    }

    @Test
    void createTask_bloomSeen_goesToDbReuse() {
        cn.iocoder.txgy.module.bidding.service.crawlexternal.bloom.UrlBloomFilter bloom = installBloom();
        when(bloom.mightContain("HASH-1")).thenReturn(true);
        when(crawlTaskMapper.selectOne(any())).thenReturn(task(0));

        Long id = service.createTaskIfAbsent(1L, "S1", "https://example.com/1", "HASH-1", "GENERIC", null, 1);

        assertEquals(5L, id);
        verify(crawlTaskMapper, never()).insert(any(CrawlTaskDO.class));
        verify(bloom).mark("HASH-1");
    }

    @Test
    void createTask_bloomUnseen_duplicateKeyRace_fallsBackToReuse() {
        cn.iocoder.txgy.module.bidding.service.crawlexternal.bloom.UrlBloomFilter bloom = installBloom();
        when(bloom.mightContain("HASH-1")).thenReturn(false);
        when(crawlTaskMapper.insert(any(CrawlTaskDO.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("uk_task_url_hash"));
        when(crawlTaskMapper.selectOne(any())).thenReturn(task(0));

        Long id = service.createTaskIfAbsent(1L, "S1", "https://example.com/1", "HASH-1", "GENERIC", null, 1);

        assertEquals(5L, id, "撞唯一键（重启窗口竞态）回退 DB 复用，正确性由 DB 兜底");
        verify(crawlTaskMapper, times(1)).insert(any(CrawlTaskDO.class));
        verify(crawlTaskMapper, times(1)).selectOne(any());
    }

}
