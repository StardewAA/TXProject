package cn.iocoder.txgy.module.bidding.controller.admin.prequal;

import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo.PrequalApplyReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo.PrequalApplyRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo.PrequalRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo.PrequalReviewReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo.PrequalSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.prequal.PrequalApplyDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.prequal.PrequalDO;
import cn.iocoder.txgy.module.bidding.service.prequal.PrequalApplyService;
import cn.iocoder.txgy.module.bidding.service.prequal.PrequalService;
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
 * **资格预审接口层契约测试（standalone，不依赖 Spring 容器/DB）**：以
 * 《后端变更通知-获取文件与资格预审-2026-09-28.md》为准，校验 9 个接口的
 * 路径、方法、鉴权权限码、@Idempotent、响应 JSON 字段集合。
 */
class PrequalControllerApiContractTest {

    private PrequalService prequalService;
    private PrequalApplyService prequalApplyService;
    private PrequalController controller;
    private MockMvc mockMvc;
    private cn.iocoder.txgy.module.bidding.service.attachment.AttachmentService attachmentService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    /** 契约 §2：资格预审主记录响应 12 字段。 */
    private static final Set<String> PREQUAL_RESP_FIELDS = new TreeSet<>(List.of(
            "id", "prequalCode", "tenderProjectCode", "bidSectionCode", "title", "noticeCode",
            "fileId", "applyStartTime", "applyDeadline", "status", "remark", "createTime"));

    /**
     * 契约 §2：资格预审申请响应 13 字段 + 多文件化 R3 新增 attachments（可为空数组）——
     * 14 字段（无 remark——申请备注不外发）。
     */
    private static final Set<String> APPLY_RESP_FIELDS = new TreeSet<>(List.of(
            "id", "prequalId", "prequalCode", "tenderProjectCode", "bidSectionCode",
            "subjectId", "userId", "fileId", "applyTime", "status",
            "reviewResult", "reviewReason", "reviewTime", "attachments"));

    /** 契约 §2：save 请求 9 字段。 */
    private static final Set<String> SAVE_REQ_FIELDS = new TreeSet<>(List.of(
            "id", "tenderProjectCode", "bidSectionCode", "title", "noticeCode",
            "fileId", "applyStartTime", "applyDeadline", "remark"));

    /** 契约 §2：apply 请求 4 字段。 */
    private static final Set<String> APPLY_REQ_FIELDS = new TreeSet<>(List.of(
            "prequalId", "subjectId", "fileId", "remark"));

    /** 契约 §2：review 请求 3 字段。 */
    private static final Set<String> REVIEW_REQ_FIELDS = new TreeSet<>(List.of(
            "applyId", "passed", "reviewReason"));

