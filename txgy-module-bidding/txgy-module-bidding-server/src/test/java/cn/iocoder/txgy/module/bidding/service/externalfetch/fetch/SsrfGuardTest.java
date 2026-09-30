package cn.iocoder.txgy.module.bidding.service.externalfetch.fetch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SSRF 防护金标用例（后端审查 B1：下载链路无 host/内网校验）。
 *
 * <p>IP 字面量解析不依赖 DNS，可离线稳定运行；一旦有人放宽 {@link SsrfGuard} 的判定，这里会立刻红。
 */
class SsrfGuardTest {

    @Test
    void blocksLoopbackAnyLinkLocalSiteLocalMulticast() throws Exception {
        assertTrue(SsrfGuard.isBlockedAddress(InetAddress.getByName("127.0.0.1")));
        assertTrue(SsrfGuard.isBlockedAddress(InetAddress.getByName("0.0.0.0")));
        assertTrue(SsrfGuard.isBlockedAddress(InetAddress.getByName("169.254.169.254")), "云元数据地址必须拒");
        assertTrue(SsrfGuard.isBlockedAddress(InetAddress.getByName("10.1.2.3")));
        assertTrue(SsrfGuard.isBlockedAddress(InetAddress.getByName("172.16.5.5")));
        assertTrue(SsrfGuard.isBlockedAddress(InetAddress.getByName("192.168.1.1")));
        assertTrue(SsrfGuard.isBlockedAddress(InetAddress.getByName("224.0.0.1")));
    }

    @Test
    void blocksIpv6LoopbackLinkLocalAndUniqueLocal() throws Exception {
        assertTrue(SsrfGuard.isBlockedAddress(InetAddress.getByName("::1")));
        assertTrue(SsrfGuard.isBlockedAddress(InetAddress.getByName("fe80::1")));
        assertTrue(SsrfGuard.isBlockedAddress(InetAddress.getByName("fc00::1")));
        assertTrue(SsrfGuard.isBlockedAddress(InetAddress.getByName("fd12:3456::1")));
    }

    @Test
    void blocksIpv4MappedIpv6() throws Exception {
        assertTrue(SsrfGuard.isBlockedAddress(InetAddress.getByName("::ffff:127.0.0.1")));
        assertTrue(SsrfGuard.isBlockedAddress(InetAddress.getByName("::ffff:10.0.0.1")));
    }

    @Test
    void allowsPublicAddress() throws Exception {
        assertFalse(SsrfGuard.isBlockedAddress(InetAddress.getByName("8.8.8.8")));
        assertFalse(SsrfGuard.isBlockedAddress(InetAddress.getByName("1.1.1.1")));
    }

    @Test
    void allowlistExemptsAddressBlockingButNotProtocol() {
        // 精确匹配（IP 字面量）：内网采集源显式加白后放行
        assertDoesNotThrow(() -> SsrfGuard.checkOutbound("http://10.0.0.5/", "10.0.0.5"));
        // 通配子域：放行且**不解析 DNS**（内网域名在公网 DNS 上解析不了，离线也应通过）
        assertDoesNotThrow(() -> SsrfGuard.checkOutbound("http://oa.corp.internal/x", "*.corp.internal"));
        // 未命中白名单：仍然拦
        assertThrows(IOException.class, () -> SsrfGuard.checkOutbound("http://10.0.0.6/", "10.0.0.5"));
        // 白名单**不豁免协议**（ftp 即便在主機白名单里也拒）
        assertThrows(IOException.class, () -> SsrfGuard.checkOutbound("ftp://10.0.0.5/x", "10.0.0.5"));
    }

    @Test
    void isAllowlistedMatching() {
        assertTrue(SsrfGuard.isAllowlisted("OA.Corp.Internal", " *.corp.internal , x.com "));
        assertTrue(SsrfGuard.isAllowlisted("10.0.0.5", "10.0.0.5"));
        assertFalse(SsrfGuard.isAllowlisted("corp.internal", "*.corp.internal"), "裸域不该被 *. 通配匹配");
        assertFalse(SsrfGuard.isAllowlisted("evil-corp.internal", "*.corp.internal"));
        assertFalse(SsrfGuard.isAllowlisted("10.0.0.5", ""));
        assertFalse(SsrfGuard.isAllowlisted("10.0.0.5", null));
    }

    @Test
    void checkOutboundRejectsDangerousTargets() {
        assertThrows(IOException.class, () -> SsrfGuard.checkOutbound("ftp://example.com/a"), "非 http(s) 协议必须拒");
        assertThrows(IOException.class, () -> SsrfGuard.checkOutbound("file:///etc/passwd"));
        assertThrows(IOException.class, () -> SsrfGuard.checkOutbound("http://127.0.0.1/x"));
        assertThrows(IOException.class, () -> SsrfGuard.checkOutbound("http://169.254.169.254/latest/meta-data/"));
        assertThrows(IOException.class, () -> SsrfGuard.checkOutbound("http://10.0.0.5/"));
        assertThrows(IOException.class, () -> SsrfGuard.checkOutbound(""));
    }
}