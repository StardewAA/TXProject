import { AppError } from '../errors.js';
import type { TaskType } from './types.js';

/** 四类任务的阶段序列（终态统一为 DONE） */
export const TASK_PHASES: Record<TaskType, readonly string[]> = {
  parse: ['INIT', 'RETRIEVE', 'EXTRACT', 'CONFIRM', 'PERSIST', 'DONE'],
  outline: ['INIT', 'LOAD', 'PLAN', 'CONFIRM', 'PERSIST', 'DONE'],
  draft: ['INIT', 'SELECT', 'RETRIEVE', 'WRITE', 'VERIFY', 'COMMIT', 'DONE'],
  // 一键生成标书：全流程任务只对外暴露三个阶段（与 Java 任务台账 current_step 对齐）；
  // 模板回填由导出阶段按「标签留白 / 书签」两种模板形态完成（2026-09-22 起不再有 FILL 阶段）
  generate: ['INIT', 'PARSE', 'OUTLINE', 'DRAFT', 'DONE'],
};

export const TERMINAL_PHASE = 'DONE';

export class PhaseMachine {
  readonly taskType: TaskType;

  readonly phases: readonly string[];

  private cursor = 0;

  constructor(taskType: TaskType, phases?: readonly string[], startPhase?: string) {
    this.taskType = taskType;
    this.phases = phases ?? TASK_PHASES[taskType];
    if (startPhase) {
      const index = this.phases.indexOf(startPhase);
      if (index < 0) throw new AppError(`${taskType} 任务不含阶段 ${startPhase}`, { category: 'validation' });
      this.cursor = index;
    }
  }

  get current(): string {
    return this.phases[this.cursor] ?? TERMINAL_PHASE;
  }

  get index(): number {
    return this.cursor;
  }

  get total(): number {
    return this.phases.length;
  }

  /** 全局进度 = 阶段位置；章节类任务由调用方用 阶段+节点 线性叠加覆盖 */
  progress(): number {
    if (this.total <= 1) return 1;
    return Math.min(1, this.cursor / (this.total - 1));
  }

  moveTo(phase: string): string {
    const index = this.phases.indexOf(phase);
    if (index < 0) {
      throw new AppError(`${this.taskType} 任务不支持阶段 ${phase}`, { category: 'validation' });
    }
    if (index < this.cursor) {
      throw new AppError(`不允许从 ${this.current} 回退到 ${phase}，请走 rollback 接口`, {
        category: 'conflict',
        status: 409,
      });
    }
    this.cursor = index;
    return this.current;
  }

  advance(): string {
    if (this.cursor < this.phases.length - 1) this.cursor += 1;
    return this.current;
  }

  resetTo(phase: string): string {
    const index = this.phases.indexOf(phase);
    if (index < 0) throw new AppError(`未知阶段 ${phase}`, { category: 'validation' });
    this.cursor = index;
    return this.current;
  }
}
