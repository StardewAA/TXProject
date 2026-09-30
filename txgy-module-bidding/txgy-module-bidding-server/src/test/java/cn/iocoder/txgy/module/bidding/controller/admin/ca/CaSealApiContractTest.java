package cn.iocoder.txgy.module.bidding.controller.admin.ca;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.CaSealCheckRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.CaSealRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.CaSealStatsRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.SealCallbackReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.SealDocumentRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.ca.CaSealDO;
import cn.iocoder.txgy.module.bidding.service.ca.CaSealService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 文件电子签章接口层契约测试（本地单测，不依赖数据库/Spring 容器）。
 *
 * <p>以 30 号文档（CA互认H5签章组件）后端待办 #2/#5 为契约：document 签发（multipart 上传待签文件）
 * 与签章完成回调两条接口的路径/方法/鉴权/响应 JSON 字段集合。
 */
class CaSealApiContractTest {

    private CaSealService caSealService;
    private CaSealController controller;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        caSealService = mock(CaSealService.class);
        controller = new CaSealController();
        ReflectionTestUtils.setField(controller, "caSealService", caSealService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("30号文档#2 POST /bidding/ca/seal/document：鉴权/multipart/SealDocumentRespVO 15 字段全等")
    void sealDocument_matchesContract() throws Exception {
        assertPermission("sealDocument");

        SealDocumentRespVO vo = new SealDocumentRespVO();
        vo.setDocumentId("SEAL-DOC-abc123");
        vo.setTradingSystemCode("axj11111111");
        vo.setNetworkPath("http://127.0.0.1:48080/admin-api/infra/file/4/get/bidding/seal/original/x.pdf");
        vo.setCallbackUrl("http://127.0.0.1:48080/admin-api/bidding/ca/seal/callback");
        vo.setSignature("MEUCIQD...");
        vo.setCertSn("SN-LOCAL-CA-TEST-0001");
        vo.setHash("728fac6544903794ea72e8e3fd929628a1fd13d34ef30cb612d81d19c17680b6");
        vo.setSha256("9f2c...");
        vo.setOrgName("本地CA测试单位");
        vo.setSealTime("2026-09-19 10:00:00");
        vo.setSealFileId(1001L);
        vo.setSealFileUrl("http://127.0.0.1:48080/admin-api/infra/file/4/get/bidding/seal/result/x-sealed.pdf");
        vo.setSealFileName("x-已签章.pdf");
        vo.setTrackId(90001L);
        when(caSealService.sealDocument(any(), any(), any())).thenReturn(vo);

        MockMultipartFile file = new MockMultipartFile("file", "投标文件.pdf",
                "application/pdf", new byte[]{'%', 'P', 'D', 'F', '-'});
        String body = mockMvc.perform(multipart("/bidding/ca/seal/document").file(file)
                        .param("businessCode", "90001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.documentId").value("SEAL-DOC-abc123"))
                .andExpect(jsonPath("$.data.signature").value("MEUCIQD..."))
                .andExpect(jsonPath("$.data.trackId").value(90001))
                .andReturn().getResponse().getContentAsString();
        assertFieldSet(body, "data", new TreeSet<>(List.of(
                "documentId", "tradingSystemCode", "networkPath", "callbackUrl", "signature",
                "certSn", "hash", "sha256", "orgName", "sealTime", "sealFileId", "sealFileUrl",
                "sealFileName", "sealFileSize", "trackId")));
    }

    @Test
    @DisplayName("批量盖章 POST /bidding/ca/seal/batch：isAuthenticated + @Idempotent + 多文件返回列表")
    void batch_matchesContract() throws Exception {
        assertPermission("batch");
        assertIdempotent("batch");

        SealDocumentRespVO vo1 = new SealDocumentRespVO();
        vo1.setDocumentId("SEAL-DOC-batch001");
        vo1.setTradingSystemCode("axj11111111");
        vo1.setNetworkPath("http://127.0.0.1:48080/admin-api/infra/file/4/get/bidding/seal/original/a.pdf");
        vo1.setSignature("MEUCIQD...1");
        vo1.setCertSn("SN-LOCAL-CA-TEST-0001");
        vo1.setHash("728fac6544903794ea72e8e3fd929628a1fd13d34ef30cb612d81d19c17680b6");
        vo1.setSha256("9f2c...a");
        vo1.setOrgName("本地CA测试单位");
        vo1.setSealTime("2026-09-28 20:00:00");
        vo1.setSealFileId(12001L);
        vo1.setSealFileUrl("http://127.0.0.1:48080/admin-api/infra/file/4/get/bidding/seal/result/a-sealed.pdf");
        vo1.setSealFileName("投标文件一册-已签章.pdf");
        vo1.setTrackId(90001L);

        SealDocumentRespVO vo2 = new SealDocumentRespVO();
        vo2.setDocumentId("SEAL-DOC-batch002");
        vo2.setTradingSystemCode("axj11111111");
        vo2.setNetworkPath("http://127.0.0.1:48080/admin-api/infra/file/4/get/bidding/seal/original/b.pdf");
        vo2.setSignature("MEUCIQD...2");
        vo2.setCertSn("SN-LOCAL-CA-TEST-0001");
        vo2.setHash("cdef6544903794ea72e8e3fd929628a1fd13d34ef30cb612d81d19c17680b7");
        vo2.setSha256("9f2c...b");
        vo2.setOrgName("本地CA测试单位");
        vo2.setSealTime("2026-09-28 20:00:01");
        vo2.setSealFileId(12002L);
        vo2.setSealFileUrl("http://127.0.0.1:48080/admin-api/infra/file/4/get/bidding/seal/result/b-sealed.pdf");
        vo2.setSealFileName("投标文件二册-已签章.pdf");
        vo2.setTrackId(90001L);
        when(caSealService.sealDocumentBatch(any(), any(), any())).thenReturn(List.of(vo1, vo2));

        MockMultipartFile f1 = new MockMultipartFile("files", "投标文件一册.pdf", "application/pdf", new byte[]{'%', 'P', 'D', 'F', '-'});
        MockMultipartFile f2 = new MockMultipartFile("files", "投标文件二册.pdf", "application/pdf", new byte[]{'%', 'P', 'D', 'F', '-'});
        String body = mockMvc.perform(multipart("/bidding/ca/seal/batch").file(f1).file(f2)
                        .param("businessCode", "90001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].documentId").value("SEAL-DOC-batch001"))
                .andExpect(jsonPath("$.data[1].documentId").value("SEAL-DOC-batch002"))
                .andExpect(jsonPath("$.data[1].sealFileName").value("投标文件二册-已签章.pdf"))
                .andReturn().getResponse().getContentAsString();
        assertFieldSetAt(body, 0, new TreeSet<>(List.of(
                "documentId", "tradingSystemCode", "networkPath", "callbackUrl", "signature",
                "certSn", "hash", "sha256", "orgName", "sealTime", "sealFileId", "sealFileUrl",
                "sealFileName", "sealFileSize", "trackId")));
    }

    @Test
    @DisplayName("30号文档#5 POST /bidding/ca/seal/callback：鉴权/JSON/回调成功返回 true")
    void callback_matchesContract() throws Exception {
        assertPermission("callback");

        SealCallbackReqVO req = new SealCallbackReqVO();
        req.setBusinessCode("90001");
        req.setDocumentId("SEAL-DOC-abc123");
        req.setSignature("MEUCIQD...");
        req.setCertSn("SN-LOCAL-CA-TEST-0001");
        req.setHash("728fac6544903794ea72e8e3fd929628a1fd13d34ef30cb612d81d19c17680b6");
        req.setSealFileId(1001L);
        when(caSealService.saveCallback(req)).thenReturn(true);

        mockMvc.perform(post("/bidding/ca/seal/callback")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));
    }

