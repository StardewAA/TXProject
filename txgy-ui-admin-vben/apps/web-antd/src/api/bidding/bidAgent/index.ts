import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

const LLM_REQUEST_TIMEOUT = 1_200_000;

/** 契约来源：txgy-module-bidding 的 bidagent 域（BidAgentKnowledgeController / BidAgentConfigController）
 *  权限码：bidding:bid-agent:{query,create,update,delete,retrieve,eval,preview}（已在 2026-09-26-bid-agent.sql 注册）
 *  写接口均带 @Idempotent：前端必须带 idempotency-key 请求头（下表统一由 withIdempotency 生成）。
 *  约定：fileId 是材料落库唯一依据；fileUrl 仅用于取内容入库与即时预览。
 */
export namespace BidAgentApi {
  /** 材料台账出参（BidAgentMaterialRespVO） */
  export interface Material {
    id?: number;
    docType: string;
    name: string;
    bidSectionCode?: null | string;
    /** 所属项目（PRICING 报价数据） */
    projectId?: null | number;
    fileId: number;
    fileUrl?: null | string;
    fileName?: null | string;
    /** 文件大小（字节） */
    fileSize?: null | number;
    /** 0 待入库 1 已入库 2 失败 */
    indexStatus?: number;
    segmentCount?: number;
    indexMessage?: null | string;
    validUntil?: null | string;
    remark?: null | string;
    /** 创建时间（毫秒，epoch） */
    createTime?: number;
  }

  export interface MaterialPageParams extends PageParam {
    docType?: string;
    bidSectionCode?: string;
    indexStatus?: number;
    name?: string;
  }

  /** 材料登记入参（BidAgentMaterialSaveReqVO） */
  export interface MaterialSaveReq {
    docType: string;
    name: string;
    bidSectionCode?: string;
    /** 所属项目（PRICING 报价数据用；空=默认 0，同一键生成口径） */
    projectId?: number;
    fileId: number;
    fileUrl?: string;
    fileName?: string;
    /** 文件大小（字节） */
    fileSize?: number;
    validUntil?: string;
    remark?: string;
  }

  /** 切片出参（BidAgentSegmentRespVO） */
  export interface Segment {
    id?: number;
    materialId?: number;
    chunkNo?: number;
    content?: null | string;
    sourceBlockIds?: null | string;
    pageNo?: null | number;
    /** 0 未被人工编辑 1 已编辑 */
    edited?: number;
  }

  /** 检索入参（BidAgentRetrieveReqVO） */
  export interface RetrieveReq {
    query: string;
    docType?: string;
    bidSectionCode?: string;
    topK?: number;
    /** 相似度阈值（召回调试覆盖配置 min-score） */
    minScore?: number;
    /** 检索模式：hybrid / vector / bm25 */
    mode?: string;
    mandatory?: string[];
  }

  /** 检索命中片段（BidAgentRetrieveRespVO.Chunk） */
  export interface RetrieveChunk {
    chunkId?: null | string;
    content?: null | string;
    sourceDocName?: null | string;
    chapterPath?: null | string;
    pageNo?: null | number;
    score?: null | number;
    /** 命中方式：vector 仅向量 / bm25 仅关键词（MySQL FULLTEXT ngram）/ hybrid 双通道 / rerank 已过重排（rerank 开启时的最终标记） */
    matchedBy?: null | string;
    sourceBlockIds?: string[];
  }

  /** 检索出参（BidAgentRetrieveRespVO） */
  export interface RetrieveResp {
    hit?: boolean;
    chunks?: RetrieveChunk[];
    /** 本次检索耗时（毫秒） */
    elapsedMs?: null | number;
  }

  /** 精准度测试用例出参（BidAgentEvalCaseRespVO） */
  export interface EvalCase {
    id?: number;
    name: string;
    query: string;
    docType?: null | string;
    bidSectionCode?: null | string;
    topK?: number;
    expectKeywords?: null | string;
    expectBlockIds?: null | string;
    enabled?: number;
    remark?: null | string;
    /** 创建时间（毫秒，epoch） */
    createTime?: number;
  }

