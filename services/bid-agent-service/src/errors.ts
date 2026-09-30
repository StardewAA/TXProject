export type ErrorCategory =
  | 'timeout'
  | 'network'
  | 'rate_limit'
  | 'auth'
  | 'server'
  | 'validation'
  | 'abort'
  | 'retrieval_miss'
  | 'output_invalid'
  | 'not_found'
  | 'conflict'
  | 'unknown';

export interface AppErrorOptions {
  category?: ErrorCategory;
  status?: number;
  retryable?: boolean;
  details?: unknown;
}

const RETRYABLE: ReadonlySet<ErrorCategory> = new Set<ErrorCategory>([
  'timeout',
  'network',
  'rate_limit',
  'server',
]);

export class AppError extends Error {
  readonly category: ErrorCategory;

  readonly status: number;

  readonly retryable: boolean;

  readonly details?: unknown;

  constructor(message: string, options: AppErrorOptions = {}) {
    super(message);
    this.name = 'AppError';
    this.category = options.category ?? 'unknown';
    this.status = options.status ?? 500;
    this.retryable = options.retryable ?? RETRYABLE.has(this.category);
    this.details = options.details;
  }

  /**
   * 调用方该采取的动作（B7）：retry=可重试；fix_request=调用方修正请求；manual=需人工介入。
   * 让前端/Java 侧能够区分"重试"与"补参数/转人工"，而不是只看一个错误码。
   */
  get action(): 'retry' | 'fix_request' | 'manual' {
    if (this.retryable) return 'retry';
    if (
      this.category === 'validation'
      || this.category === 'not_found'
      || this.category === 'conflict'
      || this.category === 'auth'
      || this.category === 'rate_limit'
    ) {
      return 'fix_request';
    }
    return 'manual';
  }

  toJSON(): Record<string, unknown> {
    return {
      error: this.name,
      code: this.category,
      message: this.message,
      status: this.status,
      retryable: this.retryable,
      action: this.action,
      details: this.details ?? null,
    };
  }
}

export function isAppError(error: unknown): error is AppError {
  return error instanceof AppError;
}

export function isAbortError(error: unknown): boolean {
  return (
    (error instanceof Error && (error.name === 'AbortError' || error.name === 'TimeoutError')) ||
    (isAppError(error) && (error.category === 'abort' || error.category === 'timeout'))
  );
}

/** 把任意异常归一化为 AppError，便于重试策略与对外错误码统一 */
export function normalizeError(error: unknown): AppError {
  if (isAppError(error)) return error;
  if (error instanceof Error) {
    const cause = (error as { cause?: { code?: string } }).cause;
    const code = cause?.code ?? '';
    if (error.name === 'AbortError' || error.name === 'TimeoutError') {
      return new AppError(`操作超时：${error.message}`, { category: 'timeout', status: 504 });
    }
    if (code === 'ECONNRESET' || code === 'ECONNREFUSED' || code === 'ENOTFOUND' || code === 'EAI_AGAIN') {
      return new AppError(`网络不可达：${error.message}`, { category: 'network', status: 502 });
    }
    return new AppError(error.message, { category: 'unknown' });
  }
  return new AppError(String(error), { category: 'unknown' });
}

export function httpStatusToAppError(status: number, body: string): AppError {
  const snippet = body.slice(0, 500);
  const details = { upstreamStatus: status };
  if (status === 401 || status === 403) {
    return new AppError(`鉴权失败(${status})：${snippet}`, { category: 'auth', status: 401, details });
  }
  if (status === 400 || status === 422) {
    return new AppError(`上游参数错误(${status})：${snippet}`, { category: 'validation', status, details });
  }
  if (status === 404) {
    return new AppError(`资源不存在：${snippet}`, { category: 'not_found', status: 404, details });
  }
  if (status === 409) {
    return new AppError(`冲突：${snippet}`, { category: 'conflict', status: 409, details });
  }
  if (status === 429) {
    return new AppError(`上游限流：${snippet}`, { category: 'rate_limit', status: 429, details });
  }
  if (status >= 500) {
    // 保留上游状态码，调用方可以区分 500/502/503/504，而不是一律 502
    return new AppError(`上游服务错误(${status})：${snippet}`, { category: 'server', status, details });
  }
  return new AppError(`请求失败(${status})：${snippet}`, { category: 'validation', status, details });
}