    /** 契约：CA 签章台账响应 22 字段（2026-09-23 补 sealFileSize）。 */
    private static final Set<String> SEAL_RESP_FIELDS = new TreeSet<>(List.of(
            "id", "documentId", "documentName", "documentType", "fileId", "fileUrl",
            "fileSha256", "sm3Hash", "signature", "certSn", "orgName", "sealTime",
            "sealFileId", "sealFileUrl", "sealFileName", "sealFileSize", "trackId", "verifyStatus",
            "verifyTime", "operatorUserId", "remark", "createTime"));

    @Test
    @DisplayName("上传检测 GET /bidding/ca/seal/check：isAuthenticated + CaSealCheckRespVO 10 字段全等")
    void check_matchesContract() throws Exception {
        assertPermission("check");

        CaSealCheckRespVO vo = new CaSealCheckRespVO();
        vo.setCaAuthed(false);
        when(caSealService.checkSealAuth(21001L, null)).thenReturn(vo);

        String body = mockMvc.perform(get("/bidding/ca/seal/check").param("fileId", "21001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.caAuthed").value(false))
                .andReturn().getResponse().getContentAsString();
        assertFieldSet(body, "data", new TreeSet<>(List.of(
                "caAuthed", "sealId", "documentId", "documentName", "orgName",
                "sealTime", "verifyStatus", "sealFileId", "sealFileUrl", "sealFileSize")));
    }

