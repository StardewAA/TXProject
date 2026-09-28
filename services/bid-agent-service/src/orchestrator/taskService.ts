import fs from 'node:fs/promises';
import path from 'node:path';
import type { AppConfig } from '../config.js';
import type { JavaApiClient } from '../client/javaApi.js';
import { AppError, normalizeError } from '../errors.js';
import { ensureDir, randomId, writeAtomic } from '../lib/fsx.js';
import { Metrics } from '../lib/metrics.js';
import type { Logger } from '../logger.js';
import { createBackend } from '../session/providers/piSdk.js';
import { AgentSession } from '../session/piSessionFactory.js';
import { ToolRegistry, type ToolContext } from '../tools/index.js';
import { WorkspaceManager } from '../workspace/manager.js';
import { getTaskHandler } from '../tasks/registry.js';
import { loadMcpTools } from '../tools/mcpTools.js';
import { CheckpointStore } from './checkpoints.js';
import type { HilService, PendingQuestion } from './hil.js';
import { PhaseMachine, TASK_PHASES } from './stateMachine.js';
import type {
  TaskOutcome,
  TaskRecord,
  TaskRunContext,
  TaskStatus,
  TaskType,
  TaskView,
} from './types.js';
import { AgentLoop } from './runner.js';

export interface CreateTaskInput {
  tenantId: string;
  projectId: string;
  taskType: TaskType;
  payload?: Record<string, unknown>;
  idempotencyKey?: string;
}

interface ActiveTask {
  record: TaskRecord;
  controller: AbortController;
  /** 已执行一次取值：同任务重跑（rollback）后旧协程不得再改写 status */
  generation: number;
  /** 已被 API 侧判定终止 */
  stopped: boolean;
}

/** 排队待执行的任务（A11 背压 / A12 项目亲和） */
interface QueuedTask {
  taskId: string;
  record: TaskRecord;
  startPhase?: string;
  reason: 'create' | 'rollback' | 'reconcile';
}

/** 任务终态：不允许再被 interrupt 改写（A3） */
const TERMINAL_STATUSES: ReadonlySet<TaskStatus> = new Set<TaskStatus>([
  'completed',
  'failed',
  'interrupted',
]);

export class TaskService {
  private readonly config: AppConfig;

  private readonly logger: Logger;

  private readonly java: JavaApiClient;

  private readonly hil: HilService;

  private readonly metrics: Metrics;

  /** 任务状态变更订阅者（B6）：SSE 事件流据此推送，不再轮询磁盘 */
  private readonly listeners = new Set<(record: TaskRecord) => void>();

  /** 任务记录内存索引：磁盘仍是持久化事实源，内存避免每次全量扫描（A13） */
  private readonly records = new Map<string, TaskRecord>();

  /** 正在运行、已占用并发额度的任务 */
  private readonly active = new Map<string, ActiveTask>();

  /** 等待调度的任务队列（A11） */
  private readonly queue: QueuedTask[] = [];

  /** 正在被占用的项目工作区：同一项目串行，避免并发覆盖共享产物（A12） */
  private readonly lockedProjects = new Set<string>();

  /** 当前运行中的任务数（A11） */
  private running = 0;

  /** 是否已从磁盘加载过任务记录 */
  private loaded = false;

  /** 同一任务的持久化串行化：避免并发 patch 在 Windows 上撞 rename */
  private readonly writeQueue = new Map<string, Promise<void>>();

  /** 单调递增的运行代号，用于区分同一任务的多次执行 */
  private runGeneration = 0;

  constructor(deps: {
    config: AppConfig;
    logger: Logger;
    java: JavaApiClient;
    hil: HilService;
    metrics?: Metrics;
  }) {
    this.config = deps.config;
    this.logger = deps.logger;
    this.java = deps.java;
    this.hil = deps.hil;
    this.metrics = deps.metrics ?? new Metrics();
  }

