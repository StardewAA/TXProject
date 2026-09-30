import { spawn } from 'node:child_process';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import type { AppConfig } from '../config.js';
import { AppError, normalizeError } from '../errors.js';
import { randomId } from '../lib/fsx.js';
import { validateAgainstSchema, formatIssues, type JsonSchema } from '../lib/jsonSchema.js';
import { Metrics } from '../lib/metrics.js';
import { runWithTraceId } from '../lib/trace.js';
import type { Logger } from '../logger.js';
import { ToolRegistry } from '../tools/index.js';
import { KbService } from '../kb/kbService.js';
import { JavaApiClient } from '../client/javaApi.js';
import { BidDocxExporter } from '../export/bidDocxExporter.js';
import { TaskService } from '../orchestrator/taskService.js';
import { TASK_TYPES, type TaskType } from '../orchestrator/types.js';
import {
  assertTenant,
  authenticate,
  corsHeaders,
  isPublicPath,
  tenantScope,
  type CallerContext,
} from './auth.js';

const MAX_BODY_BYTES = 4 * 1024 * 1024;

const createTaskSchema: JsonSchema = {
  type: 'object',
  properties: {
    projectId: { type: 'string' },
    tenantId: { type: 'string' },
    taskType: { type: 'string', enum: [...TASK_TYPES] },
    payload: { type: 'object' },
  },
  required: ['projectId', 'taskType'],
  additionalProperties: false,
};

const answersSchema: JsonSchema = {
  type: 'object',
  properties: {
    answers: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          questionId: { type: 'string' },
          value: { type: 'string' },
        },
        required: ['questionId', 'value'],
      },
    },
  },
  required: ['answers'],
  additionalProperties: false,
};

const rollbackSchema: JsonSchema = {
  type: 'object',
  properties: {
    toPhase: { type: 'string' },
    keepSession: { type: 'boolean' },
    nodeId: { type: 'string', description: '章节类任务（draft）按节点回退，实现单章重跑' },
  },
  required: ['toPhase'],
  additionalProperties: false,
};

function sendJson(res: http.ServerResponse, status: number, body: unknown): void {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(payload),
  });
  res.end(payload);
}

async function readJsonBody(req: http.IncomingMessage): Promise<unknown> {
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of req) {
    const buffer = chunk as Buffer;
    size += buffer.length;
    if (size > MAX_BODY_BYTES) {
      throw new AppError('请求体过大', { category: 'validation', status: 413 });
    }
    chunks.push(buffer);
  }
  if (chunks.length === 0) return {};
  const text = Buffer.concat(chunks).toString('utf8');
  try {
    return JSON.parse(text);
  } catch (error) {
    throw new AppError(`请求体不是合法 JSON：${(error as Error).message}`, {
      category: 'validation',
      status: 400,
    });
  }
}

async function readBinaryBody(req: http.IncomingMessage, maxBytes: number): Promise<Buffer> {
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of req) {
    const buffer = chunk as Buffer;
    size += buffer.length;
    if (size > maxBytes) {
      throw new AppError('请求体过大', { category: 'validation', status: 413 });
    }
    chunks.push(buffer);
  }
  return Buffer.concat(chunks);
}

function validate(schema: JsonSchema, value: unknown): Record<string, unknown> {
  const issues = validateAgainstSchema(schema, value);
  if (issues.length > 0) {
    throw new AppError(formatIssues(issues), { category: 'validation', status: 400 });
  }
  return value as Record<string, unknown>;
}

