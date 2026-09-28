package cn.iocoder.txgy.module.bidding.service.ca;

import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.CaShareLaunchParamVO;
import cn.iocoder.txgy.module.bidding.infra.ca.CaSdkProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 官方 CA 调起参数签发单测（契约 R3）。
 *
 * <p>锁定的都是**改名即对接失败**的官方字段名与结构，防止后人「顺手规范化」把
 * {@code CAShareUseScene} / {@code ClientSercet} / {@code callBackUrl} 改掉。
 */
class CaShareLaunchServiceImplTest {

    private CaShareLaunchServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CaShareLaunchServiceImpl();
        CaSdkProperties props = new CaSdkProperties();
        props.setTradingSystemCode("axj11111111");
        props.setServiceUrl("http://127.0.0.1:8992/");
        props.setClientId("trading_system");
        props.setClientSecret("SECRET-ABC");
        props.setSignatureSecret("SIGN-KEY-XYZ");
        props.setCallbackDomain("http://127.0.0.1:48110");
        ReflectionTestUtils.setField(service, "caSdkProperties", props);
    }

    @Test
    @DisplayName("R3 签发参数：官方字段名逐字保留（CAShareUseScene/ClientSercet/callBackUrl）")
    void issueLaunchParam_keepsOfficialFieldNames() {
        CaShareLaunchParamVO param = service.issueLaunchParam(
                "D:/docs/投标文件.pdf", "投标文件.pdf", null, null, null);

        assertEquals("00", param.getCAShareUseScene(), "CAShareUseScene 必须是大驼峰且值为 00");
        // 平台密钥必须出现在响应里（前端靠它调起官方工具），否则工具无法鉴权
        CaShareLaunchParamVO.CaShareComponentConfig c = param.getCaShareComponentConfig();
        assertEquals("SECRET-ABC", c.getClientSecret());
        assertEquals("SECRET-ABC", c.getClientSercet(), "ClientSercet 是官方文档表格写法，两字段同值");
        assertEquals("SIGN-KEY-XYZ", c.getAccessKeySecret());
        assertEquals("axj11111111", c.getTradingSystemCode());
    }

    @Test
    @DisplayName("R3 签发参数：回调地址两个官方写法都填（callBackUrl + callbackRequestUrl）")
    void issueLaunchParam_fillsBothCallbackFieldSpellings() {
        CaShareLaunchParamVO param = service.issueLaunchParam(
                "D:/docs/投标文件.pdf", "投标文件.pdf", null, null, null);

        String callback = param.getTradingSystemConfig().getCallBackUrl();
        assertNotNull(callback);
        assertTrue(callback.endsWith("/admin-api/bidding/ca/share/seal/client-callback"), callback);
        assertEquals(callback, param.getTradingSystemConfig().getCallbackRequestUrl(),
                "官方参数表写 callbackRequestUrl，须与 callBackUrl 同值");
    }

    @Test
    @DisplayName("R3 签发参数：签后路径/名称缺省时派生 _sealed，文件类型缺省 pdf")
    void issueLaunchParam_derivesDefaults() {
        CaShareLaunchParamVO param = service.issueLaunchParam(
                "D:/docs/投标文件.pdf", "投标文件.pdf", null, null, null);

        CaShareLaunchParamVO.KeyParameter.DocumentItem doc = param.getKeyParameter().getDocument().get(0);
        assertEquals("D:/docs/投标文件_sealed.pdf", doc.getSignatureFileSavePath());
        assertEquals("投标文件_sealed.pdf", doc.getSignatureFileName());
        assertEquals("pdf", doc.getAwaitingSignatureFileType(), "官方 20241024 起默认 pdf");
        assertEquals("投标文件.pdf", doc.getAwaitingSignatureFileName());
    }

    @Test
    @DisplayName("R3 签发参数：显式传入的签后路径与类型不被覆盖")
    void issueLaunchParam_respectsExplicitValues() {
        CaShareLaunchParamVO param = service.issueLaunchParam(
                "D:/docs/a.ofd", "a.ofd", "D:/out/b.ofd", "b.ofd", "ofd");

        CaShareLaunchParamVO.KeyParameter.DocumentItem doc = param.getKeyParameter().getDocument().get(0);
        assertEquals("D:/out/b.ofd", doc.getSignatureFileSavePath());
        assertEquals("b.ofd", doc.getSignatureFileName());
        assertEquals("ofd", doc.getAwaitingSignatureFileType());
    }
}