  /** 订阅任务状态变更（B6）；返回取消订阅函数 */
  onUpdate(listener: (record: TaskRecord) => void): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  /** 是否为终态（B6）：SSE 收到终态后应推送结束事件并关闭连接 */
  static isTerminal(status: TaskStatus): boolean {
    return TERMINAL_STATUSES.has(status);
  }

  private emit(record: TaskRecord): void {
    for (const listener of this.listeners) {
      try {
        listener(record);
      } catch (error) {
        this.logger.warn('任务事件订阅者异常', { taskId: record.taskId, message: String(error) });
      }
    }
  }

  private taskFile(taskId: string): string {
    return path.join(this.config.paths.sessions, taskId, 'task.json');
  }

  private async persist(record: TaskRecord): Promise<void> {
    const previous = this.writeQueue.get(record.taskId) ?? Promise.resolve();
    const next = previous.then(async () => {
      await this.writeRecord(record);
    });
    this.writeQueue.set(record.taskId, next);
    try {
      await next;
      this.records.set(record.taskId, record);
    } finally {
      // 队列收敛：长期运行下 writeQueue 不再无限增长（A13）
      if (this.writeQueue.get(record.taskId) === next) this.writeQueue.delete(record.taskId);
    }
  }

  /**
   * 启动对账（A9 / A10）：进程重启后，崩溃前处于 running/pending/awaiting_human 的任务
   * 都不可能再被原协程推进（HIL 等待者随进程消失），统一收敛为 interrupted 并给出说明，
   * 避免任务永久悬挂在中间状态。可通过 rollback 从检查点续跑。
   */
  async reconcileOnStartup(): Promise<{ scanned: number; recovered: string[] }> {
    await this.ensureLoaded();
    const recovered: string[] = [];
    for (const record of this.records.values()) {
      if (record.status !== 'running' && record.status !== 'pending' && record.status !== 'awaiting_human') {
        continue;
      }
      const previous = record.status;
      await this.patch(record, {
        status: 'interrupted',
        questions: [],
        error: {
          code: 'abort',
          message:
            previous === 'awaiting_human'
              ? '服务重启，人工确认会话已失效；如需继续请通过 rollback 从检查点重跑该阶段'
              : '服务重启导致任务中断；如需继续请通过 rollback 从检查点续跑',
        },
      });
      recovered.push(record.taskId);
    }
    if (recovered.length > 0) {
      this.logger.warn('启动对账：已将悬挂任务收敛为 interrupted', {
        count: recovered.length,
        taskIds: recovered.slice(0, 20),
      });
    }
    return { scanned: this.records.size, recovered };
  }

  private async ensureLoaded(): Promise<void> {
    if (this.loaded) return;
    const dir = this.config.paths.sessions;
    let entries: string[] = [];
    try {
      entries = await fs.readdir(dir);
    } catch {
      entries = [];
    }
    for (const entry of entries) {
      try {
        const text = await fs.readFile(path.join(dir, entry, 'task.json'), 'utf8');
        const record = JSON.parse(text) as TaskRecord;
        this.records.set(record.taskId, record);
      } catch {
        // 跳过非任务目录或损坏文件
      }
    }
    this.loaded = true;
  }

  private async writeRecord(record: TaskRecord): Promise<void> {
    const file = this.taskFile(record.taskId);
    const payload = `${JSON.stringify(record, null, 2)}\n`;
    let lastError: unknown;
    for (let attempt = 1; attempt <= 3; attempt += 1) {
      try {
        await writeAtomic(file, payload);
        lastError = undefined;
        break;
      } catch (error) {
        lastError = error;
        await new Promise((resolve) => setTimeout(resolve, 20 * attempt));
      }
    }
    if (lastError !== undefined) {
      this.logger.error('任务状态持久化失败', { taskId: record.taskId, message: String(lastError) });
      return;
    }
    this.reportShadowStatus(record);
  }

