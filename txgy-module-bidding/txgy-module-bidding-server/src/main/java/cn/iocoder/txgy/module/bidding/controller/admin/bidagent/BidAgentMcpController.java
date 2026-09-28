package cn.iocoder.txgy.module.bidding.controller.admin.bidagent;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentMcpVO;
import cn.iocoder.txgy.module.bidding.service.bidagent.BidAgentMcpServerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_AGENT_MCP_UNAVAILABLE;

@Tag(name = "管理后台 - 标书智能体 MCP 服务")
@RestController
@RequestMapping("/bidding/bid-agent/mcp")
@Validated
public class BidAgentMcpController {

    @Resource
    private BidAgentMcpServerService mcpServerService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:mcp:query')")
    @Operation(summary = "MCP 服务分页")
    public CommonResult<PageResult<BidAgentMcpVO.RespVO>> page(@Valid BidAgentMcpVO.PageReqVO reqVO) {
        return success(mcpServerService.page(reqVO));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:mcp:query')")
    @Operation(summary = "MCP 服务详情（env/headers 脱敏）")
    public CommonResult<BidAgentMcpVO.RespVO> get(@RequestParam("id") Long id) {
        return success(mcpServerService.get(id));
    }

    @PostMapping("/save")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:mcp:create') or @ss.hasPermission('bidding:bid-agent:mcp:update')")
    @Idempotent
    @Operation(summary = "MCP 服务新增/修改（env/headers 留空=不修改）")
    public CommonResult<Long> save(@Valid @RequestBody BidAgentMcpVO.SaveReqVO reqVO) {
        return success(mcpServerService.save(reqVO));
    }

    @PostMapping("/delete")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:mcp:delete')")
    @Idempotent
    @Operation(summary = "MCP 服务删除")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        mcpServerService.delete(id);
        return success(true);
    }

    @PostMapping("/check")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:mcp:test')")
    @Idempotent
    @Operation(summary = "MCP 连通性自检（tools/list）与单次试调（tools/call）")
    public CommonResult<BidAgentMcpVO.CheckRespVO> check(@RequestBody BidAgentMcpVO.CheckReqVO reqVO) {
        return success(mcpServerService.check(reqVO));
    }

    @GetMapping("/profile-list")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:mcp:query')")
    @Operation(summary = "智能体挂载的 MCP 服务")
    public CommonResult<List<BidAgentMcpServerService.McpBinding>> profileList(@RequestParam("profileId") Long profileId) {
        return success(mcpServerService.listBindingsByProfile(profileId));
    }

    @PostMapping("/profile-save")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:mcp:update')")
    @Idempotent
    @Operation(summary = "设置智能体挂载的 MCP 服务与允许的工具子集（覆盖式）")
    public CommonResult<Boolean> profileSave(@RequestParam("profileId") Long profileId,
                                             @RequestBody(required = false) List<Map<String, Object>> bindings) {
        List<BidAgentMcpServerService.McpBinding> list = new ArrayList<>();
        if (bindings != null) {
            for (Map<String, Object> item : bindings) {
                Object serverId = item.get("mcpServerId");
                Object toolNames = item.get("toolNames");
                List<String> names = new ArrayList<>();
                if (toolNames instanceof List<?> raw) {
                    raw.forEach(name -> names.add(String.valueOf(name)));
                }
                Long mcpServerId = null;
                if (serverId != null) {
                    try {
                        mcpServerId = Long.valueOf(String.valueOf(serverId));
                    } catch (NumberFormatException ex) {
                        // 非数字入参返回业务错误而非 500（脚本/第三方调用方兜底）
                        throw exception(BID_AGENT_MCP_UNAVAILABLE, "mcpServerId 非法：" + serverId);
                    }
                }
                list.add(new BidAgentMcpServerService.McpBinding(mcpServerId, names));
            }
        }
        mcpServerService.setProfileBindings(profileId, list);
        return success(true);
    }
}
