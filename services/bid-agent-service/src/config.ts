import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const MODULE_DIR = path.dirname(fileURLToPath(import.meta.url));

/** bid-agent-service 根目录（dist 的上一级） */
export const SERVICE_ROOT = path.resolve(MODULE_DIR, '..');

export type SessionBackendKind = 'openai-compat' | 'pi-sdk';

export interface LlmConfig {
  baseUrl: string;
  model: string;
  apiKey: string;
  temperature: number;
  maxTokens: number;
  timeoutMs: number;
  maxRetries: number;
}

export interface JavaConfig {
  baseUrl: string;
  apiKey: string;
  /** 机对机鉴权密钥，作为 X-Agent-Key 请求头下发 */
  agentKey: string;
  timeoutMs: number;
  maxRetries: number;
  shadowStatusPath: string;
  /** 知识库入库用的 Java 管理员凭据（登录换取 Bearer 调 /bid/segment/index） */
  adminUsername: string;
  adminPassword: string;
}

export interface RetrieveConfig {
  topK: number;
}

/** 入站 API Key：可通过 `key` 或 `key:tenantA|tenantB` 形式绑定租户（B1 / B2） */
export interface ApiKeyEntry {
  key: string;
  /** 该 Key 允许访问的租户；空数组表示不限制 */
  tenants: string[];
}

export interface AuthConfig {
  keys: ApiKeyEntry[];
  /** 未配置任何 Key 时是否强制拒绝受保护接口（B1） */
  required: boolean;
  /** 允许的跨域来源；`*` 表示放开（B1） */
  allowedOrigins: string[];
}

export interface OrchestratorConfig {
  maxToolRounds: number;
  maxFixAttempts: number;
  hilWaitTimeoutMs: number;
  /** 单进程同时执行的任务数上限，其余进入排队（A11 背压） */
  maxConcurrentTasks: number;
  /** 排队深度上限，超出后创建任务直接拒绝（A11 背压） */
  maxQueueDepth: number;
  /** 单任务 token 预算，0 表示不限制（A14 成本治理） */
  maxTaskTokens: number;
  /** 断点续跑回灌的历史消息条数上限（A2） */
  resumeHistoryMessages: number;
  /** 断点续跑回灌的历史消息字符上限（A2） */
  resumeHistoryChars: number;
}

export interface AppConfig {
  host: string;
  port: number;
  logLevel: string;
  auth: AuthConfig;
  /** 对外可访问的基础地址（Java 回拉文件等场景）；留空则按本机端口推断（B5） */
  publicBaseUrl: string;
  java: JavaConfig;
  llm: LlmConfig;
  sessionBackend: SessionBackendKind;
  piSdkModule: string;
  retrieve: RetrieveConfig;
  orchestrator: OrchestratorConfig;
  sse: {
    /** SSE 连接空闲上限，到期主动关闭由客户端重连（B6） */
    maxIdleMs: number;
  };
  docx: {
    /** 是否允许调用本机 Word 做 PDF 转换；默认关闭（B8） */
    pdfViaWord: boolean;
    /** 标书模板 docx 路径；存在时最终交付件以「回填该模板」为准，否则回退内置预览排版 */
    templatePath: string;
  };
  paths: {
    workspace: string;
    sessions: string;
  };
}

function stripQuotes(raw: string): string {
  const value = raw.trim();
  if (
    (value.startsWith('"') && value.endsWith('"')) ||
    (value.startsWith("'") && value.endsWith("'"))
  ) {
    return value.slice(1, -1);
  }
  return value;
}

/** 极简 .env 解析：避免为了读配置引入运行时依赖 */
export function loadDotEnv(file: string): Record<string, string> {
  const result: Record<string, string> = {};
  if (!fs.existsSync(file)) return result;
  const text = fs.readFileSync(file, 'utf8');
  for (const rawLine of text.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) continue;
    const eq = line.indexOf('=');
    if (eq <= 0) continue;
    result[line.slice(0, eq).trim()] = stripQuotes(line.slice(eq + 1));
  }
  return result;
}

