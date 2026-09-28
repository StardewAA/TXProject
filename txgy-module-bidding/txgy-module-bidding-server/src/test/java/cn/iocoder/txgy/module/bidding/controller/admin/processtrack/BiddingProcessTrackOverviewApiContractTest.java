package cn.iocoder.txgy.module.bidding.controller.admin.processtrack;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo.ProcessTrackDetailRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo.ProcessTrackEventVO;
import cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo.ProcessTrackItemVO;
import cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo.ProcessTrackOptionsRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo.ProcessTrackPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo.ProcessTrackStatsRespVO;
import cn.iocoder.txgy.module.bidding.service.processtrack.BiddingProcessTrackService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * **投标流程跟踪管理端总览接口层契约测试（standalone，不依赖 Spring 容器/DB）**：以
 * 《后端变更请求-投标流程跟踪管理端总览-2026-09-18.md》§5 为准，校验 page/stats/options/detail
 * 四个读接口的路径、方法、鉴权权限码（bidding:process-track:query）、响应 JSON 字段集合。
 */
class BiddingProcessTrackOverviewApiContractTest {

    private BiddingProcessTrackService processTrackService;
    private BiddingProcessTrackController controller;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    /** 契约 §5 接口1 ProcessTrackItemVO 字段集（28 字段 + 多文件化 R2 attachments，共 29）。 */
    private static final Set<String> ITEM_FIELDS = new TreeSet<>(List.of(
            "id", "tenantId", "tenantName", "followItemId", "followKey", "followType",
            "projectName", "projectCode", "issuerName", "region", "amount", "deadline",
            "state", "stateName", "ownerUserId", "ownerName", "ownerPost", "ownerPhone",
            "intentionTime", "registerTime", "reviewTime", "biddedTime", "terminatedTime",
            "reviewResult", "reviewReason", "documentName", "remark", "attachments"));

    /** 契约 §5 接口4 events[] 字段集（7 字段）。 */
    private static final Set<String> EVENT_FIELDS = new TreeSet<>(List.of(
            "eventType", "eventName", "fromState", "toState", "operatorName",
            "operationDesc", "occurredAt"));

