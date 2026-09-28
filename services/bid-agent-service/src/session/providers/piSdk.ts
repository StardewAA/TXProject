import type { AppConfig } from '../../config.js';
import { AppError } from '../../errors.js';
import type { Logger } from '../../logger.js';
import type { ChatRequest, ChatResponse, SessionBackend } from '../types.js';
import { OpenAiCompatBackend } from './openaiCompat.js';

/**
 * Pi SDK 适配器（架构文档 §4.1 / 风险登记表）。
 *
 * 约定：PI_SDK_MODULE 指向一个可 import 的模块，默认导出需要具备
 *   createBidSession({ model, baseUrl, apiKey }) => { chat(messages, options) }
 * 的工厂方法（易标 piSessionFactory.cjs 剥离 Electron 依赖后即为此形态）。
 *
 * 会话与工作区格式由本服务自己定义（transcript.jsonl），避免与 Pi SDK 深绑定；
 * 因此即使换成 LangGraph 或后续升级 Pi 版本，编排层无需改动。
 */

interface PiSdkSessionFactory {
  createSession(input: {
    model: string;
    baseUrl: string;
    apiKey: string;
  }): Promise<PiSessionLike> | PiSessionLike;
}

interface PiSessionLike {
  chat(input: {
    messages: ChatRequest['messages'];
    tools?: ChatRequest['tools'];
    toolChoice?: ChatRequest['toolChoice'];
    temperature?: number;
    maxTokens?: number;
    signal?: AbortSignal;
  }): Promise<{ content?: string | null; toolCalls?: ChatRequest['messages'][number]['toolCalls']; raw?: unknown }>;
}

export class PiSdkBackend implements SessionBackend {
  readonly name = 'pi-sdk';

  private sessionPromise: Promise<PiSessionLike> | undefined;

  private readonly config: AppConfig;

  private readonly logger: Logger;

  constructor(config: AppConfig, logger: Logger) {
    this.config = config;
    this.logger = logger;
  }

  private async resolveSession(): Promise<PiSessionLike> {
    if (!this.sessionPromise) {
      this.sessionPromise = this.createSession();
    }
    return this.sessionPromise;
  }

  private async createSession(): Promise<PiSessionLike> {
    if (!this.config.piSdkModule) {
      throw new AppError('SESSION_BACKEND=pi-sdk 时必须配置 PI_SDK_MODULE', {
        category: 'validation',
        status: 400,
        retryable: false,
      });
    }
    this.logger.info('加载 Pi SDK', { module: this.config.piSdkModule });
    try {
      const mod = (await import(this.config.piSdkModule)) as
        | PiSdkSessionFactory
        | { default: PiSdkSessionFactory }
        | PiSessionLike;
      const factory = (mod as { default?: PiSdkSessionFactory }).default ?? (mod as PiSdkSessionFactory);
      if (typeof factory?.createSession !== 'function') {
        throw new AppError(`${this.config.piSdkModule} 未提供 createSession 工厂方法`, {
          category: 'validation',
          status: 400,
          retryable: false,
        });
      }
      return await factory.createSession({
        model: this.config.llm.model,
        baseUrl: this.config.llm.baseUrl,
        apiKey: this.config.llm.apiKey,
      });
    } catch (error) {
      if (error instanceof AppError) throw error;
      throw new AppError(`Pi SDK 加载失败：${(error as Error).message}`, {
        category: 'unknown',
        retryable: false,
      });
    }
  }

  async chat(request: ChatRequest): Promise<ChatResponse> {
    const session = await this.resolveSession();
    const result = await session.chat({
      messages: request.messages,
      tools: request.tools,
      toolChoice: request.toolChoice,
      temperature: request.temperature,
      maxTokens: request.maxTokens,
      signal: request.signal,
    });
    return {
      message: {
        role: 'assistant',
        content: result.content ?? '',
        toolCalls: result.toolCalls,
      },
      raw: result.raw,
    };
  }
}

/** 会话工厂：按配置选择后端，Pi SDK 不可用时退化为直连 vLLM */
export function createBackend(config: AppConfig, logger: Logger): SessionBackend {
  if (config.sessionBackend === 'pi-sdk') return new PiSdkBackend(config, logger);
  return new OpenAiCompatBackend(config, logger);
}
