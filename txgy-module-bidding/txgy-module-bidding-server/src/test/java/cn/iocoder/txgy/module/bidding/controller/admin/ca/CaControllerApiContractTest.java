package cn.iocoder.txgy.module.bidding.controller.admin.ca;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.bidding.infra.ca.CaClientFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * **接口层契约测试（本地单测，不依赖数据库/Spring 容器/CA 组件）**：以 29 号文档
 * 《外部 CA 与身份认证对接清单》定义的扫码三阶段形态为准，校验「前端拿到的 HTTP 形状」——
 * 路径、方法、鉴权（isAuthenticated()）、响应 JSON 字段集合。
 *
 * <p>边界（诚实说明）：standalone MockMvc 不启动 Spring 容器 ⇒ 不校验租户拦截/鉴权切面/
 * 幂等切面；bidding.ca.sdk.enabled 缺省 false 走占位实现——真实 SDK 的端到端（起服务 + 组件）
 * 需凭证就绪后另行验证（见对接前置条件清单）。本测试保证接口形状不会悄悄变。
 */
class CaControllerApiContractTest {

    private CaClientFacade caClientFacade;
    private CaController controller;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        caClientFacade = mock(CaClientFacade.class);
        controller = new CaController();
        ReflectionTestUtils.setField(controller, "caClientFacade", caClientFacade);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("§5.3 POST /bidding/ca/login/qrcode：鉴权/路径/LoginQrCode 4 字段全等")
    void loginQrcode_matchesContract() throws Exception {
        assertPermission("createLoginRequest");

        CaClientFacade.LoginQrCode qr = new CaClientFacade.LoginQrCode();
        qr.tid = "trading_systemuuid1234";
        qr.qrCodeImageBase64 = "iVBORw0KGgoAAAANSUhEUgAA";
        qr.qrCodeJumpUrl = "http://127.0.0.1:8992/cashare/?tradingSystemCode=trading_system";
        qr.qrCodeShowText = "CA扫码登录";
        when(caClientFacade.createLoginRequest()).thenReturn(qr);

        String body = mockMvc.perform(post("/bidding/ca/login/qrcode"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tid").value("trading_systemuuid1234"))
                .andExpect(jsonPath("$.data.qrCodeShowText").value("CA扫码登录"))
                .andReturn().getResponse().getContentAsString();
        assertFieldSet(body, "data", new TreeSet<>(List.of(
                "tid", "qrCodeImageBase64", "qrCodeJumpUrl", "qrCodeShowText")));
    }

    @Test
    @DisplayName("§5.3 GET /bidding/ca/login/status：鉴权/路径/LoginStatus 3 字段全等")
    void loginStatus_matchesContract() throws Exception {
        assertPermission("queryLoginStatus");

        CaClientFacade.LoginStatus st = new CaClientFacade.LoginStatus();
        st.scanned = true;
        st.status = "01";
        st.message = "已扫码";
        when(caClientFacade.queryLoginStatus("tid123")).thenReturn(st);

        String body = mockMvc.perform(get("/bidding/ca/login/status").param("tid", "tid123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scanned").value(true))
                .andExpect(jsonPath("$.data.status").value("01"))
                .andReturn().getResponse().getContentAsString();
        assertFieldSet(body, "data", new TreeSet<>(List.of("scanned", "status", "message")));
    }

    @Test
    @DisplayName("§5.3 GET /bidding/ca/login/auth-info：鉴权/路径/AuthInfo 10 字段全等（进房核对字段）")
    void authInfo_matchesContract() throws Exception {
        assertPermission("fetchAuthInfo");

        CaClientFacade.AuthInfo ai = new CaClientFacade.AuthInfo();
        ai.tid = "tid123";
        ai.orgName = "测试企业有限公司";
        ai.orgCode = "91330100MA27XXXXX";
        ai.personalName = "张三";
        ai.signatureCertSn = "SN123456";
        ai.signatureCert = "MIIC...";
        ai.publicKeyAlgorithm = "SM2";
        ai.loginUserType = "PERSON";
        ai.caOrgCode = "CA001";
        ai.telephoneNumber = "13800000000";
        when(caClientFacade.fetchAuthInfo("tid123")).thenReturn(ai);

        String body = mockMvc.perform(get("/bidding/ca/login/auth-info").param("tid", "tid123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orgName").value("测试企业有限公司"))
                .andExpect(jsonPath("$.data.publicKeyAlgorithm").value("SM2"))
                .andReturn().getResponse().getContentAsString();
        assertFieldSet(body, "data", new TreeSet<>(List.of(
                "tid", "orgName", "orgCode", "personalName", "signatureCertSn", "signatureCert",
                "publicKeyAlgorithm", "loginUserType", "caOrgCode", "telephoneNumber",
                "orgTransactionCode", "personalTransactionCode", "idCardHash",
                "dataSignatureValue", "signatureData")));
    }

    @Test
    @DisplayName("§5.3 POST /bidding/ca/sign/qrcode：鉴权/路径/SignQrCode 4 字段全等")
    void signQrcode_matchesContract() throws Exception {
        assertPermission("createSignRequest");

        CaClientFacade.SignQrCode qr = new CaClientFacade.SignQrCode();
        qr.tid = "siguuid5678";
        qr.qrCodeImageBase64 = "iVBORw0KGgo=";
        qr.qrCodeJumpUrl = "http://127.0.0.1:8992/cashare/";
        qr.qrCodeShowText = "扫码签名";
        when(caClientFacade.createSignRequest("66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0")).thenReturn(qr);

        String body = mockMvc.perform(post("/bidding/ca/sign/qrcode")
                        .param("digestHex", "66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tid").value("siguuid5678"))
                .andReturn().getResponse().getContentAsString();
        assertFieldSet(body, "data", new TreeSet<>(List.of(
                "tid", "qrCodeImageBase64", "qrCodeJumpUrl", "qrCodeShowText")));
    }

    @Test
    @DisplayName("§5.3 GET /bidding/ca/sign/status + /sign/value：鉴权/路径/字段全等")
    void signStatusAndValue_matchContract() throws Exception {
        assertPermission("querySignStatus");
        assertPermission("fetchSignatureValue");

        CaClientFacade.SignStatus ss = new CaClientFacade.SignStatus();
        ss.scanned = true;
        ss.signed = true;
        ss.status = "03";
        ss.message = "已签名";
        when(caClientFacade.querySignStatus("siguuid5678")).thenReturn(ss);
        String body1 = mockMvc.perform(get("/bidding/ca/sign/status").param("tid", "siguuid5678"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signed").value(true))
                .andReturn().getResponse().getContentAsString();
        assertFieldSet(body1, "data", new TreeSet<>(List.of("scanned", "signed", "status", "message")));

        CaClientFacade.SignValue sv = new CaClientFacade.SignValue();
        sv.signatureValue = "MEUCIQ...";
        sv.signatureCertSn = "SN123456";
        sv.signatureCert = "MIIC...";
        sv.publicKeyAlgorithm = "SM2";
        when(caClientFacade.fetchSignatureValue("siguuid5678")).thenReturn(sv);
        String body2 = mockMvc.perform(get("/bidding/ca/sign/value").param("tid", "siguuid5678"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signatureCertSn").value("SN123456"))
                .andReturn().getResponse().getContentAsString();
        assertFieldSet(body2, "data", new TreeSet<>(List.of(
                "signatureValue", "signatureCertSn", "signatureCert", "publicKeyAlgorithm",
                "caOrgCode", "signatureMessage", "signatureUserType")));
    }

    @Test
    @DisplayName("§5.3 解密三阶段 POST /decrypt/qrcode + GET /decrypt/status + /decrypt/user-info + POST /decrypt/envelope + GET /decrypt/data：鉴权/路径/字段全等")
    void decryptThreePhase_matchContract() throws Exception {
        assertPermission("createDecryptRequest");
        assertPermission("queryDecryptStatus");
        assertPermission("fetchDecryptUserInfo");
        assertPermission("uploadDecryptEnvelope");
        assertPermission("fetchDecryptedData");

        CaClientFacade.DecryptQrCode qr = new CaClientFacade.DecryptQrCode();
        qr.tid = "decuuid1234";
        qr.qrCodeImageBase64 = "iVBORw0KGgo=";
        qr.qrCodeJumpUrl = "http://127.0.0.1:8992/cashare/";
        qr.qrCodeShowText = "CA扫码解密";
        when(caClientFacade.createDecryptRequest("02", "91330100MA27XXXXX", null)).thenReturn(qr);
        String body0 = mockMvc.perform(post("/bidding/ca/decrypt/qrcode")
                        .param("decryptionUserType", "02").param("orgCode", "91330100MA27XXXXX"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tid").value("decuuid1234"))
                .andReturn().getResponse().getContentAsString();
        assertFieldSet(body0, "data", new TreeSet<>(List.of(
                "tid", "qrCodeImageBase64", "qrCodeJumpUrl", "qrCodeShowText")));

        CaClientFacade.DecryptStatus ds = new CaClientFacade.DecryptStatus();
        ds.scanned = true;
        ds.status = "01";
        ds.message = "已扫码已授权";
        when(caClientFacade.queryDecryptStatus("decuuid1234")).thenReturn(ds);
        String body1 = mockMvc.perform(get("/bidding/ca/decrypt/status").param("tid", "decuuid1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scanned").value(true))
                .andReturn().getResponse().getContentAsString();
        assertFieldSet(body1, "data", new TreeSet<>(List.of("scanned", "status", "message")));

        CaClientFacade.DecryptUserInfo ui = new CaClientFacade.DecryptUserInfo();
        ui.tid = "decuuid1234";
        ui.accessToken = "ACCESS-TOKEN-X";
        ui.decryptionUserType = "02";
        ui.orgCode = "91330100MA27XXXXX";
        when(caClientFacade.fetchDecryptUserInfo("decuuid1234")).thenReturn(ui);
        String body2 = mockMvc.perform(get("/bidding/ca/decrypt/user-info").param("tid", "decuuid1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("ACCESS-TOKEN-X"))
                .andReturn().getResponse().getContentAsString();
        assertFieldSet(body2, "data", new TreeSet<>(List.of(
                "tid", "accessToken", "decryptionUserType", "idCardHash", "orgCode")));

        CaClientFacade.DecryptEnvelope env = new CaClientFacade.DecryptEnvelope();
        env.encryptedKeyDataEnvelopeId = "ENV-001";
        env.caOrgCode = "CA001";
        env.encryptionCertSn = "SN123456";
        env.encryptionKey = "SM2-CIPHER-B64";
        env.publicKeyAlgorithm = "00";
        CaClientFacade.DecryptEnvelopeResult er = new CaClientFacade.DecryptEnvelopeResult();
        er.returnCode = "200";
        er.msg = "上传成功";
        when(caClientFacade.uploadDecryptEnvelope(org.mockito.ArgumentMatchers.eq("decuuid1234"),
                org.mockito.ArgumentMatchers.eq("ACCESS-TOKEN-X"), org.mockito.ArgumentMatchers.anyList())).thenReturn(er);
        String body3 = mockMvc.perform(post("/bidding/ca/decrypt/envelope")
                        .param("tid", "decuuid1234").param("accessToken", "ACCESS-TOKEN-X")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(env))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        assertFieldSet(body3, "data", new TreeSet<>(List.of("msg", "returnCode")));

        CaClientFacade.DecryptData dd = new CaClientFacade.DecryptData();
        dd.tid = "decuuid1234";
        dd.decryptStatus = "SUCCESS";
        dd.decryptStatusInfo = "解密成功";
        dd.encryptedKeyDataEnvelopeId = "ENV-001";
        dd.decryptedKey = "SM4-KEY-PLAIN";
        when(caClientFacade.fetchDecryptedData("decuuid1234")).thenReturn(List.of(dd));
        String body4 = mockMvc.perform(get("/bidding/ca/decrypt/data").param("tid", "decuuid1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].decryptedKey").value("SM4-KEY-PLAIN"))
                .andReturn().getResponse().getContentAsString();
        // data 为数组：取首元素断言字段集（path() 是 JSON Pointer，不能解析 "data[0]"，故手工取）
        com.fasterxml.jackson.databind.JsonNode firstNode =
                objectMapper.readTree(body4).path("data").path(0);
        assertFieldSet("{\"data\":" + objectMapper.writeValueAsString(firstNode) + "}", "data",
                new TreeSet<>(List.of(
                        "tid", "decryptStatus", "decryptStatusInfo", "encryptedKeyDataEnvelopeId", "decryptedKey")));
    }

    @Test
    @DisplayName("authenticate/verify/decrypt/sm3：路径/鉴权/布尔与摘要形状")
    void simpleEndpoints_matchContract() throws Exception {
        assertPermission("authenticate");
        assertPermission("verify");
        assertPermission("decrypt");
        assertPermission("sm3");

        when(caClientFacade.authenticate("SN123", "91330100MA27")).thenReturn(true);
        mockMvc.perform(post("/bidding/ca/authenticate")
                        .param("caCertNo", "SN123").param("subjectCode", "91330100MA27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        when(caClientFacade.verify("66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0", "MEUCIQ", "SM2")).thenReturn(true);
        mockMvc.perform(post("/bidding/ca/verify")
                        .param("digestHex", "66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0").param("signatureBase64", "MEUCIQ").param("publicKeyAlgorithm", "SM2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        String cipherB64 = Base64.getEncoder().encodeToString("密文内容".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(caClientFacade.decrypt(org.mockito.ArgumentMatchers.any(byte[].class), org.mockito.ArgumentMatchers.eq("SN123"))).thenReturn("投标文件明文".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        // body 为裸 Base64 密文原文（text/plain；@RequestBody String 语义）
        mockMvc.perform(post("/bidding/ca/decrypt").param("caCertNo", "SN123")
                        .contentType(MediaType.TEXT_PLAIN).content(cipherB64))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        when(caClientFacade.sm3Hex(org.mockito.ArgumentMatchers.any(byte[].class))).thenReturn("66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0");
        mockMvc.perform(get("/bidding/ca/sm3").param("text", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0"));
    }

    /** 解析响应 JSON 指定节点字段集合。 */
    private void assertFieldSet(String body, String jsonPath, Set<String> expected) throws Exception {
        Set<String> actual = new TreeSet<>();
        objectMapper.readTree(body).path(jsonPath).fieldNames().forEachRemaining(actual::add);
        assertEquals(expected, actual, "外发字段集必须与契约全等：多了=外发内部列，少了=前端拿不到值");
    }

    /** 反射断言控制器方法带 @PreAuthorize("isAuthenticated()")。 */
    private static void assertPermission(String methodName) {
        Method method = Arrays.stream(CaController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName)).findFirst()
                .orElseThrow(() -> new AssertionError("控制器里找不到方法：" + methodName));
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation, methodName + " 缺少 @PreAuthorize（接口必须判权）");
        assertTrue(annotation.value().contains("isAuthenticated()"),
                methodName + " 鉴权应为 isAuthenticated()，实际：" + annotation.value());
    }

}