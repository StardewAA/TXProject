package cn.iocoder.txgy.module.bidding.controller.admin.certificate;

import cn.idev.excel.FastExcelFactory;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertVerifyBatchItemRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertVerifyBatchReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertVerifyBatchRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateImportExcelVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateImportFailureVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateImportRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.QualificationController;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationImportExcelVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationImportFailureVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationImportRespVO;
import cn.iocoder.txgy.module.bidding.service.certificate.CertFileVersionService;
import cn.iocoder.txgy.module.bidding.service.certificate.CertificateAlertService;
import cn.iocoder.txgy.module.bidding.service.certificate.CertificateService;
import cn.iocoder.txgy.module.bidding.service.certificate.CertificateVerifyService;
import cn.iocoder.txgy.module.bidding.service.qualification.EnterpriseQualificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * **Q1 证照批量导入 / Q2 批量验真 / Q6 资质批量导入 接口层契约测试（standalone，不依赖 Spring 容器/DB）**：
 * 以《后端变更通知-给前端.md》第 17/18/19 条（实际发前端口径）为据，校验：
 * ① Q1/Q6 导入端点用 **multipart 字段 file** 收件（此前误标 {@code @RequestBody}，multipart 请求会 415，
 *    且 ExcelUtils 只接受 MultipartFile——本测试用真实生成的 Excel 走通「绑定 → 解析 → 服务入参」链路）；
 * ② 行级回执字段集合 {successCount, failureCount, failures[{rowNum, message}]} 与通知一致；
 * ③ Q2 批量验真请求 {certIds, verifyChannel}、回执 {successCount, failureCount, results[{certId, success, message}]}；
 * ④ 三端点权限码与 @Idempotent（写操作幂等）与通知一致。
 */
class CertificateImportVerifyApiContractTest {

    private CertificateService certificateService;
    private CertFileVersionService certFileVersionService;
    private CertificateVerifyService certificateVerifyService;
    private EnterpriseQualificationService enterpriseQualificationService;
    private CertificateAlertService certificateAlertService;

    private CertificateController certificateController;
    private CertificateVerifyController verifyController;
    private QualificationController qualificationController;
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        certificateService = mock(CertificateService.class);
        certFileVersionService = mock(CertFileVersionService.class);
        certificateVerifyService = mock(CertificateVerifyService.class);
        enterpriseQualificationService = mock(EnterpriseQualificationService.class);
        certificateAlertService = mock(CertificateAlertService.class);

        certificateController = new CertificateController();
        ReflectionTestUtils.setField(certificateController, "certificateService", certificateService);
        ReflectionTestUtils.setField(certificateController, "certFileVersionService", certFileVersionService);

        verifyController = new CertificateVerifyController();
        ReflectionTestUtils.setField(verifyController, "certificateVerifyService", certificateVerifyService);

        qualificationController = new QualificationController();
        ReflectionTestUtils.setField(qualificationController, "enterpriseQualificationService", enterpriseQualificationService);
        ReflectionTestUtils.setField(qualificationController, "certificateAlertService", certificateAlertService);

