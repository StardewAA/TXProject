package cn.iocoder.txgy.module.bidding.controller.admin.deposit;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.deposit.vo.BidDepositRefundReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.deposit.vo.BidDepositRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.deposit.vo.BidDepositSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.deposit.BidDepositDO;
import cn.iocoder.txgy.module.bidding.service.deposit.BidDepositService;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * **投标保证金接口层契约测试（standalone，不依赖 Spring 容器/DB）**：以
 * 《后端变更通知-投标保证金-2026-09-28.md》为准，校验前端拿到的 HTTP 形状——
 * 路径、方法、鉴权权限码、@Idempotent、响应 JSON 字段集合（保证金条目 14 字段）。
 *
 * <p>为什么这样写：本项目已多次出现"契约写 A、接口返回 B"的静默错位，编译/单测都抓不到。
 * 这里把响应 JSON 字段名集合与契约全等断言，并用反射逐条比对鉴权权限码。
 */
class BidDepositControllerApiContractTest {

    private BidDepositService service;
    private BidDepositController controller;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    /** 契约 §1：保证金响应 15 字段。 */
    private static final Set<String> RESP_FIELDS = new TreeSet<>(List.of(
            "id", "bidSectionCode", "tenderProjectCode", "subjectId", "amount",
            "currencyCode", "payType", "status", "fileId", "depositTime",
            "refundTime", "refundStatus", "refundOverdue", "remark", "createTime"));

    /** 契约 §1：save 请求 8 字段。 */
    private static final Set<String> SAVE_REQ_FIELDS = new TreeSet<>(List.of(
            "bidSectionCode", "subjectId", "amount", "currencyCode", "payType",
            "status", "fileId", "remark"));

    /** 契约 §1：refund 请求 3 字段。 */
    private static final Set<String> REFUND_REQ_FIELDS = new TreeSet<>(List.of(
            "depositId", "refundStatus", "remark"));

    @BeforeEach
    void setUp() {
        service = mock(BidDepositService.class);
        controller = new BidDepositController();
        ReflectionTestUtils.setField(controller, "bidDepositService", service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private BidDepositDO sampleDo() {
        BidDepositDO d = new BidDepositDO();
        d.setId(7101L);
        d.setBidSectionCode("I6101002026000001001001");
        d.setTenderProjectCode("I6101002026000001001");
        d.setSubjectId(8001L);
        d.setAmount(new BigDecimal("10000.00"));
        d.setCurrencyCode("CNY");
        d.setPayType("01");
        d.setStatus("PAID");
        d.setFileId(90003L);
        d.setDepositTime(LocalDateTime.of(2026, 9, 28, 10, 0));
        d.setRefundTime(null);
        d.setRefundStatus("PENDING");
        d.setRemark("契约测试样例");
        return d;
    }

    @Test
    @DisplayName("契约 §1 POST /bidding/deposit/save：鉴权 bidding:deposit:save + 幂等 + 返回记录ID")
    void save_matchesContract() throws Exception {
        assertHasPermission("save", "bidding:deposit:save");
        assertIdempotent("save");

        when(service.save(any())).thenReturn(7101L);

        mockMvc.perform(post("/bidding/deposit/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bidSectionCode\":\"I6101002026000001001001\",\"subjectId\":8001,\"amount\":10000.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(7101));
    }

    @Test
    @DisplayName("契约 §1 GET /bidding/deposit/get：鉴权 bidding:deposit:query + 保证金字段全等（14 字段）")
    void get_matchesContract() throws Exception {
        assertHasPermission("get", "bidding:deposit:query");

        when(service.get(7101L)).thenReturn(sampleDo());

        String body = mockMvc.perform(get("/bidding/deposit/get").param("id", "7101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(7101))
                .andReturn().getResponse().getContentAsString();
        assertRespFieldSet(body, "data", RESP_FIELDS);
    }

    @Test
    @DisplayName("契约 §1 GET /bidding/deposit/page：鉴权 bidding:deposit:query + 分页返回")
    void page_matchesContract() throws Exception {
        assertHasPermission("page", "bidding:deposit:query");

        when(service.page(any())).thenReturn(new PageResult<>(List.of(sampleDo()), 1L));

        mockMvc.perform(get("/bidding/deposit/page").param("pageNo", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(7101));
    }

    @Test
    @DisplayName("契约 §1 save 请求字段集合全等（8 字段）")
    void saveRequestFields_matchContract() {
        Set<String> actual = new TreeSet<>();
        for (Field f : BidDepositSaveReqVO.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            actual.add(f.getName());
        }
        assertEquals(SAVE_REQ_FIELDS, actual,
                "save 请求字段必须与契约 §1 全等：多了=前端不知道，少了=登记缺字段");
    }

    @Test
    @DisplayName("契约 §1 响应 VO 字段集合全等（15 字段）")
    void respVoFields_matchContract() {
        Set<String> actual = new TreeSet<>();
        for (Field f : BidDepositRespVO.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            actual.add(f.getName());
        }
        assertEquals(RESP_FIELDS, actual, "保证金响应字段必须与契约 §1 全等：多了=外发内部列，少了=前端拿不到值");
    }

    @Test
    @DisplayName("契约 §1 POST /bidding/deposit/refund：鉴权 bidding:deposit:refund + 幂等 + 返回记录ID")
    void refund_matchesContract() throws Exception {
        assertHasPermission("refund", "bidding:deposit:refund");
        assertIdempotent("refund");

        when(service.refund(any())).thenReturn(7101L);

        mockMvc.perform(post("/bidding/deposit/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"depositId\":7101,\"refundStatus\":\"REFUNDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(7101));
    }

    @Test
    @DisplayName("契约 §1 refund 请求字段集合全等（3 字段）")
    void refundRequestFields_matchContract() {
        Set<String> actual = new TreeSet<>();
        for (Field f : BidDepositRefundReqVO.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            actual.add(f.getName());
        }
        assertEquals(REFUND_REQ_FIELDS, actual,
                "refund 请求字段必须与契约 §1 全等：多了=前端不知道，少了=退还缺字段");
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
        Method m = Arrays.stream(BidDepositController.class.getDeclaredMethods())
                .filter(x -> x.getName().equals(methodName)).findFirst()
                .orElseThrow(() -> new AssertionError("控制器里找不到方法：" + methodName));
        PreAuthorize a = m.getAnnotation(PreAuthorize.class);
        assertNotNull(a, methodName + " 缺少 @PreAuthorize（接口必须判权）");
        assertTrue(a.value().contains(permissionCode),
                methodName + " 鉴权应含 " + permissionCode + "，实际：" + a.value());
    }

    private static void assertIdempotent(String methodName) {
        Method m = Arrays.stream(BidDepositController.class.getDeclaredMethods())
                .filter(x -> x.getName().equals(methodName)).findFirst().orElseThrow();
        assertNotNull(m.getAnnotation(Idempotent.class),
                methodName + " 是写操作，必须带 @Idempotent");
    }
}
