package cn.iocoder.txgy.module.bidding.service.noticeexternal.url;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.bidding.dal.dataobject.crawlexternal.CrawlSourceDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.crawlexternal.CrawlSourceMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 外链中转守卫测试（安全评审意见 2：开放重定向/伪协议拦截 + 域名白名单含子域）。
 */
class ExternalRedirectServiceTest {

    private CrawlSourceMapper crawlSourceMapper;
    private ExternalRedirectService service;

    @BeforeEach
    void setUp() {
        crawlSourceMapper = mock(CrawlSourceMapper.class);
        service = new ExternalRedirectService();
        ReflectionTestUtils.setField(service, "crawlSourceMapper", crawlSourceMapper);
    }

    private void source(String baseUrl, boolean enabled) {
        CrawlSourceDO s = new CrawlSourceDO();
        s.setBaseUrl(baseUrl);
        s.setEnabled(enabled);
        when(crawlSourceMapper.selectList(any())).thenReturn(List.of(s));
    }

    @Test
    void resolve_allowedHost_passes() {
        source("https://ggzy.example.gov.cn", true);
        assertEquals("https://ggzy.example.gov.cn/trade/1",
                service.resolve("https://ggzy.example.gov.cn/trade/1"));
    }

    @Test
    void resolve_subdomainOfAllowedHost_passes() {
        source("https://ggzy.example.gov.cn", true);
        assertEquals("https://sub.ggzy.example.gov.cn/x",
                service.resolve("https://sub.ggzy.example.gov.cn/x"));
    }

    @Test
    void resolve_unrelatedHost_rejected() {
        source("https://ggzy.example.gov.cn", true);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.resolve("https://evil.example.com/phish"));
        assertEquals(ErrorCodeConstants.EXTERNAL_REDIRECT_FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void resolve_disabledSourceNotInWhitelist_rejected() {
        // enabled=TRUE 过滤由 Mapper 查询承担：禁用源不会被 selectList 返回 → 白名单为空 → 拒绝
        when(crawlSourceMapper.selectList(any())).thenReturn(java.util.List.of());
        assertThrows(ServiceException.class,
                () -> service.resolve("https://ggzy.example.gov.cn/trade/1"));
    }

    @Test
    void resolve_javascriptOrDataScheme_rejected() {
        source("https://ggzy.example.gov.cn", true);
        assertThrows(ServiceException.class, () -> service.resolve("javascript:alert(1)"));
        assertThrows(ServiceException.class, () -> service.resolve("data:text/html;base64,PGI+"));
    }

    @Test
    void resolve_blankOrUnparseable_rejected() {
        assertThrows(ServiceException.class, () -> service.resolve(""));
        assertThrows(ServiceException.class, () -> service.resolve("not a url at all"));
    }

}