    @BeforeEach
    void setUp() {
        prequalService = mock(PrequalService.class);
        prequalApplyService = mock(PrequalApplyService.class);
        attachmentService = mock(cn.iocoder.txgy.module.bidding.service.attachment.AttachmentService.class);
        controller = new PrequalController();
        ReflectionTestUtils.setField(controller, "prequalService", prequalService);
        ReflectionTestUtils.setField(controller, "prequalApplyService", prequalApplyService);
        ReflectionTestUtils.setField(controller, "attachmentService", attachmentService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private PrequalDO samplePrequalDo() {
        PrequalDO d = new PrequalDO();
        d.setId(7001L);
        d.setPrequalCode("P6101002026000001001");
        d.setTenderProjectCode("I6101002026000001001");
        d.setBidSectionCode("I6101002026000001001001");
        d.setTitle("某项目资格预审");
        d.setNoticeCode("B6101002026000001001");
        d.setFileId(90001L);
        d.setApplyStartTime(LocalDateTime.of(2026, 9, 20, 9, 0));
        d.setApplyDeadline(LocalDateTime.of(2026, 9, 27, 17, 0));
        d.setStatus("PUBLISHED");
        d.setRemark("契约测试样例");
        d.setCreateTime(LocalDateTime.of(2026, 9, 19, 10, 0));
        return d;
    }

    private PrequalApplyDO sampleApplyDo() {
        PrequalApplyDO d = new PrequalApplyDO();
        d.setId(2001L);
        d.setPrequalId(7001L);
        d.setPrequalCode("P6101002026000001001");
        d.setTenderProjectCode("I6101002026000001001");
        d.setBidSectionCode("I6101002026000001001001");
        d.setSubjectId(8001L);
        d.setUserId(1001L);
        d.setFileId(90002L);
        d.setApplyTime(LocalDateTime.of(2026, 9, 21, 10, 0));
        d.setStatus("SUBMITTED");
        d.setReviewResult(null);
        d.setReviewReason(null);
        d.setReviewTime(null);
        d.setRemark("契约测试样例");
        return d;
    }

    @Test
    @DisplayName("契约 §2 九个接口鉴权权限码全等（save/publish/close/get/page/apply/review/apply-list/apply-page）")
    void permissions_matchesContract() {
        assertHasPermission("save", "bidding:prequal:save");
        assertHasPermission("publish", "bidding:prequal:publish");
        assertHasPermission("close", "bidding:prequal:close");
        assertHasPermission("get", "bidding:prequal:query");
        assertHasPermission("page", "bidding:prequal:query");
        assertHasPermission("apply", "bidding:prequal:apply");
        assertHasPermission("review", "bidding:prequal:review");
        assertHasPermission("applyList", "bidding:prequal:query");
        assertHasPermission("applyPage", "bidding:prequal:query");
    }

    @Test
    @DisplayName("契约 §2 写方法（save/publish/close/apply/review）带 @Idempotent")
    void idempotent_matchesContract() {
        assertIdempotent("save");
        assertIdempotent("publish");
        assertIdempotent("close");
        assertIdempotent("apply");
        assertIdempotent("review");
    }

    @Test
    @DisplayName("契约 §2 POST /bidding/prequal/save：返回资格预审ID")
    void save_matchesContract() throws Exception {
        when(prequalService.save(any())).thenReturn(7001L);

        mockMvc.perform(post("/bidding/prequal/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenderProjectCode\":\"I6101002026000001001\",\"title\":\"某项目资格预审\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(7001));
    }

    @Test
    @DisplayName("契约 §2 POST /bidding/prequal/apply：返回申请ID")
    void apply_matchesContract() throws Exception {
        when(prequalApplyService.apply(any())).thenReturn(2001L);

        mockMvc.perform(post("/bidding/prequal/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prequalId\":7001,\"fileId\":90002}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(2001));
    }

    @Test
    @DisplayName("契约 §2 GET /bidding/prequal/get：主记录响应字段全等（12 字段）")
    void get_matchesContract() throws Exception {
        when(prequalService.get(7001L)).thenReturn(samplePrequalDo());

        String body = mockMvc.perform(get("/bidding/prequal/get").param("id", "7001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(7001))
                .andReturn().getResponse().getContentAsString();
        assertRespFieldSet(body, "data", PREQUAL_RESP_FIELDS);
    }

    @Test
    @DisplayName("契约 §2 GET /bidding/prequal/apply-list：申请响应字段全等（13 字段，无 remark）")
    void applyList_matchesContract() throws Exception {
        when(prequalApplyService.listByPrequal(7001L)).thenReturn(List.of(sampleApplyDo()));

        String body = mockMvc.perform(get("/bidding/prequal/apply-list").param("prequalId", "7001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(2001))
                .andReturn().getResponse().getContentAsString();
        assertRespFieldSet(body, "data[0]", APPLY_RESP_FIELDS);
    }

    @Test
    @DisplayName("契约 §2 请求 VO 字段集合全等（save 9 / apply 4 / review 3）")
    void requestVoFields_matchContract() {
        assertEquals(SAVE_REQ_FIELDS, declaredFields(PrequalSaveReqVO.class), "save 请求字段必须与契约全等");
        assertEquals(APPLY_REQ_FIELDS, declaredFields(PrequalApplyReqVO.class), "apply 请求字段必须与契约全等");
        assertEquals(REVIEW_REQ_FIELDS, declaredFields(PrequalReviewReqVO.class), "review 请求字段必须与契约全等");
    }

    @Test
    @DisplayName("契约 §2 响应 VO 字段集合全等（主记录 12 / 申请 13）")
    void respVoFields_matchContract() {
        assertEquals(PREQUAL_RESP_FIELDS, declaredFields(PrequalRespVO.class), "主记录响应字段必须与契约全等");
        assertEquals(APPLY_RESP_FIELDS, declaredFields(PrequalApplyRespVO.class), "申请响应字段必须与契约全等");
    }

    // ===== 辅助 =====

    private static Set<String> declaredFields(Class<?> clazz) {
        Set<String> actual = new TreeSet<>();
        for (Field f : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            actual.add(f.getName());
        }
        return actual;
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
        Method m = Arrays.stream(PrequalController.class.getDeclaredMethods())
                .filter(x -> x.getName().equals(methodName)).findFirst()
                .orElseThrow(() -> new AssertionError("控制器里找不到方法：" + methodName));
        PreAuthorize a = m.getAnnotation(PreAuthorize.class);
        assertNotNull(a, methodName + " 缺少 @PreAuthorize（接口必须判权）");
        assertTrue(a.value().contains(permissionCode),
                methodName + " 鉴权应含 " + permissionCode + "，实际：" + a.value());
    }

    private static void assertIdempotent(String methodName) {
        Method m = Arrays.stream(PrequalController.class.getDeclaredMethods())
                .filter(x -> x.getName().equals(methodName)).findFirst().orElseThrow();
        assertNotNull(m.getAnnotation(Idempotent.class),
                methodName + " 是写操作，必须带 @Idempotent");
    }
}
