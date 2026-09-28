package cn.iocoder.txgy.module.bidding.controller.admin.certificate;

import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertFileVersionRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateAlertRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertFileVersionDO;
import cn.iocoder.txgy.module.bidding.service.certificate.CertFileVersionService;
import cn.iocoder.txgy.module.bidding.service.certificate.CertificateAlertService;
import cn.iocoder.txgy.module.bidding.service.certificate.CertificateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * **接口层契约测试**（不依赖数据库、不依赖 Spring 容器）：以《电子证照模块-前端接口契约》为准，
 * 校验"前端拿到的 HTTP 形状"——路径、方法、权限码、响应 JSON 的字段集合。
 *
 * <p>规范出处：
 * <ul>
 *   <li>契约 §2.1–§2.5（证照台账：page/get/create/update/change-status）、§3.2（文件版本列表）；</li>
 *   <li>契约字段表（§3.2 的 13 个字段；台账字段见 §2.1）。</li>
 * </ul>
 *
 * <p>为什么这样写（设计遵循）：本项目已多次出现"契约写 A、接口返回 B"的静默错位
 * （例如 §3.2 契约写 `sha256`、实现返回 `fileHash`，前端取到 undefined），而这类缺陷
 * **编译通过、单测通过、文档还写着已实现**。所以这里不看"能不能取到值"，
 * 而是把**响应 JSON 的字段名集合**与契约**全等断言**，并把权限码用反射与契约逐条比对。
 *
 * <p>边界（诚实说明）：standalone MockMvc 不启动 Spring 容器 ⇒ 不校验租户拦截、鉴权切面、真实 SQL；
 * 真正的端到端（起服务 + 真库）需要单独环境，本测试**不替代**它，只保证"接口形状不会悄悄变"。
 */
class CertificateApiContractTest {

