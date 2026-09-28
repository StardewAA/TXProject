package cn.iocoder.txgy.module.bidding.infra.ca;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 外部 CA SDK 适配层测试。
 * ① 配置缺失时 SDK 实现首次调用必须抛清晰异常（不静默降级）；
 * ② 占位实现（缺省 enabled=false）行为宽松，业务闭环可测。
 * 本地可跑，不依赖远程 CA 组件。
 */
class CaClientFacadeTest {

    @Test
    void sdkImpl_configMissing_throwsIllegalState() {
        CaSdkProperties props = new CaSdkProperties();
        props.setEnabled(true); // 但 serviceUrl/clientId 等为空
        CaClientFacadeSdkImpl impl = new CaClientFacadeSdkImpl(props);
        IllegalStateException ex = assertThrows(IllegalStateException.class, impl::createLoginRequest);
        assertTrue(ex.getMessage().contains("bidding.ca.sdk"), "异常信息必须提示配置项");
    }

    @Test
    void sdkImpl_configBlank_alsoThrows() {
        CaSdkProperties props = new CaSdkProperties();
        props.setEnabled(true);
        props.setServiceUrl("  ");
        props.setClientId("c1");
        CaClientFacadeSdkImpl impl = new CaClientFacadeSdkImpl(props);
        assertThrows(IllegalStateException.class, () -> impl.queryLoginStatus("tid-missing-config"));
    }

    @Test
    void defaultImpl_placeholderAuthPasses() {
        CaClientFacadeDefault impl = new CaClientFacadeDefault();
        assertTrue(impl.authenticate("SN-2-CA01", "91110000MOCK000001"));
        assertFalse(impl.authenticate(null, null));
        assertFalse(impl.authenticate("", ""));
    }

    @Test
    void defaultImpl_signRoundTripAndVerify() {
        CaClientFacadeDefault impl = new CaClientFacadeDefault();
        String digest = LocalCryptoUtils.sm3Hex("投标文件SM3指纹");
        String sig = impl.sign(digest, "SN-2-CA01");
        assertNotNull(sig);
        assertTrue(impl.verify(digest, sig, "SM2"));
        assertFalse(impl.verify(digest, "SIG:other", "SM2"));
    }

    @Test
    void defaultImpl_loginThreePhase() {
        CaClientFacadeDefault impl = new CaClientFacadeDefault();
        CaClientFacade.LoginQrCode qr = impl.createLoginRequest();
        assertNotNull(qr.tid);
        CaClientFacade.LoginStatus st = impl.queryLoginStatus(qr.tid);
        assertTrue(st.scanned);
        CaClientFacade.AuthInfo auth = impl.fetchAuthInfo(qr.tid);
        assertNotNull(auth.orgName);
        assertEquals("SM2", auth.publicKeyAlgorithm);
    }

    @Test
    void defaultImpl_signThreePhase() {
        CaClientFacadeDefault impl = new CaClientFacadeDefault();
        String digest = LocalCryptoUtils.sm3Hex("投标文件");
        CaClientFacade.SignQrCode qr = impl.createSignRequest(digest);
        assertNotNull(qr.tid);
        assertTrue(impl.querySignStatus(qr.tid).signed);
        CaClientFacade.SignValue v = impl.fetchSignatureValue(qr.tid);
        assertNotNull(v.signatureValue);
    }

    @Test
    void defaultImpl_decryptThreePhase() {
        CaClientFacadeDefault impl = new CaClientFacadeDefault();
        CaClientFacade.DecryptQrCode qr = impl.createDecryptRequest("02", "91110000MOCK000001", null);
        assertNotNull(qr.tid);
        assertTrue(impl.queryDecryptStatus(qr.tid).scanned);
        CaClientFacade.DecryptUserInfo info = impl.fetchDecryptUserInfo(qr.tid);
        assertNotNull(info.accessToken);
        CaClientFacade.DecryptEnvelope env = new CaClientFacade.DecryptEnvelope();
        env.encryptedKeyDataEnvelopeId = "ENV-001";
        env.caOrgCode = "ORG-001";
        env.encryptionCertSn = "SN-001";
        env.encryptionKey = "SM2-CIPHER";
        env.publicKeyAlgorithm = "00";
        CaClientFacade.DecryptEnvelopeResult up = impl.uploadDecryptEnvelope(qr.tid, info.accessToken,
                java.util.Collections.singletonList(env));
        assertEquals("200", up.returnCode);
        java.util.List<CaClientFacade.DecryptData> data = impl.fetchDecryptedData(qr.tid);
        assertFalse(data.isEmpty());
        assertNotNull(data.get(0).decryptedKey);
    }

    @Test
    void sdkImpl_decryptThreePhase_configMissingThrows() {
        CaSdkProperties props = new CaSdkProperties();
        props.setEnabled(true);
        CaClientFacadeSdkImpl impl = new CaClientFacadeSdkImpl(props);
        assertThrows(IllegalStateException.class, () -> impl.createDecryptRequest("02", "org", null));
    }
}