  export interface EvalCasePageParams extends PageParam {
    name?: string;
    enabled?: number;
  }

  /** 跑批入参（BidAgentEvalRunReqVO） */
  export interface EvalRunReq {
    caseIds?: number[];
    topK?: number;
  }

  /** 单用例结果（BidAgentEvalRunRespVO.CaseResultVO） */
  export interface EvalCaseResult {
    caseId?: number;
    name?: string;
    query?: string;
    /** 是否配置了期望标准并参与命中率计算 */
    evaluated?: boolean;
    hit?: boolean;
    /** 实际命中通道：rerank / vector / bm25 / none */
    matchedBy?: string;
    matchedBlockIds?: string[];
    missReason?: null | string;
    /** 最佳命中片段分数 */
    score?: null | number;
    /** 本用例实际使用的 TopK */
    topK?: number;
    /** 本用例检索耗时（毫秒） */
    latencyMs?: null | number;
  }

  /** 跑批出参（BidAgentEvalRunRespVO） */
  export interface EvalRunResp {
    totalCount?: number;
    hitCount?: number;
    /** 兼容字段：已标注用例命中率 */
    recall?: null | number;
    /** 已标注用例命中率 */
    caseHitRate?: null | number;
    evaluatedCount?: number;
    unlabeledCount?: number;
    scoreAvailable?: boolean;
    /** 分数口径：rerank / vector / mixed / bm25 / none */
    scoreType?: string;
    scoredCaseCount?: number;
    unscoredCaseCount?: number;
    channelCounts?: Record<string, number>;
    /** 平均命中相关度分；无可用分数时为 null */
    avgScore?: null | number;
    /** 平均检索耗时（毫秒） */
    avgLatencyMs?: null | number;
    /** 最长检索耗时（毫秒） */
    maxLatencyMs?: null | number;
    topK?: null | number;
    cases?: EvalCaseResult[];
  }

  /** 智能体配置出参（BidAgentProfileRespVO） */
  export interface Profile {
    id?: number;
    taskType: string;
    name: string;
    description?: null | string;
    systemPrompt?: null | string;
    model?: null | string;
    temperature?: null | number;
    /** 单条回复最大 token（空=用全局云端模型配置） */
    maxTokens?: null | number;
    knowledgeRoles?: null | string;
    /** 内置工具白名单（逗号分隔，空=全部允许） */
    toolWhitelist?: null | string;
    /** 是否自动挂载推荐技能：1 是 0 否 */
    skillAutoAttach?: null | number;
    knowledgeRoleList?: string[];
    /** 1 可编辑 0 只读（锁机制保留） */
    editable?: number;
    sort?: number;
    status?: number;
    remark?: null | string;
    /** 创建时间（毫秒，epoch） */
    createTime?: number;
  }

  /** 智能体配置入参（BidAgentProfileSaveReqVO） */
  export interface ProfileSaveReq {
    id?: number;
    taskType: string;
    name: string;
    description?: string;
    systemPrompt?: string;
    model?: string;
    temperature?: number;
    /** 单条回复最大 token（空=用全局云端模型配置） */
    maxTokens?: number;
    knowledgeRoles?: string;
    /** 内置工具白名单（逗号分隔，空=全部允许） */
    toolWhitelist?: string;
    /** 是否自动挂载推荐技能：1 是 0 否 */
    skillAutoAttach?: number;
    sort?: number;
    status: number;
    remark?: string;
  }

  /** 试跑入参（BidAgentPreviewReqVO） */
  export interface PreviewReq {
    taskType: string;
    input: string;
    withRetrieve?: boolean;
  }

  /** 试跑出参（BidAgentPreviewRespVO） */
  export interface PreviewResp {
    taskType?: string;
    /** BID_AGENT 后台配置 / BUILT_IN 内置基线 */
    promptSource?: string;
    output?: string;
  }
}

/** 写接口统一带幂等键（后端 @Idempotent） */
function withIdempotency() {
  return { headers: { 'idempotency-key': `${Date.now()}-${Math.random().toString(36).slice(2)}` } };
}

