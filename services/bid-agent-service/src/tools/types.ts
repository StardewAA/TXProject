import type { JavaApiClient } from '../client/javaApi.js';
import type { JsonSchema } from '../lib/jsonSchema.js';
import type { Logger } from '../logger.js';
import type { PendingQuestion } from '../orchestrator/hil.js';
import type { TaskRecord } from '../orchestrator/types.js';
import type { ProviderToolSpec } from '../session/types.js';
import type { WorkspaceManager } from '../workspace/manager.js';

export interface ToolResult {
  ok: boolean;
  content: unknown;
  summary?: string;
}

export interface ToolContext {
  record: TaskRecord;
  logger: Logger;
  java: JavaApiClient;
  workspace: WorkspaceManager;
  signal: AbortSignal;
  /** 阻塞等待业务侧 /confirm 回调 */
  askUser(input: {
    question: string;
    options?: string[];
    required?: boolean;
    context?: string;
  }): Promise<{ answer: string; question: PendingQuestion }>;
  /** 记录失败原因，编排层在回合结束后转人工 */
  noteFailure(reason: string): void;
  /** 本轮工具执行过程中累积的失败原因 */
  failures: string[];
}

export interface Tool<P = Record<string, unknown>> {
  name: string;
  description: string;
  parameters: JsonSchema;
  execute(args: P, ctx: ToolContext): Promise<ToolResult>;
}

export function toProviderSpec<P>(tool: Tool<P>): ProviderToolSpec {
  return {
    type: 'function',
    function: {
      name: tool.name,
      description: tool.description,
      parameters: tool.parameters,
    },
  };
}

export function ok(content: unknown, summary?: string): ToolResult {
  return { ok: true, content, summary };
}

export function fail(content: unknown, summary?: string): ToolResult {
  return { ok: false, content, summary };
}