  private reportShadowStatus(record: TaskRecord): void {
    if (!this.config.java.shadowStatusPath) return;
    const message = record.error?.message ?? (typeof record.result?.summary === 'string' ? record.result.summary : undefined);
    void this.java
      .reportTaskStatus({
        tenantId: record.tenantId,
        projectId: record.projectId,
        taskId: record.taskId,
        status: record.status,
        phase: record.phase,
        progress: record.progress,
        message,
        updatedAt: record.updatedAt,
      })
      .catch((error) => this.logger.warn('影子状态上报失败', { message: String(error) }));
  }

  private async patch(
    record: TaskRecord,
    update: Partial<TaskRecord>,
    generation?: number,
  ): Promise<void> {
    const applied: Partial<TaskRecord> = { ...update };
    const active = this.active.get(record.taskId);
    // 已中断或被新实例取代的运行，不能继续改写任务状态
    const stale = generation === undefined ? false : !active || active.stopped || active.generation !== generation;
    if (stale && applied.status) delete applied.status;
    Object.assign(record, applied, {
      updatedAt: new Date().toISOString(),
      version: (record.version ?? 0) + 1,
    });
    await this.persist(record);
    this.emit(record);
  }

  async create(input: CreateTaskInput): Promise<TaskRecord> {
    const existing = input.idempotencyKey
      ? await this.findByIdempotencyKey(input.idempotencyKey)
      : undefined;
    if (existing) {
      this.logger.info('幂等命中，返回既有任务', { taskId: existing.taskId, key: input.idempotencyKey });
      return existing;
    }

    // 背压：队列满时直接拒绝，避免把压力透传到私有化 vLLM（A11）
    if (this.queue.length >= this.config.orchestrator.maxQueueDepth) {
      throw new AppError(
        `任务队列已满（上限 ${this.config.orchestrator.maxQueueDepth}），请稍后重试`,
        { category: 'rate_limit', status: 429, retryable: true },
      );
    }

    const now = new Date().toISOString();
    const record: TaskRecord = {
      taskId: randomId('task'),
      tenantId: input.tenantId,
      projectId: input.projectId,
      type: input.taskType,
      status: 'pending',
      phase: TASK_PHASES[input.taskType][0] ?? 'INIT',
      progress: 0,
      payload: input.payload ?? {},
      questions: [],
      createdAt: now,
      updatedAt: now,
      version: 1,
      idempotencyKey: input.idempotencyKey,
    };
    await ensureDir(path.join(this.config.paths.sessions, record.taskId));
    this.records.set(record.taskId, record);
    await this.persist(record);
    this.emit(record);
    this.schedule(record, undefined, 'create');
    this.metrics.increment('task_created_total', { task_type: record.type }, 1, '任务创建数');
    return record;
  }

  async findByIdempotencyKey(key: string): Promise<TaskRecord | undefined> {
    await this.ensureLoaded();
    for (const record of this.records.values()) {
      if (record.idempotencyKey === key) return record;
    }
    return undefined;
  }

  async get(taskId: string): Promise<TaskRecord | undefined> {
    await this.ensureLoaded();
    const cached = this.records.get(taskId);
    if (cached) return cached;
    try {
      const text = await fs.readFile(this.taskFile(taskId), 'utf8');
      const record = JSON.parse(text) as TaskRecord;
      this.records.set(record.taskId, record);
      return record;
    } catch {
      return undefined;
    }
  }

  toView(record: TaskRecord): TaskView {
    return {
      taskId: record.taskId,
      taskType: record.type,
      status: record.status,
      phase: record.phase,
      progress: Math.round(record.progress * 100) / 100,
      currentNodeId: record.currentNodeId,
      pendingQuestions: record.questions,
      result: record.result,
      error: record.error,
      usage: record.usage,
      createdAt: record.createdAt,
      updatedAt: record.updatedAt,
    };
  }

  async list(
    filter: {
      projectId?: string;
      status?: TaskStatus;
      type?: TaskType;
      /** 调用方可见的租户范围；传空数组表示无可见租户（B2） */
      tenantIds?: string[];
    } = {},
  ): Promise<TaskView[]> {
    await this.ensureLoaded();
    return [...this.records.values()]
      .sort((a, b) => a.createdAt.localeCompare(b.createdAt))
      .filter((record) => (filter.tenantIds ? filter.tenantIds.includes(record.tenantId) : true))
      .filter((record) => (filter.projectId ? record.projectId === filter.projectId : true))
      .filter((record) => (filter.status ? record.status === filter.status : true))
      .filter((record) => (filter.type ? record.type === filter.type : true))
      .map((record) => this.toView(record));
  }

