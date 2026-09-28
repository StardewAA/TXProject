import crypto from 'node:crypto';
import type { AppConfig } from '../config.js';
import { AppError } from '../errors.js';
import { HttpClient } from '../lib/httpClient.js';
import type { Logger } from '../logger.js';

/**
 * Agent → Java 的 OpenAPI 客户端（架构文档 §3.1）。
 * /retrieve 与 /facts 为纯读接口，可安全重试；写接口带 Idempotency-Key。
 */

export interface RetrieveFilters {
  docType?: string;
  validOn?: string;
  domain?: string;
  /** 文档级隔离：仅检索该来源文档 */
  sourceDocName?: string;
  /** 标段级隔离：仅检索该标段的招标文件（企业资料/技术母本不绑标段，仍会召回） */
  bidSectionCode?: string;
  /** 多标段检索：一次检索多个标段（长文本招标文件按标段拆分的场景） */
  bidSectionCodes?: string[];
}

export interface RetrieveRequest {
  tenantId: string;
  projectId: string;
  query: string;
  filters?: RetrieveFilters;
  topK?: number;
  mandatory?: string[];
}

export interface RetrievedChunk {
  chunkId: string;
  content: string;
  sourceDocName?: string;
  /** 标段编号（TENDER 材料绑定的标段；企业资料/技术母本为空） */
  bidSectionCode?: string;
  chapterPath?: string;
  pageNo?: number;
  score: number;
  matchedBy?: string;
  sourceBlockIds?: string[];
}

export interface RetrieveResponse {
  chunks: RetrievedChunk[];
  hit: boolean;
}

export interface ProjectContext {
  projectFacts?: Record<string, unknown>;
  tenderParse?: Record<string, unknown>;
  globalFacts?: Record<string, unknown>;
  [key: string]: unknown;
}

export interface FactItem {
  factId?: string;
  type: string;
  name: string;
  value: string;
  unit?: string;
  validUntil?: string;
  sourceDocName?: string;
  sourceBlockIds?: string[];
}

/**
 * yudao `CommonResult.code` → AppError（B7）：
 * 区分未登录 / 无权限 / 参数错误 / 资源不存在 / 业务冲突，而不是一律归为 server(502)。
 */
function businessCodeToAppError(code: number, message: string): AppError {
  const details = { upstreamCode: code };
  if (code === 401) {
    return new AppError(`Java 未登录：${message}`, { category: 'auth', status: 401, details });
  }
  if (code === 403) {
    return new AppError(`Java 无权限：${message}`, { category: 'auth', status: 403, details });
  }
  if (code === 400) {
    return new AppError(`Java 参数错误：${message}`, { category: 'validation', status: 400, details });
  }
  if (code === 404) {
    return new AppError(`Java 资源不存在：${message}`, { category: 'not_found', status: 404, details });
  }
  if (code === 409) {
    return new AppError(`Java 业务冲突：${message}`, { category: 'conflict', status: 409, details });
  }
  if (code === 429) {
    return new AppError(`Java 限流：${message}`, { category: 'rate_limit', status: 429, details });
  }
  if (code >= 500) {
    return new AppError(`Java 服务错误：${message}`, { category: 'server', status: 502, details });
  }
  return new AppError(`Java 业务失败(${code})：${message}`, {
    category: 'validation',
    status: 502,
    details,
  });
}

export interface ChapterDraftRequest {
  projectId: string;
  nodeId: string;
  content: string;
  refs: string[];
  status: 'draft' | 'review' | 'confirmed';
  version?: number;
}

export interface TaskStatusShadow {
  tenantId: string;
  projectId?: string;
  taskId: string;
  status: string;
  phase?: string;
  progress: number;
  message?: string;
  updatedAt: string;
}

export class JavaApiClient {
  private readonly http: HttpClient;

  private readonly shadowStatusPath: string;

  constructor(config: AppConfig, logger: Logger) {
    this.shadowStatusPath = config.java.shadowStatusPath;
    this.http = new HttpClient({
      baseUrl: config.java.baseUrl,
      name: 'java-api',
      logger,
      timeoutMs: config.java.timeoutMs,
      maxRetries: config.java.maxRetries,
      headers: {
        ...(config.java.apiKey ? { Authorization: `Bearer ${config.java.apiKey}` } : {}),
        ...(config.java.agentKey ? { 'X-Agent-Key': config.java.agentKey } : {}),
      },
    });
  }

  /**
   * 解包 Java 侧统一响应信封 {code,data,msg}（yudao CommonResult）。
   * code !== 0 视为业务/服务错误；若响应本身不是信封（如旧 mock 返回裸数据）则原样返回。
   */
  private unwrap<T>(body: unknown): T {
    if (body && typeof body === 'object') {
      const b = body as { code?: unknown; data?: unknown; msg?: unknown };
      if (typeof b.code === 'number') {
        if (b.code !== 0) {
          throw businessCodeToAppError(b.code, String(b.msg ?? 'Java 服务返回错误'));
        }
        return b.data as T;
      }
    }
    return body as T;
  }