export function loadConfig(extra?: Record<string, string>): AppConfig {
  const dotEnv = loadDotEnv(path.join(SERVICE_ROOT, '.env'));
  const env: Record<string, string | undefined> = { ...dotEnv, ...process.env, ...(extra ?? {}) };

  const str = (key: string, fallback = ''): string => {
    const value = env[key];
    return value === undefined || value === '' ? fallback : String(value);
  };
  const num = (key: string, fallback: number): number => {
    const value = env[key];
    if (value === undefined || value === '') return fallback;
    const parsed = Number(value);
    if (!Number.isFinite(parsed)) {
      throw new Error(`配置项 ${key} 不是合法数字：${value}`);
    }
    return parsed;
  };
  const bool = (key: string, fallback: boolean): boolean => {
    const value = env[key];
    if (value === undefined || value === '') return fallback;
    return ['1', 'true', 'yes', 'on'].includes(String(value).trim().toLowerCase());
  };

  const port = num('AGENT_PORT', 8080);
  if (port <= 0 || port > 65535) throw new Error(`AGENT_PORT 非法：${port}`);

  const backend = str('SESSION_BACKEND', 'openai-compat');
  if (backend !== 'openai-compat' && backend !== 'pi-sdk') {
    throw new Error(`SESSION_BACKEND 只支持 openai-compat / pi-sdk：${backend}`);
  }

  // B1/B2：入站 API Key；`key:tenantA|tenantB` 形式可把 Key 绑定到指定租户
  const apiKeys: ApiKeyEntry[] = str('AGENT_API_KEYS', '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
    .map((item) => {
      const separator = item.indexOf(':');
      const key = (separator < 0 ? item : item.slice(0, separator)).trim();
      const tenants = (separator < 0 ? '' : item.slice(separator + 1))
        .split('|')
        .map((tenant) => tenant.trim())
        .filter(Boolean);
      return { key, tenants };
    })
    .filter((entry) => entry.key.length > 0);

  const allowedOrigins = str('AGENT_ALLOWED_ORIGINS', 'http://127.0.0.1:5180,http://localhost:5180')
    .split(',')
    .map((origin) => origin.trim())
    .filter(Boolean);

  // 模板路径：相对路径按服务根目录解析，避免进程 cwd（Docker/守护进程）影响模板定位
  const resolveTemplatePath = (): string => {
    const configured = str('BID_TEMPLATE_DOCX', '');
    if (configured) {
      return path.isAbsolute(configured) ? configured : path.join(SERVICE_ROOT, configured);
    }
    return path.join(SERVICE_ROOT, 'templates', '投标标书_主模板.docx');
  };

  return {
    host: str('AGENT_HOST', '127.0.0.1'),
    port,
    logLevel: str('LOG_LEVEL', 'info'),
    auth: {
      keys: apiKeys,
      required: bool('AGENT_REQUIRE_AUTH', false),
      allowedOrigins,
    },
    publicBaseUrl: str('AGENT_PUBLIC_BASE_URL', ''),
    java: {
      baseUrl: str('JAVA_BASE_URL', 'http://127.0.0.1:48080'),
      apiKey: str('JAVA_API_KEY', ''),
      agentKey: str('JAVA_AGENT_KEY', ''),
      timeoutMs: num('JAVA_TIMEOUT_MS', 30_000),
      maxRetries: num('JAVA_MAX_RETRIES', 3),
      shadowStatusPath: str('JAVA_SHADOW_STATUS_PATH', ''),
      // B3：暂时保留内置默认账号以便开箱可用；生产环境请用 JAVA_ADMIN_USER/PASS 覆盖为专用服务账号
      adminUsername: str('JAVA_ADMIN_USER', 'admin'),
      adminPassword: str('JAVA_ADMIN_PASS', 'admin123'),
    },
    llm: {
      baseUrl: str('LLM_BASE_URL', 'http://127.0.0.1:8100/v1'),
      model: str('LLM_MODEL', 'Qwen2.5-14B-Instruct-AWQ'),
      apiKey: str('LLM_API_KEY', 'EMPTY'),
      temperature: num('LLM_TEMPERATURE', 0.2),
      maxTokens: num('LLM_MAX_TOKENS', 8192),
      timeoutMs: num('LLM_TIMEOUT_MS', 180_000),
      maxRetries: num('LLM_MAX_RETRIES', 3),
    },
    sessionBackend: backend,
    piSdkModule: str('PI_SDK_MODULE', ''),
    retrieve: {
      topK: num('RETRIEVE_TOPK', 8),
    },
    orchestrator: {
      maxToolRounds: num('MAX_TOOL_ROUNDS', 40),
      maxFixAttempts: num('MAX_FIX_ATTEMPTS', 3),
      hilWaitTimeoutMs: num('HIL_WAIT_TIMEOUT_MS', 86_400_000),
      maxConcurrentTasks: Math.max(1, num('MAX_CONCURRENT_TASKS', 2)),
      maxQueueDepth: Math.max(0, num('MAX_QUEUE_DEPTH', 50)),
      maxTaskTokens: Math.max(0, num('MAX_TASK_TOKENS', 0)),
      resumeHistoryMessages: Math.max(0, num('RESUME_HISTORY_MESSAGES', 12)),
      resumeHistoryChars: Math.max(0, num('RESUME_HISTORY_CHARS', 6000)),
    },
    sse: {
      maxIdleMs: Math.max(0, num('SSE_MAX_IDLE_MS', 1_800_000)),
    },
    docx: {
      pdfViaWord: bool('PDF_VIA_WORD', false),
      templatePath: resolveTemplatePath(),
    },
    paths: {
      workspace: str('WORKSPACE_DIR', '') || path.join(SERVICE_ROOT, 'workspace'),
      sessions: str('SESSIONS_DIR', '') || path.join(SERVICE_ROOT, 'sessions'),
    },
  };
}
