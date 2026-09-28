package cn.iocoder.txgy.module.bidding.controller.admin.tenderdoc;

import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.tenderdoc.vo.TenderDocDownloadRecordReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.tenderdoc.vo.TenderDocDownloadRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.tenderdoc.TenderDocDownloadDO;
import cn.iocoder.txgy.module.bidding.service.tenderdoc.TenderDocDownloadService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * **招标文件下载名单接口层契约测试（standalone，不依赖 Spring 容器/DB）**：以
 * 《后端变更通知-获取文件与资格预审-2026-09-28.md》为准，校验前端拿到的 HTTP 形状——
 * 路径、方法、鉴权权限码、@Idempotent、响应 JSON 字段集合（下载名单条目 10 字段）。
 *
 * <p>为什么这样写：本项目已多次出现"契约写 A、接口返回 B"的静默错位，编译/单测都抓不到。
 * 这里把响应 JSON 字段名集合与契约全等断言，并用反射逐条比对鉴权权限码。
 */
class TenderDocDownloadControllerApiContractTest {

    private TenderDocDownloadService service;
    private TenderDocDownloadController controller;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    /** 契约 §1：下载名单条目响应 10 字段。 */
    private static final Set<String> RESP_FIELDS = new TreeSet<>(List.of(
            "id", "tenderProjectCode", "bidSectionCode", "attachmentId", "fileId",
            "subjectId", "userId", "downloadTime", "ip", "remark"));

    /** 契约 §1：record 请求 6 字段。 */
    private static final Set<String> RECORD_REQ_FIELDS = new TreeSet<>(List.of(
            "tenderProjectCode", "bidSectionCode", "attachmentId", "fileId", "subjectId", "remark"));

    @BeforeEach
    void setUp() {
        service = mock(TenderDocDownloadService.class);
        controller = new TenderDocDownloadController();
        ReflectionTestUtils.setField(controller, "tenderDocDownloadService", service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private TenderDocDownloadDO sampleDo() {
        TenderDocDownloadDO d = new TenderDocDownloadDO();
        d.setId(7001L);
        d.setTenderProjectCode("I6101002026000001001");
        d.setBidSectionCode("I6101002026000001001001");
        d.setAttachmentId(90001L);
        d.setFileId(90002L);
        d.setSubjectId(8001L);
        d.setUserId(1001L);
        d.setDownloadTime(LocalDateTime.of(2026, 9, 28, 10, 0));
        d.setIp("127.0.0.1");
        d.setRemark("契约测试样例");
        return d;
    }

    @Test
    @DisplayName("契约 §1 POST /bidding/tender-doc/record：鉴权 bidding:tender-doc:download + 幂等 + 返回记录ID")
    void record_matchesContract() throws Exception {
        assertHasPermission("record", "bidding:tender-doc:download");
        assertIdempotent("record");

        when(service.record(any())).thenReturn(sampleDo());

        mockMvc.perform(post("/bidding/tender-doc/record")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenderProjectCode\":\"I6101002026000001001\",\"fileId\":90002}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(7001));
    }

    @Test
    @DisplayName("契约 §1 GET /bidding/tender-doc/downloaders：鉴权 bidding:tender-doc:query + 下载名单字段全等（10 字段）")
    void downloaders_matchesContract() throws Exception {
        assertHasPermission("downloaders", "bidding:tender-doc:query");

        when(service.listDownloaders("I6101002026000001001", "I6101002026000001001001"))
                .thenReturn(List.of(sampleDo()));

        String body = mockMvc.perform(get("/bidding/tender-doc/downloaders")
                        .param("tenderProjectCode", "I6101002026000001001")
                        .param("bidSectionCode", "I6101002026000001001001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(7001))
                .andReturn().getResponse().getContentAsString();
        assertRespFieldSet(body, "data[0]", RESP_FIELDS);
    }

    @Test
    @DisplayName("契约 §1 GET /bidding/tender-doc/count：鉴权 bidding:tender-doc:query + 返回人数")
    void count_matchesContract() throws Exception {
        assertHasPermission("count", "bidding:tender-doc:query");

        when(service.countDownloaders("I6101002026000001001001")).thenReturn(5L);

        mockMvc.perform(get("/bidding/tender-doc/count")
                        .param("bidSectionCode", "I6101002026000001001001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(5));
    }

    @Test
    @DisplayName("契约 §1 record 请求字段集合全等（6 字段）")
    void recordRequestFields_matchContract() {
        Set<String> actual = new TreeSet<>();
        for (Field f : TenderDocDownloadRecordReqVO.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            actual.add(f.getName());
        }
        assertEquals(RECORD_REQ_FIELDS, actual,
                "record 请求字段必须与契约 §1 全等：多了=前端不知道，少了=记录缺字段");
    }

    @Test
    @DisplayName("契约 §1 响应 VO 字段集合全等（10 字段）")
    void respVoFields_matchContract() {
        Set<String> actual = new TreeSet<>();
        for (Field f : TenderDocDownloadRespVO.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            actual.add(f.getName());
        }
        assertEquals(RESP_FIELDS, actual, "下载名单响应字段必须与契约 §1 全等：多了=外发内部列，少了=前端拿不到值");
    }

    // ===== 辅助 =====

    private void assertRespFieldSet(String body, String jsonPath, Set<String> expected) throws Exception {
        JsonNode node = objectMapper.readTree(body);
        for (String seg : jsonPath.split("\\.")) {
            int lb = seg.indexOf('[');
            if (lb >= 0 && seg.endsWith("]")) {
                String name = seg.substring(0, lb);
                int idx = Integer.parseInt(seg.substring(lb + 1, seg.length() - 1));
                node = node.path(name).get(idx);
            } else {
                node = node.path(seg);
            }
        }
        Set<String> actual = new TreeSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        assertEquals(expected, actual, "外发字段集必须与契约全等");
    }

    private static void assertHasPermission(String methodName, String permissionCode) {
        Method m = Arrays.stream(TenderDocDownloadController.class.getDeclaredMethods())
                .filter(x -> x.getName().equals(methodName)).findFirst()
                .orElseThrow(() -> new AssertionError("控制器里找不到方法：" + methodName));
        PreAuthorize a = m.getAnnotation(PreAuthorize.class);
        assertNotNull(a, methodName + " 缺少 @PreAuthorize（接口必须判权）");
        assertTrue(a.value().contains(permissionCode),
                methodName + " 鉴权应含 " + permissionCode + "，实际：" + a.value());
    }

    private static void assertIdempotent(String methodName) {
        Method m = Arrays.stream(TenderDocDownloadController.class.getDeclaredMethods())
                .filter(x -> x.getName().equals(methodName)).findFirst().orElseThrow();
        assertNotNull(m.getAnnotation(Idempotent.class),
                methodName + " 是写操作，必须带 @Idempotent");
    }
}
