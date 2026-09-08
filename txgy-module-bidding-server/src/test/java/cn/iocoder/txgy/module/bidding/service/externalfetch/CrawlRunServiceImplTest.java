package cn.iocoder.txgy.module.bidding.service.externalfetch;
// 依据：批次落库：runNow 每次执行生成 bidding_fetch_job（RUNNING→SUCCESS/PARTIAL/FAILED + summaryJson），jobId 透传 runner。

import cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo.CrawlRunRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlJobDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlJobMapper;
import cn.iocoder.txgy.module.bidding.service.externalfetch.fetch.CrawlSourceRunner;
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
        ReflectionTestUtils.setField(service, "crawlTaskMapper", mock(cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlTaskMapper.class));
        ReflectionTestUtils.setField(service, "redisMQTemplate", mock(cn.iocoder.txgy.framework.mq.redis.core.RedisMQTemplate.class));
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
    void startupRecovery_orphanRunningJob_withoutTasks_markedFailed() {
        // 启动钩子（新语义）：无未终态 task 的孤儿 RUNNING job → FAILED；有 task 的保留（异步续跑）
        CrawlJobDO orphan = new CrawlJobDO();
        orphan.setId(99L);
        orphan.setStatus("RUNNING");
        cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlTaskMapper taskMapper =
                mock(cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlTaskMapper.class);
        ReflectionTestUtils.setField(service, "crawlTaskMapper", taskMapper);
        when(jobMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(orphan));
        when(taskMapper.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(0L);

        service.recoverInterruptedJobsOnStartup();

        // 孤儿 job（无 task）被置 FAILED（update(entity=null, wrapper)；null 实体需 isNull() 匹配）
        verify(jobMapper).update(isNull(), any());
    }

    @Test
    void startupRecovery_jobWithPendingTasks_keptRunning() {
        // 有未终态 task 的 RUNNING job：保留（task 恢复后消费端续跑并收尾）
        CrawlJobDO live = new CrawlJobDO();
        live.setId(98L);
        live.setStatus("RUNNING");
        cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlTaskMapper taskMapper =
                mock(cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlTaskMapper.class);
        ReflectionTestUtils.setField(service, "crawlTaskMapper", taskMapper);
        when(jobMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(live));
        when(taskMapper.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(3L);

        service.recoverInterruptedJobsOnStartup();

        verify(jobMapper, org.mockito.Mockito.never()).update(isNull(), any());
    }


    @Test
    void runNow_asyncMode_keepsJobRunning() {
        // 投递模式：summary.async=true 且有入队 → job 不在此收尾（保持 RUNNING，消费端 tryFinalizeJob）
        CrawlSourceRunner.RunSummary async = summary(5, 0);
        async.setAsync(true);
        when(runner.runSource(eq("QYZB"), any(), eq(false))).thenReturn(async);
        when(jobMapper.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(0L, 1L);

        CrawlRunRespVO vo = service.runNow("QYZB", "MANUAL");

        // 投递后只置 produced=true（不 finishJob 收尾——job 保持 RUNNING 等消费端）
        ArgumentCaptor<CrawlJobDO> producedCap = ArgumentCaptor.forClass(CrawlJobDO.class);
        verify(jobMapper, org.mockito.Mockito.times(1)).updateById(producedCap.capture());
        assertEquals(Boolean.TRUE, producedCap.getValue().getProduced());
        assertEquals("RUNNING", producedCap.getValue().getStatus()); // 未收尾（保持 RUNNING）
        assertEquals(5, vo.getTotalIngested());
    }

    @Test
    void runNow_asyncEmptyRun_finalizesSuccess() {
        // 空跑：async 且无入队无失败 → 直接收尾 SUCCESS（不留 RUNNING 孤儿）
        CrawlSourceRunner.RunSummary empty = summary(0, 0);
        empty.setAsync(true);
        when(runner.runSource(eq("QYZB"), any(), eq(false))).thenReturn(empty);
        when(jobMapper.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(0L, 1L);

        service.runNow("QYZB", "MANUAL");

        ArgumentCaptor<CrawlJobDO> cap = ArgumentCaptor.forClass(CrawlJobDO.class);
        verify(jobMapper, org.mockito.Mockito.times(2)).updateById(cap.capture()); // produced + 收尾
        List<CrawlJobDO> all = cap.getAllValues();
        assertEquals(Boolean.TRUE, all.get(0).getProduced()); // 先置 produced
        assertEquals("SUCCESS", all.get(1).getStatus());      // 空跑直接收尾
    }

    @Test
    void tryFinalizeJob_noPendingTasks_finalizesBatch() {
        cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlTaskMapper taskMapper =
                mock(cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlTaskMapper.class);
        ReflectionTestUtils.setField(service, "crawlTaskMapper", taskMapper);
        CrawlJobDO job = new CrawlJobDO();
        job.setId(77L);
        job.setStatus("RUNNING");
        job.setProduced(Boolean.TRUE); // 生产端已投递完成
        when(jobMapper.selectById(77L)).thenReturn(job);
        // selectCount 调用序：PENDING/RUNNING 计数(0) → SUCCESS 计数(5) → FAILED 计数(1)
        when(taskMapper.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(0L, 5L, 1L);

        service.tryFinalizeJob(77L);

        ArgumentCaptor<CrawlJobDO> cap = ArgumentCaptor.forClass(CrawlJobDO.class);
        verify(jobMapper).updateById(cap.capture());
        assertEquals("PARTIAL", cap.getValue().getStatus()); // 5 成功 1 失败 → PARTIAL
        assertTrue(cap.getValue().getSummaryJson().contains("\"totalIngested\":5"));
    }

    @Test
    void tryFinalizeJob_stillPending_skips() {
        cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlTaskMapper taskMapper =
                mock(cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlTaskMapper.class);
        ReflectionTestUtils.setField(service, "crawlTaskMapper", taskMapper);
        when(taskMapper.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(2L);

        service.tryFinalizeJob(77L);

        verify(jobMapper, org.mockito.Mockito.never()).updateById(any(CrawlJobDO.class));
    }


    @Test
    void tryFinalizeJob_notProduced_skips() {
        // 生产端仍在投递（produced=false）→ 不收尾（防提前收尾竞态）
        cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlTaskMapper taskMapper =
                mock(cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlTaskMapper.class);
        ReflectionTestUtils.setField(service, "crawlTaskMapper", taskMapper);
        CrawlJobDO job = new CrawlJobDO();
        job.setId(77L);
        job.setStatus("RUNNING");
        job.setProduced(Boolean.FALSE);
        when(jobMapper.selectById(77L)).thenReturn(job);

        service.tryFinalizeJob(77L);

        verify(jobMapper, org.mockito.Mockito.never()).updateById(any(CrawlJobDO.class));
    }

}