/* ================= 材料台账 ================= */
export function getBidAgentMaterialPage(params: BidAgentApi.MaterialPageParams) {
  return requestClient.get<PageResult<BidAgentApi.Material>>('/bidding/bid-agent/material/page', { params });
}
export function getBidAgentMaterial(id: number) {
  return requestClient.get<BidAgentApi.Material>('/bidding/bid-agent/material/get', { params: { id } });
}
export function saveBidAgentMaterial(data: BidAgentApi.MaterialSaveReq) {
  return requestClient.post<number>('/bidding/bid-agent/material/save', data, withIdempotency());
}
export function deleteBidAgentMaterial(id: number) {
  return requestClient.post<boolean>('/bidding/bid-agent/material/delete', undefined, {
    params: { id },
    ...withIdempotency(),
  });
}
export function reindexBidAgentMaterial(id: number) {
  return requestClient.post<boolean>('/bidding/bid-agent/material/reindex', undefined, {
    params: { id },
    ...withIdempotency(),
  });
}
export function getBidAgentSegmentPage(params: { materialId: number; pageNo?: number; pageSize?: number }) {
  return requestClient.get<PageResult<BidAgentApi.Segment>>('/bidding/bid-agent/segment/page', { params });
}

/* ================= 切片管理（人工治理） ================= */
export function editBidAgentSegment(data: { id: number; content: string }) {
  return requestClient.post<boolean>('/bidding/bid-agent/segment/edit', data, withIdempotency());
}
export function splitBidAgentSegment(id: number) {
  return requestClient.post<boolean>('/bidding/bid-agent/segment/split', undefined, {
    params: { id },
    ...withIdempotency(),
  });
}
export function mergeBidAgentSegment(ids: number[]) {
  return requestClient.post<boolean>('/bidding/bid-agent/segment/merge', { ids }, withIdempotency());
}
export function deleteBidAgentSegment(ids: number[]) {
  return requestClient.post<boolean>('/bidding/bid-agent/segment/delete', { ids }, withIdempotency());
}

/* ================= 召回验证 ================= */
export function retrieveBidAgent(data: BidAgentApi.RetrieveReq) {
  return requestClient.post<BidAgentApi.RetrieveResp>('/bidding/bid-agent/retrieve', data, {
    headers: { 'X-Silent-Error': 'true' },
  });
}

/* ================= 精准度测试 ================= */
export function getBidAgentEvalCasePage(params: BidAgentApi.EvalCasePageParams) {
  return requestClient.get<PageResult<BidAgentApi.EvalCase>>('/bidding/bid-agent/eval/case/page', { params });
}
export function saveBidAgentEvalCase(data: BidAgentApi.EvalCase) {
  return requestClient.post<number>('/bidding/bid-agent/eval/case/save', data, withIdempotency());
}
export function deleteBidAgentEvalCase(id: number) {
  return requestClient.post<boolean>('/bidding/bid-agent/eval/case/delete', undefined, {
    params: { id },
    ...withIdempotency(),
  });
}
export function runBidAgentEval(data: BidAgentApi.EvalRunReq) {
  return requestClient.post<BidAgentApi.EvalRunResp>('/bidding/bid-agent/eval/run', data);
}

/* ================= 智能体配置 ================= */
export function listBidAgentProfile() {
  return requestClient.get<BidAgentApi.Profile[]>('/bidding/bid-agent/agent/list');
}
export function getBidAgentProfile(params: { id?: number; taskType?: string }) {
  return requestClient.get<BidAgentApi.Profile>('/bidding/bid-agent/agent/get', { params });
}
export function saveBidAgentProfile(data: BidAgentApi.ProfileSaveReq) {
  return requestClient.post<number>('/bidding/bid-agent/agent/save', data, withIdempotency());
}
export function previewBidAgent(data: BidAgentApi.PreviewReq) {
  return requestClient.post<BidAgentApi.PreviewResp>('/bidding/bid-agent/preview', data, {
    timeout: LLM_REQUEST_TIMEOUT,
  });
}
