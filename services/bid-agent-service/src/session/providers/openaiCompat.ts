import type { AppConfig } from '../../config.js';
import { AppError, httpStatusToAppError } from '../../errors.js';
import type { Logger } from '../../logger.js';
import { withRetry } from '../../lib/retry.js';
import type { ChatMessage, ChatRequest, ChatResponse, SessionBackend, ToolCall } from '../types.js';

/**
 * OpenAI 兼容后端：直连私有化 vLLM（架构文档 §6 的 Qwen2.5 容器）。
 * 之所以自建而不引第三方 SDK：编排层要控制超时、中断信号与错误归一化，SDK 反而挡路。
 */

interface OpenAiMessage {
  role: 'system' | 'user' | 'assistant' | 'tool';
  content: string | null;
  name?: string;
  tool_call_id?: string;
  tool_calls?: ToolCall[];
}

interface OpenAiChoice {
  finish_reason?: string;
  message: OpenAiMessage & Record<string, unknown>;
}

interface OpenAiChatResponse {
  choices?: OpenAiChoice[];
  usage?: { prompt_tokens?: number; completion_tokens?: number; total_tokens?: number };
}

function toOpenAiMessage(message: ChatMessage): OpenAiMessage {
  if (message.role === 'tool') {
    return { role: 'tool', content: message.content ?? '', tool_call_id: message.toolCallId ?? '' };
  }
  if (message.role === 'assistant' && message.toolCalls?.length) {
    return {
      role: 'assistant',
      content: message.content ?? '',
      tool_calls: message.toolCalls,
    };
  }
  return { role: message.role, content: message.content ?? '' };
}

export class OpenAiCompatBackend implements SessionBackend {
  readonly name = 'openai-compat';

  private readonly config: AppConfig;

  private readonly logger: Logger;

  constructor(config: AppConfig, logger: Logger) {
    this.config = config;
    this.logger = logger;
  }

  async chat(request: ChatRequest): Promise<ChatResponse> {
    const { llm } = this.config;
    const url = `${llm.baseUrl.replace(/\/+$/, '')}/chat/completions`;

    return withRetry(
      async () => {
        const headers: Record<string, string> = {
          'Content-Type': 'application/json',
          ...(llm.apiKey ? { Authorization: `Bearer ${llm.apiKey}` } : {}),
        };
        const body: Record<string, unknown> = {
          model: llm.model,
          messages: request.messages.map(toOpenAiMessage),
          temperature: request.temperature ?? llm.temperature,
          max_tokens: request.maxTokens ?? llm.maxTokens,
          stream: false,
        };
        if (request.tools?.length) {
          body.tools = request.tools;
          body.tool_choice = request.toolChoice ?? 'auto';
        }

        let response: Response;
        try {
          response = await fetch(url, {
            method: 'POST',
            headers,
            body: JSON.stringify(body),
            signal: AbortSignal.any([
              AbortSignal.timeout(llm.timeoutMs),
              ...(request.signal ? [request.signal] : []),
            ]),
          });
        } catch (error) {
          const err = error instanceof Error ? error : new Error(String(error));
          if (request.signal?.aborted) {
            throw new AppError('任务已被中断', { category: 'abort', retryable: false });
          }
          if (err.name === 'TimeoutError' || err.name === 'AbortError') {
            throw new AppError(`LLM 调用超时：${llm.model}`, { category: 'timeout', retryable: true });
          }
          throw new AppError(`LLM 调用失败：${err.message}`, { category: 'network', retryable: true });
        }

        const text = await response.text();
        if (!response.ok) throw httpStatusToAppError(response.status, text);
        let parsed: OpenAiChatResponse;
        try {
          parsed = JSON.parse(text) as OpenAiChatResponse;
        } catch {
          throw new AppError(`LLM 返回非 JSON：${text.slice(0, 200)}`, { category: 'server', retryable: false });
        }

        const choice = parsed.choices?.[0];
        if (!choice?.message) {
          throw new AppError('LLM 未返回 choices[0].message', { category: 'server', retryable: false });
        }
        this.logger.debug('LLM 返回', {
          model: llm.model,
          finishReason: choice.finish_reason,
          toolCalls: choice.message.tool_calls?.length ?? 0,
          promptTokens: parsed.usage?.prompt_tokens,
          completionTokens: parsed.usage?.completion_tokens,
        });

        const toolCalls = Array.isArray(choice.message.tool_calls) ? choice.message.tool_calls : undefined;
        return {
          message: {
            role: 'assistant',
            content: typeof choice.message.content === 'string' ? choice.message.content : '',
            toolCalls,
          },
          finishReason: choice.finish_reason,
          usage: {
            promptTokens: parsed.usage?.prompt_tokens,
            completionTokens: parsed.usage?.completion_tokens,
            totalTokens: parsed.usage?.total_tokens,
          },
          raw: parsed,
        };
      },
      {
        retries: llm.maxRetries,
        signal: request.signal,
        baseDelayMs: 1000,
        onRetry: (error, attempt, delayMs) => {
          this.logger.warn('LLM 调用重试', { attempt, delayMs, reason: error.message, category: error.category });
        },
      },
    );
  }
}
