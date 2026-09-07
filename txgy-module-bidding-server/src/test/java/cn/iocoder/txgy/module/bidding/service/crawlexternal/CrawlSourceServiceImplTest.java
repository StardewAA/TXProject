package cn.iocoder.txgy.module.bidding.service.crawlexternal;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.crawlexternal.vo.CrawlSourcePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.crawlexternal.vo.CrawlSourceRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.crawlexternal.vo.CrawlSourceSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.crawlexternal.CrawlSourceDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.crawlexternal.CrawlSourceMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.crawlexternal.CrawlTaskMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalMapper;
import cn.iocoder.txgy.module.bidding.service.crawlexternal.channel.ChannelRegistry;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 采集源配置管理单测（Mockito，无 Spring 上下文）：
 * 创建/代码唯一/URL 与通道校验/引用保护（停用/删除前置 ACTIVE 公告或未终态任务）。
 */
class CrawlSourceServiceImplTest {

    private CrawlSourceMapper sourceMapper;
    private CrawlTaskMapper taskMapper;
    private NoticeExternalMapper noticeMapper;
    private ChannelRegistry registry;
    private CrawlSourceServiceImpl service;

    @BeforeEach
    void setUp() {
        sourceMapper = mock(CrawlSourceMapper.class);
        taskMapper = mock(CrawlTaskMapper.class);
        noticeMapper = mock(NoticeExternalMapper.class);
        registry = mock(ChannelRegistry.class);
        service = new CrawlSourceServiceImpl();
        ReflectionTestUtils.setField(service, "crawlSourceMapper", sourceMapper);
        ReflectionTestUtils.setField(service, "crawlTaskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "noticeExternalMapper", noticeMapper);
        ReflectionTestUtils.setField(service, "channelRegistry", registry);
        when(registry.has(anyString())).thenReturn(true);
    }

    private CrawlSourceSaveReqVO req(String code) {
        CrawlSourceSaveReqVO req = new CrawlSourceSaveReqVO();
        req.setSourceCode(code);
        req.setSourceName("示例政务网");
        req.setBaseUrl("https://src.example.gov.cn");
        req.setChannelCode("GENERIC");
        req.setFetchIntervalSeconds(3600);
        req.setEnabled(Boolean.TRUE);
        return req;
    }

    private CrawlSourceDO sourceDO(Long id, String code) {
        CrawlSourceDO source = new CrawlSourceDO();
        source.setId(id);
        source.setSourceCode(code);
        source.setSourceName("示例政务网");
        source.setBaseUrl("https://src.example.gov.cn");
        source.setChannelCode("GENERIC");
        source.setEnabled(Boolean.TRUE);
        return source;
    }

    @Test
    void create_valid_insertsAndReturnsId() {
        AtomicLong ids = new AtomicLong();
        doAnswer(inv -> {
            CrawlSourceDO s = inv.getArgument(0);
            s.setId(ids.incrementAndGet());
            return 1;
        }).when(sourceMapper).insert(any(CrawlSourceDO.class));

        Long id = service.createSource(req("SRC-1"));

        assertEquals(1L, id);
        verify(sourceMapper).insert(ArgumentMatchers.any(CrawlSourceDO.class));
    }

    @Test
    void create_duplicateCode_throwsCodeDuplicate() {
        doAnswer(inv -> {
            throw new DuplicateKeyException("dup uk_source_code");
        }).when(sourceMapper).insert(any(CrawlSourceDO.class));

        try {
            service.createSource(req("SRC-2"));
            fail("应抛源代码重复");
        } catch (ServiceException e) {
            assertEquals(1_060_106_021, e.getCode());
        }
    }

    @Test
    void create_badUrl_rejected() {
        CrawlSourceSaveReqVO bad = req("SRC-3");
        bad.setBaseUrl("ftp://not-http.example.com");
        try {
            service.createSource(bad);
            fail("应拒绝非 http(s) URL");
        } catch (ServiceException e) {
            assertEquals(1_060_106_027, e.getCode());
        }
        verify(sourceMapper, never()).insert(any(CrawlSourceDO.class));
    }

    @Test
    void create_unknownChannel_rejected() {
        when(registry.has("GENERIC")).thenReturn(false);
        try {
            service.createSource(req("SRC-4"));
            fail("应拒绝未注册通道");
        } catch (ServiceException e) {
            assertEquals(1_060_106_027, e.getCode());
        }
        verify(sourceMapper, never()).insert(any(CrawlSourceDO.class));
    }

    @Test
    void update_sourceCodeImmutable_rejected() {
        when(sourceMapper.selectById(5L)).thenReturn(sourceDO(5L, "SRC-5"));
        CrawlSourceSaveReqVO changed = req("SRC-6");
        changed.setId(5L);
        try {
            service.updateSource(changed);
            fail("源代码不可修改");
        } catch (ServiceException e) {
            assertEquals(1_060_106_028, e.getCode());
        }
        verify(sourceMapper, never()).updateById(any(CrawlSourceDO.class));
    }

    @Test
    void disable_sourceWithActiveNotices_rejected() {
        when(sourceMapper.selectById(5L)).thenReturn(sourceDO(5L, "SRC-5"));
        when(taskMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(noticeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

        try {
            service.setSourceEnabled(5L, Boolean.FALSE);
            fail("停用被 ACTIVE 公告引用源应拒绝");
        } catch (ServiceException e) {
            assertEquals(1_060_106_022, e.getCode());
        }
        verify(sourceMapper, never()).updateById(any(CrawlSourceDO.class));
    }

    @Test
    void disable_sourceIdle_ok() {
        CrawlSourceDO source = sourceDO(5L, "SRC-5");
        when(sourceMapper.selectById(5L)).thenReturn(source);
        when(taskMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(noticeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        service.setSourceEnabled(5L, Boolean.FALSE);

        assertEquals(Boolean.FALSE, source.getEnabled());
        verify(sourceMapper).updateById(source);
    }

    @Test
    void delete_sourceWithRunningTasks_rejected() {
        when(sourceMapper.selectById(5L)).thenReturn(sourceDO(5L, "SRC-5"));
        when(taskMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        try {
            service.deleteSource(5L);
            fail("删除被未终态任务引用源应拒绝");
        } catch (ServiceException e) {
            assertEquals(1_060_106_022, e.getCode());
        }
        verify(sourceMapper, never()).deleteById(any());
    }

    @Test
    void page_mapsRow() {
        CrawlSourceDO row = sourceDO(7L, "SRC-7");
        Page<CrawlSourceDO> page = new Page<>(1, 10);
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(sourceMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<CrawlSourceRespVO> result = service.getSourcePage(new CrawlSourcePageReqVO());

        assertEquals(1, result.getList().size());
        assertEquals("SRC-7", result.getList().get(0).getSourceCode());
        assertEquals("https://src.example.gov.cn", result.getList().get(0).getBaseUrl());
    }

    @Test
    void getSource_notExists_throws() {
        when(sourceMapper.selectById(99L)).thenReturn(null);
        try {
            service.getSource(99L);
            fail("应抛采集源不存在");
        } catch (ServiceException e) {
            assertEquals(1_060_106_010, e.getCode());
        }
    }

}