export function createHttpServer(deps: {
  config: AppConfig;
  logger: Logger;
  tasks: TaskService;
  metrics?: Metrics;
}): http.Server {
  const { config, logger, tasks } = deps;
  const metrics = deps.metrics ?? new Metrics();
  const registry = new ToolRegistry();
  const kb = new KbService(config, logger);
  // COPY_PRICING 通道：报价数据经契约接口拉取（拉取失败在导出层告警降级，不阻断交付）
  const java = new JavaApiClient(config, logger);
  const docxExporter = new BidDocxExporter(config, logger, (projectId, tenantId) =>
    java.getPricing(projectId, tenantId),
  );

  /**
   * 就绪探针（B10）：Java 可达性 + 工作区/会话目录可写。
   * Java 侧只要求"有 HTTP 响应"，不绑定具体端点，避免与 Java 版本耦合。
   */
  async function checkReadiness(): Promise<{
    ok: boolean;
    checks: Record<string, { ok: boolean; detail?: string }>;
  }> {
    const checks: Record<string, { ok: boolean; detail?: string }> = {};
    try {
      const res = await fetch(config.java.baseUrl, { signal: AbortSignal.timeout(2000) });
      checks.java = { ok: true, detail: `HTTP ${res.status}` };
    } catch (error) {
      checks.java = { ok: false, detail: (error as Error).message };
    }
    const dirs: Array<[string, string]> = [
      ['workspace', config.paths.workspace],
      ['sessions', config.paths.sessions],
    ];
    for (const [name, dir] of dirs) {
      try {
        fs.mkdirSync(dir, { recursive: true });
        fs.accessSync(dir, fs.constants.W_OK);
        checks[name] = { ok: true };
      } catch (error) {
        checks[name] = { ok: false, detail: (error as Error).message };
      }
    }
    return { ok: Object.values(checks).every((item) => item.ok), checks };
  }

  /**
   * 任务归属校验（B2）：不属于调用方可访问租户的任务统一按"不存在"返回，
   * 避免通过 403/404 差异探测其它租户的 taskId。
   */
  async function requireTask(taskId: string, caller: CallerContext) {
    const record = await tasks.get(taskId);
    if (!record) {
      throw new AppError(`任务不存在：${taskId}`, { category: 'not_found', status: 404 });
    }
    const scope = tenantScope(caller);
    if (scope && !scope.includes(String(record.tenantId))) {
      throw new AppError(`任务不存在：${taskId}`, { category: 'not_found', status: 404 });
    }
    return record;
  }

  const server = http.createServer((req, res) => {
    const method = req.method ?? 'GET';
    const url = new URL(req.url ?? '/', `http://${req.headers.host ?? 'localhost'}`);
    const pathname = url.pathname.replace(/\/+$/, '') || '/';
    // B10：请求级 traceId，随日志与出站 HTTP 头透传
    const traceId = String(req.headers['x-request-id'] ?? '').trim() || randomId('req');
    const reqLogger = logger.child({ traceId, method, path: pathname });
    const startedAt = Date.now();
    // B1：CORS 按白名单回显来源（不再无条件 Access-Control-Allow-Origin: *）
    for (const [key, value] of Object.entries(corsHeaders(req, config.auth.allowedOrigins))) {
      res.setHeader(key, value);
    }
    let responseStatus = 500;
    const respond = (status: number, body: unknown): void => {
      responseStatus = status;
      sendJson(res, status, body);
    };

    void runWithTraceId(traceId, async () => {
      try {
        if (method === 'OPTIONS') {
          responseStatus = 204;
          res.writeHead(204);
          res.end();
          return;
        }
        if (pathname === '/healthz' && method === 'GET') {
          respond(200, { ok: true, service: 'bid-agent-service', backend: config.sessionBackend });
          return;
        }
        // B10：就绪探针——检查 Java 可达性与工作区可写，不只看进程存活
        if (pathname === '/readyz' && method === 'GET') {
          const readiness = await checkReadiness();
          respond(readiness.ok ? 200 : 503, { ...readiness, traceId });
          return;
        }
        // B10：指标导出（Prometheus 文本格式）
        if (pathname === '/metrics' && method === 'GET') {
          responseStatus = 200;
          const body = metrics.render();
          res.writeHead(200, {
            'Content-Type': 'text/plain; version=0.0.4; charset=utf-8',
            'Content-Length': Buffer.byteLength(body),
          });
          res.end(body);
          return;
        }

        // B1：受保护路由统一鉴权（/healthz、/readyz、/metrics 与 Java 回拉文件除外）
        const caller: CallerContext = isPublicPath(pathname)
          ? { tenants: [] }
          : authenticate(req, config);

        if (pathname === '/agent/tools' && method === 'GET') {
          respond(200, { tools: registry.names(), taskTypes: TASK_TYPES });
          return;
        }

        // ---------- 知识库（RAG 入库：上传 → Java Tika 解析 → 切分向量化 → 可检索） ----------
        if (pathname === '/kb/documents' && method === 'POST') {
          const tenantId = String(req.headers['x-tenant-id'] ?? url.searchParams.get('tenantId') ?? '');
          const projectId = url.searchParams.get('projectId') ?? '';
          const docType = (url.searchParams.get('docType') ?? 'TENDER').toUpperCase();
          const rawName = req.headers['x-file-name']
            ? decodeURIComponent(String(req.headers['x-file-name']))
            : '';
          if (!tenantId || !projectId || !rawName) {
            throw new AppError('缺少 tenantId / projectId / x-file-name', {
              category: 'validation',
              status: 400,
            });
          }
          assertTenant(caller, tenantId);
          const data = await readBinaryBody(req, 60 * 1024 * 1024);
          const doc = kb.saveUpload({ tenantId, projectId, docType, fileName: rawName, data });
          void kb.indexAsync(doc);
          respond(202, { document: doc });
          return;
        }
        if (pathname === '/kb/documents' && method === 'GET') {
          const tenantId = url.searchParams.get('tenantId') ?? undefined;
          if (tenantId) assertTenant(caller, tenantId);
          // B2：调用方绑定租户时强制按可见租户过滤，未绑定租户时按请求参数过滤
          const scope = tenantScope(caller);
          const docs = kb.list({
            tenantId: scope ? undefined : tenantId,
            tenantIds: scope,
            projectId: url.searchParams.get('projectId') ?? undefined,
          });
          respond(200, { documents: docs });
          return;
        }
        if (pathname === '/kb/documents' && method === 'DELETE') {
          const tenantId = url.searchParams.get('tenantId') ?? '';
          const projectId = url.searchParams.get('projectId') ?? '';
          const name = url.searchParams.get('name') ?? '';
          if (!tenantId || !projectId || !name) {
            throw new AppError('缺少 tenantId / projectId / name', { category: 'validation', status: 400 });
          }
          assertTenant(caller, tenantId);
          const ok = await kb.remove(tenantId, projectId, name);
          respond(200, { ok });
          return;
        }
        const kbFileRoute = /^\/kb\/files\/([^/]+)$/.exec(pathname);
        if (kbFileRoute && method === 'GET') {
          // 该端点由 Java 侧 Tika 直接回拉，无法携带 Agent 鉴权信息；fileId 为不可枚举的 UUID
          //（auth.ts 已将 /kb/files/ 前缀加入免鉴权白名单，实现与注释保持一致）
          const hit = kb.fileStream(kbFileRoute[1]!);
          if (!hit) {
            throw new AppError('文件不存在', { category: 'not_found', status: 404 });
          }
          responseStatus = 200;
          res.writeHead(200, {
            'Content-Type': 'application/octet-stream',
            'Content-Length': String(hit.bytes),
          });
          const stream = hit.stream;
          stream.on('error', (error) => {
            logger.error('kb 文件流读取失败', { message: (error as Error).message });
            if (res.headersSent) {
              res.destroy();
            } else {
              res.writeHead(500, { 'Content-Type': 'application/json; charset=utf-8' });
              res.end(JSON.stringify({ code: 500, msg: '文件读取失败' }));
            }
          });
          stream.pipe(res);
          return;
        }

        // ---------- 标书 Word 交付：排版导出 / 下载 ----------
        if (pathname === '/bid-file/docx' && method === 'GET') {
          const tenantId = url.searchParams.get('tenantId') ?? '';
          const projectId = url.searchParams.get('projectId') ?? '';
          const taskId = url.searchParams.get('taskId') ?? undefined;
          if (!tenantId || !projectId || (taskId !== undefined && !/^[\w-]{1,64}$/.test(taskId))) {
            throw new AppError('缺少 tenantId/projectId 或 taskId 非法', { category: 'validation', status: 400 });
          }
          assertTenant(caller, tenantId);
          const result = await docxExporter.export(tenantId, projectId, taskId);
          const fileName = encodeURIComponent(path.basename(result.absPath));
          responseStatus = 200;
          res.writeHead(200, {
            'Content-Type': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
            'Content-Length': String(result.bytes),
            'Content-Disposition': `attachment; filename="bid.docx"; filename*=UTF-8''${fileName}`,
          });
          const stream = fs.createReadStream(result.absPath);
          stream.on('error', (error) => {
            logger.error('docx 文件流读取失败', { message: (error as Error).message });
            if (res.headersSent) {
              res.destroy();
            } else {
              res.writeHead(500, { 'Content-Type': 'application/json; charset=utf-8' });
              res.end(JSON.stringify({ code: 500, msg: 'docx 读取失败' }));
            }
          });
          stream.pipe(res);
          return;
        }
        if (pathname === '/bid-file/export' && method === 'GET') {
          // 导出 docx；PDF 转换需显式开启 PDF_VIA_WORD=true（B8：不再默认依赖本机 Word）
          const tenantId = url.searchParams.get('tenantId') ?? '';
          const projectId = url.searchParams.get('projectId') ?? '';
          const taskId = url.searchParams.get('taskId') ?? undefined;
          if (!tenantId || !projectId || (taskId !== undefined && !/^[\w-]{1,64}$/.test(taskId))) {
            throw new AppError('缺少 tenantId/projectId 或 taskId 非法', { category: 'validation', status: 400 });
          }
          assertTenant(caller, tenantId);
          const result = await docxExporter.export(tenantId, projectId, taskId);
          let pdfRel: string | null = null;
          if (config.docx.pdfViaWord) {
            const pdfAbs = result.absPath.replace(/\.docx$/i, '.pdf');
            const ps = `$ErrorActionPreference='Stop'\n$w=New-Object -ComObject Word.Application\n$w.Visible=$false\ntry{$d=$w.Documents.Open('${result.absPath.replace(/'/g, "''")}',$false,$true);$d.SaveAs2('${pdfAbs.replace(/'/g, "''")}',17);$d.Close($false)}finally{$w.Quit()}`;
            try {
              await new Promise<void>((resolve, reject) => {
                const child = spawn('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', ps], {
                  windowsHide: true,
                });
                const to = setTimeout(() => {
                  child.kill();
                  reject(new Error('PDF 转换超时'));
                }, 90_000);
                child.on('error', () => {
                  clearTimeout(to);
                  reject(new Error('无法调用 Word'));
                });
                child.on('close', (code) => {
                  clearTimeout(to);
                  if (code === 0 && fs.existsSync(pdfAbs)) resolve();
                  else reject(new Error('PDF 转换失败'));
                });
              });
              pdfRel = result.rel.replace(/\.docx$/i, '.pdf');
            } catch (pdfErr) {
              logger.warn('PDF 转换不可用（仅交付 docx）', { message: (pdfErr as Error).message });
            }
          }
          respond(200, {
            ok: true,
            docxRel: result.rel,
            pdfRel,
            chapters: result.chapters,
            bytes: result.bytes,
            deliveryMode: result.deliveryMode,
            note: '该文件由内置排版引擎生成，用于预览与内部流转；正式递交前请按招标格式章核对（目录需在 Word 中"更新域"）。',
          });
          return;
        }
        if (pathname === '/bid-file/download' && method === 'GET') {
          const tenantId = url.searchParams.get('tenantId') ?? '';
          const projectId = url.searchParams.get('projectId') ?? '';
          const taskId = url.searchParams.get('taskId') ?? undefined;
          const rel = (url.searchParams.get('rel') ?? '').replace(/^\/+/, '');
          if (!tenantId || !projectId || !rel || !/\.(docx|pdf|md|json|txt|csv)$/i.test(rel)
              || (taskId !== undefined && !/^[\w-]{1,64}$/.test(taskId))) {
            throw new AppError('缺少参数或文件类型不支持', { category: 'validation', status: 400 });
          }
          assertTenant(caller, tenantId);
          const wsRoot = taskId
            ? path.join(config.paths.workspace, tenantId, projectId, taskId)
            : path.join(config.paths.workspace, tenantId, projectId);
          const target = path.resolve(wsRoot, rel);
          if (!target.startsWith(wsRoot + path.sep) || !fs.existsSync(target) || !fs.statSync(target).isFile()) {
            throw new AppError('文件不存在', { category: 'not_found', status: 404 });
          }
          const name = encodeURIComponent(path.basename(target));
          const ctype = rel.endsWith('.docx')
            ? 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
            : rel.endsWith('.pdf')
              ? 'application/pdf'
              : 'text/plain; charset=utf-8';
          responseStatus = 200;
          res.writeHead(200, {
            'Content-Type': ctype,
            'Content-Length': String(fs.statSync(target).size),
            'Content-Disposition': `attachment; filename="f"; filename*=UTF-8''${name}`,
          });
          const stream = fs.createReadStream(target);
          stream.on('error', (error) => {
            logger.error('文件流读取失败', { message: (error as Error).message });
            if (res.headersSent) {
              res.destroy();
            } else {
              res.writeHead(500, { 'Content-Type': 'application/json; charset=utf-8' });
              res.end(JSON.stringify({ code: 500, msg: '文件读取失败' }));
            }
          });
          stream.pipe(res);
          return;
        }

        // ---------- §3.2 Java/Agent 对外接口 ----------
        if (pathname === '/agent/tasks' && method === 'GET') {
          // B2：列表强制按调用方可见租户过滤
          const view = await tasks.list({
            projectId: url.searchParams.get('projectId') ?? undefined,
            status: (url.searchParams.get('status') ?? undefined) as never,
            type: (url.searchParams.get('type') ?? undefined) as never,
            tenantIds: tenantScope(caller),
          });
          respond(200, { tasks: view });
          return;
        }

        if (pathname === '/agent/tasks' && method === 'POST') {
          const body = validate(createTaskSchema, await readJsonBody(req));
          const tenantId = String(body.tenantId ?? req.headers['x-tenant-id'] ?? '');
          if (!tenantId) {
            throw new AppError('缺少 tenantId（可放在请求头 X-Tenant-Id）', {
              category: 'validation',
              status: 400,
            });
          }
          assertTenant(caller, tenantId);
          const record = await tasks.create({
            tenantId,
            projectId: String(body.projectId),
            taskType: body.taskType as TaskType,
            payload: (body.payload as Record<string, unknown>) ?? {},
            idempotencyKey: req.headers['idempotency-key']
              ? String(req.headers['idempotency-key'])
              : undefined,
          });
          respond(201, { taskId: record.taskId });
          return;
        }

        // ---------- 产出文件只读端点（前端查看任务产物） ----------
        const outputRoute = /^\/agent\/tasks\/([^/]+)\/outputs$/.exec(pathname);
        if (outputRoute && method === 'GET') {
          const rec = await requireTask(decodeURIComponent(outputRoute[1]!), caller);
          const wsRoot = path.join(
            config.paths.workspace,
            String(rec.tenantId),
            String(rec.projectId),
          );
          const files: Array<{ rel: string; bytes: number; mtime: number }> = [];
          if (fs.existsSync(wsRoot)) {
            const stack = [wsRoot];
            while (stack.length > 0) {
              const dir = stack.pop()!;
              for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
                const full = path.join(dir, ent.name);
                if (ent.isDirectory()) {
                  stack.push(full);
                } else if (/\.(md|json|txt|csv|docx|pdf)$/i.test(ent.name)) {
                  const st = fs.statSync(full);
                  files.push({
                    rel: path.relative(wsRoot, full).split(path.sep).join('/'),
                    bytes: st.size,
                    mtime: st.mtimeMs,
                  });
                }
              }
            }
          }
          files.sort((a, b) => a.rel.localeCompare(b.rel));
          respond(200, { files, workspace: path.relative(config.paths.workspace, wsRoot) });
          return;
        }

        const fileRoute = /^\/agent\/tasks\/([^/]+)\/file$/.exec(pathname);
        if (fileRoute && method === 'GET') {
          const rec = await requireTask(decodeURIComponent(fileRoute[1]!), caller);
          const rel = (url.searchParams.get('rel') ?? '').replace(/^\/+/, '');
          if (!rel || !/\.(md|json|txt|csv)$/i.test(rel)) {
            throw new AppError('仅支持读取 md/json/txt/csv 文件', { category: 'validation', status: 400 });
          }
          const wsRoot = path.join(
            config.paths.workspace,
            String(rec.tenantId),
            String(rec.projectId),
          );
          const target = path.resolve(wsRoot, rel);
          if (!target.startsWith(wsRoot + path.sep)) {
            throw new AppError('路径越界', { category: 'validation', status: 400 });
          }
          if (!fs.existsSync(target) || !fs.statSync(target).isFile()) {
            throw new AppError(`文件不存在：${rel}`, { category: 'not_found', status: 404 });
          }
          const size = fs.statSync(target).size;
          if (size > 1024 * 1024) {
            throw new AppError('文件过大，暂不预览', { category: 'validation', status: 413 });
          }
          respond(200, { rel, content: fs.readFileSync(target, 'utf8') });
          return;
        }

        // ---------- SSE 任务事件流（对话式前端流式推进） ----------
        const eventsRoute = /^\/agent\/tasks\/([^/]+)\/events$/.exec(pathname);
        if (eventsRoute && method === 'GET') {
          const evTaskId = decodeURIComponent(eventsRoute[1]!);
          const evFirst = await requireTask(evTaskId, caller);
          responseStatus = 200;
          res.writeHead(200, {
            'Content-Type': 'text/event-stream; charset=utf-8',
            'Cache-Control': 'no-cache',
            Connection: 'keep-alive',
          });
          res.write('retry: 2000\n\n');
          let closed = false;
          let lastSig = '';
          let lastActivity = Date.now();
          const cleanupEv = () => {
            if (closed) return;
            closed = true;
            clearInterval(hbTimer);
            clearInterval(idleTimer);
            unsubscribe();
            try { res.end(); } catch { /* ignore */ }
          };
          const hbTimer = setInterval(() => {
            try { res.write(': hb\n\n'); } catch { cleanupEv(); }
          }, 25000);
          const pushView = (taskId: string): void => {
            if (closed) return;
            void tasks
              .get(taskId)
              .then((rec) => {
                if (closed || !rec) return;
                const view = tasks.toView(rec);
                lastActivity = Date.now();
                res.write(`event: task\ndata: ${JSON.stringify(view)}\n\n`);
                if (TaskService.isTerminal(rec.status)) {
                  // B6：终态显式告知客户端结束，前端可据此 close()，不再触发无谓重连
                  res.write('event: end\ndata: {"reason":"terminal"}\n\n');
                  cleanupEv();
                }
              })
              .catch(() => cleanupEv());
          };
          // B6：改为订阅任务状态变更事件（不再 800ms 轮询磁盘），仅在内容变化时推送
          const unsubscribe = tasks.onUpdate((record) => {
            if (record.taskId !== evTaskId || closed) return;
            const sig = JSON.stringify(tasks.toView(record));
            if (sig === lastSig) return;
            lastSig = sig;
            pushView(evTaskId);
          });
          // B6：长连接空闲上限，避免连接长期悬挂（客户端按 retry 重连）
          const idleTimer = setInterval(() => {
            if (config.sse.maxIdleMs > 0 && Date.now() - lastActivity > config.sse.maxIdleMs) {
              cleanupEv();
            }
          }, 30_000);
          req.on('close', cleanupEv);
          req.on('error', cleanupEv);
          // 首帧立即回放当前状态（客户端可能在任务已开始后才接入）
          void tasks.get(evTaskId).then((rec) => {
            if (closed || !rec) return;
            const view = tasks.toView(rec);
            lastSig = JSON.stringify(view);
            res.write(`event: task\ndata: ${lastSig}\n\n`);
            if (TaskService.isTerminal(rec.status)) {
              res.write('event: end\ndata: {"reason":"terminal"}\n\n');
              cleanupEv();
            }
          }).catch(() => cleanupEv());
          return;
        }

        const taskRoute = /^\/agent\/tasks\/([^/]+)(?:\/(confirm|interrupt|rollback))?$/.exec(pathname);
        if (taskRoute) {
          const taskId = decodeURIComponent(taskRoute[1]!);
          const action = taskRoute[2];
          // B2：所有任务级操作先做归属校验
          await requireTask(taskId, caller);

          if (!action && method === 'GET') {
            const record = await tasks.get(taskId);
            if (!record) {
              throw new AppError(`任务不存在：${taskId}`, { category: 'not_found', status: 404 });
            }
            respond(200, tasks.toView(record));
            return;
          }

          if (action === 'confirm' && method === 'POST') {
            const body = validate(answersSchema, await readJsonBody(req));
            const record = await tasks.confirm(
              taskId,
              (body.answers as Array<{ questionId: string; value: string }>) ?? [],
            );
            respond(200, { taskId: record.taskId, status: record.status });
            return;
          }

          if (action === 'interrupt' && method === 'POST') {
            const record = await tasks.interrupt(taskId);
            respond(200, { taskId: record.taskId, status: record.status });
            return;
          }

          if (action === 'rollback' && method === 'POST') {
            const body = validate(rollbackSchema, await readJsonBody(req));
            const record = await tasks.rollback(
              taskId,
              String(body.toPhase),
              body.keepSession !== false,
              body.nodeId === undefined ? undefined : String(body.nodeId),
            );
            respond(200, { taskId: record.taskId, status: record.status, phase: record.phase });
            return;
          }
        }

        respond(404, { error: 'Not Found', code: 'not_found', path: pathname });
      } catch (error) {
        const normalized = normalizeError(error);
        const status = normalized.status >= 400 ? normalized.status : 500;
        if (status >= 500) {
          reqLogger.error('接口异常', { message: normalized.message, action: normalized.action });
        } else {
          reqLogger.warn('请求被拒绝', { message: normalized.message, action: normalized.action });
        }
        respond(status, {
          error: normalized.message,
          code: normalized.category,
          status,
          retryable: normalized.retryable,
          action: normalized.action,
          details: normalized.details ?? null,
          traceId,
        });
      } finally {
        // B10：请求级指标与耗时日志（traceId 已在 child logger 中）
        metrics.increment(
          'http_requests_total',
          { method, status: String(responseStatus) },
          1,
          'HTTP 请求数',
        );
        reqLogger.debug('请求完成', {
          status: responseStatus,
          ms: Date.now() - startedAt,
        });
      }
    });
  });

  return server;
}
