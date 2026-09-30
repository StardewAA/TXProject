import crypto from 'node:crypto';
import fs from 'node:fs';
import http from 'node:http';
import https from 'node:https';
import path from 'node:path';
import type { AppConfig } from '../config.js';
import { AppError } from '../errors.js';
import type { Logger } from '../logger.js';

/**
 * 知识库（RAG 入库）服务：
 * 前端上传原始文档 → 本服务落盘并暴露下载 URL → 调 Java /bid/segment/index(url)
 * （Java Tika 解析 + 切分 + 向量化入 Qdrant/片段表）→ 文档状态落盘供轮询。
 * Java 管理员登录由内部完成，Agent 侧无需持有 token。
 */

export type KbStatus = 'uploaded' | 'indexing' | 'done' | 'failed';

export interface KbDocument {
  fileId: string;
  fileName: string;
  docType: string;
  tenantId: string;
  projectId: string;
  bytes: number;
  ext: string;
  /** 相对上传根目录的路径（B5）：用于把 fileId 直接映射到磁盘文件，避免全盘遍历 */
  relPath?: string;
  status: KbStatus;
  chunks?: number;
  error?: string;
  createdAt: string;
  updatedAt: string;
}

interface MetaFile {
  documents: KbDocument[];
}

const MAX_UPLOAD_BYTES = 60 * 1024 * 1024;
const INDEX_TIMEOUT_MS = 20 * 60 * 1000;
/** 入库管理员 token 的缓存时长（B3） */
const TOKEN_TTL_MS = 20 * 60 * 1000;
const ALLOWED_EXT = new Set(['.docx', '.doc', '.pdf', '.txt', '.md', '.xlsx', '.pptx']);

/** 租户/项目目录名收敛，避免路径穿越（B4 相邻问题） */
function safeSegment(value: string): string {
  const cleaned = value.replace(/[^\p{L}\p{N}._-]/gu, '_').slice(0, 80);
  return cleaned.length > 0 ? cleaned : 'default';
}

/**
 * 用 node:http 发起请求，而不是 fetch。
 * 原因：Node 内置 fetch（undici）有 5 分钟的响应头超时硬上限，且无法通过
 * AbortSignal 覆盖。真实入库一份 8MB 投标文件时 Java 侧耗时 309s（>300s），
 * 会被 undici 中途掐断并在 Agent 侧记成 "fetch failed"，而 Java 其实已经入库成功。
 * 这里改为显式总超时（由调用方决定，入库可用 20 分钟），并禁用 socket 空闲超时。
 */
function httpRequest(
  url: string,
  options: {
    method: string;
    headers: Record<string, string>;
    body?: string;
    timeoutMs: number;
    signal?: AbortSignal;
  },
): Promise<{ status: number; text: string }> {
  return new Promise((resolve, reject) => {
    let target: URL;
    try {
      target = new URL(url);
    } catch (error) {
      reject(error);
      return;
    }
    const lib = target.protocol === 'https:' ? https : http;
    const payload = options.body === undefined ? undefined : Buffer.from(options.body, 'utf8');
    let settled = false;
    let timer: NodeJS.Timeout | undefined;
    const finish = (fn: () => void): void => {
      if (settled) return;
      settled = true;
      if (timer) clearTimeout(timer);
      fn();
    };

    const req = lib.request(
      {
        protocol: target.protocol,
        hostname: target.hostname,
        port: target.port || undefined,
        path: `${target.pathname}${target.search}`,
        method: options.method,
        headers: {
          ...options.headers,
          ...(payload ? { 'Content-Length': String(payload.length) } : {}),
        },
      },
      (res) => {
        const chunks: Buffer[] = [];
        res.on('data', (chunk) => chunks.push(chunk as Buffer));
        res.on('end', () =>
          finish(() => resolve({ status: res.statusCode ?? 0, text: Buffer.concat(chunks).toString('utf8') })),
        );
        res.on('error', (error) => finish(() => reject(error)));
      },
    );

    req.setTimeout(0); // 关掉 socket 空闲超时：长任务期间连接本来就是静默的
    timer = setTimeout(() => {
      req.destroy(new Error(`请求超时（${options.timeoutMs}ms）`));
    }, options.timeoutMs);
    timer.unref?.();
    const onAbort = (): void => {
      req.destroy(new Error('任务已被中断'));
    };
    options.signal?.addEventListener('abort', onAbort, { once: true });
    req.on('close', () => options.signal?.removeEventListener('abort', onAbort));
    req.on('error', (error) => finish(() => reject(error)));
    if (payload) req.write(payload);
    req.end();
  });
}

