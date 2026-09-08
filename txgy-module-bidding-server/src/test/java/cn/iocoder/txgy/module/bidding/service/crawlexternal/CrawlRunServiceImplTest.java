package cn.iocoder.txgy.module.bidding.service.crawlexternal;
// 依据：批次落库：runNow 每次执行生成 bidding_crawl_job（RUNNING→SUCCESS/PARTIAL/FAILED + summaryJson），jobId 透传 runner。

import cn.iocoder.txgy.module.bidding.controller.admin.crawlexternal.vo.CrawlRunRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.crawlexternal.CrawlJobDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.crawlexternal.CrawlJobMapper;
import cn.iocoder.txgy.module.bidding.service.crawlexternal.fetch.CrawlSourceRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrawlRunServiceImplTest {

    private CrawlSourceRunner runner;
    private CrawlJobMapper jobMapper;
    private CrawlRunServiceImpl service;
    private final List<String> insertSnapshot = new ArrayList<>();

    @BeforeEach
    void setUp() {
        runner = mock(CrawlSourceRunner.class);
        jobMapper = mock(CrawlJobMapper.class);
        service = new CrawlRunServiceImpl();
        ReflectionTestUtils.setField(service, "crawlSourceRunner", runner);
        ReflectionTestUtils.setField(service, "crawlJobMapper", jobMapper);
        insertSnapshot.clear();
        when(jobMapper.insert(any(CrawlJobDO.class))).thenAnswer(inv -> {
            CrawlJobDO j = inv.getArgument(0);
            insertSnapshot.add("status=" + j.getStatus() + ",trigger=" + j.getTriggerType() + ",code=" + j.getSourceCode());
            j.setId(77L);
            return 1;
        });
    }

    private CrawlSourceRunner.RunSummary summary(int ingested, int failed) {
        CrawlSourceRunner.RunSummary s = new CrawlSourceRunner.RunSummary();
        s.setSourceCode("QYZB");
        s.setStartedAt(LocalDateTime.now().minusSeconds(10));
        s.setFinishedAt(LocalDateTime.now());
        s.setTotalIngested(ingested);
        s.setTotalFailed(failed);
        CrawlSourceRunner.CategorySummary cs = new CrawlSourceRunner.CategorySummary();
        cs.setName("变更公告");
        cs.setFetched(ingested + failed);
        cs.setIngested(ingested);
        cs.setFailed(failed);
        s.setCategories(List.of(cs));
        return s;
    }

    @Test
    void runNow_persistsBatch_runningThenPartial_withSummaryAndJobIdPassed() {
        when(runner.runSource(eq("QYZB"), any())).thenReturn(summary(2, 1));

        CrawlRunRespVO vo = service.runNow("QYZB");

        verify(jobMapper, times(1)).insert(any(CrawlJobDO.class));
        assertEquals(1, insertSnapshot.size());
        assertEquals("status=RUNNING,trigger=MANUAL,code=QYZB", insertSnapshot.get(0));
        ArgumentCaptor<CrawlJobDO> updCap = ArgumentCaptor.forClass(CrawlJobDO.class);
        verify(jobMapper, times(1)).updateById(updCap.capture());
        CrawlJobDO updated = updCap.getValue();
        assertEquals("PARTIAL", updated.getStatus());
        assertNotNull(updated.getFinishedAt());
        assertNotNull(updated.getSummaryJson());
        assertTrue(updated.getSummaryJson().contains("\"totalFailed\":1"));
        assertTrue(updated.getSummaryJson().contains("\"categories\""));
        verify(runner, times(1)).runSource(eq("QYZB"), eq(77L));
        assertEquals(2, vo.getTotalIngested());
    }

    @Test
    void runNow_triggerSchedule_recordsScheduleBatch() {
        when(runner.runSource(eq("QYZB"), any())).thenReturn(summary(0, 0));

        service.runNow("QYZB", "SCHEDULE");

        verify(jobMapper).insert(any(CrawlJobDO.class));
        assertEquals(1, insertSnapshot.size());
        assertEquals("status=RUNNING,trigger=SCHEDULE,code=QYZB", insertSnapshot.get(0));
        ArgumentCaptor<CrawlJobDO> upd = ArgumentCaptor.forClass(CrawlJobDO.class);
        verify(jobMapper).updateById(upd.capture());
        assertEquals("SUCCESS", upd.getValue().getStatus());
    }

    @Test
    void runNow_whenRunnerFails_marksJobFailed_andRethrows() {
        when(runner.runSource(eq("QYZB"), any())).thenThrow(new IllegalStateException("fetch boom"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.runNow("QYZB"));

        assertEquals("fetch boom", ex.getMessage());
        ArgumentCaptor<CrawlJobDO> upd = ArgumentCaptor.forClass(CrawlJobDO.class);
        verify(jobMapper).updateById(upd.capture());
        assertEquals("FAILED", upd.getValue().getStatus());
        assertNotNull(upd.getValue().getSummaryJson());
    }

}
