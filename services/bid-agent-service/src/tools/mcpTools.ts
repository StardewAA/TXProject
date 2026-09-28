import type { Logger } from '../logger.js';
import type { McpServerRuntime } from '../orchestrator/types.js';
import type { Tool } from './types.js';

/**
 * MCP 工具接入（执行侧）：把智能体管理登记的 MCP 服务工具，转成本地 function calling 工具。
 *
 * 设计要点：
 * 1. 官方 SDK 用**动态 import**：未安装 `@modelcontextprotocol/sdk` 时只告警并跳过，不影响内置工具与整体流程；
 * 2. 工具命名 `mcp__{server}__{tool}`，与 Java 管理页展示一致，并统一受 toolWhitelist 约束；
 * 3. 传输支持 streamable-http / sse / stdio（stdio 由 SDK 起子进程，env 来自管理页配置）。
 */

const MODULE_CLIENT = '@modelcontextprotocol/sdk/client/index.js';
const MODULE_HTTP = '@modelcontextprotocol/sdk/client/streamableHttp.js';
const MODULE_SSE = '@modelcontextprotocol/sdk/client/sse.js';
const MODULE_STDIO = '@modelcontextprotocol/sdk/client/stdio.js';

async function optionalImport(specifier: string): Promise<any | null> {
  try {
    // 变量说明符：SDK 未安装时 TS 不会因缺包报错，运行时再降级
    return await import(specifier);
  } catch {
    return null;
  }
}

export async function loadMcpTools(
  servers: McpServerRuntime[] | null | undefined,
  logger: Logger,
): Promise<Array<Tool<any>>> {
  if (!servers || servers.length === 0) return [];

  const clientMod = await optionalImport(MODULE_CLIENT);
  if (!clientMod?.Client) {
    logger.warn('未安装 @modelcontextprotocol/sdk，已跳过 MCP 工具（npm i @modelcontextprotocol/sdk 后重启生效）', {
      servers: servers.map((server) => server.name),
    });
    return [];
  }

  const tools: Array<Tool<any>> = [];
  for (const server of servers) {
    try {
      const transport = await createTransport(server);
      if (!transport) {
        logger.warn('MCP 传输配置不完整，已跳过', { server: server.name, transport: server.transport });
        continue;
      }
      const client = new clientMod.Client({ name: 'txgy-bidding', version: '1.0.0' }, { capabilities: {} });
      await client.connect(transport);
      const listed = await client.listTools();
      const allow = server.toolNames && server.toolNames.length > 0 ? new Set(server.toolNames) : null;
      for (const item of listed?.tools ?? []) {
        if (allow && !allow.has(item.name)) continue;
        const toolName = `mcp__${server.name}__${item.name}`;
        tools.push({
          name: toolName,
          description: item.description ?? `MCP 工具 ${server.name}.${item.name}`,
          parameters: item.inputSchema ?? { type: 'object', properties: {} },
          async execute(args: Record<string, unknown>) {
            const result = await client.callTool({ name: item.name, arguments: args ?? {} });
            return { ok: true, content: result };
          },
        });
      }
      logger.info('MCP 工具已加载', { server: server.name, count: listed?.tools?.length ?? 0 });
    } catch (error) {
      // 单个 MCP 服务不可用不影响整条生成链路
      logger.warn('MCP 服务接入失败，已跳过', { server: server.name, error: (error as Error).message });
    }
  }
  return tools;
}

async function createTransport(server: McpServerRuntime): Promise<any | null> {
  const transport = String(server.transport ?? '').toLowerCase();
  const headers = parseJsonObject(server.headers);
  if (transport === 'stdio') {
    const mod = await optionalImport(MODULE_STDIO);
    if (!mod?.StdioClientTransport || !server.command) return null;
    return new mod.StdioClientTransport({
      command: server.command,
      args: parseJsonArray(server.args),
      env: { ...process.env, ...parseJsonObject(server.env) } as Record<string, string>,
    });
  }
  if (!server.url) return null;
  if (transport === 'sse') {
    const mod = await optionalImport(MODULE_SSE);
    if (!mod?.SSEClientTransport) return null;
    return new mod.SSEClientTransport(new URL(server.url), { requestInit: { headers } });
  }
  const mod = await optionalImport(MODULE_HTTP);
  if (!mod?.StreamableHTTPClientTransport) return null;
  return new mod.StreamableHTTPClientTransport(new URL(server.url), { requestInit: { headers } });
}

function parseJsonObject(value?: string | null): Record<string, string> {
  if (!value) return {};
  try {
    const parsed = JSON.parse(value);
    const result: Record<string, string> = {};
    for (const [key, item] of Object.entries(parsed ?? {})) {
      result[key] = String(item ?? '');
    }
    return result;
  } catch {
    return {};
  }
}

function parseJsonArray(value?: string | null): string[] {
  if (!value) return [];
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed.map((item) => String(item)) : [];
  } catch {
    return [];
  }
}
