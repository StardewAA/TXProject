package cn.iocoder.txgy.module.bidding.service.bidagent;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentMcpVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentMcpServerDO;

import java.util.List;

/** MCP 服务服务（登记外部 MCP server + 自检 + 试调 + 智能体挂载） */
public interface BidAgentMcpServerService {

    PageResult<BidAgentMcpVO.RespVO> page(BidAgentMcpVO.PageReqVO reqVO);

    BidAgentMcpVO.RespVO get(Long id);

    Long save(BidAgentMcpVO.SaveReqVO reqVO);

    void delete(Long id);

    /** 自检（tools/list）或试调（tools/call） */
    BidAgentMcpVO.CheckRespVO check(BidAgentMcpVO.CheckReqVO reqVO);

    /** 智能体挂载的 MCP 服务（含允许的工具子集） */
    List<McpBinding> listBindingsByProfile(Long profileId);

    void setProfileBindings(Long profileId, List<McpBinding> bindings);

    /** 智能体与 MCP 的绑定关系 */
    class McpBinding {
        private Long mcpServerId;
        private List<String> toolNames;

        public McpBinding() {
        }

        public McpBinding(Long mcpServerId, List<String> toolNames) {
            this.mcpServerId = mcpServerId;
            this.toolNames = toolNames;
        }

        public Long getMcpServerId() {
            return mcpServerId;
        }

        public void setMcpServerId(Long mcpServerId) {
            this.mcpServerId = mcpServerId;
        }

        public List<String> getToolNames() {
            return toolNames;
        }

        public void setToolNames(List<String> toolNames) {
            this.toolNames = toolNames;
        }
    }
}
