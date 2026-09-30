import crypto from 'node:crypto';
import type { IncomingMessage } from 'node:http';
import type { AppConfig } from '../config.js';
import { AppError } from '../errors.js';

/** 调用方上下文：由入站鉴权得出（B1 / B2） */
export interface CallerContext {
  /** 命中的 API Key 标识；未启用鉴权时为 undefined */
  keyId?: string;
  /** 允许访问的租户；空数组表示不限制 */
  tenants: string[];
}

/** 无需鉴权的路径：探活与指标抓取 */
const PUBLIC_PATHS = new Set(['/healthz', '/readyz', '/metrics']);

/** 无需鉴权的路径前缀：/kb/files/{fileId} 由 Java 侧 Tika 直接回拉（无法携带 Agent 鉴权信息），
 *  fileId 为不可枚举的随机 UUID（能力型安全模型：拿到 id 即可读，等价于文件分享链接） */
const PUBLIC_PREFIXES = ['/kb/files/'];

export function isPublicPath(pathname: string): boolean {
  if (PUBLIC_PATHS.has(pathname)) return true;
  return PUBLIC_PREFIXES.some((prefix) => pathname.startsWith(prefix));
}

function safeEqual(a: string, b: string): boolean {
  const left = Buffer.from(a, 'utf8');
  const right = Buffer.from(b, 'utf8');
  if (left.length !== right.length) return false;
  return crypto.timingSafeEqual(left, right);
}

/**
 * 入站鉴权（B1）：`X-Agent-Key` 或 `Authorization: Bearer <key>`。
 * 未配置 AGENT_API_KEYS 时：默认放行（保持本机开发可用，启动时告警）；
 * 若显式设置 AGENT_REQUIRE_AUTH=true，则一律 401。
 */
export function authenticate(req: IncomingMessage, config: AppConfig): CallerContext {
  const { keys, required } = config.auth;
  if (keys.length === 0) {
    if (required) {
      throw new AppError('服务未配置 AGENT_API_KEYS，按 AGENT_REQUIRE_AUTH=true 拒绝访问', {
        category: 'auth',
        status: 401,
      });
    }
    return { tenants: [] };
  }
  const headerKey = String(req.headers['x-agent-key'] ?? '').trim();
  const bearer = String(req.headers.authorization ?? '').replace(/^Bearer\s+/i, '').trim();
  const provided = headerKey || bearer;
  if (!provided) {
    throw new AppError('缺少 API Key：请在 X-Agent-Key 请求头中携带', {
      category: 'auth',
      status: 401,
    });
  }
  const matched = keys.find((entry) => safeEqual(entry.key, provided));
  if (!matched) {
    throw new AppError('API Key 不合法', { category: 'auth', status: 401 });
  }
  return { keyId: matched.key.slice(0, 6), tenants: matched.tenants };
}

/** 校验调用方是否有权访问该租户（B2） */
export function assertTenant(caller: CallerContext, tenantId: string): void {
  if (caller.tenants.length === 0) return;
  if (!caller.tenants.includes(tenantId)) {
    throw new AppError(`无权访问租户 ${tenantId}`, { category: 'auth', status: 403 });
  }
}

/** 列表类接口的租户过滤范围；undefined 表示不限制 */
export function tenantScope(caller: CallerContext): string[] | undefined {
  return caller.tenants.length > 0 ? [...caller.tenants] : undefined;
}

/** CORS 响应头（B1）：按白名单回显来源，未命中则不返回跨域头 */
export function corsHeaders(req: IncomingMessage, allowedOrigins: string[]): Record<string, string> {
  const origin = String(req.headers.origin ?? '');
  const base: Record<string, string> = {
    Vary: 'Origin',
    'Access-Control-Allow-Methods': 'GET,POST,DELETE,OPTIONS',
    'Access-Control-Allow-Headers':
      'Content-Type,Authorization,Idempotency-Key,X-Tenant-Id,X-File-Name,X-Agent-Key,X-Request-Id',
    'Access-Control-Max-Age': '86400',
  };
  if (!origin) return base;
  if (allowedOrigins.includes('*')) return { ...base, 'Access-Control-Allow-Origin': '*' };
  if (allowedOrigins.includes(origin)) {
    return { ...base, 'Access-Control-Allow-Origin': origin };
  }
  return base;
}