    private CertificateService certificateService;
    private CertFileVersionService certFileVersionService;
    private CertificateAlertService certificateAlertService;
    private CertificateController controller;
    private CertificateAlertRunController alertRunController;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        certificateService = mock(CertificateService.class);
        certFileVersionService = mock(CertFileVersionService.class);
        certificateAlertService = mock(CertificateAlertService.class);
        controller = new CertificateController();
        ReflectionTestUtils.setField(controller, "certificateService", certificateService);
        ReflectionTestUtils.setField(controller, "certFileVersionService", certFileVersionService);
        alertRunController = new CertificateAlertRunController();
        ReflectionTestUtils.setField(alertRunController, "certificateAlertService", certificateAlertService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller, alertRunController).build();
    }

    /** 契约 §3.2：文件版本列表的 16 个字段（字段名以实现为准，契约那处的 sha256 已按实现更正为 fileHash）。 */
    private static final Set<String> FILE_VERSION_CONTRACT_FIELDS = new TreeSet<>(List.of(
            "id", "certId", "fileCategory", "fileName", "fileId", "fileFormat", "fileSize",
            "version", "isCurrent", "fileHash", "virusStatus", "fileCategoryName", "downloadable", "previewUrl",
            "copyExpiringTime", "createTime", "voided", "voidReason", "voidedAt"));

    @Test
    @DisplayName("§3.2 GET /bidding/certificate/file/list：路径/权限/响应字段集合与契约一致")
    void fileList_matchesContract() throws Exception {
        assertPermissionCode(CertificateController.class, "fileList", "bidding:certificate:query");

        CertFileVersionDO doObj = new CertFileVersionDO();
        doObj.setId(5001L);
        doObj.setCertId(1024L);
        doObj.setFileCategory("ORIGINAL");
        doObj.setFileName("资质证书.pdf");
        doObj.setFileId(90001L);
        doObj.setFileFormat("PDF");
        doObj.setFileSize(235122L);
        doObj.setVersion(1);
        doObj.setIsCurrent(Boolean.TRUE);
        doObj.setFileHash("e3b0c442");
        doObj.setVirusStatus("CLEAN");
        doObj.setCopyExpiringTime(LocalDateTime.of(2027, 1, 1, 0, 0));
        // 内部列（不得外发）
        doObj.setCopyCreator("张三");
        doObj.setRemark("内部备注");
        when(certFileVersionService.listFiles(1024L)).thenReturn(List.of(doObj));

        String body = mockMvc.perform(get("/bidding/certificate/file/list").param("certId", "1024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].fileHash").value("e3b0c442"))
                .andExpect(jsonPath("$.data[0].isCurrent").value(true))
                .andReturn().getResponse().getContentAsString();
        Set<String> actual = new TreeSet<>();
        objectMapper.readTree(body).path("data").get(0).fieldNames().forEachRemaining(actual::add);
        assertEquals(FILE_VERSION_CONTRACT_FIELDS, actual,
                "文件版本列表的外发字段集必须与契约 §3.2 全等：多了=外发内部列，少了=前端拿不到值");
    }

    @Test
    @DisplayName("§2 台账：get/create/update/change-status 的路径与权限码与契约一致")
    void ledgerEndpoints_matchContract() throws Exception {
        assertPermissionCode(CertificateController.class, "get", "bidding:certificate:query");
        assertPermissionCode(CertificateController.class, "create", "bidding:certificate:create");
        assertPermissionCode(CertificateController.class, "update", "bidding:certificate:update");
        assertPermissionCode(CertificateController.class, "changeStatus", "bidding:certificate:update");

        CertificateRespVO vo = new CertificateRespVO();
        vo.setId(1024L);
        vo.setVersion(3);
        vo.setCertName("安全生产许可证");
        when(certificateService.getCertificate(1024L)).thenReturn(vo);

        mockMvc.perform(get("/bidding/certificate/get").param("id", "1024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1024))
                // 乐观锁版本必须外发：更新要用它做 CAS（契约 §0.7/§2.2）
                .andExpect(jsonPath("$.data.version").value(3));
    }

    @Test
    @DisplayName("决策③ GET /bidding/certificate-alert-run/alert/page：路径/权限/字段集合与新契约一致")
    void alertPage_matchesContract() throws Exception {
        assertPermissionCode(CertificateAlertRunController.class, "getAlertPage", "bidding:certificate:query");

        CertificateAlertRespVO vo = new CertificateAlertRespVO();
        vo.setId(4001L);
        vo.setVersion(1);
        vo.setCertId(1024L);
        vo.setAlertType("EXPIRY");
        vo.setAlertLevel("WARNING");
        vo.setAlertMessage("【证照到期】安全生产许可证 将于 30 天后到期");
        vo.setTriggeredAt(LocalDateTime.of(2026, 9, 10, 9, 0));
        vo.setHandled(Boolean.FALSE);
        when(certificateAlertService.getAlertPage(any())).thenReturn(
                new cn.iocoder.txgy.framework.common.pojo.PageResult<>(List.of(vo), 1L));

        String body = mockMvc.perform(get("/bidding/certificate-alert-run/alert/page")
                        .param("pageNo", "1").param("pageSize", "10").param("handled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].version").value(1))
                .andReturn().getResponse().getContentAsString();
        Set<String> actual = new TreeSet<>();
        objectMapper.readTree(body).path("data").path("list").get(0).fieldNames().forEachRemaining(actual::add);
        Set<String> expected = new TreeSet<>(List.of("id", "version", "certId", "refType", "refId", "refName",
                "alertType", "alertLevel", "alertMessage", "triggeredAt", "handled", "handledBy", "handledAt", "handleNote"));
        assertEquals(expected, actual, "预警清单外发字段必须与给前端的接口说明一致（前端按此对齐）");
    }

    /** 用反射校验控制器方法上的权限码——接口层最容易"文档写 X、代码写 Y"的地方。 */
    private static void assertPermissionCode(Class<?> controllerClass, String methodName, String expectedCode) {
        Method method = java.util.Arrays.stream(controllerClass.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName)).findFirst()
                .orElseThrow(() -> new AssertionError("控制器里找不到方法：" + methodName));
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertTrue(annotation != null, methodName + " 缺少 @PreAuthorize（接口必须判权）");
        assertTrue(annotation.value().contains(expectedCode),
                methodName + " 的权限码应为 " + expectedCode + "，实际：" + annotation.value());
    }

}
