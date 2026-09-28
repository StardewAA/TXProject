package cn.iocoder.txgy.module.bidding.service.bidagent;

import java.util.Map;

/** 智能体运行时配置服务：执行侧（Node 编排）在任务开始时拉取一次 */
public interface BidAgentRuntimeService {

    /**
     * 组装某任务类型的运行配置：模型（全局 + 覆盖）、提示词、技能片段、工具白名单、MCP 服务与允许的工具子集。
     *
     * @param taskType 任务类型（PARSE/OUTLINE/DRAFT/FILL）
     * @param tenantId 租户编号（空=取上下文）
     */
    Map<String, Object> buildRuntime(String taskType, Long tenantId);
}