        mockMvc = MockMvcBuilders.standaloneSetup(certificateController, verifyController, qualificationController).build();
    }

    // ===================== Q1 证照批量导入 =====================

    @Test
    @DisplayName("Q1 POST /bidding/certificate/import：multipart file 绑定可用（非 @RequestBody）+ 权限 + 幂等 + 行级回执字段")
    void certificateImport_multipartBindingWorks() throws Exception {
        assertPermissionCode(CertificateController.class, "importCertificates", "bidding:certificate:import");
        assertIdempotent(CertificateController.class, "importCertificates");
        assertImportMethodTakesMultipartFile(CertificateController.class, "importCertificates");

        when(certificateService.importCertificates(anyList(), any())).thenReturn(certificateImportResp());

        byte[] excel = xlsx(CertificateImportExcelVO.class, certificateRow());
        String body = mockMvc.perform(multipart("/bidding/certificate/import")
                        .file(new MockMultipartFile("file", "证照导入.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();

        assertFieldSet(body, "data", Set.of("successCount", "failureCount", "failCount", "totalRows", "failures"));
        assertFieldSet(body, "data.failures[0]", Set.of("rowNum", "message"));

        // 绑定与解析链路真通：服务收到的行数=Excel 数据行数（此前 @RequestBody 时请求在进入方法前即失败）
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<CertificateImportExcelVO>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(certificateService).importCertificates(captor.capture(), any());
        assertEquals(1, captor.getValue().size(), "Excel 应被解析出 1 行并交给服务");
        assertEquals("示例：营业执照", captor.getValue().get(0).getCertName(), "解析出的证照名称应回填到行对象");
    }

    @Test
    @DisplayName("Q1 GET /bidding/certificate/import-template：返回模板附件（无需数据库）")
    void certificateImportTemplate_downloads() throws Exception {
        mockMvc.perform(get("/bidding/certificate/import-template"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")));
    }

    // ===================== Q2 批量验真 =====================

    @Test
    @DisplayName("Q2 POST /bidding/certificate-verify/verify-batch：权限 + 幂等 + 请求/回执字段与通知一致")
    void verifyBatch_matchesNotice() throws Exception {
        assertPermissionCode(CertificateVerifyController.class, "verifyBatch", "bidding:certificate:verify");
        assertIdempotent(CertificateVerifyController.class, "verifyBatch");
        assertEquals(new TreeSet<>(List.of("certIds", "verifyChannel")), declaredFields(CertVerifyBatchReqVO.class),
                "批量验真请求字段应=通知口径 {certIds, verifyChannel}");

        when(certificateVerifyService.verifyCertificateBatch(anyList(), any(), any())).thenReturn(verifyBatchResp());

        String body = mockMvc.perform(post("/bidding/certificate-verify/verify-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"certIds\":[1001,1002]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();

        assertFieldSet(body, "data", Set.of("successCount", "failureCount", "results", "items"));
        assertFieldSet(body, "data.results[0]", Set.of("certId", "certName", "success", "accepted", "verifyResult", "verifyChannel", "message"));
    }

    // ===================== Q6 资质批量导入（同类缺陷一并修复） =====================

    @Test
    @DisplayName("Q6 POST /bidding/qualification/import：multipart file 绑定可用 + 权限 + 幂等 + 行级回执字段")
    void qualificationImport_multipartBindingWorks() throws Exception {
        assertPermissionCode(QualificationController.class, "importQualification", "bidding:qualification:import");
        assertIdempotent(QualificationController.class, "importQualification");
        assertImportMethodTakesMultipartFile(QualificationController.class, "importQualification");

        when(enterpriseQualificationService.importQualifications(anyList(), any()))
                .thenReturn(qualificationImportResp());

        byte[] excel = xlsx(QualificationImportExcelVO.class, qualificationRow());
        String body = mockMvc.perform(multipart("/bidding/qualification/import")
                        .file(new MockMultipartFile("file", "资质导入.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();

        assertFieldSet(body, "data", Set.of("successCount", "failureCount", "failCount", "totalRows", "failures"));
        assertFieldSet(body, "data.failures[0]", Set.of("rowNum", "message"));

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<QualificationImportExcelVO>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(enterpriseQualificationService).importQualifications(captor.capture(), any());
        assertEquals(1, captor.getValue().size(), "Excel 应被解析出 1 行并交给服务");
    }

    // ===================== 数据与辅助 =====================

    private static CertificateImportExcelVO certificateRow() {
        CertificateImportExcelVO row = new CertificateImportExcelVO();
        row.setCertName("示例：营业执照");
        row.setCertNo("91XXXXXXXXXXXXXX");
        row.setCertType("ADMIN_LICENSE");
        row.setHolderName("陕西××建设集团有限公司");
        return row;
    }

    private static QualificationImportExcelVO qualificationRow() {
        QualificationImportExcelVO row = new QualificationImportExcelVO();
        row.setQualSerial("SHI_GONG_ZONG");
        row.setQualCategory("建筑工程施工总承包");
        row.setQualGrade("贰级");
        row.setCertNo("DZ0000000001");
        row.setIssuingAuthority("陕西省住房和城乡建设厅");
        return row;
    }

    private static CertificateImportRespVO certificateImportResp() {
        CertificateImportFailureVO failure = new CertificateImportFailureVO();
        failure.setRowNum(3);
        failure.setMessage("证书编号已存在");
        CertificateImportRespVO resp = new CertificateImportRespVO();
        resp.setSuccessCount(1);
        resp.setFailureCount(1);
        resp.setFailures(List.of(failure));
        return resp;
    }

    private static QualificationImportRespVO qualificationImportResp() {
        QualificationImportFailureVO failure = new QualificationImportFailureVO();
        failure.setRowNum(3);
        failure.setMessage("资质编号已存在");
        QualificationImportRespVO resp = new QualificationImportRespVO();
        resp.setSuccessCount(1);
        resp.setFailureCount(1);
        resp.setFailures(List.of(failure));
        return resp;
    }

    private static CertVerifyBatchRespVO verifyBatchResp() {
        CertVerifyBatchItemRespVO item = new CertVerifyBatchItemRespVO();
        item.setCertId(1001L);
        item.setSuccess(true);
        item.setMessage(null);
        CertVerifyBatchRespVO resp = new CertVerifyBatchRespVO();
        resp.setSuccessCount(1);
        resp.setFailureCount(1);
        resp.setResults(List.of(item));
        return resp;
    }

    /** 用真实 Excel 写出字节：保证读回链路（FastExcel 读 → VO）与前端上传的文件同类。 */
    private static byte[] xlsx(Class<?> head, Object row) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FastExcelFactory.write(out, head).sheet("数据").doWrite(List.of(row));
        return out.toByteArray();
    }

    /** 导入端点必须用 multipart 收件（MultipartFile 形参），不得再出现 {@code @RequestBody} 包装类。 */
    private static void assertImportMethodTakesMultipartFile(Class<?> controllerClass, String methodName) {
        Method method = Arrays.stream(controllerClass.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName)).findFirst()
                .orElseThrow(() -> new AssertionError("找不到方法：" + methodName));
        assertTrue(Arrays.stream(method.getParameterTypes()).anyMatch(t -> t.getName().endsWith("MultipartFile")),
                methodName + " 必须以 MultipartFile 形参收件（multipart 字段 file），否则 multipart 请求无法绑定");
    }

    private static void assertPermissionCode(Class<?> controllerClass, String methodName, String expectedCode) {
        Method method = Arrays.stream(controllerClass.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName)).findFirst()
                .orElseThrow(() -> new AssertionError("找不到方法：" + methodName));
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation, methodName + " 缺少 @PreAuthorize");
        assertTrue(annotation.value().contains(expectedCode),
                methodName + " 鉴权应含 " + expectedCode + "，实际：" + annotation.value());
    }

    private static void assertIdempotent(Class<?> controllerClass, String methodName) {
        Method method = Arrays.stream(controllerClass.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName)).findFirst().orElseThrow();
        assertNotNull(method.getAnnotation(Idempotent.class), methodName + " 是写操作，必须带 @Idempotent");
    }

    private static Set<String> declaredFields(Class<?> clazz) {
        Set<String> fields = new TreeSet<>();
        for (Field field : clazz.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                fields.add(field.getName());
            }
        }
        return fields;
    }

    private void assertFieldSet(String body, String jsonPath, Set<String> expected) throws Exception {
        JsonNode node = objectMapper.readTree(body);
        for (String seg : jsonPath.split("\\.")) {
            int lb = seg.indexOf('[');
            if (lb >= 0 && seg.endsWith("]")) {
                String name = seg.substring(0, lb);
                int index = Integer.parseInt(seg.substring(lb + 1, seg.length() - 1));
                node = node.path(name).get(index);
            } else {
                node = node.path(seg);
            }
        }
        Set<String> actual = new TreeSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        assertEquals(expected, actual, "外发字段集必须与通知口径全等");
    }
}
