package cn.iocoder.txgy.module.bidding.service.noticeexternal.url;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlSourceDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlSourceMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    private void sourceWithAllowedHosts(String baseUrl, String allowedHosts) {
        CrawlSourceDO s = new CrawlSourceDO();
        s.setBaseUrl(baseUrl);
        s.setAllowedHosts(allowedHosts);
        s.setEnabled(true);
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

    @Test
    void resolve_unicodeUrl_passesAfterAsciiEscape() {
        // 审查修复：附件溯源 URL 常含未编码中文/空格，new URI() 解析失败不应误拒；
        // 返回值须为 ASCII 转义版（Location 头仅允许 ISO-8859-1，原样回写会抛 UnmappableCharacterException）
        source("https://ggzy.example.gov.cn", true);
        String chinese = "https://ggzy.example.gov.cn/trade/公告/采购文件.pdf";
        String resolved = service.resolve(chinese);
        assertTrue(resolved.contains("%E5%85%AC%E5%91%8A"), "中文应百分号转义: " + resolved);
        assertFalse(resolved.chars().anyMatch(c -> c >= 0x4E00 && c <= 0x9FFF), "返回值不含 CJK 字符");
        assertEquals("https://ggzy.example.gov.cn/trade/",
                resolved.substring(0, resolved.indexOf('%')), "host/前缀原样保留");
        String space = "https://ggzy.example.gov.cn/trade/2026 年大修.docx";
        assertTrue(service.resolve(space).contains("%20"), "空格应转义为 %20");
    }

    @Test
    void resolve_cdnInAllowedHosts_passes_othersRejected() {
        // 审查修复：源配置 allowed_hosts 放行附件 CDN/文件服务器（白名单 = base_url 域 ∪ allowed_hosts）
        sourceWithAllowedHosts("https://ggzy.example.gov.cn", "cdn.example.cn, files.example.cn");
        assertEquals("https://files.example.cn/dl/1.pdf",
                service.resolve("https://files.example.cn/dl/1.pdf"));
        assertEquals("https://cdn.example.cn/att/2", service.resolve("https://cdn.example.cn/att/2"));
        assertThrows(ServiceException.class,
                () -> service.resolve("https://other.example.net/phish"));
    }

}