  async confirm(taskId: string, answers: Array<{ questionId: string; value: string }>): Promise<TaskRecord> {
    const record = await this.get(taskId);
    if (!record) throw new AppError(`任务不存在：${taskId}`, { category: 'not_found', status: 404 });
    if (record.status !== 'awaiting_human') {
      throw new AppError(`任务当前不在等待人工状态（status=${record.status}）`, {
        category: 'conflict',
        status: 409,
      });
    }
    const results = answers.map((answer) => this.hil.answer(answer.questionId, answer.value));
    const unresolved = answers.filter((_, index) => results[index] === false);
    if (unresolved.length > 0) {
      throw new AppError(`以下问题已失效：${unresolved.map((item) => item.questionId).join(', ')}`, {
        category: 'not_found',
        status: 404,
      });
    }
    await this.patch(record, {
      questions: record.questions.filter(
        (question) => !answers.some((answer) => answer.questionId === question.questionId),
      ),
      status: 'running',
    });
    return record;
  }

  async interrupt(taskId: string, reason = '业务侧中断'): Promise<TaskRecord> {
    const record = await this.get(taskId);
    if (!record) throw new AppError(`任务不存在：${taskId}`, { category: 'not_found', status: 404 });
    // 终态守卫：completed / failed / interrupted 不允许被改写成 interrupted（A3）
    if (TERMINAL_STATUSES.has(record.status)) {
      throw new AppError(`任务已处于终态（status=${record.status}），不能再执行中断`, {
        category: 'conflict',
        status: 409,
      });
    }
    const active = this.active.get(taskId);
    if (!active) {
      this.dequeue(taskId);
      await this.patch(record, { status: 'interrupted', questions: [] });
      return record;
    }
    active.stopped = true;
    this.hil.cancelTask(taskId, reason);
    active.controller.abort(new AppError(reason, { category: 'abort' }));
    await this.patch(active.record, { status: 'interrupted', questions: [] });
    return active.record;
  }

  /**
   * 回退重跑：阶段指针回到 toPhase 并重新执行任务。
   * keepSession=true 保留 LLM 会话历史（断点续跑），false 则清空会话让模型重新推理。
   * nodeId（A4）：章节类任务按节点定位检查点，实现"只重跑某一章"。
   */
  async rollback(
    taskId: string,
    toPhase: string,
    keepSession = true,
    nodeId?: string,
  ): Promise<TaskRecord> {
    const record = await this.get(taskId);
    if (!record) throw new AppError(`任务不存在：${taskId}`, { category: 'not_found', status: 404 });
    const machine = new PhaseMachine(record.type);
    machine.resetTo(toPhase);
    const active = this.active.get(taskId);
    if (active) {
      this.hil.cancelTask(taskId, '回退重跑，已中断当前执行');
      active.controller.abort(new AppError('回退重跑中断', { category: 'abort' }));
    }
    this.dequeue(taskId);
    const store = new CheckpointStore(this.config.paths.sessions, record.taskId);
    await store.load();
    const kept = await store.truncateAfter(toPhase, nodeId);
    if (!kept) {
      this.logger.warn('回退未匹配到检查点，将从该阶段起点重跑', { taskId, toPhase, nodeId });
    }
    const session = await AgentSession.open({
      taskId: record.taskId,
      sessionsDir: this.config.paths.sessions,
      config: this.config,
      logger: this.logger,
    });
    await session.reset(keepSession);
    await this.patch(record, {
      status: 'rolled_back',
      phase: machine.current,
      progress: machine.progress(),
      resumedFrom: toPhase,
      error: undefined,
    });
    this.schedule(record, machine.current, 'rollback');
    return record;
  }

