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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
        // 守卫按调用顺序返回：doRunNow 先查 RUNNING（第 1 次）再查 SUCCESS（第 2 次）。
        // 默认：无 RUNNING（0）、有 1 次 SUCCESS（1）→ 非首次 → 增量 full=false
        when(jobMapper.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(0L, 1L);
        when(runner.runSource(eq("QYZB"), any(), eq(false))).thenReturn(summary(0, 0));
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
        when(runner.runSource(eq("QYZB"), any(), eq(false))).thenReturn(summary(2, 1));

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
        verify(runner, times(1)).runSource(eq("QYZB"), eq(77L), eq(false));
        assertEquals(2, vo.getTotalIngested());
    }

    @Test
    void runNow_triggerSchedule_recordsScheduleBatch() {
        when(runner.runSource(eq("QYZB"), any(), eq(false))).thenReturn(summary(0, 0));

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
        when(runner.runSource(eq("QYZB"), any(), eq(false))).thenThrow(new IllegalStateException("fetch boom"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.runNow("QYZB"));

        assertEquals("fetch boom", ex.getMessage());
        ArgumentCaptor<CrawlJobDO> upd = ArgumentCaptor.forClass(CrawlJobDO.class);
        verify(jobMapper).updateById(upd.capture());
        assertEquals("FAILED", upd.getValue().getStatus());
        assertNotNull(upd.getValue().getSummaryJson());
    }

    // ===== 2026-09-09 首跑全量 / 并发防重 / 崩溃自愈 =====

    @Test
    void runNow_firstEver_noSuccess_marksFirstFullAndRunsFullWindow() {
        // 该源从无 SUCCESS → firstFull=true（首次全量，回溯 fullLookbackDays 补历史）；无 RUNNING、无 SUCCESS
        when(jobMapper.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(0L, 0L);
        when(runner.runSource(eq("QYZB"), any(), eq(true))).thenReturn(summary(3, 0));

        CrawlRunRespVO vo = service.runNow("QYZB", "SCHEDULE");

        verify(runner, times(1)).runSource(eq("QYZB"), eq(77L), eq(true));
        assertEquals(3, vo.getTotalIngested());
        ArgumentCaptor<CrawlJobDO> upd = ArgumentCaptor.forClass(CrawlJobDO.class);
        verify(jobMapper).updateById(upd.capture());
        assertEquals("SUCCESS", upd.getValue().getStatus());
    }

    @Test
    void runNow_concurrentRunning_sameSource_skipsThisRound() {
        // 同源已有 RUNNING（其它实例/线程正在跑）→ 本轮跳过，不调用 runner，不落新批次
        when(jobMapper.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1L);

        CrawlRunRespVO vo = service.runNow("QYZB", "SCHEDULE");

        verify(runner, times(0)).runSource(any(), any(), anyBoolean());
        verify(jobMapper, times(0)).insert(any(CrawlJobDO.class));
        assertEquals(0, vo.getTotalIngested() == null ? 0 : vo.getTotalIngested());
    }

    @Test
    void runNow_afterCrash_noSuccess_stillFirstFull_retries() {
        // 模拟崩溃重启后：RUNNING 被启动钩子置 FAILED → 无 SUCCESS → 仍判首次 → full 重试（幂等补齐，不丢远历史）
        when(jobMapper.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(0L, 0L); // 无 RUNNING、无 SUCCESS → firstFull
        when(runner.runSource(eq("QYZB"), any(), eq(true))).thenReturn(summary(0, 0));

        service.runNow("QYZB", "SCHEDULE");

        verify(runner, times(1)).runSource(eq("QYZB"), eq(77L), eq(true));
    }

    @Test
    void startupRecovery_marksStaleRunning_asFailed() {
        // 启动钩子：残留 RUNNING 统一置 FAILED（否则 RUNNING 守卫永久跳过该源）
        when(jobMapper.update(isNull(), any())).thenReturn(2);

        service.recoverInterruptedJobsOnStartup();

        // 校验确实调用了 update 批量置 FAILED（update(entity=null, wrapper)）；null 实体需 isNull() 匹配
        verify(jobMapper).update(isNull(), any());
    }

}
