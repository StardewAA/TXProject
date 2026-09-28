import { AsyncLocalStorage } from 'node:async_hooks';

/**
 * 请求级 traceId 透传（B10）：在 HTTP 入口设置，出站 HTTP 客户端与日志自动带上，
 * 免去逐层传参。
 */
const storage = new AsyncLocalStorage<string>();

export function runWithTraceId<T>(traceId: string, fn: () => T): T {
  return storage.run(traceId, fn);
}

export function currentTraceId(): string | undefined {
  return storage.getStore();
}
