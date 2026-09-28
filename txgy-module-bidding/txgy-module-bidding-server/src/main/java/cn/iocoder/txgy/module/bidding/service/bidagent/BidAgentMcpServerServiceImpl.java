package cn.iocoder.txgy.module.bidding.service.bidagent;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentMcpVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentMcpServerDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentProfileMcpDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidagent.BidAgentMcpServerMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidagent.BidAgentProfileMcpMapper;
import cn.iocoder.txgy.module.bidding.framework.bidagent.McpHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_AGENT_MCP_NOT_EXISTS;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_AGENT_MCP_UNAVAILABLE;

@Service
@Slf4j
public class BidAgentMcpServerServiceImpl implements BidAgentMcpServerService {

    private static final String TRANSPORT_STDIO = "stdio";

    @Resource
    private BidAgentMcpServerMapper mcpServerMapper;
    @Resource
    private BidAgentProfileMcpMapper profileMcpMapper;
    @Resource
    private McpHttpClient mcpHttpClient;

    @Override
    public PageResult<BidAgentMcpVO.RespVO> page(BidAgentMcpVO.PageReqVO reqVO) {
        PageResult<BidAgentMcpServerDO> page = mcpServerMapper.selectPage(reqVO, null);
        List<BidAgentMcpVO.RespVO> list = new ArrayList<>();
        for (BidAgentMcpServerDO item : page.getList()) {
            list.add(toRespVO(item));
        }
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public BidAgentMcpVO.RespVO get(Long id) {
        return toRespVO(requireServer(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(BidAgentMcpVO.SaveReqVO reqVO) {
        BidAgentMcpServerDO item = reqVO.getId() == null ? new BidAgentMcpServerDO() : mcpServerMapper.selectById(reqVO.getId());
        if (item == null) {
            throw exception(BID_AGENT_MCP_NOT_EXISTS);
        }
        item.setName(reqVO.getName());
        item.setTransport(StrUtil.blankToDefault(reqVO.getTransport(), "streamable-http"));
        item.setCommand(reqVO.getCommand());
        item.setArgs(reqVO.getArgs());
        if (StrUtil.isNotBlank(reqVO.getEnv()) && !reqVO.getEnv().contains("****")) {
            item.setEnv(reqVO.getEnv());
        }
        item.setUrl(reqVO.getUrl());
        if (StrUtil.isNotBlank(reqVO.getHeaders()) && !reqVO.getHeaders().contains("****")) {
            item.setHeaders(reqVO.getHeaders());
        }
        item.setTimeoutSeconds(reqVO.getTimeoutSeconds() == null ? 60 : reqVO.getTimeoutSeconds());
        item.setEnabled(reqVO.getEnabled() == null ? 1 : reqVO.getEnabled());
        item.setRemark(reqVO.getRemark());
        if (reqVO.getId() == null) {
            // 重建同名 MCP 前，物理清理已逻辑删除的历史行，避免撞唯一键 (tenant_id, name, deleted)
            mcpServerMapper.purgeDeletedByName(reqVO.getName());
            mcpServerMapper.insert(item);
        } else {
            mcpServerMapper.updateById(item);
        }
        return item.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireServer(id);
        mcpServerMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BidAgentMcpVO.CheckRespVO check(BidAgentMcpVO.CheckReqVO reqVO) {
        BidAgentMcpServerDO server = requireServer(reqVO.getId());
        BidAgentMcpVO.CheckRespVO resp = new BidAgentMcpVO.CheckRespVO();
        String message;
        try {
            if (TRANSPORT_STDIO.equalsIgnoreCase(server.getTransport())) {
                // stdio 需要起子进程：出于安全与运行环境一致性，自检放在执行侧（Node 底座）做
                message = "stdio 类型不在服务端自检（需起子进程）：请在底座侧用 SDK 验证连通性";
                resp.setOk(false);
                resp.setMessage(message);
                updateCheckResult(server, message);
                return resp;
            }
            List<Map<String, Object>> tools = new ArrayList<>();
            JsonNode toolNodes = mcpHttpClient.listTools(server.getUrl(), parseHeaders(server), server.getTimeoutSeconds());
            for (JsonNode node : toolNodes) {
                Map<String, Object> tool = new LinkedHashMap<>();
                tool.put("name", node.path("name").asText());
                tool.put("description", node.path("description").asText(null));
                tools.add(tool);
            }
            server.setToolSnapshotJson(JsonUtils.toJsonString(tools));
            resp.setTools(tools);
            if (StrUtil.isNotBlank(reqVO.getToolName())) {
                resp.setCallResult(JsonUtils.parseObject(
                        mcpHttpClient.callTool(server.getUrl(), parseHeaders(server), server.getTimeoutSeconds(),
                                reqVO.getToolName(), reqVO.getArguments() == null ? Map.of() : reqVO.getArguments()).toString(),
                        Object.class));
                message = StrUtil.format("自检成功，发现 {} 个工具；已试调 {}", tools.size(), reqVO.getToolName());
            } else {
                message = StrUtil.format("自检成功，发现 {} 个工具", tools.size());
            }
            resp.setOk(true);
            resp.setMessage(message);
            updateCheckResult(server, message);
        } catch (Exception ex) {
            log.warn("[BidAgentMcpServerServiceImpl][check] 自检失败 server={} msg={}", server.getName(), ex.getMessage());
            resp.setOk(false);
            resp.setMessage(StrUtil.maxLength(ex.getMessage(), 400));
            updateCheckResult(server, resp.getMessage());
        }
        return resp;
    }

    @Override
    public List<McpBinding> listBindingsByProfile(Long profileId) {
        List<McpBinding> bindings = new ArrayList<>();
        for (BidAgentProfileMcpDO relation : profileMcpMapper.selectListByProfileId(profileId)) {
            bindings.add(new McpBinding(relation.getMcpServerId(), parseToolNames(relation.getToolNames())));
        }
        return bindings;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setProfileBindings(Long profileId, List<McpBinding> bindings) {
        profileMcpMapper.deleteByProfileId(profileId);
        if (bindings == null) {
            return;
        }
        for (McpBinding binding : bindings) {
            if (binding == null || binding.getMcpServerId() == null) {
                continue;
            }
            BidAgentProfileMcpDO relation = new BidAgentProfileMcpDO();
            relation.setProfileId(profileId);
            relation.setMcpServerId(binding.getMcpServerId());
            relation.setToolNames(binding.getToolNames() == null ? null : JsonUtils.toJsonString(binding.getToolNames()));
            profileMcpMapper.insert(relation);
        }
    }

    private BidAgentMcpServerDO requireServer(Long id) {
        BidAgentMcpServerDO server = id == null ? null : mcpServerMapper.selectById(id);
        if (server == null) {
            throw exception(BID_AGENT_MCP_NOT_EXISTS);
        }
        return server;
    }

    private void updateCheckResult(BidAgentMcpServerDO server, String message) {
        server.setLastCheckAt(LocalDateTime.now());
        server.setLastCheckResult(StrUtil.maxLength(message, 480));
        mcpServerMapper.updateById(server);
    }

    private Map<String, String> parseHeaders(BidAgentMcpServerDO server) {
        if (StrUtil.isBlank(server.getHeaders())) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = JsonUtils.parseObject(server.getHeaders(), Map.class);
            Map<String, String> headers = new LinkedHashMap<>();
            raw.forEach((key, value) -> headers.put(key, value == null ? null : String.valueOf(value)));
            return headers;
        } catch (Exception ex) {
            throw exception(BID_AGENT_MCP_UNAVAILABLE, "headers 不是合法 JSON：" + ex.getMessage());
        }
    }

    private List<String> parseToolNames(String json) {
        if (StrUtil.isBlank(json)) {
            return List.of();
        }
        try {
            return JsonUtils.parseObject(json, List.class);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private BidAgentMcpVO.RespVO toRespVO(BidAgentMcpServerDO item) {
        BidAgentMcpVO.RespVO vo = BeanUtils.toBean(item, BidAgentMcpVO.RespVO.class);
        vo.setEnvMask(maskJson(item.getEnv()));
        vo.setHeadersMask(maskJson(item.getHeaders()));
        return vo;
    }

    private String maskJson(String json) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            Map<String, Object> raw = JsonUtils.parseObject(json, HashMap.class);
            Map<String, Object> masked = new LinkedHashMap<>();
            raw.forEach((key, value) -> masked.put(key, "****"));
            return JsonUtils.toJsonString(masked);
        } catch (Exception ex) {
            return "****";
        }
    }
}
