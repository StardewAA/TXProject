import type { ChatMessage, ProviderToolSpec } from '../session/types.js';
import type { Tool, ToolContext, ToolResult } from './types.js';
import { bidRetrieveTool } from './bidRetrieve.js';
import { jsonValidationTool } from './jsonValidation.js';
import { taskFailureTool } from './taskFailure.js';
import { askUserTool } from './userQuestion.js';
import { readFileTool, listFilesTool, writeFileTool } from './workspaceFiles.js';

const ALL_TOOLS: ReadonlyArray<Tool<never> | Tool<Record<string, never>> | Tool<any>> = [
  bidRetrieveTool,
  jsonValidationTool,
  askUserTool,
  taskFailureTool,
  readFileTool,
  writeFileTool,
  listFilesTool,
];

export class ToolRegistry {
  private readonly tools = new Map<string, Tool<any>>();

  /**
   * @param tools      工具集合（默认全部内置工具）
   * @param allowNames 白名单（来自 Java 智能体管理：tool_whitelist / 技能建议工具）；
   *                   为空或未传表示不限制。MCP 工具名形如 mcp__{server}__{tool}，一并按此过滤。
   */
  constructor(tools: Iterable<Tool<any>> = ALL_TOOLS, allowNames?: readonly string[] | null) {
    const allow = allowNames && allowNames.length > 0 ? new Set(allowNames) : null;
    for (const tool of tools) {
      if (allow && !allow.has(tool.name)) continue;
      this.tools.set(tool.name, tool);
    }
  }

  /** 追加外部工具（MCP 等），仍受白名单约束 */
  register(tool: Tool<any>, allowNames?: readonly string[] | null): void {
    const allow = allowNames && allowNames.length > 0 ? new Set(allowNames) : null;
    if (allow && !allow.has(tool.name)) return;
    this.tools.set(tool.name, tool);
  }

  get(name: string): Tool<any> | undefined {
    return this.tools.get(name);
  }

  has(name: string): boolean {
    return this.tools.has(name);
  }

  names(): string[] {
    return [...this.tools.keys()];
  }

  specs(names?: string[]): ProviderToolSpec[] {
    const list = names ?? this.names();
    return list
      .map((name) => this.tools.get(name))
      .filter((tool): tool is Tool<any> => Boolean(tool))
      .map((tool) => ({
        type: 'function' as const,
        function: {
          name: tool.name,
          description: tool.description,
          parameters: tool.parameters,
        },
      }));
  }

  async execute(name: string, args: unknown, ctx: ToolContext): Promise<ToolResult> {
    const tool = this.tools.get(name);
    if (!tool) {
      return {
        ok: false,
        content: { error: `未知工具：${name}`, available: this.names() },
        summary: '未知工具',
      };
    }
    return tool.execute((args ?? {}) as never, ctx);
  }

  static toolMessage(toolCallId: string, name: string, result: ToolResult): ChatMessage {
    return {
      role: 'tool',
      toolCallId,
      name,
      content: JSON.stringify(result.content ?? null),
    };
  }
}

export { ALL_TOOLS };
export type { Tool, ToolContext, ToolResult };
