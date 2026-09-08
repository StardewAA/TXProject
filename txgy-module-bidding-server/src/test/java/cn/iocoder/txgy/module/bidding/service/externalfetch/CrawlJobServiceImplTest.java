package cn.iocoder.txgy.module.bidding.service.crawlexternal;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.crawlexternal.vo.CrawlJobPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.crawlexternal.vo.CrawlJobRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.crawlexternal.CrawlJobDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.crawlexternal.CrawlJobMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 采集批次查询单测（Mockito，无 Spring 上下文）。
 */
class CrawlJobServiceImplTest {

    private CrawlJobMapper jobMapper;
    private CrawlJobServiceImpl service;

    @BeforeEach
    void setUp() {
        jobMapper = mock(CrawlJobMapper.class);
        service = new CrawlJobServiceImpl();
        ReflectionTestUtils.setField(service, "crawlJobMapper", jobMapper);
    }

    @Test
    void getJobPage_mapsRow() {
        CrawlJobDO row = new CrawlJobDO();
        row.setId(10L);
        row.setSourceCode("SRC");
        row.setTriggerType("SCHEDULE");
        row.setStatus("PARTIAL");
        row.setSummaryJson("{\"total\":5}");
        Page<CrawlJobDO> page = new Page<>(1, 10);
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(jobMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<CrawlJobRespVO> result = service.getJobPage(new CrawlJobPageReqVO());

        assertEquals(1, result.getList().size());
        assertEquals("SRC", result.getList().get(0).getSourceCode());
        assertEquals("SCHEDULE", result.getList().get(0).getTriggerType());
        assertEquals("PARTIAL", result.getList().get(0).getStatus());
    }

    @Test
    void getJob_notExists_throws() {
        when(jobMapper.selectById(99L)).thenReturn(null);
        try {
            service.getJob(99L);
            fail("应抛批次不存在");
        } catch (ServiceException e) {
            assertEquals(1_060_106_024, e.getCode());
        }
    }

}
