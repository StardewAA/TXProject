// 标书智能体 · 平台化接口（云端模型 / 技能 / MCP / 一键生成任务）
// 对应后端：BidAgentModelController、BidAgentSkillController、BidAgentMcpController、BidAgentTaskController
import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

const LLM_REQUEST_TIMEOUT = 1_200_000;

export namespace BidAgentPlatformApi {
  export interface ModelConfig {
    id?: number;
    name: string;
    provider?: string;
    baseUrl: string;
    model: string;
    /** 保存时留空=不修改；返回时为脱敏值 */
    apiKey?: null | string;
    apiKeyMask?: null | string;
    apiKeyConfigured?: boolean;
    temperature?: null | number;
    maxTokens?: null | number;
    timeoutSeconds?: null | number;
    maxRetries?: null | number;
    enabled?: number;
    remark?: null | string;
  }

  export interface ModelTestResp {
    ok: boolean;
    message: string;
  }

  export interface Skill {
    id?: number;
    /** 租户编号：0 = 平台预置（只读，不可编辑/删除）；>0 = 租户自建 */
    tenantId?: null | number;
    code: string;
    name: string;
    description?: null | string;
    promptFragment?: null | string;
    knowledgeRoles?: null | string;
    toolWhitelist?: null | string;
    /** PARSE/OUTLINE/DRAFT/FILL，逗号分隔 */
    applicableTaskTypes?: null | string;
    autoAttach?: number;
    status?: number;
    sort?: number;
    remark?: null | string;
  }

  export interface ProfileSkillItem {
    id: number;
    code: string;
    name: string;
    description?: null | string;
    applicableTaskTypes?: null | string;
    autoAttach?: number;
  }

  export interface McpServer {
    id?: number;
    name: string;
    transport?: string;
    command?: null | string;
    args?: null | string;
    env?: null | string;
    envMask?: null | string;
    url?: null | string;
    headers?: null | string;
    headersMask?: null | string;
    timeoutSeconds?: null | number;
    enabled?: number;
    toolSnapshotJson?: null | string;
    lastCheckAt?: null | string;
    lastCheckResult?: null | string;
    remark?: null | string;
  }

  export interface McpCheckResp {
    ok: boolean;
    message: string;
    tools?: Array<{ description?: string; name: string }>;
    callResult?: unknown;
  }

  /** MCP 服务发现的工具（来自 toolSnapshotJson） */
  export interface McpTool {
    name: string;
    description?: null | string;
  }

  /** 智能体与 MCP 服务的挂载关系（BidAgentMcpServerService.McpBinding） */
  export interface ProfileMcpBinding {
    mcpServerId: number;
    toolNames: string[];
  }

  export interface GenerateTask {
    id: number;
    taskNo: string;
    projectId: number;
    bidSectionCode: string;
    /** 标书类型：technical 技术标书 / business 商务标书 */
    docCategory?: null | string;
    tenderMaterialName?: null | string;
    /** 标书名称（一键生成时前台填写；列表与下载文件名优先用它） */
    remark?: null | string;
    status: string;
    currentStep?: null | string;
    progress?: null | number;
    openQuestionsJson?: null | string;
    artifactUrl?: null | string;
    errorMessage?: null | string;
    totalTokens?: null | number;
    createTime?: number;
  }

  export interface GenerateReq {
    projectId: number;
    bidSectionCode: string;
    tenderMaterialId?: number;
    templateMaterialId?: number;
    force?: boolean;
    /** 标书类型：technical 技术标书 / business 商务标书（后端落台账并透传编排侧） */
    docCategory?: 'technical' | 'business';
    remark?: string;
  }

  export interface ConfirmReq {
    answers: Array<{ questionId: string; value: string }>;
  }

  export interface Chapter {
    nodeId: string;
    title: string;
    content?: null | string;
    status?: null | string;
    version?: number;
  }

  export interface OutlineResp {
    taskId: number;
    projectId: number;
    outlineJson?: null | string;
    chapters?: Chapter[];
  }
}

function withIdempotency() {
  return { headers: { 'idempotency-key': `${Date.now()}-${Math.random().toString(36).slice(2)}` } };
}

// ================= 云端模型 =================

export function listBidAgentModel() {
  return requestClient.get<BidAgentPlatformApi.ModelConfig[]>('/bidding/bid-agent/model/list');
}

export function saveBidAgentModel(data: BidAgentPlatformApi.ModelConfig) {
  return requestClient.post<number>('/bidding/bid-agent/model/save', data, withIdempotency());
}

export function testBidAgentModel(data: Partial<BidAgentPlatformApi.ModelConfig>) {
  return requestClient.post<BidAgentPlatformApi.ModelTestResp>('/bidding/bid-agent/model/test', data, {
    ...withIdempotency(),
    timeout: LLM_REQUEST_TIMEOUT,
  });
}

