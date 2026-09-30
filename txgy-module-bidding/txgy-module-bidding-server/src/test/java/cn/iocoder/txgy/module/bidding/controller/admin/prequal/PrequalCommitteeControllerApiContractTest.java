package cn.iocoder.txgy.module.bidding.controller.admin.prequal;

import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo.PrequalCommitteeRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo.PrequalCommitteeSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.prequal.PrequalCommitteeDO;
import cn.iocoder.txgy.module.bidding.service.prequal.PrequalCommitteeService;
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
import java.util.Arrays;
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
 * **资格审查委员会接口层契约测试（standalone，不依赖 Spring 容器/DB）**：以
 * 《后端变更通知-资格审查委员会-2026-09-28.md》为准，校验 2 个接口的路径、方法、
 * 鉴权权限码、@Idempotent、响应 JSON 字段集合，以及组成校验语义（条例第37条）。
 */
class PrequalCommitteeControllerApiContractTest {

    private PrequalCommitteeService prequalCommitteeService;
    private PrequalCommitteeController controller;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    /** 契约 §2：委员会成员响应 10 字段。 */
    private static final Set<String> COMMITTEE_RESP_FIELDS = new TreeSet<>(List.of(
            "id", "prequalId", "memberName", "memberCategory", "memberRole",
            "memberOrg", "memberTitle", "sort", "remark", "createTime"));

    /** 契约 §2：save 请求 2 字段（prequalId + members[]；members 项 8 字段）。 */
    private static final Set<String> SAVE_REQ_FIELDS = new TreeSet<>(List.of(
            "prequalId", "members"));

    private static final Set<String> MEMBER_ITEM_FIELDS = new TreeSet<>(List.of(
            "memberName", "memberCategory", "memberRole", "memberOrg",
            "memberTitle", "sort", "remark"));

    @BeforeEach
    void setUp() {
        prequalCommitteeService = mock(PrequalCommitteeService.class);
        controller = new PrequalCommitteeController();
        ReflectionTestUtils.setField(controller, "prequalCommitteeService", prequalCommitteeService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private PrequalCommitteeDO sampleMember() {
        PrequalCommitteeDO d = new PrequalCommitteeDO();
        d.setId(3001L);
        d.setPrequalId(7001L);
        d.setMemberName("张三");
        d.setMemberCategory("TECH_EXPERT");
        d.setMemberRole("MEMBER");
        d.setMemberOrg("某设计院");
        d.setMemberTitle("高级工程师");
        d.setSort(1);
        d.setRemark("契约测试样例");
        return d;
    }

    @Test
    @DisplayName("契约 §2 两个接口鉴权权限码全等（save=committee / list=query）")
    void permissions_matchesContract() {
        assertHasPermission("save", "bidding:prequal:committee");
        assertHasPermission("list", "bidding:prequal:query");
    }

    @Test
    @DisplayName("契约 §2 save 写方法带 @Idempotent")
    void idempotent_matchesContract() {
        Method m = Arrays.stream(PrequalCommitteeController.class.getDeclaredMethods())
                .filter(x -> x.getName().equals("save")).findFirst().orElseThrow();
        assertNotNull(m.getAnnotation(Idempotent.class), "save 是写操作，必须带 @Idempotent");
    }

    @Test
    @DisplayName("契约 §2 POST /bidding/prequal/committee/save：返回 true")
    void save_matchesContract() throws Exception {
        mockMvc.perform(post("/bidding/prequal/committee/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prequalId\":7001,\"members\":[{\"memberName\":\"张三\",\"memberCategory\":\"TECH_EXPERT\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    @DisplayName("契约 §2 GET /bidding/prequal/committee/list：成员响应字段全等（10 字段）")
    void list_matchesContract() throws Exception {
        when(prequalCommitteeService.listByPrequal(7001L)).thenReturn(List.of(sampleMember()));

        String body = mockMvc.perform(get("/bidding/prequal/committee/list").param("prequalId", "7001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(3001))
                .andReturn().getResponse().getContentAsString();
        assertRespFieldSet(body, "data[0]", COMMITTEE_RESP_FIELDS);
    }

    @Test
    @DisplayName("契约 §2 请求 VO 字段集合全等（save 2 / 成员项 7）")
    void requestVoFields_matchContract() {
        assertEquals(SAVE_REQ_FIELDS, declaredFields(PrequalCommitteeSaveReqVO.class), "save 请求字段必须与契约全等");
        assertEquals(MEMBER_ITEM_FIELDS, declaredFields(PrequalCommitteeSaveReqVO.MemberItem.class), "成员项字段必须与契约全等");
    }

    @Test
    @DisplayName("契约 §2 响应 VO 字段集合全等（成员 10）")
    void respVoFields_matchContract() {
        assertEquals(COMMITTEE_RESP_FIELDS, declaredFields(PrequalCommitteeRespVO.class), "成员响应字段必须与契约全等");
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
        Method m = Arrays.stream(PrequalCommitteeController.class.getDeclaredMethods())
                .filter(x -> x.getName().equals(methodName)).findFirst()
                .orElseThrow(() -> new AssertionError("控制器里找不到方法：" + methodName));
        PreAuthorize a = m.getAnnotation(PreAuthorize.class);
        assertNotNull(a, methodName + " 缺少 @PreAuthorize（接口必须判权）");
        assertTrue(a.value().contains(permissionCode),
                methodName + " 鉴权应含 " + permissionCode + "，实际：" + a.value());
    }
}