    @Test
    @DisplayName("签章台账 GET /bidding/ca/seal/page：鉴权 bidding:ca:query + 分页返回")
    void page_matchesContract() throws Exception {
        assertLedgerPermission("page", "bidding:ca:query");

        when(caSealService.page(any())).thenReturn(new PageResult<>(List.of(sampleSealDo()), 1L));

        mockMvc.perform(get("/bidding/ca/seal/page").param("pageNo", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(9901))
                .andExpect(jsonPath("$.data.list[0].verifyStatus").value("UNVERIFIED"));
    }

    @Test
    @DisplayName("签章台账 GET /bidding/ca/seal/get：鉴权 bidding:ca:query + CaSealRespVO 22 字段全等")
    void get_matchesContract() throws Exception {
        assertLedgerPermission("get", "bidding:ca:query");

        when(caSealService.get(9901L)).thenReturn(sampleSealDo());

        String body = mockMvc.perform(get("/bidding/ca/seal/get").param("id", "9901"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(9901))
                .andExpect(jsonPath("$.data.documentId").value("SEAL-DOC-ledger001"))
                .andReturn().getResponse().getContentAsString();
        assertFieldSet(body, "data", SEAL_RESP_FIELDS);
    }

    @Test
    @DisplayName("签章台账 POST /bidding/ca/seal/verify：鉴权 bidding:ca:verify + @Idempotent + 返回验签结果")
    void verify_matchesContract() throws Exception {
        assertLedgerPermission("verify", "bidding:ca:verify");
        assertIdempotent("verify");

        CaSealRespVO result = new CaSealRespVO();
        result.setId(9901L);
        result.setVerifyStatus("PASS");
        result.setVerifyTime(LocalDateTime.of(2026, 9, 28, 18, 0));
        when(caSealService.verifySeal(9901L)).thenReturn(result);

        mockMvc.perform(post("/bidding/ca/seal/verify").param("id", "9901"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(9901))
                .andExpect(jsonPath("$.data.verifyStatus").value("PASS"));
    }

    @Test
    @DisplayName("签章台账 GET /bidding/ca/seal/stats：鉴权 bidding:ca:query + 统计 4 字段全等")
    void stats_matchesContract() throws Exception {
        assertLedgerPermission("stats", "bidding:ca:query");

        CaSealStatsRespVO vo = new CaSealStatsRespVO();
        vo.setTotal(5L);
        vo.setPass(4L);
        vo.setFail(0L);
        vo.setUnverified(1L);
        when(caSealService.stats()).thenReturn(vo);

        String body = mockMvc.perform(get("/bidding/ca/seal/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(5))
                .andExpect(jsonPath("$.data.pass").value(4))
                .andReturn().getResponse().getContentAsString();
        assertFieldSet(body, "data", new TreeSet<>(List.of("total", "pass", "fail", "unverified")));
    }

    @Test
    @DisplayName("演示数据 POST /bidding/ca/seal/demo-seed：isAuthenticated + @Idempotent + 返回列表")
    void demoSeed_matchesContract() throws Exception {
        assertPermission("demoSeed");
        assertIdempotent("demoSeed");

        when(caSealService.demoSeed(3)).thenReturn(List.of(
                BeanUtilsOfSeal(sampleSealDo(), 9901L, "SEAL-DOC-demo001")));

        mockMvc.perform(post("/bidding/ca/seal/demo-seed").param("count", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(9901))
                .andExpect(jsonPath("$.data[0].verifyStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.data[0].sealFileName").value("投标文件-已签章.pdf"));
    }

    @Test
    @DisplayName("篡改样例 POST /bidding/ca/seal/demo-tamper：isAuthenticated + @Idempotent + 返回列表(FAIL)")
    void demoTamper_matchesContract() throws Exception {
        assertPermission("demoTamper");
        assertIdempotent("demoTamper");

        CaSealRespVO tampered = BeanUtilsOfSeal(sampleSealDo(), 9902L, "SEAL-DOC-tamper001");
        tampered.setDocumentName("演示篡改文件-1.pdf");
        tampered.setVerifyStatus("FAIL");
        when(caSealService.demoTamper(2)).thenReturn(List.of(tampered));

        mockMvc.perform(post("/bidding/ca/seal/demo-tamper").param("count", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(9902))
                .andExpect(jsonPath("$.data[0].documentName").value("演示篡改文件-1.pdf"))
                .andExpect(jsonPath("$.data[0].verifyStatus").value("FAIL"));
    }

    @Test
    @DisplayName("演示清理 POST /bidding/ca/seal/demo-clear：isAuthenticated + @Idempotent + 返回删除条数")
    void demoClear_matchesContract() throws Exception {
        assertPermission("demoClear");
        assertIdempotent("demoClear");

        when(caSealService.demoClear()).thenReturn(7L);

        mockMvc.perform(post("/bidding/ca/seal/demo-clear"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(7));
    }

    /** 从样例 DO 转 CaSealRespVO（模拟控制器 BeanUtils 行为）。 */
    private static CaSealRespVO BeanUtilsOfSeal(CaSealDO d, Long id, String documentId) {
        CaSealRespVO vo = new CaSealRespVO();
        vo.setId(id);
        vo.setDocumentId(documentId);
        vo.setDocumentName(d.getDocumentName());
        vo.setDocumentType(d.getDocumentType());
        vo.setFileId(d.getFileId());
        vo.setFileUrl(d.getFileUrl());
        vo.setFileSha256(d.getFileSha256());
        vo.setSm3Hash(d.getSm3Hash());
        vo.setSignature(d.getSignature());
        vo.setCertSn(d.getCertSn());
        vo.setOrgName(d.getOrgName());
        vo.setSealTime(d.getSealTime());
        vo.setSealFileId(d.getSealFileId());
        vo.setSealFileUrl(d.getSealFileUrl());
        vo.setSealFileName(d.getSealFileName());
        vo.setTrackId(d.getTrackId());
        vo.setVerifyStatus(d.getVerifyStatus());
        vo.setVerifyTime(d.getVerifyTime());
        vo.setOperatorUserId(d.getOperatorUserId());
        vo.setRemark(d.getRemark());
        vo.setCreateTime(d.getCreateTime());
        return vo;
    }

    /** 台账样例记录。 */
    private static CaSealDO sampleSealDo() {
        CaSealDO d = new CaSealDO();
        d.setId(9901L);
        d.setDocumentId("SEAL-DOC-ledger001");
        d.setDocumentName("投标文件.pdf");
        d.setDocumentType("pdf");
        d.setFileId(21001L);
        d.setFileUrl("http://127.0.0.1:48080/admin-api/infra/file/4/get/bidding/seal/original/x.pdf");
        d.setFileSha256("9f2c...");
        d.setSm3Hash("728fac6544903794ea72e8e3fd929628a1fd13d34ef30cb612d81d19c17680b6");
        d.setSignature("MEUCIQD...");
        d.setCertSn("SN-LOCAL-CA-TEST-0001");
        d.setOrgName("本地CA测试单位");
        d.setSealTime("2026-09-28 17:00:00");
        d.setSealFileId(21002L);
        d.setSealFileUrl("http://127.0.0.1:48080/admin-api/infra/file/4/get/bidding/seal/result/x-sealed.pdf");
        d.setSealFileName("投标文件-已签章.pdf");
        d.setTrackId(90001L);
        d.setVerifyStatus("UNVERIFIED");
        d.setOperatorUserId(1L);
        d.setRemark("契约测试样例");
        d.setCreateTime(LocalDateTime.of(2026, 9, 28, 17, 0));
        return d;
    }

    /** 反射断言台账接口带指定权限码。 */
    private static void assertLedgerPermission(String methodName, String permissionCode) {
        Method method = Arrays.stream(CaSealController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName)).findFirst()
                .orElseThrow(() -> new AssertionError("控制器里找不到方法：" + methodName));
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation, methodName + " 缺少 @PreAuthorize（接口必须判权）");
        assertTrue(annotation.value().contains(permissionCode),
                methodName + " 鉴权应含 " + permissionCode + "，实际：" + annotation.value());
    }

    /** 反射断言写操作带 @Idempotent。 */
    private static void assertIdempotent(String methodName) {
        Method method = Arrays.stream(CaSealController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName)).findFirst().orElseThrow();
        assertNotNull(method.getAnnotation(Idempotent.class),
                methodName + " 是写操作，必须带 @Idempotent");
    }

    /** 解析响应 JSON 指定节点字段集合。 */
    private void assertFieldSet(String body, String jsonPath, Set<String> expected) throws Exception {
        Set<String> actual = new TreeSet<>();
        objectMapper.readTree(body).path(jsonPath).fieldNames().forEachRemaining(actual::add);
        assertEquals(expected, actual, "外发字段集必须与契约全等：多了=外发内部列，少了=前端拿不到值");
    }

    /** 解析响应 JSON data 数组指定下标元素字段集合。 */
    private void assertFieldSetAt(String body, int index, Set<String> expected) throws Exception {
        Set<String> actual = new TreeSet<>();
        objectMapper.readTree(body).path("data").get(index).fieldNames().forEachRemaining(actual::add);
        assertEquals(expected, actual, "外发字段集必须与契约全等：多了=外发内部列，少了=前端拿不到值");
    }

    /** 反射断言控制器方法带 @PreAuthorize("isAuthenticated()")。 */
    private static void assertPermission(String methodName) {
        Method method = Arrays.stream(CaSealController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName)).findFirst()
                .orElseThrow(() -> new AssertionError("控制器里找不到方法：" + methodName));
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation, methodName + " 缺少 @PreAuthorize（接口必须判权）");
        assertTrue(annotation.value().contains("isAuthenticated()"),
                methodName + " 鉴权应为 isAuthenticated()，实际：" + annotation.value());
    }
}
