package cn.iocoder.txgy.module.bidding.controller.admin.attachment;

import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.attachment.vo.AttachmentRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.attachment.vo.AttachmentSaveReqVO;
import cn.iocoder.txgy.module.bidding.service.attachment.AttachmentService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * **接口层契约测试（本地单测，不依赖数据库/Spring 容器/CA 平台）**：以契约 29
 * 《bidding 通用附件域与关注域-前端契约》为准，校验"前端拿到的 HTTP 形状"——
 * 路径、方法、鉴权（isAuthenticated()）、@Idempotent、请求/响应 JSON 字段集合。
 *
 * <p>为什么这样写：本项目已多次出现"契约写 A、接口返回 B"的静默错位（如 sha256 vs fileHash），
 * 这类缺陷编译通过、单测通过。所以这里不看"能不能取到值"，而是把响应 JSON 的字段名集合
 * 与契约全等断言，并把鉴权注解用反射逐条比对。
 *
 * <p>边界（诚实说明）：standalone MockMvc 不启动 Spring 容器 ⇒ 不校验租户拦截、鉴权切面、
 * 真实 SQL、幂等切面（Redis 去重）与 Nacos；真正的端到端（起服务 + 真库）登记在
 * 待落地清单 §6.3（server 恢复后一次性验证清单），本测试不替代它，只保证"接口形状不会悄悄变"。
 */
class AttachmentApiContractTest {

    private AttachmentService attachmentService;
    private AttachmentController controller;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    /** 契约 §2.5：附件行响应 15 字段（字段名以契约为准）。 */
    private static final Set<String> RESP_CONTRACT_FIELDS = new TreeSet<>(List.of(
            "id", "bizType", "bizCode", "attachmentType", "attachmentName", "fileId", "url",
            "fileName", "sha256", "size", "mimeType", "virusStatus", "seq", "remark", "createTime"));

    /** 契约 §2.4：save 请求 10 字段（无 id——save 为纯绑定语义，删除走 DELETE?id= 用附件行 id）。 */
    private static final Set<String> SAVE_CONTRACT_FIELDS = new TreeSet<>(List.of(
            "bizType", "bizCode", "attachmentType", "attachmentName", "fileId", "url",
            "fileName", "sha256", "size", "remark"));

    @BeforeEach
    void setUp() {
        attachmentService = mock(AttachmentService.class);
        controller = new AttachmentController();
        ReflectionTestUtils.setField(controller, "attachmentService", attachmentService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 造一条契约齐全的附件行（15 字段全 set，保证字段集合断言与 null 输出策略无关）。 */
    private AttachmentRespVO sampleVo() {
        AttachmentRespVO vo = new AttachmentRespVO();
        vo.setId(5001L);
        vo.setBizType("PROJECT");
        vo.setBizCode("P20260918001");
        vo.setAttachmentType("TENDER_FILE");
        vo.setAttachmentName("招标文件");
        vo.setFileId(90001L);
        vo.setUrl("http://127.0.0.1:9000/bucket/tender.pdf");
        vo.setFileName("招标文件.pdf");
        vo.setSha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        vo.setSize(235122L);
        vo.setMimeType("application/pdf");
        vo.setVirusStatus("CLEAN");
        vo.setSeq(1);
        vo.setRemark("契约测试样例");
        vo.setCreateTime(LocalDateTime.of(2026, 9, 18, 10, 0));
        return vo;
    }

    @Test
    @DisplayName("契约 §1.4/§2.4/§2.5 POST /bidding/attachment/save：鉴权/幂等/请求字段/响应字段全等")
    void save_matchesContract() throws Exception {
        assertPermissionAndIdempotent("save");

        AttachmentRespVO vo = sampleVo();
        when(attachmentService.save(any())).thenReturn(vo);

        String body = mockMvc.perform(post("/bidding/attachment/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bizType\":\"PROJECT\",\"bizCode\":\"P20260918001\",\"fileId\":90001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(5001))
                .andExpect(jsonPath("$.data.createTime").exists())
                .andReturn().getResponse().getContentAsString();
        assertRespFieldSet(body, "data", RESP_CONTRACT_FIELDS);
    }

    @Test
    @DisplayName("契约 §1.4 GET /bidding/attachment/list：鉴权/响应字段全等（一对 N 列表）")
    void list_matchesContract() throws Exception {
        assertPermission("list");

        when(attachmentService.list("PROJECT", "P20260918001")).thenReturn(List.of(sampleVo()));

        String body = mockMvc.perform(get("/bidding/attachment/list")
                        .param("bizType", "PROJECT").param("bizCode", "P20260918001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(5001))
                .andReturn().getResponse().getContentAsString();
        assertRespFieldSet(body, "data[0]", RESP_CONTRACT_FIELDS);
    }

    @Test
    @DisplayName("契约 §1.4 DELETE /bidding/attachment/delete：鉴权/幂等/返回 true")
    void remove_matchesContract() throws Exception {
        assertPermissionAndIdempotent("remove");

        mockMvc.perform(delete("/bidding/attachment/delete").param("id", "5001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    @DisplayName("契约 §2.4 save 请求字段集合全等（10 字段，无 id）")
    void saveRequestFields_matchContract() throws Exception {
        Set<String> actual = new TreeSet<>();
        for (Field f : AttachmentSaveReqVO.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            actual.add(f.getName());
        }
        assertEquals(SAVE_CONTRACT_FIELDS, actual,
                "save 请求字段必须与契约 §2.4 全等：多了=前端不知道，少了=绑定缺字段；注意无 id（纯绑定语义）");
    }

    /** 解析响应 JSON 的指定节点字段集合，与契约字段集全等。路径段支持 "data" 与 "data[0]" 下标形式。 */
    private void assertRespFieldSet(String body, String jsonPath, Set<String> expected) throws Exception {
        var node = objectMapper.readTree(body);
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
        assertEquals(expected, actual,
                "外发字段集必须与契约全等：多了=外发内部列，少了=前端拿不到值");
    }

    /** 反射断言控制器方法带 @PreAuthorize("isAuthenticated()")（契约 §1.4：登录即可）。 */
    private static void assertPermission(String methodName) {
        Method method = Arrays.stream(AttachmentController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName)).findFirst()
                .orElseThrow(() -> new AssertionError("控制器里找不到方法：" + methodName));
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation, methodName + " 缺少 @PreAuthorize（接口必须判权）");
        assertTrue(annotation.value().contains("isAuthenticated()"),
                methodName + " 鉴权应为 isAuthenticated()（契约 §1.4），实际：" + annotation.value());
    }

    /** 反射断言写方法带 @Idempotent（契约 §1.4：写操作带 @Idempotent）。 */
    private static void assertIdempotent(String methodName) {
        Method method = Arrays.stream(AttachmentController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName)).findFirst().orElseThrow();
        assertNotNull(method.getAnnotation(Idempotent.class),
                methodName + " 是写操作，必须带 @Idempotent（契约 §1.4）");
    }

    private static void assertPermissionAndIdempotent(String methodName) {
        assertPermission(methodName);
        assertIdempotent(methodName);
    }
}
