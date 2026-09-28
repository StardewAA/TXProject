package cn.iocoder.txgy.module.bidding.service.bidagent;
// [依据] 标书智能体契约端 GET /contract/agent-runtime（docs/前端规范-全域接口说明/24-...§7）：
//       执行侧只在任务开始时拉一次顶层配置，逐阶段（stages）只覆盖提示词/技能；
//       而 generate 没有自己的智能体配置行，故必须把各阶段挂载的 MCP 服务并集上浮到顶层。

import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentProfileVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentMcpServerDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidagent.BidAgentMcpServerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 运行配置组装测试（Mockito）：重点覆盖「generate 全流程任务的 MCP 服务上浮」——
 * 这是真机联调发现的缺陷：管理页给 PARSE/DRAFT 等挂的 MCP 服务在全流程任务里原本不生效。
 */
class BidAgentRuntimeServiceImplTest {

    private BidAgentModelService modelService;
    private BidAgentProfileService profileService;
    private BidAgentSkillService skillService;
    private BidAgentMcpServerService mcpServerService;
    private BidAgentMcpServerMapper mcpServerMapper;
    private BidAgentRuntimeServiceImpl service;

    @BeforeEach
    void setUp() {
        modelService = mock(BidAgentModelService.class);
        profileService = mock(BidAgentProfileService.class);
        skillService = mock(BidAgentSkillService.class);
        mcpServerService = mock(BidAgentMcpServerService.class);
        mcpServerMapper = mock(BidAgentMcpServerMapper.class);
        service = new BidAgentRuntimeServiceImpl();
        ReflectionTestUtils.setField(service, "modelService", modelService);
        ReflectionTestUtils.setField(service, "profileService", profileService);
        ReflectionTestUtils.setField(service, "skillService", skillService);
        ReflectionTestUtils.setField(service, "mcpServerService", mcpServerService);
        ReflectionTestUtils.setField(service, "mcpServerMapper", mcpServerMapper);
        // 未配云端模型的默认态（真实联调库当前即为此态），会走 warnings 分支但不影响 MCP 组装
        when(modelService.resolveEnabledModelOrNull()).thenReturn(null);
        when(skillService.listAutoAttachByTaskType(anyString())).thenReturn(List.of());
        when(skillService.listByProfile(any(), anyString(), anyBoolean())).thenReturn(List.of());
        // generate 没有自己的配置行
        when(profileService.getByTaskType("generate")).thenReturn(null);
    }

    @Test
    void generate_floatsStageMcpServersToTopLevel_andMergesToolNames() {
        when(profileService.getByTaskType("PARSE")).thenReturn(profile(11L, "PARSE"));
        when(profileService.getByTaskType("DRAFT")).thenReturn(profile(13L, "DRAFT"));
        // 同一个 MCP 服务被 PARSE、DRAFT 分别挂载，且各开了不同工具
        when(mcpServerService.listBindingsByProfile(11L))
                .thenReturn(List.of(new BidAgentMcpServerService.McpBinding(9L, List.of("echo_text"))));
        when(mcpServerService.listBindingsByProfile(13L))
                .thenReturn(List.of(new BidAgentMcpServerService.McpBinding(9L, List.of("other_tool"))));
        when(mcpServerMapper.selectById(9L)).thenReturn(mcpServer(9L, "e2e-echo", 1));

        Map<String, Object> runtime = service.buildRuntime("generate", 1L);

        List<Map<String, Object>> servers = mcpServersOf(runtime);
        assertEquals(1, servers.size(), "同名 MCP 服务应去重为一条");
        assertEquals("e2e-echo", servers.get(0).get("name"));
        assertEquals(List.of("echo_text", "other_tool"), servers.get(0).get("toolNames"), "toolNames 应取并集");
        assertTrue(runtime.containsKey("stages"), "generate 仍应返回三阶段配置");
    }

    @Test
    void generate_stageWithoutToolSubset_keepsUnrestricted() {
        when(profileService.getByTaskType("OUTLINE")).thenReturn(profile(12L, "OUTLINE"));
        // OUTLINE 挂载时未限定工具子集（空列表＝不限制）
        when(mcpServerService.listBindingsByProfile(12L))
                .thenReturn(List.of(new BidAgentMcpServerService.McpBinding(9L, List.of())));
        when(mcpServerMapper.selectById(9L)).thenReturn(mcpServer(9L, "e2e-echo", 1));

        List<Map<String, Object>> servers = mcpServersOf(service.buildRuntime("generate", 1L));

        assertEquals(1, servers.size());
        assertEquals(List.of(), servers.get(0).get("toolNames"), "任一阶段不限制工具，顶层也应不限制");
    }

    @Test
    void generate_skipsDisabledMcpServer() {
        when(profileService.getByTaskType("OUTLINE")).thenReturn(profile(12L, "OUTLINE"));
        when(mcpServerService.listBindingsByProfile(12L))
                .thenReturn(List.of(new BidAgentMcpServerService.McpBinding(9L, List.of("echo_text"))));
        when(mcpServerMapper.selectById(9L)).thenReturn(mcpServer(9L, "e2e-echo", 0));

        assertEquals(List.of(), mcpServersOf(service.buildRuntime("generate", 1L)), "停用的 MCP 服务不应下发");
    }

    @Test
    void singleStageTask_returnsItsOwnProfileBindings() {
        when(profileService.getByTaskType("PARSE")).thenReturn(profile(11L, "PARSE"));
        when(mcpServerService.listBindingsByProfile(11L))
                .thenReturn(List.of(new BidAgentMcpServerService.McpBinding(9L, List.of("echo_text"))));
        when(mcpServerMapper.selectById(9L)).thenReturn(mcpServer(9L, "e2e-echo", 1));

        Map<String, Object> runtime = service.buildRuntime("PARSE", 1L);

        List<Map<String, Object>> servers = mcpServersOf(runtime);
        assertEquals(1, servers.size());
        assertEquals(List.of("echo_text"), servers.get(0).get("toolNames"));
        assertTrue(!runtime.containsKey("stages"), "单阶段任务不应带 stages");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mcpServersOf(Map<String, Object> runtime) {
        return (List<Map<String, Object>>) runtime.get("mcpServers");
    }

    private static BidAgentProfileVO.RespVO profile(Long id, String taskType) {
        BidAgentProfileVO.RespVO resp = new BidAgentProfileVO.RespVO();
        resp.setId(id);
        resp.setTaskType(taskType);
        resp.setName(taskType + " 智能体");
        return resp;
    }

    private static BidAgentMcpServerDO mcpServer(Long id, String name, Integer enabled) {
        BidAgentMcpServerDO server = new BidAgentMcpServerDO();
        server.setId(id);
        server.setName(name);
        server.setTransport("stdio");
        server.setCommand("node");
        server.setArgs("[]");
        server.setEnabled(enabled);
        return server;
    }
}
