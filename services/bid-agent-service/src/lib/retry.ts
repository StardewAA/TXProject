import { AppError, isAppError, normalizeError } from '../errors.js';

export interface RetryOptions {
  retries: number;
  baseDelayMs?: number;
  maxDelayMs?: number;
  signal?: AbortSignal;
  onRetry?: (error: AppError, attempt: number, delayMs: number) => void;
}

function sleep(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(new AppError('任务已被中断', { category: 'abort', status: 409, retryable: false }));
      return;
    }
    const timer = setTimeout(() => {
      if (signal) signal.removeEventListener('abort', onAbort);
      resolve();
    }, ms);
    timer.unref?.();
    function onAbort() {
      clearTimeout(timer);
      reject(new AppError('任务已被中断', { category: 'abort', status: 409, retryable: false }));
    }
    if (signal) signal.addEventListener('abort', onAbort, { once: true });
  });
}

/** 指数退避重试：仅对 AppError.retryable 的错误重试 */
export async function withRetry<T>(fn: () => Promise<T>, options: RetryOptions): Promise<T> {
  const { retries, baseDelayMs = 500, maxDelayMs = 8000, signal, onRetry } = options;
  let attempt = 0;
  for (;;) {
    try {
      return await fn();
    } catch (rawError) {
      const error = normalizeError(rawError);
      attempt += 1;
      if (isAppError(error) && (error.category === 'abort' || error.category === 'timeout')) {
        if (signal?.aborted) throw error;
      }
      if (!error.retryable || attempt > retries) throw error;
      const delay = Math.min(maxDelayMs, baseDelayMs * 2 ** (attempt - 1));
      const jitter = Math.floor(delay * (0.5 + Math.random() * 0.5));
      onRetry?.(error, attempt, jitter);
      await sleep(jitter, signal);
    }
  }
}
