import type { JsonSchema } from '../lib/jsonSchema.js';

/**
 * 会话后端抽象：隔离具体 SDK（Pi SDK / LangGraph / 直连 vLLM）。
 * 编排层只依赖这里定义的消息格式，Pi SDK 版本变动或替换方案时改动收敛在 provider 内。
 */

export type Role = 'system' | 'user' | 'assistant' | 'tool';

export interface ToolCall {
  id: string;
  type: 'function';
  function: {
    name: string;
    arguments: string;
  };
}

export interface ChatMessage {
  role: Role;
  content: string | null;
  name?: string;
  toolCallId?: string;
  toolCalls?: ToolCall[];
}

export interface ProviderToolSpec {
  type: 'function';
  function: {
    name: string;
    description: string;
    parameters: JsonSchema;
    strict?: boolean;
  };
}

export type ToolChoice = 'auto' | 'none' | 'required' | { type: 'function'; function: { name: string } };

export interface ChatRequest {
  messages: ChatMessage[];
  tools?: ProviderToolSpec[];
  toolChoice?: ToolChoice;
  temperature?: number;
  maxTokens?: number;
  signal?: AbortSignal;
  /** 透传给底层 SDK 的扩展参数 */
  extra?: Record<string, unknown>;
}

export interface Usage {
  promptTokens?: number;
  completionTokens?: number;
  totalTokens?: number;
}

export interface ChatResponse {
  message: ChatMessage;
  finishReason?: string;
  usage?: Usage;
  raw?: unknown;
}

export interface SessionBackend {
  readonly name: string;
  chat(request: ChatRequest): Promise<ChatResponse>;
}

export function textMessage(role: Role, content: string): ChatMessage {
  return { role, content };
}
