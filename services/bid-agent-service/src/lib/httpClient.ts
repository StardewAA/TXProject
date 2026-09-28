import { AppError, httpStatusToAppError } from '../errors.js';
import type { Logger } from '../logger.js';
import { withRetry } from './retry.js';
import { currentTraceId } from './trace.js';

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';

export interface HttpRequestOptions {
  method?: HttpMethod;
  /** 相对 baseUrl 的路径 */
  path?: string;
  /** 绝对路径（优先级高于 path） */
  url?: string;
  query?: Record<string, string | number | boolean | undefined | null>;
  body?: unknown;
  headers?: Record<string, string>;
  timeoutMs?: number;
  signal?: AbortSignal;
  /** 写接口幂等键，随 Idempotency-Key 头下发 */
  idempotencyKey?: string;
  retries?: number;
  /** 允许 404 返回 null 而非抛错 */
  allowNotFound?: boolean;
}

export interface HttpClientOptions {
  baseUrl: string;
  name: string;
  logger: Logger;
  timeoutMs: number;
  maxRetries: number;
  headers?: Record<string, string>;
}

export class HttpClient {
  private readonly baseUrl: string;

  private readonly name: string;

  private readonly logger: Logger;

  private readonly timeoutMs: number;

  private readonly maxRetries: number;

  private readonly baseHeaders: Record<string, string>;

  constructor(options: HttpClientOptions) {
    this.baseUrl = options.baseUrl.replace(/\/+$/, '');
    this.name = options.name;
    this.logger = options.logger;
    this.timeoutMs = options.timeoutMs;
    this.maxRetries = options.maxRetries;
    this.baseHeaders = options.headers ?? {};
  }

  private buildUrl(request: HttpRequestOptions): string {
    if (request.url) return request.url;
    const relative = (request.path ?? '/').replace(/^\/+/, '');
    const url = new URL(`${this.baseUrl}/${relative}`);
    for (const [key, value] of Object.entries(request.query ?? {})) {
      if (value === undefined || value === null || value === '') continue;
      url.searchParams.set(key, String(value));
    }
    return url.toString();
  }

  async request<T = unknown>(request: HttpRequestOptions): Promise<T> {
    const method = request.method ?? 'GET';
    const url = this.buildUrl(request);
    const started = Date.now();

    return withRetry(
      async () => {
        const signals: AbortSignal[] = [];
        const timeout = AbortSignal.timeout(request.timeoutMs ?? this.timeoutMs);
        signals.push(timeout);
        if (request.signal) signals.push(request.signal);
        const headers: Record<string, string> = {
          Accept: 'application/json',
          ...this.baseHeaders,
          ...(request.headers ?? {}),
        };
        // B10：把请求级 traceId 透传给下游（Java），便于跨服务串联排障
        const traceId = currentTraceId();
        if (traceId && !headers['X-Request-Id']) headers['X-Request-Id'] = traceId;
        let payload: string | undefined;
        if (request.body !== undefined && request.body !== null) {
          payload = JSON.stringify(request.body);
          headers['Content-Type'] = 'application/json';
        }
        // 请求头必须是 ByteString：对可能含中文的键做百分号编码
        if (request.idempotencyKey) {
          headers['Idempotency-Key'] = encodeURIComponent(request.idempotencyKey).slice(0, 200);
        }

        let response: Response;
        try {
          response = await fetch(url, {
            method,
            headers,
            body: payload,
            signal: AbortSignal.any(signals),
          });
        } catch (error) {
          const normalized = error instanceof Error ? error : new Error(String(error));
          if (normalized.name === 'TimeoutError' || normalized.name === 'AbortError') {
            if (request.signal?.aborted) {
              throw new AppError('任务已被中断', { category: 'abort', retryable: false });
            }
            throw new AppError(`请求超时：${this.name} ${method} ${url}`, {
              category: 'timeout',
              retryable: true,
            });
          }
          throw new AppError(`请求失败：${this.name} ${method} ${url} ${normalized.message}`, {
            category: 'network',
            retryable: true,
          });
        }

        const text = await response.text();
        this.logger.debug(`${this.name} ${method} ${url} -> ${response.status}`, {
          ms: Date.now() - started,
        });

        if (response.status === 404 && request.allowNotFound) {
          return null as T;
        }
        if (!response.ok) {
          throw httpStatusToAppError(response.status, text);
        }
        if (!text) return undefined as T;
        try {
          return JSON.parse(text) as T;
        } catch {
          throw new AppError(`${this.name} 返回非 JSON：${text.slice(0, 200)}`, {
            category: 'server',
            retryable: false,
          });
        }
      },
      {
        retries: request.retries ?? this.maxRetries,
        signal: request.signal,
        onRetry: (error, attempt, delayMs) => {
          this.logger.warn(`${this.name} 请求重试`, {
            attempt,
            delayMs,
            reason: error.message,
            category: error.category,
          });
        },
      },
    );
  }

  get<T = unknown>(path: string, query?: HttpRequestOptions['query'], options: Omit<HttpRequestOptions, 'method' | 'path' | 'query'> = {}): Promise<T> {
    return this.request<T>({ ...options, method: 'GET', path, query });
  }

  post<T = unknown>(path: string, body?: unknown, options: Omit<HttpRequestOptions, 'method' | 'path' | 'body'> = {}): Promise<T> {
    return this.request<T>({ ...options, method: 'POST', path, body });
  }
}
