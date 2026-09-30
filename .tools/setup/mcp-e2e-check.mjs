/**
 * MCP 执行侧联调自检（2026-09-17）
 *
 * 目的：证明 bid-agent-service 的 loadMcpTools 能在装上 @modelcontextprotocol/sdk 后，
 *      真的把 stdio 型 MCP 服务的工具挂进本地工具表，并且能调通。
 *
 * 用法（父进程）：node mcp-e2e-check.mjs
 * 内部会以 `node mcp-e2e-check.mjs --serve` 自举一个最小 MCP 服务作为被测服务端。
 */
import { existsSync } from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';

const AGENT_DIR = process.env.BID_AGENT_DIR ?? 'D:/招投标项目/bid-agent-service';
const SDK = 'D:/招投标项目/bid-agent-service/node_modules/@modelcontextprotocol/sdk';
const NODE_BIN = process.env.BID_AGENT_NODE ?? 'D:/招投标项目/.tools/node/node.exe';

if (process.argv.includes('--serve')) {
  // ---------- 被测 MCP 服务端（stdio） ----------
  const { McpServer } = await import(
    pathToFileURL(`${SDK}/dist/esm/server/mcp.js`).href
  );
  const { StdioServerTransport } = await import(
    pathToFileURL(`${SDK}/dist/esm/server/stdio.js`).href
  );
  const { z } = await import(
    pathToFileURL(`${AGENT_DIR}/node_modules/zod/index.js`).href
  );

  const server = new McpServer({ name: 'e2e-echo', version: '1.0.0' });
  server.registerTool(
    'echo_text',
    {
      description: '回显输入文本（联调用）',
      inputSchema: { text: z.string() },
    },
    async ({ text }) => ({ content: [{ type: 'text', text: `echo:${text}` }] }),
  );
  await server.connect(new StdioServerTransport());
} else {
  // ---------- 父进程：走执行侧的真实接入代码 ----------
  const distMcpTools = `${AGENT_DIR}/dist/tools/mcpTools.js`;
  if (!existsSync(distMcpTools)) {
    console.error(`[FAIL] 未找到已编译的 mcpTools.js：${distMcpTools}（先执行 npm run build）`);
    process.exit(1);
  }

  const { loadMcpTools } = await import(pathToFileURL(distMcpTools).href);

  const logs = [];
  const logger = {
    info: (msg, extra) => logs.push({ level: 'info', msg, extra }),
    warn: (msg, extra) => logs.push({ level: 'warn', msg, extra }),
    error: (msg, extra) => logs.push({ level: 'error', msg, extra }),
    debug: () => {},
  };

  const tools = await loadMcpTools(
    [
      {
        name: 'e2e-echo',
        transport: 'stdio',
        command: NODE_BIN,
        // 契约口径：args / env / headers 都是 JSON 字符串（与 bidding_bid_agent_mcp_server 列一致）
        args: JSON.stringify([fileURLToPath(import.meta.url), '--serve']),
        toolNames: ['echo_text'],
      },
    ],
    logger,
  );

  const expected = 'mcp__e2e-echo__echo_text';
  const tool = tools.find((item) => item.name === expected);
  console.log('加载到的 MCP 工具：', tools.map((item) => item.name));
  console.log('接入日志：', JSON.stringify(logs));

  if (!tool) {
    console.error(`[FAIL] 未挂载期望工具 ${expected}`);
    process.exit(1);
  }
  const result = await tool.execute({ text: 'hello-mcp' });
  const text = result?.content?.content?.[0]?.text;
  console.log('调用结果：', JSON.stringify(result));
  if (text !== 'echo:hello-mcp') {
    console.error(`[FAIL] 工具调用结果不符合预期：${text}`);
    process.exit(1);
  }
  console.log('[PASS] MCP 工具挂载 + 调用链路通');
  process.exit(0);
}