// ================= 技能 =================

export function getBidAgentSkillPage(params: PageParam & { code?: string; name?: string; status?: number }) {
  return requestClient.get<PageResult<BidAgentPlatformApi.Skill>>('/bidding/bid-agent/skill/page', { params });
}

export function saveBidAgentSkill(data: BidAgentPlatformApi.Skill) {
  return requestClient.post<number>('/bidding/bid-agent/skill/save', data, withIdempotency());
}

export function deleteBidAgentSkill(id: number) {
  return requestClient.post<boolean>('/bidding/bid-agent/skill/delete', undefined, { ...withIdempotency(), params: { id } });
}

export function listBidAgentProfileSkills(profileId: number, taskType?: string) {
  return requestClient.get<BidAgentPlatformApi.ProfileSkillItem[]>('/bidding/bid-agent/skill/profile-list', {
    params: { profileId, taskType },
  });
}

export function saveBidAgentProfileSkills(profileId: number, skillIds: number[]) {
  return requestClient.post<boolean>('/bidding/bid-agent/skill/profile-save', { profileId, skillIds }, withIdempotency());
}

// ================= MCP 服务 =================

export function getBidAgentMcpPage(params: PageParam & { name?: string; transport?: string; enabled?: number }) {
  return requestClient.get<PageResult<BidAgentPlatformApi.McpServer>>('/bidding/bid-agent/mcp/page', { params });
}

export function saveBidAgentMcp(data: BidAgentPlatformApi.McpServer) {
  return requestClient.post<number>('/bidding/bid-agent/mcp/save', data, withIdempotency());
}

export function deleteBidAgentMcp(id: number) {
  return requestClient.post<boolean>('/bidding/bid-agent/mcp/delete', undefined, { ...withIdempotency(), params: { id } });
}

export function checkBidAgentMcp(data: { id: number; toolName?: string; arguments?: Record<string, unknown> }) {
  return requestClient.post<BidAgentPlatformApi.McpCheckResp>('/bidding/bid-agent/mcp/check', data, withIdempotency());
}

export function listBidAgentProfileMcp(profileId: number) {
  return requestClient.get<BidAgentPlatformApi.ProfileMcpBinding[]>('/bidding/bid-agent/mcp/profile-list', {
    params: { profileId },
  });
}

export function saveBidAgentProfileMcp(profileId: number, bindings: BidAgentPlatformApi.ProfileMcpBinding[]) {
  return requestClient.post<boolean>(
    '/bidding/bid-agent/mcp/profile-save',
    bindings,
    { ...withIdempotency(), params: { profileId } },
  );
}

/** 解析 toolSnapshotJson（[{name, description}, ...]），解析失败返回空数组 */
export function parseToolSnapshot(json?: null | string): BidAgentPlatformApi.McpTool[] {
  if (!json) {
    return [];
  }
  try {
    const parsed = JSON.parse(json);
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed
      .filter((item): item is { name?: string; description?: string } => Boolean(item && typeof item === 'object'))
      .map((item) => ({ name: String(item.name ?? ''), description: item.description ?? null }))
      .filter((item) => item.name);
  } catch {
    return [];
  }
}

// ================= 一键生成任务 =================

export function generateBidTask(data: BidAgentPlatformApi.GenerateReq) {
  return requestClient.post<number>('/bidding/bid-agent/task/generate', data, withIdempotency());
}

export function getBidTaskPage(params: PageParam & { projectId?: number; status?: string }) {
  return requestClient.get<PageResult<BidAgentPlatformApi.GenerateTask>>('/bidding/bid-agent/task/page', { params });
}

export function getBidTask(id: number) {
  return requestClient.get<BidAgentPlatformApi.GenerateTask>('/bidding/bid-agent/task/get', { params: { id } });
}

export function cancelBidTask(id: number) {
  return requestClient.post<boolean>('/bidding/bid-agent/task/cancel', undefined, { ...withIdempotency(), params: { id } });
}

export function confirmBidTask(id: number, data: BidAgentPlatformApi.ConfirmReq) {
  return requestClient.post<boolean>('/bidding/bid-agent/task/confirm', data, { ...withIdempotency(), params: { id } });
}

export function retryBidTask(id: number) {
  return requestClient.post<boolean>('/bidding/bid-agent/task/retry', undefined, { ...withIdempotency(), params: { id } });
}

export function getBidTaskOutline(id: number) {
  return requestClient.get<BidAgentPlatformApi.OutlineResp>('/bidding/bid-agent/task/outline', { params: { id } });
}

export function getBidTaskArtifact(id: number) {
  return requestClient.get<string>('/bidding/bid-agent/task/artifact', { params: { id } });
}