export class KbService {
  private readonly rootDir: string;
  private readonly metaPath: string;
  private readonly logger: Logger;
  private readonly config: AppConfig;
  private readonly publicBaseUrl: string;
  /** 按租户缓存管理员 token（B3：此前是全局单 token，跨租户复用） */
  private readonly tokens = new Map<string, { token: string; obtainedAt: number }>();

  constructor(config: AppConfig, logger: Logger) {
    this.config = config;
    this.logger = logger;
    this.rootDir = path.join(config.paths.workspace, '..', 'uploads');
    fs.mkdirSync(this.rootDir, { recursive: true });
    this.metaPath = path.join(this.rootDir, 'meta.json');
    this.publicBaseUrl = config.publicBaseUrl || `http://127.0.0.1:${config.port}`;
    if (!config.publicBaseUrl) {
      this.logger.warn(
        '未配置 AGENT_PUBLIC_BASE_URL，入库时下发给 Java 的文件地址按本机端口推断；Java 与 Agent 不同机/容器时请显式配置',
        { fallback: this.publicBaseUrl },
      );
    }
    this.loadMeta();
  }

  private loadMeta(): MetaFile {
    try {
      if (!fs.existsSync(this.metaPath)) return { documents: [] };
      return JSON.parse(fs.readFileSync(this.metaPath, 'utf8')) as MetaFile;
    } catch {
      return { documents: [] };
    }
  }

  private persist(meta: MetaFile): void {
    const tmp = this.metaPath + '.tmp';
    fs.writeFileSync(tmp, JSON.stringify(meta, null, 2), 'utf8');
    fs.renameSync(tmp, this.metaPath);
  }

  private dirFor(tenantId: string, projectId: string): string {
    const dir = path.join(this.rootDir, safeSegment(tenantId), safeSegment(projectId));
    fs.mkdirSync(dir, { recursive: true });
    return dir;
  }

