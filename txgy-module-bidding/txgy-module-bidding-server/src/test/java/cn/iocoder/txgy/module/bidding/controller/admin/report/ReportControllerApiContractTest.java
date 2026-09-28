package cn.iocoder.txgy.module.bidding.controller.admin.report;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.report.vo.ReportRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.report.vo.ReportSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.report.ReportDO;
import cn.iocoder.txgy.module.bidding.service.report.ReportService;
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
 * **定标书面报告接口层契约测试（standalone，不依赖 Spring 容器/DB）**：以
 * 《后端变更通知-定标书面报告-2026-09-28.md》为准，校验前端拿到的 HTTP 形状——
 * 路径、方法、鉴权权限码、@Idempotent、响应 JSON 字段集合（报告条目 12 字段）。
 */
class ReportControllerApiContractTest {

    private ReportService service;
    private ReportController controller;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    /** 契约 §1：报告响应 12 字段。 */
    private static final Set<String> RESP_FIELDS = new TreeSet<>(List.of(
            "id", "tenderProjectCode", "bidSectionCode", "reportCode", "reportName",
            "fileId", "submitTime", "awardTime", "status", "reportOverdue",
            "remark", "createTime"));

    /** 契约 §1：submit 请求 6 字段。 */
    private static final Set<String> SUBMIT_REQ_FIELDS = new TreeSet<>(List.of(
            "tenderProjectCode", "bidSectionCode", "reportCode", "reportName", "fileId", "remark"));

    @BeforeEach
    void setUp() {
        service = mock(ReportService.class);
        controller = new ReportController();
        ReflectionTestUtils.setField(controller, "reportService", service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private ReportDO sampleDo() {
        ReportDO d = new ReportDO();
        d.setId(7401L);
        d.setTenderProjectCode("I6101002026000001001");
        d.setBidSectionCode("I6101002026000001001001");
        d.setReportCode("R20260928001");
        d.setReportName("招标投标情况书面报告");
        d.setFileId(9001L);
        d.setSubmitTime(LocalDateTime.of(2026, 9, 28, 10, 0));
        d.setAwardTime(LocalDateTime.of(2026, 9, 20, 15, 0));
        d.setStatus("SUBMITTED");
        d.setReportOverdue(false);
        d.setRemark("契约测试样例");
        return d;
    }

    @Test
    @DisplayName("契约 §1 POST /bidding/report/submit：鉴权 bidding:report:submit + 幂等 + 返回记录ID")
    void submit_matchesContract() throws Exception {
        assertHasPermission("submit", "bidding:report:submit");
        assertIdempotent("submit");

        when(service.submit(any())).thenReturn(7401L);

        mockMvc.perform(post("/bidding/report/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenderProjectCode\":\"I6101002026000001001\",\"reportCode\":\"R20260928001\",\"reportName\":\"招标投标情况书面报告\",\"fileId\":9001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(7401));
    }

    @Test
    @DisplayName("契约 §1 GET /bidding/report/get：鉴权 bidding:report:query + 报告字段全等（12 字段）")
    void get_matchesContract() throws Exception {
        assertHasPermission("get", "bidding:report:query");

        when(service.get(7401L)).thenReturn(sampleDo());

        String body = mockMvc.perform(get("/bidding/report/get").param("id", "7401"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(7401))
                .andReturn().getResponse().getContentAsString();
        assertRespFieldSet(body, "data", RESP_FIELDS);
    }

    @Test
    @DisplayName("契约 §1 GET /bidding/report/page：鉴权 bidding:report:query + 分页返回")
    void page_matchesContract() throws Exception {
        assertHasPermission("page", "bidding:report:query");

        when(service.page(any())).thenReturn(new PageResult<>(List.of(sampleDo()), 1L));

        mockMvc.perform(get("/bidding/report/page").param("pageNo", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(7401));
    }

    @Test
    @DisplayName("契约 §1 submit 请求字段集合全等（6 字段）")
    void submitRequestFields_matchContract() {
        assertFieldSet(ReportSaveReqVO.class, SUBMIT_REQ_FIELDS,
                "submit 请求字段必须与契约全等");
    }

    @Test
    @DisplayName("契约 §1 响应 VO 字段集合全等（12 字段）")
    void respVoFields_matchContract() {
        assertFieldSet(ReportRespVO.class, RESP_FIELDS,
                "报告响应字段必须与契约 §1 全等：多了=外发内部列，少了=前端拿不到值");
    }

    // ===== 辅助 =====

    private static void assertFieldSet(Class<?> clazz, Set<String> expected, String message) {
        Set<String> actual = new TreeSet<>();
        for (Field f : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            actual.add(f.getName());
        }
        assertEquals(expected, actual, message);
    }

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
        Method m = Arrays.stream(ReportController.class.getDeclaredMethods())
                .filter(x -> x.getName().equals(methodName)).findFirst()
                .orElseThrow(() -> new AssertionError("控制器里找不到方法：" + methodName));
        PreAuthorize a = m.getAnnotation(PreAuthorize.class);
        assertNotNull(a, methodName + " 缺少 @PreAuthorize（接口必须判权）");
        assertTrue(a.value().contains(permissionCode),
                methodName + " 鉴权应含 " + permissionCode + "，实际：" + a.value());
    }

    private static void assertIdempotent(String methodName) {
        Method m = Arrays.stream(ReportController.class.getDeclaredMethods())
                .filter(x -> x.getName().equals(methodName)).findFirst().orElseThrow();
        assertNotNull(m.getAnnotation(Idempotent.class),
                methodName + " 是写操作，必须带 @Idempotent");
    }
}
