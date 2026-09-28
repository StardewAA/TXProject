/**
 * 私有化模型服务 Mock：提供 OpenAI 兼容的 /v1/chat/completions。
 * 用途：在没有 vLLM 的环境里验证编排闭环（工具调用 + 结构化输出 + HIL）。
 *
 * 行为：
 * 1. 从最后一条 user 消息中解析出 ```json <schema> ``` 片段；
 * 2. 首轮按 MOCK_LLM_TOOL 指定的工具发起一次工具调用（用于验证工具回环）；
 * 3. 之后返回符合 schema 的样例 JSON（用于验证结构化输出校验链路）。
 */
import http from 'node:http';

const PORT = Number(process.env.MOCK_LLM_PORT ?? 8100);
const SIMULATE_TOOL = process.env.MOCK_LLM_TOOL ?? 'json-validation';

function extractSchema(messages) {
  for (let i = messages.length - 1; i >= 0; i -= 1) {
    const message = messages[i];
    if (!message || message.role !== 'user') continue;
    const text = String(message.content ?? '');
    const fence = text.match(/```json\s*([\s\S]*?)```/);
    if (!fence) continue;
    try {
      return JSON.parse(fence[1]);
    } catch {
      // 继续向前找
    }
  }
  return null;
}

function textFor(name) {
  const key = String(name).toLowerCase();
  if (key.includes('sourceblockid') || key.includes('blockid')) return 'B-1001';
  if (key.includes('content')) {
    return '本章依据招标文件要求编制，覆盖施工部署、进度安排、质量与安全保证措施等内容，工序衔接与资源配置按方案执行。';
  }
  if (key.includes('claim')) return '本项目按招标文件要求组织施工并通过验收';
  if (key.includes('name')) return '投标人名称字段';
  return `示例值-${name}`;
}

function clampNumber(value, schema) {
  let result = value;
  if (typeof schema.minimum === 'number') result = Math.max(result, schema.minimum);
  if (typeof schema.maximum === 'number') result = Math.min(result, schema.maximum);
  if (typeof schema.minimum !== 'number' && typeof schema.maximum !== 'number') return value;
  return result;
}

function sample(schema, name = 'value') {
  if (!schema || typeof schema !== 'object') return '示例值';
  if (Array.isArray(schema.enum) && schema.enum.length > 0) return schema.enum[0];
  if (Object.prototype.hasOwnProperty.call(schema, 'const')) return schema.const;
  const types = Array.isArray(schema.type) ? schema.type : [schema.type ?? 'string'];
  const type = types.find((candidate) => candidate !== 'null') ?? 'null';
  switch (type) {
    case 'object': {
      const out = {};
      for (const [key, sub] of Object.entries(schema.properties ?? {})) out[key] = sample(sub, key);
      return out;
    }
    case 'array':
      return schema.items ? [sample(schema.items, name)] : [];
    case 'integer':
      return Math.round(clampNumber(7, schema));
    case 'number':
      return clampNumber(7.5, schema);
    case 'boolean':
      return true;
    case 'null':
      return null;
    default:
      return textFor(name);
  }
}

function readBody(req) {
  return new Promise((resolve) => {
    const chunks = [];
    req.on('data', (chunk) => chunks.push(chunk));
    req.on('end', () => {
      if (chunks.length === 0) return resolve({});
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString('utf8')));
      } catch {
        resolve({});
      }
    });
  });
}

function json(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(payload),
  });
  res.end(payload);
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url ?? '/', `http://${req.headers.host ?? 'localhost'}`);
  const pathname = url.pathname.replace(/\/+$/, '') || '/';

  if (pathname === '/v1/models') {
    return json(res, 200, { object: 'list', data: [{ id: 'mock-model', object: 'model' }] });
  }

  if (pathname !== '/v1/chat/completions' || req.method !== 'POST') {
    return json(res, 404, { error: 'not found' });
  }

  const body = await readBody(req);
  const messages = Array.isArray(body.messages) ? body.messages : [];
  const schema = extractSchema(messages);
  const hasToolResult = messages.some((message) => message.role === 'tool');
  const toolsDeclared = Array.isArray(body.tools) && body.tools.length > 0;

  let message;
  if (toolsDeclared && SIMULATE_TOOL !== 'none' && !hasToolResult && schema) {
    const target = body.tools.find((tool) => tool.function?.name === SIMULATE_TOOL);
    if (target) {
      const value = sample(schema);
      message = {
        role: 'assistant',
        content: null,
        tool_calls: [
          {
            id: 'call_mock_1',
            type: 'function',
            function: {
              name: SIMULATE_TOOL,
              arguments: JSON.stringify(
                SIMULATE_TOOL === 'json-validation'
                  ? { jsonText: JSON.stringify(value), schema }
                  : { query: '招标文件 废标条款' },
              ),
            },
          },
        ],
      };
    }
  }

  if (!message) {
    const value = schema ? sample(schema) : { reply: '（mock）未检测到输出结构要求' };
    message = { role: 'assistant', content: JSON.stringify(value) };
  }

  console.log(
    `[mock-llm] model=${body.model ?? '-'} messages=${messages.length} -> ${message.tool_calls ? 'tool_calls' : 'final'}`,
  );

  return json(res, 200, {
    id: 'chatcmpl-mock',
    object: 'chat.completions',
    created: Math.floor(Date.now() / 1000),
    model: body.model ?? 'mock-model',
    choices: [{ index: 0, message, finish_reason: message.tool_calls ? 'tool_calls' : 'stop' }],
    usage: { prompt_tokens: 100, completion_tokens: 50, total_tokens: 150 },
  });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`[mock-llm] OpenAI 兼容 Mock 已启动: http://0.0.0.0:${PORT}/v1`);
});
