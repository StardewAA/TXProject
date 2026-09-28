package cn.iocoder.txgy.module.bidding.controller.admin.objection;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.objection.vo.ObjectionReplyReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.objection.vo.ObjectionRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.objection.vo.ObjectionSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.objection.ObjectionDO;
import cn.iocoder.txgy.module.bidding.service.objection.ObjectionService;
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
 * **投标异议接口层契约测试（standalone，不依赖 Spring 容器/DB）**：以
 * 《后端变更通知-异议-2026-09-28.md》为准，校验前端拿到的 HTTP 形状——
 * 路径、方法、鉴权权限码、@Idempotent、响应 JSON 字段集合（异议条目 13 字段）。
 */
class ObjectionControllerApiContractTest {

    private ObjectionService service;
    private ObjectionController controller;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    /** 契约 §1：异议响应 13 字段。 */
    private static final Set<String> RESP_FIELDS = new TreeSet<>(List.of(
            "id", "tenderProjectCode", "bidSectionCode", "objectionType", "subjectId",
            "content", "proposeTime", "status", "replyTime", "replyContent",
            "replyOverdue", "remark", "createTime"));

    /** 契约 §1：propose 请求 6 字段。 */
    private static final Set<String> PROPOSE_REQ_FIELDS = new TreeSet<>(List.of(
            "tenderProjectCode", "bidSectionCode", "objectionType", "subjectId", "content", "remark"));

    /** 契约 §1：reply 请求 2 字段。 */
    private static final Set<String> REPLY_REQ_FIELDS = new TreeSet<>(List.of(
            "objectionId", "replyContent"));

    @BeforeEach
    void setUp() {
        service = mock(ObjectionService.class);
        controller = new ObjectionController();
        ReflectionTestUtils.setField(controller, "objectionService", service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private ObjectionDO sampleDo() {
        ObjectionDO d = new ObjectionDO();
        d.setId(7301L);
        d.setTenderProjectCode("I6101002026000001001");
        d.setBidSectionCode("I6101002026000001001001");
        d.setObjectionType("TENDER_FILE");
        d.setSubjectId(8001L);
        d.setContent("招标文件评分标准存在倾向性");
        d.setProposeTime(LocalDateTime.of(2026, 9, 8, 10, 0));
        d.setStatus("PENDING");
        d.setReplyOverdue(false);
        d.setRemark("契约测试样例");
        return d;
    }

    @Test
    @DisplayName("契约 §1 POST /bidding/objection/propose：鉴权 bidding:objection:propose + 幂等 + 返回记录ID")
    void propose_matchesContract() throws Exception {
        assertHasPermission("propose", "bidding:objection:propose");
        assertIdempotent("propose");

        when(service.propose(any())).thenReturn(7301L);

        mockMvc.perform(post("/bidding/objection/propose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenderProjectCode\":\"I6101002026000001001\",\"bidSectionCode\":\"I6101002026000001001001\",\"objectionType\":\"TENDER_FILE\",\"subjectId\":8001,\"content\":\"评分标准存在倾向性\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(7301));
    }

    @Test
    @DisplayName("契约 §1 POST /bidding/objection/reply：鉴权 bidding:objection:reply + 幂等 + 返回记录ID")
    void reply_matchesContract() throws Exception {
        assertHasPermission("reply", "bidding:objection:reply");
        assertIdempotent("reply");

        when(service.reply(any())).thenReturn(7301L);

        mockMvc.perform(post("/bidding/objection/reply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectionId\":7301,\"replyContent\":\"已复核，评分标准无倾向性\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(7301));
    }

    @Test
    @DisplayName("契约 §1 GET /bidding/objection/get：鉴权 bidding:objection:query + 异议字段全等（13 字段）")
    void get_matchesContract() throws Exception {
        assertHasPermission("get", "bidding:objection:query");

        when(service.get(7301L)).thenReturn(sampleDo());

        String body = mockMvc.perform(get("/bidding/objection/get").param("id", "7301"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(7301))
                .andReturn().getResponse().getContentAsString();
        assertRespFieldSet(body, "data", RESP_FIELDS);
    }

    @Test
    @DisplayName("契约 §1 GET /bidding/objection/page：鉴权 bidding:objection:query + 分页返回")
    void page_matchesContract() throws Exception {
        assertHasPermission("page", "bidding:objection:query");

        when(service.page(any())).thenReturn(new PageResult<>(List.of(sampleDo()), 1L));

        mockMvc.perform(get("/bidding/objection/page").param("pageNo", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(7301));
    }

    @Test
    @DisplayName("契约 §1 propose 请求字段集合全等（6 字段）")
    void proposeRequestFields_matchContract() {
        assertFieldSet(ObjectionSaveReqVO.class, PROPOSE_REQ_FIELDS,
                "propose 请求字段必须与契约全等");
    }

    @Test
    @DisplayName("契约 §1 reply 请求字段集合全等（2 字段）")
    void replyRequestFields_matchContract() {
        assertFieldSet(ObjectionReplyReqVO.class, REPLY_REQ_FIELDS,
                "reply 请求字段必须与契约全等");
    }

    @Test
    @DisplayName("契约 §1 响应 VO 字段集合全等（13 字段）")
    void respVoFields_matchContract() {
        assertFieldSet(ObjectionRespVO.class, RESP_FIELDS,
                "异议响应字段必须与契约 §1 全等：多了=外发内部列，少了=前端拿不到值");
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
        Method m = Arrays.stream(ObjectionController.class.getDeclaredMethods())
                .filter(x -> x.getName().equals(methodName)).findFirst()
                .orElseThrow(() -> new AssertionError("控制器里找不到方法：" + methodName));
        PreAuthorize a = m.getAnnotation(PreAuthorize.class);
        assertNotNull(a, methodName + " 缺少 @PreAuthorize（接口必须判权）");
        assertTrue(a.value().contains(permissionCode),
                methodName + " 鉴权应含 " + permissionCode + "，实际：" + a.value());
    }

    private static void assertIdempotent(String methodName) {
        Method m = Arrays.stream(ObjectionController.class.getDeclaredMethods())
                .filter(x -> x.getName().equals(methodName)).findFirst().orElseThrow();
        assertNotNull(m.getAnnotation(Idempotent.class),
                methodName + " 是写操作，必须带 @Idempotent");
    }
}
