import type { AppConfig } from '../config.js';
import type { JavaApiClient } from '../client/javaApi.js';
import type { AppError, ErrorCategory } from '../errors.js';
import type { Logger } from '../logger.js';
import type { CheckpointStore } from './checkpoints.js';
import type { HilService, PendingQuestion } from './hil.js';
import type { PhaseMachine } from './stateMachine.js';
import type { AgentLoop } from './runner.js';
import type { ChatMessage, SessionBackend } from '../session/types.js';
import type { AgentSession } from '../session/piSessionFactory.js';
import type { ToolRegistry } from '../tools/index.js';
import type { WorkspaceManager } from '../workspace/manager.js';

/** 任务级累计 token 用量（A14） */
export interface TokenUsageTotal {
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  /** 触发预算拦截时为 true（A14：MAX_TASK_TOKENS > 0 时生效） */
  budgetExceeded?: boolean;
}

export type TaskType = 'parse' | 'outline' | 'draft' | 'generate';

export const TASK_TYPES: readonly TaskType[] = ['parse', 'outline', 'draft', 'generate'];

export type TaskStatus =
  | 'pending'
  | 'running'
  | 'awaiting_human'
  | 'completed'
  | 'failed'
  | 'interrupted'
  | 'rolled_back';

export interface TaskRecord {
  taskId: string;
  tenantId: string;
  projectId: string;
  type: TaskType;
  status: TaskStatus;
  phase: string;
  progress: number;
  currentNodeId?: string;
  payload: Record<string, unknown>;
  questions: PendingQuestion[];
  result?: Record<string, unknown>;
  error?: { code: ErrorCategory; message: string; details?: unknown };
  createdAt: string;
  updatedAt: string;
  version: number;
  resumedFrom?: string;
  idempotencyKey?: string;
  /** 已写入的检查点数量 */
  checkpoints?: number;
  /** 任务累计 token 用量（A14） */
  usage?: TokenUsageTotal;
}

/** §3.2 GET /agent/tasks/{taskId} 的响应形态 */
export interface TaskView {
  taskId: string;
  taskType: TaskType;
  status: TaskStatus;
  phase: string;
  progress: number;
  currentNodeId?: string;
  pendingQuestions: PendingQuestion[];
  result?: Record<string, unknown>;
  error?: TaskRecord['error'];
  /** 任务累计 token 用量（A14） */
  usage?: TokenUsageTotal;
  createdAt: string;
  updatedAt: string;
}

export interface TaskRunContext {
  record: TaskRecord;
  /**
   * 从 Java 拉取的运行配置（模型/提示词/技能/工具白名单/MCP）。
   * 只存在内存，不写入 payload 与 session 文件（其中含云端模型 api-key）。
   */
  runtime?: AgentRuntime | null;
  config: AppConfig;
  logger: Logger;
  signal: AbortSignal;
  java: JavaApiClient;
  workspace: WorkspaceManager;
  session: AgentSession;
  backend: SessionBackend;
  tools: ToolRegistry;
  hil: HilService;
  sm: PhaseMachine;
  checkpoints: CheckpointStore;
  loop: AgentLoop;
  /** 断点续跑回灌的历史消息，新任务为空数组（A2） */
  resumeHistory: ChatMessage[];
  /** 更新并持久化任务记录 */
  patch(update: Partial<TaskRecord>): Promise<void>;
  /** 进入下一阶段并落点 */
  advance(phase?: string): Promise<void>;
  /** 等待人工答复（会挂起当前协程直到 /confirm） */
  askUser(input: {
    question: string;
    options?: string[];
    required?: boolean;
    context?: string;
  }): Promise<{ answer: string; question: PendingQuestion }>;
  fail(error: AppError | Error): Promise<void>;
}

export interface TaskArtifact {
  path: string;
  kind: 'markdown' | 'json' | 'other';
}

export interface TaskOutcome {
  summary: string;
  data?: Record<string, unknown>;
  artifacts?: TaskArtifact[];
}

/** 单个技能（提示词增强包） */
export interface AgentSkillRuntime {
  code: string;
  name: string;
  promptFragment?: string | null;
  knowledgeRoles?: string[];
  toolWhitelist?: string[];
}

/** MCP 服务运行配置 */
export interface McpServerRuntime {
  name: string;
  transport: 'stdio' | 'sse' | 'streamable-http' | string;
  url?: string | null;
  command?: string | null;
  args?: string | null;
  env?: string | null;
  headers?: string | null;
  timeoutSeconds?: number | null;
  toolNames?: string[] | null;
}

/** 云端模型运行参数（Java 智能体管理下发；缺失时回退 .env 的 LLM_*） */
export interface LlmRuntime {
  provider?: string;
  baseUrl: string;
  model: string;
  apiKey: string;
  temperature?: number | null;
  maxTokens?: number | null;
  timeoutMs?: number | null;
  maxRetries?: number | null;
}

/** 单阶段运行配置（generate 全流程按阶段取用） */
export interface AgentStageRuntime {
  systemPrompt?: string | null;
  skills?: AgentSkillRuntime[];
  toolWhitelist?: string[];
  knowledgeRoles?: string[];
  mcpServers?: McpServerRuntime[];
}

/** 智能体运行配置（Java GET /contract/agent-runtime 的返回） */
export interface AgentRuntime extends AgentStageRuntime {
  taskType?: string;
  tenantId?: string | number | null;
  llm?: LlmRuntime | null;
  warnings?: string[];
  /** taskType=generate 时按阶段给出配置（键为 PARSE/OUTLINE/DRAFT） */
  stages?: Record<string, AgentStageRuntime>;
}

export interface TaskHandler {
  readonly type: TaskType;
  readonly displayName: string;
  run(ctx: TaskRunContext): Promise<TaskOutcome>;
}
