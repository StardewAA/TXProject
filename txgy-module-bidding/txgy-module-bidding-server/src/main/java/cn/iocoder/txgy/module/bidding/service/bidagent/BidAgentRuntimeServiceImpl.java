package cn.iocoder.txgy.module.bidding.service.bidagent;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentProfileVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentMcpServerDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentModelDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentSkillDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidagent.BidAgentMcpServerMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 运行配置组装：模型（全局 + 智能体覆盖）、提示词、技能片段、工具白名单、MCP 服务。
 *
 * <p>执行侧（Node）在任务开始时拉一次；模型未配置时返回 llm=null 并给 warnings，由 Node 决定回退到 .env。
 */
@Service
@Slf4j
public class BidAgentRuntimeServiceImpl implements BidAgentRuntimeService {

    @Resource
    private BidAgentModelService modelService;
    @Resource
    private BidAgentProfileService profileService;
    @Resource
    private BidAgentSkillService skillService;
    @Resource
    private BidAgentMcpServerService mcpServerService;
    @Resource
    private BidAgentMcpServerMapper mcpServerMapper;

    @Override
    public Map<String, Object> buildRuntime(String taskType, Long tenantId) {
        Long effectiveTenantId = tenantId != null ? tenantId : TenantContextHolder.getTenantId();
        if (effectiveTenantId == null) {
            return doBuildRuntime(taskType, null);
        }
        // 契约端（@TenantIgnore，无租户上下文）机对机调用：显式切到目标租户再组装，
        // 保证模型/提示词/技能/MCP 都按「租户优先、平台兜底」口径取数，避免读到其他租户的配置
        return TenantUtils.execute(effectiveTenantId, () -> doBuildRuntime(taskType, effectiveTenantId));
    }

    private Map<String, Object> doBuildRuntime(String taskType, Long effectiveTenantId) {
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("taskType", taskType);
        runtime.put("tenantId", effectiveTenantId);
        List<String> warnings = new ArrayList<>();

        // 1. 智能体配置（平台默认 + 租户覆盖；REVIEW 本期不参与生成，但仍可返回配置）
        BidAgentProfileVO.RespVO profile = StrUtil.isBlank(taskType) ? null : profileService.getByTaskType(taskType);
        runtime.put("systemPrompt", profile == null ? null : profile.getSystemPrompt());

        // 2. 模型：全局启用配置（租户优先、平台兜底）+ 智能体覆盖（model/temperature/maxTokens）
        BidAgentModelDO model = modelService.resolveEnabledModelOrNull();
        if (model == null) {
            warnings.add("未配置启用的云端模型（智能体管理 → 云端模型），执行侧将回退本地 .env 的 LLM_* 配置");
            runtime.put("llm", null);
        } else {
            Map<String, Object> llm = new LinkedHashMap<>();
            llm.put("provider", StrUtil.blankToDefault(model.getProvider(), "openai-compat"));
            llm.put("baseUrl", model.getBaseUrl());
            llm.put("model", profile != null && StrUtil.isNotBlank(profile.getModel()) ? profile.getModel() : model.getModel());
            llm.put("apiKey", model.getApiKey());
            Object temperature = profile != null && profile.getTemperature() != null
                    ? profile.getTemperature() : model.getTemperature();
            llm.put("temperature", temperature);
            Object maxTokens = profile != null && profile.getMaxTokens() != null
                    ? profile.getMaxTokens() : model.getMaxTokens();
            llm.put("maxTokens", maxTokens);
            llm.put("timeoutMs", (model.getTimeoutSeconds() == null ? 180 : model.getTimeoutSeconds()) * 1000);
            llm.put("maxRetries", model.getMaxRetries() == null ? 3 : model.getMaxRetries());
            runtime.put("llm", llm);
        }

        // 3. 技能（首次读取自动挂载推荐技能）+ 工具白名单（智能体白名单优先，其次技能并集）
        List<BidAgentSkillDO> skills = profile == null
                ? skillService.listAutoAttachByTaskType(taskType)
                : skillService.listByProfile(profile.getId(), taskType, true);
        List<Map<String, Object>> skillPayload = new ArrayList<>();
        Set<String> skillTools = new LinkedHashSet<>();
        for (BidAgentSkillDO skill : skills) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", skill.getCode());
            item.put("name", skill.getName());
            item.put("promptFragment", skill.getPromptFragment());
            item.put("knowledgeRoles", split(skill.getKnowledgeRoles()));
            item.put("toolWhitelist", split(skill.getToolWhitelist()));
            skillPayload.add(item);
            skillTools.addAll(split(skill.getToolWhitelist()));
        }
        runtime.put("skills", skillPayload);
        List<String> toolWhitelist = profile == null || StrUtil.isBlank(profile.getToolWhitelist())
                ? new ArrayList<>(skillTools) : split(profile.getToolWhitelist());
        runtime.put("toolWhitelist", toolWhitelist);
        runtime.put("knowledgeRoles", profile == null ? List.of() : split(profile.getKnowledgeRoles()));