  /** 入队并尝试调度（A11） */
  private schedule(
    record: TaskRecord,
    startPhase?: string,
    reason: QueuedTask['reason'] = 'create',
  ): void {
    this.dequeue(record.taskId);
    this.queue.push({ taskId: record.taskId, record, startPhase, reason });
    this.pump();
  }

  private dequeue(taskId: string): boolean {
    const index = this.queue.findIndex((item) => item.taskId === taskId);
    if (index < 0) return false;
    this.queue.splice(index, 1);
    return true;
  }

  private projectKey(record: TaskRecord): string {
    return `${record.tenantId}/${record.projectId}`;
  }

  /**
   * 调度循环：并发上限（A11）+ 项目亲和（A12，同一项目串行，避免并发覆盖共享产物）。
   * 项目被占用时跳过该任务、继续找其他项目，避免空占并发额度。
   */
  private pump(): void {
    const limit = this.config.orchestrator.maxConcurrentTasks;
    while (this.running < limit && this.queue.length > 0) {
      const index = this.queue.findIndex(
        (item) => !this.lockedProjects.has(this.projectKey(item.record)),
      );
      if (index < 0) {
        this.refreshQueueMetrics();
        return;
      }
      const [item] = this.queue.splice(index, 1);
      if (!item) return;
      const record = item.record;
      this.lockedProjects.add(this.projectKey(record));
      this.running += 1;
      this.runGeneration += 1;
      const entry: ActiveTask = {
        record,
        controller: new AbortController(),
        generation: this.runGeneration,
        stopped: false,
      };
      this.active.set(record.taskId, entry);
      this.logger.info('任务开始执行', {
        taskId: record.taskId,
        taskType: record.type,
        reason: item.reason,
        running: this.running,
        queued: this.queue.length,
      });
      void this.execute(record, entry, item.startPhase)
        .catch(async (error) => {
          const normalized = normalizeError(error);
          this.logger.error('任务执行失败', { taskId: record.taskId, error: normalized.message });
          this.metrics.increment(
            'task_failed_total',
            { task_type: record.type, category: normalized.category },
            1,
            '任务失败数',
          );
          await this.patch(
            record,
            {
              status: 'failed',
              error: { code: normalized.category, message: normalized.message, details: normalized.details },
            },
            entry.generation,
          );
        })
        .finally(() => {
          this.running -= 1;
          this.lockedProjects.delete(this.projectKey(record));
          this.active.delete(record.taskId);
          this.refreshQueueMetrics();
          this.pump();
        });
    }
    this.refreshQueueMetrics();
  }

  private refreshQueueMetrics(): void {
    this.metrics.set('tasks_running', this.running, {}, '运行中任务数');
    this.metrics.set('tasks_queued', this.queue.length, {}, '排队任务数');
  }

  /**
   * 拉取智能体运行配置（Java 智能体管理）：模型/提示词/技能/工具白名单/MCP。
   * 目的：提示词与模型由后台统一维护，底座只负责执行；拉取失败一律回退 .env 与内置提示词，不阻断任务。
   */
  private async fetchRuntime(record: TaskRecord, logger: Logger): Promise<Record<string, any> | null> {
    try {
      const runtime = await this.java.getAgentRuntime(record.type, record.tenantId);
      const warnings = Array.isArray(runtime?.warnings) ? (runtime.warnings as string[]) : [];
      logger.info('运行配置已拉取', {
        taskType: record.type,
        hasLlm: Boolean(runtime?.llm),
        skills: Array.isArray(runtime?.skills) ? (runtime.skills as unknown[]).length : 0,
        toolWhitelist: runtime?.toolWhitelist ?? null,
        mcpServers: Array.isArray(runtime?.mcpServers) ? (runtime.mcpServers as unknown[]).length : 0,
        warnings,
      });
      return runtime as Record<string, any>;
    } catch (error) {
      logger.warn('拉取运行配置失败，回退 .env 与内置提示词', { error: (error as Error).message });
      return null;
    }
  }

