import type { AppConfig } from '../config.js';
import { AppError } from '../errors.js';
import { parseAndValidate, type JsonSchema } from '../lib/jsonSchema.js';
import type { Logger } from '../logger.js';
import type { AgentSession } from '../session/piSessionFactory.js';
import type { ChatMessage, SessionBackend, ToolChoice, Usage } from '../session/types.js';
import { ToolRegistry, type ToolContext } from '../tools/index.js';

export interface AgentLoopDeps {
  backend: SessionBackend;
  tools: ToolRegistry;
  toolContext: ToolContext;
  session: AgentSession;
  logger: Logger;
  config: AppConfig;
}

export interface RunOptions {
  label?: string;
  system: string;
  messages: ChatMessage[];
  /** 断点续跑回灌的历史消息（A2）；仅作上下文，与本次证据冲突时以本次为准 */
  history?: ChatMessage[];
  tools?: string[];
  toolChoice?: ToolChoice;
  outputSchema?: JsonSchema;
  outputHint?: string;
  maxRounds?: number;
  maxFixAttempts?: number;
  temperature?: number;
  signal?: AbortSignal;
}

export interface RunResult<T = unknown> {
  text: string;
  value?: T;
  rounds: number;
  usage?: Usage;
}

function schemaInstruction(schema: JsonSchema, hint: string | undefined): string {
  return [
    hint ?? '请基于证据与事实卡输出结果。',
    '严格输出符合下面 JSON Schema 的 JSON 对象：不要输出解释性文字、不要使用代码块以外的散文。',
    '```json',
    JSON.stringify(schema, null, 2),
    '```',
  ].join('\n');
}

/**
 * Agent 回合控制器：工具调度 + 结构化输出校验 + 失败修复 + 中断响应。
 * 与具体 SDK 无关，Pi SDK / LangGraph 只影响 backend 实现。
 */
export class AgentLoop {
  private readonly backend: SessionBackend;

  private readonly tools: ToolRegistry;

  private readonly toolContext: ToolContext;

  private readonly session: AgentSession;

  private readonly logger: Logger;

  private readonly config: AppConfig;

  /** 本任务累计 token 用量（A14），跨同一任务内的多次 run 累加 */
  private readonly usageTotal: Usage = {};

  constructor(deps: AgentLoopDeps) {
    this.backend = deps.backend;
    this.tools = deps.tools;
    this.toolContext = deps.toolContext;
    this.session = deps.session;
    this.logger = deps.logger;
    this.config = deps.config;
  }

  /** 任务累计用量（A14），任务结束后由编排层落盘 */
  get usage(): Usage {
    return { ...this.usageTotal };
  }

  async run<T = unknown>(options: RunOptions): Promise<RunResult<T>> {
    const maxRounds = options.maxRounds ?? this.config.orchestrator.maxToolRounds;
    const maxFixAttempts = options.maxFixAttempts ?? this.config.orchestrator.maxFixAttempts;
    const label = options.label ?? 'agent-run';
    const signal = options.signal;

    const messages: ChatMessage[] = [{ role: 'system', content: options.system }];
    const history = options.history ?? [];
    if (history.length > 0) {
      messages.push({
        role: 'user',
        content:
          '以下是本任务此前已产生的会话记录（可能来自中断或回退前的执行）。仅作为上下文参考；' +
          '若与本次提供的证据或项目事实卡冲突，一律以本次内容为准，并重新给出完整结果。',
      });
      messages.push(...history);
    }
    messages.push(...options.messages);
    if (options.outputSchema) {
      messages.push({ role: 'user', content: schemaInstruction(options.outputSchema, options.outputHint) });
    }

    const toolSpecs = this.tools.specs(options.tools);
    let rounds = 0;
    let fixAttempts = 0;
    const usage: Usage = {};

    while (rounds < maxRounds) {
      if (signal?.aborted) {
        throw new AppError('任务已被中断', { category: 'abort', retryable: false });
      }
      rounds += 1;

      const response = await this.backend.chat({
        messages,
        tools: toolSpecs.length > 0 ? toolSpecs : undefined,
        toolChoice: options.toolChoice,
        temperature: options.temperature,
        signal,
      });
      const assistant = response.message;
      messages.push(assistant);
      await this.session.append(assistant);
      if (response.usage) {
        const prompt = response.usage.promptTokens ?? 0;
        const completion = response.usage.completionTokens ?? 0;
        const total = response.usage.totalTokens ?? prompt + completion;
        usage.promptTokens = (usage.promptTokens ?? 0) + prompt;
        usage.completionTokens = (usage.completionTokens ?? 0) + completion;
        usage.totalTokens = (usage.totalTokens ?? 0) + total;
        this.usageTotal.promptTokens = (this.usageTotal.promptTokens ?? 0) + prompt;
        this.usageTotal.completionTokens = (this.usageTotal.completionTokens ?? 0) + completion;
        this.usageTotal.totalTokens = (this.usageTotal.totalTokens ?? 0) + total;
        const budget = this.config.orchestrator.maxTaskTokens;
        if (budget > 0 && (this.usageTotal.totalTokens ?? 0) >= budget) {
          throw new AppError(
            `任务 token 用量已达上限（${budget}），已中止以避免成本失控`,
            { category: 'output_invalid', retryable: false, details: { usage: this.usageTotal, budget } },
          );
        }
      }

      const toolCalls = assistant.toolCalls ?? [];
      if (toolCalls.length > 0) {
        for (const call of toolCalls) {
          let args: unknown = {};
          try {
            args = call.function.arguments ? JSON.parse(call.function.arguments) : {};
          } catch (error) {
            args = { __parseError: (error as Error).message };
          }
          this.logger.debug('工具调用', { label, tool: call.function.name, args });
          let result;
          try {
            result = await this.tools.execute(call.function.name, args, this.toolContext);
          } catch (error) {
            const err = error instanceof Error ? error : new Error(String(error));
            result = { ok: false, content: { error: `工具执行异常：${err.message}` }, summary: '工具异常' };
          }
          const toolMessage = ToolRegistry.toolMessage(call.id, call.function.name, result);
          messages.push(toolMessage);
          await this.session.append(toolMessage);
        }
        // task-failure 上报后立即收束回合，交由编排层转人工
        if (this.toolContext.failures.length > 0) {
          this.logger.warn('工具上报失败，结束回合', { label, reasons: this.toolContext.failures });
          throw new AppError(this.toolContext.failures.join('；'), { category: 'validation', retryable: false });
        }
        continue;
      }

      const text = assistant.content ?? '';
      if (!options.outputSchema) {
        return { text, rounds, usage };
      }

      const parsed = parseAndValidate<T>(options.outputSchema, text);
      if (parsed.ok) {
        return { text, value: parsed.value, rounds, usage };
      }

      fixAttempts += 1;
      if (fixAttempts > maxFixAttempts) {
        throw new AppError(`多次修复后输出仍不符合结构要求：${parsed.error}`, {
          category: 'output_invalid',
          retryable: false,
        });
      }
      this.logger.warn('结构化输出校验失败，触发修复轮', { label, attempt: fixAttempts, error: parsed.error });
      const repair: ChatMessage = {
        role: 'user',
        content: [
          `上一版输出未通过校验：${parsed.error}`,
          '请先调用 json-validation 自检，再重新只输出 JSON。',
          schemaInstruction(options.outputSchema, options.outputHint),
        ].join('\n'),
      };
      messages.push(repair);
      await this.session.append(repair);
    }

    throw new AppError(`超过最大回合数（${maxRounds}）仍未产出合规结果`, {
      category: 'output_invalid',
      retryable: false,
    });
  }
}
