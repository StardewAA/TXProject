import { AppError } from '../errors.js';
import type { Logger } from '../logger.js';
import { randomId } from '../lib/fsx.js';

/** HIL（人工确认闸门）：ask-user 工具的运行时支撑 */
export interface PendingQuestion {
  questionId: string;
  taskId: string;
  question: string;
  options?: string[];
  required: boolean;
  context?: string;
  createdAt: string;
}

interface Waiter {
  taskId: string;
  resolve: (value: string) => void;
  reject: (error: Error) => void;
  timer: NodeJS.Timeout;
}

export class HilService {
  private readonly waiters = new Map<string, Waiter>();

  private readonly logger: Logger;

  private readonly waitTimeoutMs: number;

  constructor(logger: Logger, waitTimeoutMs: number) {
    this.logger = logger;
    this.waitTimeoutMs = waitTimeoutMs;
  }

  ask(input: {
    taskId: string;
    question: string;
    options?: string[];
    required?: boolean;
    context?: string;
    signal?: AbortSignal;
  }): { questionId: string; question: PendingQuestion; answer: Promise<string> } {
    const questionId = randomId('q');
    const question: PendingQuestion = {
      questionId,
      taskId: input.taskId,
      question: input.question,
      options: input.options,
      required: input.required ?? true,
      context: input.context,
      createdAt: new Date().toISOString(),
    };

    const answer = new Promise<string>((resolve, reject) => {
      const timer = setTimeout(() => {
        this.waiters.delete(questionId);
        reject(
          new AppError('人工确认超时', { category: 'timeout', status: 504, details: { questionId } }),
        );
      }, this.waitTimeoutMs);
      timer.unref?.();

      this.waiters.set(questionId, { taskId: input.taskId, resolve, reject, timer });
      input.signal?.addEventListener(
        'abort',
        () => {
          this.rejectQuestion(questionId, new AppError('任务已被中断', { category: 'abort', status: 409 }));
        },
        { once: true },
      );
    });

    this.logger.info('等待人工确认', { taskId: input.taskId, questionId, question: input.question });
    return { questionId, question, answer };
  }

  answer(questionId: string, value: string): boolean {
    const waiter = this.waiters.get(questionId);
    if (!waiter) return false;
    clearTimeout(waiter.timer);
    this.waiters.delete(questionId);
    this.logger.info('收到人工答复', { taskId: waiter.taskId, questionId });
    waiter.resolve(value);
    return true;
  }

  private rejectQuestion(questionId: string, error: Error): boolean {
    const waiter = this.waiters.get(questionId);
    if (!waiter) return false;
    clearTimeout(waiter.timer);
    this.waiters.delete(questionId);
    waiter.reject(error);
    return true;
  }

  /** 中断某个任务的所有待决问题 */
  cancelTask(taskId: string, reason = '任务已被中断'): number {
    let count = 0;
    for (const [questionId, waiter] of [...this.waiters.entries()]) {
      if (waiter.taskId !== taskId) continue;
      if (this.rejectQuestion(questionId, new AppError(reason, { category: 'abort', status: 409 }))) {
        count += 1;
      }
    }
    return count;
  }

  pendingCount(): number {
    return this.waiters.size;
  }
}