  private async execute(record: TaskRecord, entry: ActiveTask, startPhase?: string): Promise<void> {
    const controller = entry.controller;
    const generation = entry.generation;
    const handler = getTaskHandler(record.type);
    const logger = this.logger.child({ taskId: record.taskId, taskType: record.type });
    // 运行配置：模型（云端，来自 Java 智能体管理）优先，缺失回退 .env；技能/工具白名单/MCP 同步生效。
    const runtime = await this.fetchRuntime(record, logger);
    // 合并时跳过空值：云端模型未配 API Key 时（llm.apiKey 为空）保留 .env 的 LLM_API_KEY 兜底，
    // 避免 null 覆盖掉部署环境里的可用 Key（401）。
    const runtimeLlm = (runtime?.llm ?? {}) as Record<string, unknown>;
    const llmOverride = Object.fromEntries(
      Object.entries(runtimeLlm).filter(
        ([, value]) => value !== null && value !== undefined && value !== '',
      ),
    );
    const taskConfig: AppConfig = runtimeLlm
      ? ({ ...this.config, llm: { ...this.config.llm, ...llmOverride } } as AppConfig)
      : this.config;
    const workspace = WorkspaceManager.open(
      this.config.paths.workspace,
      record.tenantId,
      record.projectId,
      // 一键生成按任务隔离工作区：门户任务共享 projectId=0，按 taskId 隔离避免多用户产物互相覆盖（串档）；
      // 单独跑的 parse/outline/draft 任务仍共享项目工作区（阶段间传递常驻文件）
      record.type === 'generate' ? record.taskId : undefined,
    );
    await workspace.init();
    const checkpoints = new CheckpointStore(this.config.paths.sessions, record.taskId);
    await checkpoints.load();
    const backend = createBackend(taskConfig, logger);
    const session = await AgentSession.open({
      taskId: record.taskId,
      sessionsDir: this.config.paths.sessions,
      config: taskConfig,
      logger,
      backend,
    });
    // 断点续跑：把本任务此前的会话记录按上限回灌（A2）。首次执行时 transcript 为空，天然为空数组。
    const resumeHistory = session.historyForResume(
      this.config.orchestrator.resumeHistoryMessages,
      this.config.orchestrator.resumeHistoryChars,
    );
    // 工具白名单：来自智能体管理（tool_whitelist 或技能建议工具并集）；空=全部允许
    const tools = new ToolRegistry(undefined, runtime?.toolWhitelist ?? null);
    // MCP 工具（智能体管理登记的 MCP 服务）：以 mcp__{server}__{tool} 注入，并同样受白名单约束
    for (const tool of await loadMcpTools(runtime?.mcpServers ?? [], logger)) {
      tools.register(tool, runtime?.toolWhitelist ?? null);
    }
    const sm = new PhaseMachine(record.type, undefined, startPhase ?? record.phase);
    const failures: string[] = [];

    const patch = async (update: Partial<TaskRecord>): Promise<void> => {
      await this.patch(record, update, generation);
    };

    const toolContext: ToolContext = {
      record,
      logger,
      java: this.java,
      workspace,
      signal: controller.signal,
      failures,
      askUser: (input) => this.ask(record, controller.signal, input, generation),
      noteFailure: (reason) => failures.push(reason),
    };

    const loop = new AgentLoop({ backend, tools, toolContext, session, logger, config: this.config });

    const ctx: TaskRunContext = {
      record,
      config: this.config,
      logger,
      signal: controller.signal,
      java: this.java,
      workspace,
      session,
      backend,
      tools,
      hil: this.hil,
      sm,
      checkpoints,
      loop,
      resumeHistory,
      patch,
      advance: async (phase?: string) => {
        if (phase) sm.moveTo(phase);
        else sm.advance();
        const saved = await checkpoints.save({
          taskId: record.taskId,
          type: record.type,
          phase: sm.current,
          snapshot: { currentNodeId: record.currentNodeId, progress: sm.progress() },
        });
        await patch({ phase: sm.current, progress: sm.progress(), checkpoints: saved.seq });
      },
      askUser: (input) => this.ask(record, controller.signal, input, generation),
      fail: async (error: AppError) => {
        const normalized = normalizeError(error);
        await patch({
          status: 'failed',
          error: { code: normalized.category, message: normalized.message, details: normalized.details },
        });
      },
    };

    ctx.runtime = runtime;
    await patch({ status: 'running', phase: sm.current, progress: sm.progress() });
    logger.info('任务开始', { taskType: record.type, backend: session.backendName, phase: sm.current });

    let outcome: TaskOutcome;
    try {
      outcome = await handler.run(ctx);
    } finally {
      // 无论成功失败都落盘用量，避免成本与异常消耗不可归因（A14）
      const usage = loop.usage;
      const promptTokens = usage.promptTokens ?? 0;
      const completionTokens = usage.completionTokens ?? 0;
      const totalTokens = usage.totalTokens ?? promptTokens + completionTokens;
      if (promptTokens > 0 || completionTokens > 0) {
        await patch({ usage: { promptTokens, completionTokens, totalTokens } });
        this.metrics.increment('llm_tokens_total', { task_type: record.type }, totalTokens, '累计 token 用量');
      }
    }

    await patch({
      status: 'completed',
      phase: 'DONE',
      progress: 1,
      result: {
        summary: outcome.summary,
        ...(outcome.data ?? {}),
        artifacts: outcome.artifacts ?? [],
      },
    });
    logger.info('任务完成', { summary: outcome.summary });
    this.metrics.increment('task_completed_total', { task_type: record.type }, 1, '任务完成数');
  }

