package cn.iocoder.txgy.module.bidding.service.externalfetch;

// [依据] 外部信息获取 v1.0 §五：投递=落 task(PENDING 防重) + payload 轻量引用 + Redis 轻信号；全文生产端归档 FileApi。

import cn.iocoder.txgy.framework.mq.redis.core.RedisMQTemplate;
import cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlTaskMapper;
import cn.iocoder.txgy.module.bidding.service.externalfetch.mq.CrawlNoticeFetchMessage;
import cn.iocoder.txgy.module.bidding.service.externalfetch.parser.NoticeParseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrawlTaskDispatchServiceImplTest {

    private CrawlTaskService taskService;
    private CrawlTaskMapper taskMapper;
    private RedisMQTemplate mq;
    private CrawlTaskDispatchServiceImpl service;

    @BeforeEach
    void setUp() {
        taskService = mock(CrawlTaskService.class);
        taskMapper = mock(CrawlTaskMapper.class);
        mq = mock(RedisMQTemplate.class);
        service = new CrawlTaskDispatchServiceImpl();
        ReflectionTestUtils.setField(service, "crawlTaskService", taskService);
        ReflectionTestUtils.setField(service, "crawlTaskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "redisMQTemplate", mq);
    }

    private NoticeParseResult.ListEntry entry() {
        NoticeParseResult.ListEntry e = new NoticeParseResult.ListEntry();
        e.setTitle("某项目公告");
        e.setUrl("https://src.example.gov.cn/x/123.html");
        e.setDate("2026-09-01");
        e.setCategory("招标公告");
        return e;
    }

    @Test
    void enqueue_newTask_writesPayloadAndSendsSignal() {
        when(taskService.createTaskIfAbsent(any(), any(), any(), any(), any(), any(), any())).thenReturn(100L);
        CrawlTaskDO task = new CrawlTaskDO();
        task.setId(100L);
        when(taskMapper.selectById(100L)).thenReturn(task);
        when(mq.send(any(CrawlNoticeFetchMessage.class))).thenReturn(null);

        Long id = service.enqueue(9L, "QYZB", "QYZB_JSON", entry());

        assertEquals(100L, id);
        ArgumentCaptor<CrawlTaskDO> cap = ArgumentCaptor.forClass(CrawlTaskDO.class);
        verify(taskMapper).updateById(cap.capture());
        assertNotNull(cap.getValue().getPayloadJson());
        assertTrue(cap.getValue().getPayloadJson().contains("某项目公告"));
        assertTrue(cap.getValue().getPayloadJson().contains("https://src.example.gov.cn/x/123.html"));
        verify(mq).send(any(CrawlNoticeFetchMessage.class));
    }

    @Test
    void enqueue_taskWithPayloadAlready_reusesWithoutResend() {
        when(taskService.createTaskIfAbsent(any(), any(), any(), any(), any(), any(), any())).thenReturn(100L);
        CrawlTaskDO task = new CrawlTaskDO();
        task.setId(100L);
        task.setPayloadJson("{\"title\":\"existing\"}");
        when(taskMapper.selectById(100L)).thenReturn(task);

        Long id = service.enqueue(9L, "QYZB", "QYZB_JSON", entry());

        assertEquals(100L, id);
        verify(taskMapper, never()).updateById(any(CrawlTaskDO.class)); // 不覆盖既有 payload
        verify(mq, never()).send(any(CrawlNoticeFetchMessage.class)); // 复用任务不重发
    }

    @Test
    void enqueue_sendFailure_taskStillPending() {
        when(taskService.createTaskIfAbsent(any(), any(), any(), any(), any(), any(), any())).thenReturn(100L);
        CrawlTaskDO task = new CrawlTaskDO();
        task.setId(100L);
        when(taskMapper.selectById(100L)).thenReturn(task);
        when(mq.send(any(CrawlNoticeFetchMessage.class))).thenThrow(new RuntimeException("redis down"));

        Long id = service.enqueue(9L, "QYZB", "QYZB_JSON", entry());

        assertEquals(100L, id); // 消息失败不回滚：task 留在 PENDING，启动自愈可补发
    }
}