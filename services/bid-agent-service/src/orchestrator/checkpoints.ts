import fs from 'node:fs/promises';
import path from 'node:path';
import { AppError } from '../errors.js';
import { ensureDir, writeJsonAtomic } from '../lib/fsx.js';
import type { TaskType } from './types.js';
import { TASK_PHASES } from './stateMachine.js';

export interface CheckpointRecord {
  seq: number;
  taskId: string;
  type: TaskType;
  phase: string;
  nodeId?: string;
  snapshot: Record<string, unknown>;
  savedAt: string;
}

/** 检查点：每完成一个阶段写一次，是断点续跑与回退重跑的依据 */
export class CheckpointStore {
  private readonly file: string;

  private records: CheckpointRecord[] = [];

  constructor(sessionsDir: string, taskId: string) {
    this.file = path.join(sessionsDir, taskId, 'checkpoints.json');
  }

  async load(): Promise<void> {
    try {
      const text = await fs.readFile(this.file, 'utf8');
      const parsed = JSON.parse(text);
      this.records = Array.isArray(parsed) ? (parsed as CheckpointRecord[]) : [];
    } catch {
      this.records = [];
    }
  }

  private async persist(): Promise<void> {
    await ensureDir(path.dirname(this.file));
    await writeJsonAtomic(this.file, this.records);
  }

  all(): readonly CheckpointRecord[] {
    return this.records;
  }

  latest(): CheckpointRecord | undefined {
    return this.records[this.records.length - 1];
  }

  latestForPhase(phase: string): CheckpointRecord | undefined {
    for (let i = this.records.length - 1; i >= 0; i -= 1) {
      const record = this.records[i];
      if (record?.phase === phase) return record;
    }
    return undefined;
  }

  async save(input: {
    taskId: string;
    type: TaskType;
    phase: string;
    nodeId?: string;
    snapshot: Record<string, unknown>;
  }): Promise<CheckpointRecord> {
    const record: CheckpointRecord = {
      seq: this.records.length + 1,
      taskId: input.taskId,
      type: input.type,
      phase: input.phase,
      nodeId: input.nodeId,
      snapshot: input.snapshot,
      savedAt: new Date().toISOString(),
    };
    this.records.push(record);
    await this.persist();
    return record;
  }

  /**
   * 回退：丢弃该阶段之后的所有检查点，返回该阶段最后一个快照。
   * 取"最后一个匹配项"而非第一个：draft 这类任务同一阶段会因多个章节重复落点（A4）。
   * 传入 nodeId 时按节点定位，用于"只重跑某一章"。
   */
  async truncateAfter(phase: string, nodeId?: string): Promise<CheckpointRecord | undefined> {
    let index = -1;
    for (let i = this.records.length - 1; i >= 0; i -= 1) {
      const record = this.records[i];
      if (!record || record.phase !== phase) continue;
      if (nodeId && record.nodeId !== nodeId) continue;
      index = i;
      break;
    }
    if (index < 0) return undefined;
    const kept = this.records.slice(0, index + 1);
    this.records = kept;
    await this.persist();
    return kept[kept.length - 1];
  }

  /** 已提交（COMMIT 阶段落检查点）的章节节点：draft 断点续跑据此跳过已完成章节（A4） */
  committedNodeIds(): Set<string> {
    const ids = new Set<string>();
    for (const record of this.records) {
      if (record.phase === 'COMMIT' && record.nodeId) ids.add(record.nodeId);
    }
    return ids;
  }

  /** 从指定阶段恢复运行时状态：阶段 + 该阶段最后一个快照 */
  async resumeState(type: TaskType): Promise<{ phase: string; snapshot: Record<string, unknown> } | undefined> {
    await this.load();
    const last = this.latest();
    if (!last) return undefined;
    const phases = TASK_PHASES[type];
    // 停留在最后一个已完成阶段的下一个阶段继续执行
    const lastIndex = phases.indexOf(last.phase);
    const nextPhase = lastIndex >= 0 && lastIndex < phases.length - 1 ? phases[lastIndex + 1] : last.phase;
    return { phase: nextPhase, snapshot: last.snapshot };
  }

  async assertKnownPhase(type: TaskType, phase: string): Promise<void> {
    if (!TASK_PHASES[type].includes(phase)) {
      throw new AppError(`${type} 任务不支持回退到阶段 ${phase}`, { category: 'validation', status: 400 });
    }
  }
}