  async ask(
    record: TaskRecord,
    signal: AbortSignal,
    input: { question: string; options?: string[]; required?: boolean; context?: string },
    generation?: number,
  ): Promise<{ answer: string; question: PendingQuestion }> {
    // 一键生成（generate 全流程）不阻塞等人：阶段确认闸门与 ask-user 工具统一自动答复，
    // 不再进入 awaiting_human 等待 /confirm。单独跑的 parse/outline/draft 任务仍保留人工确认。
    if (record.type === 'generate') {
      const options = input.options ?? [];
      const answer = options.includes('确认') ? '确认'
        : options.includes('保留') ? '保留'
        : '跳过';
      this.logger.info('一键生成自动确认（HIL 旁路）', {
        taskId: record.taskId,
        phase: record.phase,
        answer,
        question: input.question,
      });
      return {
        answer,
        question: {
          questionId: `auto-${Date.now()}`,
          taskId: record.taskId,
          question: input.question,
          options: input.options,
          required: input.required ?? true,
          context: input.context,
          createdAt: new Date().toISOString(),
        },
      };
    }
    const { question, answer } = this.hil.ask({
      taskId: record.taskId,
      question: input.question,
      options: input.options,
      required: input.required,
      context: input.context,
      signal,
    });
    const patchQuestions = async (questions: PendingQuestion[], status?: TaskStatus): Promise<void> => {
      await this.patch(record, { questions, ...(status ? { status } : {}) }, generation);
    };
    await patchQuestions([...record.questions, question], 'awaiting_human');
    try {
      const value = await answer;
      await patchQuestions(
        record.questions.filter((item) => item.questionId !== question.questionId),
        'running',
      );
      return { answer: value, question };
    } catch (error) {
      await patchQuestions(record.questions.filter((item) => item.questionId !== question.questionId));
      throw normalizeError(error);
    }
  }

  async shutdown(reason = '服务关闭'): Promise<void> {
    // 排队中的任务尚未开始，直接收敛为 interrupted（A11）
    for (const item of this.queue.splice(0, this.queue.length)) {
      await this.patch(item.record, {
        status: 'interrupted',
        questions: [],
        error: { code: 'abort', message: reason },
      });
    }
    for (const [taskId, task] of [...this.active.entries()]) {
      this.hil.cancelTask(taskId, reason);
      task.controller.abort(new AppError(reason, { category: 'abort' }));
      await this.patch(task.record, { status: 'interrupted' });
    }
  }
}