  /** 智能体运行配置（模型/提示词/技能/工具白名单/MCP）：执行侧在任务开始时拉一次 */
  async getAgentRuntime(taskType: string, tenantId: string, signal?: AbortSignal): Promise<Record<string, unknown>> {
    const response = await this.http.get<unknown>(
      '/admin-api/bidding/bid-agent/contract/agent-runtime',
      { taskType, tenantId },
      { signal },
    );
    return this.unwrap<Record<string, unknown>>(response) ?? {};
  }

  /** 回写项目事实卡与招标解析结果（parse 阶段产出） */
  async saveProjectFacts(
    body: { tenantId: string; projectId: string; factsJson?: string; tenderParseJson?: string },
    signal?: AbortSignal,
  ): Promise<void> {
    await this.http.post<unknown>(
      '/admin-api/bidding/bid-agent/contract/project/facts',
      body,
      { signal, idempotencyKey: `facts:${body.projectId}` },
    );
  }

  /** 回写标书章节树（outline 阶段产出） */
  async saveOutline(
    body: { tenantId: string; projectId: string; outlineJson?: string },
    signal?: AbortSignal,
  ): Promise<void> {
    await this.http.post<unknown>(
      '/admin-api/bidding/bid-agent/contract/outline',
      body,
      { signal, idempotencyKey: `outline:${body.projectId}` },
    );
  }

  /** 检索服务门户：向量 + BM25 + 必检通道 + 阈值闸门 + 溯源标注，全部在 Java 侧完成 */
  async retrieve(request: RetrieveRequest, signal?: AbortSignal): Promise<RetrieveResponse> {
    const response = await this.http.post<unknown>(
      '/admin-api/bidding/bid-agent/contract/retrieve',
      {
        tenantId: request.tenantId,
        projectId: request.projectId,
        query: request.query,
        filters: request.filters ?? {},
        topK: request.topK,
        mandatory: request.mandatory ?? [],
      },
      { signal },
    );
    const data = this.unwrap<RetrieveResponse | null>(response);
    return {
      chunks: Array.isArray(data?.chunks) ? data.chunks : [],
      hit: data?.hit === true,
    };
  }

  async getProjectContext(projectId: string, tenantId: string, signal?: AbortSignal): Promise<ProjectContext> {
    const response = await this.http.get<unknown>(
      `/admin-api/bidding/bid-agent/contract/context/${encodeURIComponent(projectId)}`,
      { tenantId },
      { signal },
    );
    return this.unwrap<ProjectContext>(response) ?? {};
  }

  /** COPY_FACT 通道的唯一数据源 */
  async getFacts(tenantId: string, types?: string[], signal?: AbortSignal): Promise<FactItem[]> {
    const response = await this.http.get<unknown>(
      '/admin-api/bidding/bid-agent/contract/facts',
      { tenantId, types: types?.join(',') },
      { signal },
    );
    const data = this.unwrap<FactItem[] | { facts?: FactItem[] } | null>(response);
    if (Array.isArray(data)) return data;
    return data?.facts ?? [];
  }

  /** COPY_PRICING 通道的唯一数据源：共享变量 + 各计价表数据行（列名 → 文本值） */
  async getPricing(
    projectId: string,
    tenantId: string,
    signal?: AbortSignal,
  ): Promise<{ shared: Record<string, string>; tables: Record<string, Array<Record<string, string>>> } | null> {
    const response = await this.http.get<unknown>(
      '/admin-api/bidding/bid-agent/contract/pricing',
      { projectId, tenantId },
      { signal },
    );
    const data = this.unwrap<{ shared?: Record<string, string>; tables?: Record<string, Array<Record<string, string>>> } | null>(response);
    if (!data) return null;
    return {
      shared: data.shared ?? {},
      tables: data.tables ?? {},
    };
  }

  /** 章节草稿回写，按 (projectId, nodeId, version) 幂等 */
  async saveChapterDraft(request: ChapterDraftRequest, tenantId: string, signal?: AbortSignal): Promise<unknown> {
    const version = request.version ?? 1;
    const response = await this.http.post<unknown>(
      '/admin-api/bidding/bid-agent/contract/draft/chapter',
      { ...request, version, tenantId },
      {
        signal,
        idempotencyKey: `${request.projectId}:${request.nodeId}:${version}`,
      },
    );
    return this.unwrap(response);
  }

  /** 影子状态上报：默认关闭，配置 JAVA_SHADOW_STATUS_PATH 后启用 */
  async reportTaskStatus(payload: TaskStatusShadow): Promise<void> {
    if (!this.shadowStatusPath) return;
    await this.http.post(this.shadowStatusPath, payload, {
      idempotencyKey: crypto
        .createHash('sha1')
        .update(`${payload.taskId}:${payload.status}:${payload.phase ?? ''}:${payload.progress}`)
        .digest('hex'),
      retries: 1,
    });
  }
}