    @BeforeEach
    void setUp() {
        processTrackService = mock(BiddingProcessTrackService.class);
        controller = new BiddingProcessTrackController();
        ReflectionTestUtils.setField(controller, "processTrackService", processTrackService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private ProcessTrackItemVO sampleItem() {
        ProcessTrackItemVO vo = new ProcessTrackItemVO();
        vo.setId("2101082484060155905");
        vo.setTenantId("1");
        vo.setTenantName("陕西XX建设有限公司");
        vo.setFollowItemId("9001");
        vo.setFollowKey("I6101002026000001");
        vo.setFollowType("BID_SECTION");
        vo.setProjectName("XX市政工程招标项目");
        vo.setProjectCode("I6101002026000001001");
        vo.setIssuerName("XX市住房和城乡建设局");
        vo.setRegion("610100");
        vo.setAmount("5800000");
        vo.setDeadline(1785600000000L);
        vo.setState("PENDING_BID");
        vo.setStateName("待投标");
        vo.setOwnerUserId("4");
        vo.setOwnerName("张三");
        vo.setOwnerPost("投标专员");
        vo.setOwnerPhone("13800000000");
        vo.setIntentionTime(java.time.LocalDateTime.of(2026, 9, 18, 10, 0));
        vo.setRegisterTime(java.time.LocalDateTime.of(2026, 9, 20, 9, 30));
        vo.setReviewTime(java.time.LocalDateTime.of(2026, 9, 21, 14, 0));
        vo.setBiddedTime(null);
        vo.setTerminatedTime(null);
        vo.setReviewResult("APPROVED");
        vo.setReviewReason(null);
        vo.setDocumentName(null);
        vo.setRemark("契约测试样例");
        vo.setAttachments(java.util.List.of());
        return vo;
    }

    @Test
    @DisplayName("契约 §5 四个总览接口鉴权权限码全等（bidding:process-track:query）")
    void permissions_matchesContract() {
        assertHasPermission("overviewPage", "bidding:process-track:query");
        assertHasPermission("overviewStats", "bidding:process-track:query");
        assertHasPermission("overviewOptions", "bidding:process-track:query");
        assertHasPermission("overviewDetail", "bidding:process-track:query");
    }

    @Test
    @DisplayName("契约 §5 接口1 GET /bidding/process-track/page：出参字段全等（28 字段）")
    void page_matchesContract() throws Exception {
        when(processTrackService.getOverviewPage(org.mockito.ArgumentMatchers.any(ProcessTrackPageReqVO.class), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(new PageResult<>(List.of(sampleItem()), 1L));

        String body = mockMvc.perform(get("/bidding/process-track/page")
                        .param("pageNo", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].id").value("2101082484060155905"))
                .andReturn().getResponse().getContentAsString();
        assertRespFieldSet(body, "data.list[0]", ITEM_FIELDS);
    }

    @Test
    @DisplayName("契约 §5 接口2 GET /bidding/process-track/stats：KPI 出参字段全等（7 字段）")
    void stats_matchesContract() throws Exception {
        ProcessTrackStatsRespVO stats = new ProcessTrackStatsRespVO();
        stats.setTenantCount(3L);
        stats.setProjectCount(12L);
        stats.setOngoing(6L);
        stats.setBidded(4L);
        stats.setTerminated(2L);
        stats.setOwnerCount(8L);
        stats.setByState(Map.of("INTENTION", 2L, "QUALIFICATION_REVIEW", 2L, "PENDING_BID", 2L, "BIDDED", 4L, "TERMINATED", 2L));
        when(processTrackService.getOverviewStats(org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(stats);

        mockMvc.perform(get("/bidding/process-track/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.projectCount").value(12))
                .andExpect(jsonPath("$.data.ongoing").value(6))
                .andExpect(jsonPath("$.data.byState.PENDING_BID").value(2));
    }

    @Test
    @DisplayName("契约 §5 接口3 GET /bidding/process-track/options：owners/tenants 候选形状")
    void options_matchesContract() throws Exception {
        ProcessTrackOptionsRespVO options = new ProcessTrackOptionsRespVO();
        ProcessTrackOptionsRespVO.Option owner = new ProcessTrackOptionsRespVO.Option();
        owner.setValue("4");
        owner.setLabel("张三");
        owner.setExtra("陕西XX建设有限公司");
        options.setOwners(List.of(owner));
        ProcessTrackOptionsRespVO.Option tenant = new ProcessTrackOptionsRespVO.Option();
        tenant.setValue("1");
        tenant.setLabel("陕西XX建设有限公司");
        options.setTenants(List.of(tenant));
        when(processTrackService.getOverviewOptions(org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(options);

        mockMvc.perform(get("/bidding/process-track/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.owners[0].value").value("4"))
                .andExpect(jsonPath("$.data.owners[0].label").value("张三"))
                .andExpect(jsonPath("$.data.owners[0].extra").value("陕西XX建设有限公司"))
                .andExpect(jsonPath("$.data.tenants[0].value").value("1"));
    }

    @Test
    @DisplayName("契约 §5 接口4 GET /bidding/process-track/detail：item + events[] 事件史（操作记录跟踪）")
    void detail_matchesContract() throws Exception {
        ProcessTrackEventVO event = new ProcessTrackEventVO();
        event.setEventType("REGISTER");
        event.setEventName("报名投标");
        event.setFromState("INTENTION");
        event.setToState("QUALIFICATION_REVIEW");
        event.setOperatorName("张三");
        event.setOperationDesc("报名投标");
        event.setOccurredAt(java.time.LocalDateTime.of(2026, 9, 20, 9, 30));
        ProcessTrackDetailRespVO detail = new ProcessTrackDetailRespVO();
        detail.setItem(sampleItem());
        detail.setEvents(List.of(event));
        when(processTrackService.getOverviewDetail(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(detail);

        String body = mockMvc.perform(get("/bidding/process-track/detail").param("id", "2101082484060155905"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.item.id").value("2101082484060155905"))
                .andExpect(jsonPath("$.data.events[0].operatorName").value("张三"))
                .andReturn().getResponse().getContentAsString();
        // 事件史字段集校验
        JsonNode root = objectMapper.readTree(body);
        JsonNode eventNode = root.path("data").path("events").get(0);
        Set<String> actual = new TreeSet<>();
        eventNode.fieldNames().forEachRemaining(actual::add);
        assertEquals(EVENT_FIELDS, actual, "事件史外发字段集必须与契约全等");
        // 总览项字段集校验
        JsonNode itemNode = root.path("data").path("item");
        Set<String> actualItem = new TreeSet<>();
        itemNode.fieldNames().forEachRemaining(actualItem::add);
        assertEquals(ITEM_FIELDS, actualItem, "总览项外发字段集必须与契约全等");
    }

    @Test
    @DisplayName("契约 §5 响应 VO 字段集合全等（item 28 / event 7）")
    void voFields_matchContract() {
        assertEquals(ITEM_FIELDS, declaredFields(ProcessTrackItemVO.class), "总览项字段必须与契约全等");
        assertEquals(EVENT_FIELDS, declaredFields(ProcessTrackEventVO.class), "事件史字段必须与契约全等");
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
        Method m = Arrays.stream(BiddingProcessTrackController.class.getDeclaredMethods())
                .filter(x -> x.getName().equals(methodName)).findFirst()
                .orElseThrow(() -> new AssertionError("控制器里找不到方法：" + methodName));
        PreAuthorize a = m.getAnnotation(PreAuthorize.class);
        assertNotNull(a, methodName + " 缺少 @PreAuthorize（接口必须判权）");
        assertTrue(a.value().contains(permissionCode),
                methodName + " 鉴权应含 " + permissionCode + "，实际：" + a.value());
    }
}