  /** 上传原始字节并落盘，返回文档记录（状态 uploaded）；随后可异步触发入库 */
  saveUpload(opts: {
    tenantId: string;
    projectId: string;
    docType: string;
    fileName: string;
    data: Buffer;
  }): KbDocument {
    const { tenantId, projectId, docType, fileName, data } = opts;
    if (fileName.length === 0 || fileName.length > 200) {
      throw new AppError('文件名长度不合法', { category: 'validation', status: 400 });
    }
    // 仅拦截路径/系统危险字符与控制符；中文、全角符号、空格均允许
    if (/[<>:"/\\|?*\u0000-\u001f]/.test(fileName)) {
      throw new AppError('文件名包含非法字符（不允许 \\ / : * ? " < > | 等）', {
        category: 'validation',
        status: 400,
      });
    }
    if (data.byteLength > MAX_UPLOAD_BYTES) {
      throw new AppError('文件超过 60MB 限制', { category: 'validation', status: 413 });
    }
    const ext = path.extname(fileName).toLowerCase();
    if (!ALLOWED_EXT.has(ext)) {
      throw new AppError(`暂不支持 ${ext || '该格式'}，支持 docx/doc/pdf/txt/md/xlsx/pptx`, {
        category: 'validation',
        status: 400,
      });
    }
    const now = new Date().toISOString();
    const fileId = crypto.randomUUID();
    const relPath = [safeSegment(tenantId), safeSegment(projectId), `${fileId}${ext}`].join('/');
    const doc: KbDocument = {
      fileId,
      fileName,
      docType,
      tenantId,
      projectId,
      bytes: data.byteLength,
      ext,
      relPath,
      status: 'uploaded',
      createdAt: now,
      updatedAt: now,
    };
    this.dirFor(tenantId, projectId);
    fs.writeFileSync(path.join(this.rootDir, ...relPath.split('/')), data);
    const meta = this.loadMeta();
    meta.documents.push(doc);
    this.persist(meta);
    this.logger.info('文档已上传，待入库', {
      fileId: doc.fileId, fileName: doc.fileName, tenantId, projectId,
    });
    return doc;
  }

  /** 发起 RAG 入库（后台任务），状态在 meta 中推进；登录态失效自动重登重试一次 */
  async indexAsync(doc: KbDocument): Promise<void> {
    const meta = this.loadMeta();
    const item = meta.documents.find((d) => d.fileId === doc.fileId);
    if (!item) return;
    item.status = 'indexing';
    item.updatedAt = new Date().toISOString();
    this.persist(meta);

    // 单次入库调用（含取 token）。token 按租户缓存并设 TTL；遇到 auth 失败会强制重登重试一次
    const performIndex = async (forceLogin: boolean): Promise<number> => {
      const token = await this.ensureToken(doc.tenantId, forceLogin);
      // B5：文件地址改为可配置的对外基础地址，避免 Java 与 Agent 不同机时 127.0.0.1 不可达
      const fileUrl = `${this.publicBaseUrl.replace(/\/+$/, '')}/kb/files/${doc.fileId}`;
      const body = {
        tenantId: Number(doc.tenantId),
        projectId: Number(doc.projectId),
        docType: doc.docType,
        sourceDocName: doc.fileName,
        url: fileUrl,
      };
      const resp = await this.javaJson(
        token,
        doc.tenantId,
        '/admin-api/bidding/bid-agent/contract/segment/index',
        'POST',
        body,
        INDEX_TIMEOUT_MS,
      );
      return typeof resp?.data === 'number' ? resp.data : 0;
    };

    try {
      let chunks: number;
      try {
        chunks = await performIndex(false);
      } catch (error) {
        // Java 重启等导致缓存 token 失效：强制重登并重试一次（B3）
        if (error instanceof AppError && error.category === 'auth') {
          this.logger.warn('Java 登录态失效，强制重新登录后重试入库', { fileId: doc.fileId });
          chunks = await performIndex(true);
        } else {
          throw error;
        }
      }
      const m2 = this.loadMeta();
      const item2 = m2.documents.find((d) => d.fileId === doc.fileId);
      if (!item2) return;
      item2.status = 'done';
      item2.chunks = chunks;
      item2.error = undefined;
      item2.updatedAt = new Date().toISOString();
      this.persist(m2);
      this.logger.info('文档入库完成', { fileId: doc.fileId, chunks, fileName: doc.fileName });
    } catch (error) {
      this.logger.warn('文档入库失败', { fileId: doc.fileId, message: (error as Error).message });
      const m3 = this.loadMeta();
      const item3 = m3.documents.find((d) => d.fileId === doc.fileId);
      if (item3) {
        item3.status = 'failed';
        item3.error = (error as Error).message.slice(0, 500);
        item3.updatedAt = new Date().toISOString();
        this.persist(m3);
      }
    }
  }

  /** 供 Java Tika 下载原始文件 */
  fileStream(fileId: string): { stream: fs.ReadStream; bytes: number } | null {
    // B5：先用元数据里的 relPath 直连定位；老数据没有 relPath 时回退为按 fileId 前缀查找一次
    const doc = this.get(fileId);
    const candidates: string[] = [];
    if (doc) {
      candidates.push(doc.relPath ?? [safeSegment(doc.tenantId), safeSegment(doc.projectId), `${doc.fileId}${doc.ext}`].join('/'));
    } else {
      candidates.push(...this.findLegacyPaths(fileId));
    }
    const base = path.resolve(this.rootDir);
    for (const rel of candidates) {
      const target = path.resolve(this.rootDir, ...rel.split('/'));
      if (target !== base && !target.startsWith(`${base}${path.sep}`)) continue;
      if (!fs.existsSync(target) || !fs.statSync(target).isFile()) continue;
      return { stream: fs.createReadStream(target), bytes: fs.statSync(target).size };
    }
    return null;
  }

  /** 兼容历史数据：按 fileId 前缀在两级目录内查找（仅元数据缺失时使用） */
  private findLegacyPaths(fileId: string): string[] {
    const hits: string[] = [];
    for (const tenantDir of fs.readdirSync(this.rootDir, { withFileTypes: true })) {
      if (!tenantDir.isDirectory()) continue;
      const tenantPath = path.join(this.rootDir, tenantDir.name);
      for (const projDir of fs.readdirSync(tenantPath, { withFileTypes: true })) {
        if (!projDir.isDirectory()) continue;
        const fullDir = path.join(tenantPath, projDir.name);
        for (const ent of fs.readdirSync(fullDir, { withFileTypes: true })) {
          if (ent.isFile() && ent.name.startsWith(fileId)) {
            hits.push([tenantDir.name, projDir.name, ent.name].join('/'));
          }
        }
      }
    }
    return hits;
  }

  list(filter: { tenantId?: string; projectId?: string; tenantIds?: string[] } = {}): KbDocument[] {
    let docs = this.loadMeta().documents;
    if (filter.tenantId) docs = docs.filter((d) => d.tenantId === filter.tenantId);
    if (filter.tenantIds) docs = docs.filter((d) => filter.tenantIds!.includes(d.tenantId));
    if (filter.projectId) docs = docs.filter((d) => d.projectId === filter.projectId);
    return docs.sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }

  get(fileId: string): KbDocument | undefined {
    return this.loadMeta().documents.find((d) => d.fileId === fileId);
  }

  /** 删除：先清 Java 向量库片段，再移除磁盘文件 */
  async remove(tenantId: string, projectId: string, fileName: string): Promise<boolean> {
    const docs = this.loadMeta().documents.filter(
      (d) => d.tenantId === tenantId && d.projectId === projectId && d.fileName === fileName,
    );
    if (docs.length === 0) {
      throw new AppError('文档不存在', { category: 'not_found', status: 404 });
    }
    const ids = new Set(docs.map((doc) => doc.fileId));
    const token = await this.ensureToken(tenantId);
    const qs = new URLSearchParams({
      tenantId: String(tenantId),
      sourceDocName: fileName,
    });
    await this.javaJson(token, tenantId, `/admin-api/bidding/bid-agent/contract/segment/delete?${qs}`, 'DELETE', undefined, 60_000);
    for (const doc of docs) {
      const rel = doc.relPath
        ?? [safeSegment(doc.tenantId), safeSegment(doc.projectId), `${doc.fileId}${doc.ext}`].join('/');
      const target = path.resolve(this.rootDir, ...rel.split('/'));
      const base = path.resolve(this.rootDir);
      if (!target.startsWith(`${base}${path.sep}`)) continue;
      if (fs.existsSync(target)) fs.unlinkSync(target);
    }
    // B4：删除时重新读取元数据（await 期间可能被其它请求改写），并按 fileId 集合全量清理；
    // 此前只移除 docs[0] 一条，同名文档会残留“文件已删、记录仍在”的孤儿
    const latest = this.loadMeta();
    latest.documents = latest.documents.filter((doc) => !ids.has(doc.fileId));
    this.persist(latest);
    return true;
  }

  /** 取租户管理员 token（B3）：按租户缓存 + TTL；凭据为空时给出明确错误（默认账号兜底时不触发） */
  private async ensureToken(tenantId: string, force = false): Promise<string> {
    const cached = this.tokens.get(tenantId);
    if (!force && cached && Date.now() - cached.obtainedAt < TOKEN_TTL_MS) return cached.token;
    const { adminUsername, adminPassword } = this.config.java;
    if (!adminUsername || !adminPassword) {
      throw new AppError(
        '知识库管理员凭据为空：请配置 JAVA_ADMIN_USER / JAVA_ADMIN_PASS',
        { category: 'validation', status: 400, retryable: false },
      );
    }
    const resp = await this.javaJson(
      null,
      tenantId,
      '/admin-api/system/auth/login',
      'POST',
      { username: adminUsername, password: adminPassword },
      30_000,
      true,
    );
    const token = resp?.data?.accessToken;
    if (!token) {
      throw new AppError('Java 管理员登录失败：未获取到 token', { category: 'server', status: 502 });
    }
    this.tokens.set(tenantId, { token: String(token), obtainedAt: Date.now() });
    return String(token);
  }

  private async javaJson(
    bearer: string | null,
    tenantId: string,
    pathname: string,
    method: string,
    body?: unknown,
    timeoutMs = 60_000,
    isLogin = false,
  ): Promise<any> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const headers: Record<string, string> = { 'tenant-id': tenantId };
      if (bearer) headers.Authorization = `Bearer ${bearer}`;
      if (body !== undefined) headers['Content-Type'] = 'application/json; charset=utf-8';
      // 用 node:http 而非 fetch：入库可能耗时 5 分钟以上，fetch(undici) 有 5 分钟硬超时
      const resp = await httpRequest(this.config.java.baseUrl.replace(/\/+$/, '') + pathname, {
        method,
        headers,
        body: body !== undefined ? JSON.stringify(body) : undefined,
        timeoutMs,
        signal: controller.signal,
      });
      const text = resp.text;
      let json: any = {};
      try {
        json = JSON.parse(text);
      } catch {
        /* 非 JSON */
      }
      const httpOk = resp.status >= 200 && resp.status < 300;
      if (!httpOk || (json && typeof json.code === 'number' && json.code !== 0)) {
        const detail = json?.msg || text.slice(0, 300);
        const bizCode = typeof json?.code === 'number' ? json.code : 0;
        // Java(yudao) 未登录通常返回 HTTP 200 + code=401：需同时识别并轮换 token
        const authFail = !isLogin && !!bearer && (resp.status === 401 || bizCode === 401);
        if (authFail) {
          this.tokens.delete(tenantId);
          throw new AppError(`Java 登录态失效(${pathname} ${resp.status}): ${detail}`, {
            category: 'auth',
            status: 401,
          });
        }
        if (!httpOk) {
          throw new AppError(`Java 接口失败 ${pathname} ${resp.status}: ${detail}`, {
            category: 'server',
            status: 502,
          });
        }
        throw new AppError(`Java 业务失败 ${pathname}: ${detail}`, {
          category: 'server',
          status: 502,
        });
      }
      return json;
    } finally {
      clearTimeout(timer);
    }
  }
}
