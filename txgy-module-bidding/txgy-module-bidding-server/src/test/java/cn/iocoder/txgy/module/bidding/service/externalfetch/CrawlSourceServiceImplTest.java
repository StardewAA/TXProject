package cn.iocoder.txgy.module.bidding.service.externalfetch;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo.CrawlSourcePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo.CrawlSourceRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo.CrawlSourceSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlSourceDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlSourceMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlTaskMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalMapper;
import cn.iocoder.txgy.module.bidding.service.externalfetch.channel.ChannelRegistry;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void disable_sourceWithActiveNotices_allowed() {
        // 2026-09-25 merge 变更：停用=停止采集（调度器按 enabled 过滤），不再要求任务/公告归零；
        // 已展示 ACTIVE 公告的外链 /go 跳转按全部源白名单加载，不受停用影响（见 ExternalRedirectService）。
        when(sourceMapper.selectById(5L)).thenReturn(sourceDO(5L, "SRC-5"));
        when(taskMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(noticeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

        service.setSourceEnabled(5L, Boolean.FALSE);

        verify(sourceMapper).updateById(any(CrawlSourceDO.class));
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


    @Test
    void create_apiTypeWithoutApiConfig_rejected() {
        CrawlSourceSaveReqVO bad = req("SRC-API-1");
        bad.setFetchType("API"); // API 源缺 api_config_json → 拒绝（防漏配静默空跑）
        try {
            service.createSource(bad);
            fail("API 源缺接口配置应拒绝");
        } catch (ServiceException e) {
            assertEquals(1_060_106_027, e.getCode());
        }
        verify(sourceMapper, never()).insert(any(CrawlSourceDO.class));
    }

    @Test
    void create_apiTypeWithInvalidConfigJson_rejected() {
        CrawlSourceSaveReqVO bad = req("SRC-API-2");
        bad.setFetchType("API");
        bad.setApiConfigJson("not-json");
        try {
            service.createSource(bad);
            fail("api_config_json 非 JSON 对象应拒绝");
        } catch (ServiceException e) {
            assertEquals(1_060_106_027, e.getCode());
        }
        verify(sourceMapper, never()).insert(any(CrawlSourceDO.class));
    }

    @Test
    void create_unknownFetchType_rejected() {
        CrawlSourceSaveReqVO bad = req("SRC-API-3");
        bad.setFetchType("MQTT");
        try {
            service.createSource(bad);
            fail("未知获取方式应拒绝");
        } catch (ServiceException e) {
            assertEquals(1_060_106_027, e.getCode());
        }
        verify(sourceMapper, never()).insert(any(CrawlSourceDO.class));
    }

    @Test
    void create_apiTypeValid_insertsWithFields() {
        AtomicLong ids = new AtomicLong();
        doAnswer(inv -> {
            CrawlSourceDO s = inv.getArgument(0);
            s.setId(ids.incrementAndGet());
            return 1;
        }).when(sourceMapper).insert(any(CrawlSourceDO.class));
        CrawlSourceSaveReqVO ok = req("SRC-API-4");
        ok.setFetchType("API");
        ok.setApiConfigJson("{\"method\":\"POST\",\"url\":\"https://api.example.gov.cn/list\",\"rowsPath\":\"data\"}");

        Long id = service.createSource(ok);

        assertEquals(1L, id);
        ArgumentCaptor<CrawlSourceDO> captor = ArgumentCaptor.forClass(CrawlSourceDO.class);
        verify(sourceMapper).insert(captor.capture());
        assertEquals("API", captor.getValue().getFetchType());
        assertEquals("{\"method\":\"POST\",\"url\":\"https://api.example.gov.cn/list\",\"rowsPath\":\"data\"}", captor.getValue().getApiConfigJson());
    }

    @Test
    void create_fetchTypeOmitted_defaultsToCrawler() {
        AtomicLong ids = new AtomicLong();
        doAnswer(inv -> {
            CrawlSourceDO s = inv.getArgument(0);
            s.setId(ids.incrementAndGet());
            return 1;
        }).when(sourceMapper).insert(any(CrawlSourceDO.class));

        service.createSource(req("SRC-DEF")); // 未声明 fetchType → 默认 CRAWLER（向后兼容存量配置）

        ArgumentCaptor<CrawlSourceDO> captor = ArgumentCaptor.forClass(CrawlSourceDO.class);
        verify(sourceMapper).insert(captor.capture());
        assertEquals("CRAWLER", captor.getValue().getFetchType());
    }


    @Test
    void create_apiStructuredFields_assemblesApiConfig() {
        // 前端友好模式：填结构化字段（apiUrl/apiMethod/apiRowsPath/apiMappingJson...）→ 服务端组装 api_config_json
        AtomicLong ids = new AtomicLong();
        doAnswer(inv -> {
            CrawlSourceDO s = inv.getArgument(0);
            s.setId(ids.incrementAndGet());
            return 1;
        }).when(sourceMapper).insert(any(CrawlSourceDO.class));
        CrawlSourceSaveReqVO req = req("SRC-API-S1");
        req.setFetchType("API");
        req.setApiUrl("https://data.xxx.gov.cn/gateway/api/1/tenders");
        req.setApiMethod("GET");
        req.setApiRowsPath("data.list");
        req.setApiMappingJson("{\"title\":\"notice_name\",\"url\":\"url\",\"date\":\"publish_time\"}");

        Long id = service.createSource(req);

        assertEquals(1L, id);
        ArgumentCaptor<CrawlSourceDO> cap = ArgumentCaptor.forClass(CrawlSourceDO.class);
        verify(sourceMapper).insert(cap.capture());
        String cfg = cap.getValue().getApiConfigJson();
        assertNotNull(cfg);
        assertTrue(cfg.contains("\"url\":\"https://data.xxx.gov.cn/gateway/api/1/tenders\""));
        assertTrue(cfg.contains("\"method\":\"GET\""));
        assertTrue(cfg.contains("\"rowsPath\":\"data.list\""));
        assertTrue(cfg.contains("\"notice_name\""));
    }

    @Test
    void create_apiStructuredMissingUrl_rejected() {
        // API 源结构化模式但缺 apiUrl → 拒绝（防漏配静默空跑）
        CrawlSourceSaveReqVO req = req("SRC-API-S2");
        req.setFetchType("API");
        req.setApiRowsPath("rows");
        try {
            service.createSource(req);
            fail("API 源缺接口地址应拒绝");
        } catch (ServiceException e) {
            assertEquals(1_060_106_027, e.getCode());
        }
        verify(sourceMapper, never()).insert(any(CrawlSourceDO.class));
    }

    @Test
    void getSource_structuredFieldsFilled() {
        // 回显：api_config_json 拆成结构化字段（前端表单回显）
        CrawlSourceDO source = sourceDO(9L, "SRC-API-S3");
        source.setFetchType("API");
        source.setApiConfigJson("{\"method\":\"POST\",\"url\":\"https://api.example.gov.cn/list\","
                + "\"headers\":{\"X-Client-Id\":\"tok1\"},\"rowsPath\":\"res.rows\",\"pageSize\":20,"
                + "\"mapping\":{\"title\":\"title\",\"url\":\"url\",\"date\":\"publishDate\"}}");
        when(sourceMapper.selectById(9L)).thenReturn(source);

        CrawlSourceRespVO resp = service.getSource(9L);

        assertEquals("https://api.example.gov.cn/list", resp.getApiUrl());
        assertEquals("POST", resp.getApiMethod());
        assertEquals("res.rows", resp.getApiRowsPath());
        assertEquals(Integer.valueOf(20), resp.getApiPageSize());
        assertEquals("{\"X-Client-Id\":\"tok1\"}", resp.getApiHeadersJson());
        assertNotNull(resp.getApiMappingJson());
        assertEquals("{\"title\":\"title\",\"url\":\"url\",\"date\":\"publishDate\"}", resp.getApiMappingJson());
    }

}
