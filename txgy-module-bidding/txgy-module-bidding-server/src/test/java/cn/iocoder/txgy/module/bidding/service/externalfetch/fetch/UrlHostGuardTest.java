package cn.iocoder.txgy.module.bidding.service.externalfetch.fetch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 下载目标主机守卫单测：协议白名单/主机相等与子域/多域名 CSV/裸域名填写/平台文件豁免/内网与异域拒绝。
 */
class UrlHostGuardTest {

    private static final String BASE = "https://qyzb.shccmg.com";
    private static final String ALLOWED = "ebidding.com.cn, https://cdn.example.org/attachments";

    @Test
    void allowSameHost() {
        assertTrue(UrlHostGuard.isExternalAllowed("https://qyzb.shccmg.com/cms/detail.html", BASE, null));
    }

    @Test
    void allowSubdomain() {
        assertTrue(UrlHostGuard.isExternalAllowed("https://file.qyzb.shccmg.com/x.pdf", BASE, null));
        assertTrue(UrlHostGuard.isExternalAllowed("https://a.b.qyzb.shccmg.com/x", BASE, null));
    }

    @Test
    void allowExtraHostsCsv() {
        assertTrue(UrlHostGuard.isExternalAllowed("https://ebidding.com.cn/notice/1.html", BASE, ALLOWED));
        // allowed_hosts 里填完整 URL 也按主机放行
        assertTrue(UrlHostGuard.isExternalAllowed("https://cdn.example.org/attachments/a.pdf", BASE, ALLOWED));
    }

    @Test
    void rejectForeignHost() {
        assertFalse(UrlHostGuard.isExternalAllowed("https://evil.example.com/x.html", BASE, ALLOWED));
        // 相似域名不误放（后缀域匹配必须带点）
        assertFalse(UrlHostGuard.isExternalAllowed("https://qyzb.shccmg.com.evil.com/x", BASE, null));
        assertFalse(UrlHostGuard.isExternalAllowed("https://myqyzb.shccmg.com.evil.io/x", BASE, null));
    }

    @Test
    void rejectInnerHost() {
        assertFalse(UrlHostGuard.isExternalAllowed("http://127.0.0.1:48080/admin-api/system/user/list", BASE, null));
        assertFalse(UrlHostGuard.isExternalAllowed("http://192.168.3.7:8080/x", BASE, null));
        assertFalse(UrlHostGuard.isExternalAllowed("http://localhost:8080/x", BASE, null));
    }

    @Test
    void rejectBadSchemeAndUnparsable() {
        assertFalse(UrlHostGuard.isExternalAllowed("javascript:alert(1)", BASE, ALLOWED));
        assertFalse(UrlHostGuard.isExternalAllowed("file:///etc/passwd", BASE, ALLOWED));
        assertFalse(UrlHostGuard.isExternalAllowed("", BASE, ALLOWED));
        assertFalse(UrlHostGuard.isExternalAllowed(null, BASE, ALLOWED));
    }

    @Test
    void internalFileUrlOnlyForOwnEndpoints() {
        assertTrue(UrlHostGuard.isInternalFileUrl("http://127.0.0.1:48080/admin-api/infra/file/4/get/bidding/x.html"));
        assertTrue(UrlHostGuard.isInternalFileUrl("http://host.docker.internal:48080/admin-api/infra/file/1/get/a.pdf"));
        assertFalse(UrlHostGuard.isInternalFileUrl("http://127.0.0.1:48080/admin-api/system/user/list"));
        assertFalse(UrlHostGuard.isInternalFileUrl("https://qyzb.shccmg.com/admin-api/infra/file/1/get/a.pdf"));
    }

    @Test
    void allowChineseEscapedUrl() {
        // 未编码中文（源站附件常见）按 UTF-8 转义解析后仍放行
        assertTrue(UrlHostGuard.isExternalAllowed("https://qyzb.shccmg.com/附件/招标文件.pdf", BASE, null));
    }

    @Test
    void bareDomainBaseUrl() {
        assertTrue(UrlHostGuard.isExternalAllowed("https://gx.e-bidding.org/bulletin/1.html", "gx.e-bidding.org", null));
        assertFalse(UrlHostGuard.isExternalAllowed("https://other.org/x", "gx.e-bidding.org", null));
    }
}