        // 4. MCP 服务（含允许的工具子集）
        List<Map<String, Object>> mcpServers = new ArrayList<>();
        if (profile != null) {
            for (BidAgentMcpServerService.McpBinding binding : mcpServerService.listBindingsByProfile(profile.getId())) {
                BidAgentMcpServerDO server = mcpServerMapper.selectById(binding.getMcpServerId());
                if (server == null || server.getEnabled() == null || server.getEnabled() != 1) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", server.getName());
                item.put("transport", server.getTransport());
                item.put("url", server.getUrl());
                item.put("command", server.getCommand());
                item.put("args", server.getArgs());
                item.put("env", server.getEnv());
                item.put("headers", server.getHeaders());
                item.put("timeoutSeconds", server.getTimeoutSeconds());
                item.put("toolNames", binding.getToolNames());
                mcpServers.add(item);
            }
        }
        runtime.put("mcpServers", mcpServers);
        // 全流程任务（generate）：按阶段给出三个智能体各自的配置，供底座逐阶段取用
        // （2026-09-22 起无 FILL 阶段：模板回填由 Node 导出阶段按标签留白/书签完成）
        if (StrUtil.isBlank(taskType) || "generate".equalsIgnoreCase(taskType)) {
            Map<String, Object> stages = new LinkedHashMap<>();
            for (String stage : List.of("PARSE", "OUTLINE", "DRAFT")) {
                Map<String, Object> stageRuntime = buildRuntime(stage, effectiveTenantId);
                stageRuntime.remove("stages");
                stageRuntime.remove("llm");
                stageRuntime.remove("warnings");
                stages.put(stage, stageRuntime);
            }
            runtime.put("stages", stages);
            // generate 没有自己的智能体配置行（管理页只有 PARSE/OUTLINE/DRAFT/FILL/REVIEW 五类），
            // 因此上面按 profile 取到的顶层 mcpServers 恒为空；而执行侧只在**任务开始时**读一次顶层
            // mcpServers（逐阶段只覆盖提示词/技能），会导致"管理页给某类智能体挂的 MCP 服务在全流程任务里不生效"。
            // 这里把各阶段挂载的 MCP 服务并集上浮到顶层（同名服务去重、toolNames 取并集）。
            if (mcpServers.isEmpty()) {
                runtime.put("mcpServers", mergeStageMcpServers(stages));
            }
        }
        runtime.put("warnings", warnings);
        return runtime;
    }

    /**
     * 合并各阶段 runtime 里的 MCP 服务：同名服务只保留一份，toolNames 取并集（任一阶段为空＝该服务不限制工具）。
     */
    private List<Map<String, Object>> mergeStageMcpServers(Map<String, Object> stages) {
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        Set<String> unrestricted = new java.util.HashSet<>();
        for (Object stageValue : stages.values()) {
            if (!(stageValue instanceof Map<?, ?> stageRuntime)) {
                continue;
            }
            if (!(stageRuntime.get("mcpServers") instanceof List<?> stageServers)) {
                continue;
            }
            for (Object element : stageServers) {
                if (!(element instanceof Map<?, ?> item)) {
                    continue;
                }
                String name = String.valueOf(item.get("name"));
                Map<String, Object> target = merged.get(name);
                if (target == null) {
                    target = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : item.entrySet()) {
                        target.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    merged.put(name, target);
                }
                Set<String> union = new LinkedHashSet<>();
                if (target.get("toolNames") instanceof List<?> existTools) {
                    if (existTools.isEmpty()) {
                        unrestricted.add(name);
                    }
                    existTools.forEach(tool -> union.add(String.valueOf(tool)));
                }
                if (item.get("toolNames") instanceof List<?> stageTools) {
                    if (stageTools.isEmpty()) {
                        unrestricted.add(name);
                    }
                    stageTools.forEach(tool -> union.add(String.valueOf(tool)));
                }
                target.put("toolNames", new ArrayList<>(union));
            }
        }
        for (String name : unrestricted) {
            Map<String, Object> target = merged.get(name);
            if (target != null) {
                target.put("toolNames", new ArrayList<>());
            }
        }
        return new ArrayList<>(merged.values());
    }

    private List<String> split(String value) {
        if (StrUtil.isBlank(value)) {
            return new ArrayList<>();
        }
        List<String> list = new ArrayList<>();
        for (String item : value.split(",")) {
            if (StrUtil.isNotBlank(item)) {
                list.add(item.trim());
            }
        }
        return list;
    }
}
