package cn.iocoder.txgy.module.bidding.controller.admin.contract;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.contract.vo.ContractRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.contract.vo.ContractSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.contract.ContractDO;
import cn.iocoder.txgy.module.bidding.service.contract.ContractService;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * **工程合同接口层契约测试（standalone，不依赖 Spring 容器/DB）**：以
 * 《后端变更通知-合同签署-2026-09-28.md》为准，校验前端拿到的 HTTP 形状——
 * 路径、方法、鉴权权限码、@Idempotent、响应 JSON 字段集合（合同条目 17 字段）。
 */
class ContractControllerApiContractTest {

    private ContractService service;
    private ContractController controller;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    /** 契约 §1：合同响应 17 字段（2026-09-28 增补归档三列 archiveTime/retentionUntil/retentionWarn
     *  与一致性比对列 compareResult，见《后端变更通知-存档15年归档锁-2026-09-28.md》/《后端变更通知-合同一致性比对-2026-09-28.md》）。 */
    private static final Set<String> RESP_FIELDS = new TreeSet<>(List.of(
            "id", "tenderProjectCode", "bidSectionCode", "contractCode", "contractName",
            "contractPrice", "performanceBond", "winNoticeTime", "signTime", "fileId",
            "status", "archiveTime", "retentionUntil", "retentionWarn", "compareResult",
            "remark", "createTime"));

    /** 契约 §1：save 请求 11 字段。 */
    private static final Set<String> SAVE_REQ_FIELDS = new TreeSet<>(List.of(
            "tenderProjectCode", "bidSectionCode", "contractCode", "contractName", "contractPrice",
            "performanceBond", "winNoticeTime", "signTime", "fileId", "status", "remark"));

    @BeforeEach
    void setUp() {
        service = mock(ContractService.class);
        controller = new ContractController();
        ReflectionTestUtils.setField(controller, "contractService", service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private ContractDO sampleDo() {
        ContractDO d = new ContractDO();
        d.setId(7201L);
        d.setTenderProjectCode("I6101002026000001001");
        d.setBidSectionCode("I6101002026000001001001");
        d.setContractCode("C20260928001");
        d.setContractName("某项目施工合同");
        d.setContractPrice(new BigDecimal("1000000.00"));
        d.setPerformanceBond(new BigDecimal("100000.00"));
        d.setWinNoticeTime(LocalDateTime.of(2026, 9, 1, 9, 0));
        d.setSignTime(LocalDateTime.of(2026, 9, 20, 10, 0));
        d.setFileId(90004L);
        d.setStatus("SIGNED");
        d.setRemark("契约测试样例");
        return d;
    }

    @Test
    @DisplayName("契约 §1 POST /bidding/contract/save：鉴权 bidding:contract:save + 幂等 + 返回记录ID")
    void save_matchesContract() throws Exception {
        assertHasPermission("save", "bidding:contract:save");
        assertIdempotent("save");

        when(service.save(any())).thenReturn(7201L);

        mockMvc.perform(post("/bidding/contract/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenderProjectCode\":\"I6101002026000001001\",\"contractCode\":\"C20260928001\",\"contractPrice\":1000000.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(7201));
    }

    @Test
    @DisplayName("契约 §1 GET /bidding/contract/get：鉴权 bidding:contract:query + 合同字段全等（17 字段）")
    void get_matchesContract() throws Exception {
        assertHasPermission("get", "bidding:contract:query");

        when(service.get(7201L)).thenReturn(sampleDo());

        String body = mockMvc.perform(get("/bidding/contract/get").param("id", "7201"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(7201))
                .andReturn().getResponse().getContentAsString();
        assertRespFieldSet(body, "data", RESP_FIELDS);
    }

    @Test
    @DisplayName("契约 §1 GET /bidding/contract/page：鉴权 bidding:contract:query + 分页返回")
    void page_matchesContract() throws Exception {
        assertHasPermission("page", "bidding:contract:query");

        when(service.page(any())).thenReturn(new PageResult<>(List.of(sampleDo()), 1L));

        mockMvc.perform(get("/bidding/contract/page").param("pageNo", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(7201));
    }

    @Test
    @DisplayName("契约 §1 save 请求字段集合全等（11 字段）")
    void saveRequestFields_matchContract() {
        Set<String> actual = new TreeSet<>();
        for (Field f : ContractSaveReqVO.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            actual.add(f.getName());
        }
        assertEquals(SAVE_REQ_FIELDS, actual,
                "save 请求字段必须与契约 §1 全等：多了=前端不知道，少了=登记缺字段");
    }

    @Test
    @DisplayName("契约 §1 响应 VO 字段集合全等（17 字段）")
    void respVoFields_matchContract() {
        Set<String> actual = new TreeSet<>();
        for (Field f : ContractRespVO.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            actual.add(f.getName());
        }
        assertEquals(RESP_FIELDS, actual, "合同响应字段必须与契约 §1 全等：多了=外发内部列，少了=前端拿不到值");
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
        Method m = Arrays.stream(ContractController.class.getDeclaredMethods())
                .filter(x -> x.getName().equals(methodName)).findFirst()
                .orElseThrow(() -> new AssertionError("控制器里找不到方法：" + methodName));
        PreAuthorize a = m.getAnnotation(PreAuthorize.class);
        assertNotNull(a, methodName + " 缺少 @PreAuthorize（接口必须判权）");
        assertTrue(a.value().contains(permissionCode),
                methodName + " 鉴权应含 " + permissionCode + "，实际：" + a.value());
    }

    private static void assertIdempotent(String methodName) {
        Method m = Arrays.stream(ContractController.class.getDeclaredMethods())
                .filter(x -> x.getName().equals(methodName)).findFirst().orElseThrow();
        assertNotNull(m.getAnnotation(Idempotent.class),
                methodName + " 是写操作，必须带 @Idempotent");
    }
}
