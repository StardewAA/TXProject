import path from 'node:path';
import fs from 'node:fs/promises';
import type { AppConfig } from '../config.js';
import { ensureDir } from '../lib/fsx.js';
import type { Logger } from '../logger.js';
import { createBackend } from './providers/piSdk.js';
import type { ChatMessage, SessionBackend } from './types.js';

/**
 * 会话工厂（对应易标 piSessionFactory.cjs 的服务端改造版）：
 * - provider 指向私有化 vLLM / TEI 网关
 * - 会话落 sessions/{taskId}/transcript.jsonl，格式自定义，是断点续跑与回退重跑的基础
 * - 会话目录必须挂在持久化卷上，多副本时按 taskId 做会话亲和
 */
export class AgentSession {
  readonly taskId: string;

  readonly dir: string;

  readonly transcriptFile: string;

  private readonly logger: Logger;

  private readonly backend: SessionBackend;

  private messages: ChatMessage[] = [];

  private constructor(taskId: string, sessionsDir: string, logger: Logger, backend: SessionBackend) {
    this.taskId = taskId;
    this.dir = path.join(sessionsDir, taskId);
    this.transcriptFile = path.join(this.dir, 'transcript.jsonl');
    this.logger = logger;
    this.backend = backend;
  }

  static async open(options: {
    taskId: string;
    sessionsDir: string;
    config: AppConfig;
    logger: Logger;
    backend?: SessionBackend;
  }): Promise<AgentSession> {
    const dir = path.join(options.sessionsDir, options.taskId);
    await ensureDir(dir);
    const session = new AgentSession(
      options.taskId,
      options.sessionsDir,
      options.logger,
      options.backend ?? createBackend(options.config, options.logger),
    );
    await session.load();
    return session;
  }

  get backendName(): string {
    return this.backend.name;
  }

  async chat(request: Parameters<SessionBackend['chat']>[0]): Promise<Awaited<ReturnType<SessionBackend['chat']>>> {
    return this.backend.chat(request);
  }

  private async load(): Promise<void> {
    try {
      const text = await fs.readFile(this.transcriptFile, 'utf8');
      const messages: ChatMessage[] = [];
      for (const line of text.split(/\r?\n/)) {
        if (!line.trim()) continue;
        try {
          messages.push(JSON.parse(line) as ChatMessage);
        } catch {
          // 容忍单行损坏，跳过即可
        }
      }
      this.messages = messages;
      if (messages.length > 0) {
        this.logger.info('会话断点恢复', { taskId: this.taskId, turns: messages.length });
      }
    } catch {
      this.messages = [];
    }
  }

  getMessages(): ChatMessage[] {
    return this.messages;
  }

  /**
   * 断点续跑用（A2）：取最近若干条可安全回灌的历史消息。
   * - 跳过 system 消息（system 由当次任务按当前项目上下文重新生成）
   * - 去掉开头孤立的 tool 消息（其配对的 assistant tool_calls 已被裁掉时，回灌会被模型接口拒绝）
   * - 同时受条数与字符数上限约束，避免历史无限膨胀
   */
  historyForResume(maxMessages: number, maxChars: number): ChatMessage[] {
    if (maxMessages <= 0 || maxChars <= 0) return [];
    const picked: ChatMessage[] = [];
    let chars = 0;
    for (let i = this.messages.length - 1; i >= 0 && picked.length < maxMessages; i -= 1) {
      const message = this.messages[i];
      if (!message || message.role === 'system') continue;
      const size = (message.content ?? '').length + JSON.stringify(message.toolCalls ?? []).length;
      if (chars + size > maxChars) break;
      chars += size;
      picked.unshift(message);
    }
    // 裁掉开头无法配对的 tool 消息
    while (picked.length > 0 && picked[0]?.role === 'tool') picked.shift();
    // 结尾若是"只调工具、没有工具结果"的 assistant，也一并裁掉，避免悬空 tool_calls
    while (picked.length > 0) {
      const last = picked[picked.length - 1];
      if (last?.role === 'assistant' && (last.toolCalls?.length ?? 0) > 0) picked.pop();
      else break;
    }
    return picked;
  }

  async append(message: ChatMessage): Promise<void> {
    this.messages.push(message);
    await fs.appendFile(this.transcriptFile, `${JSON.stringify(message)}\n`, 'utf8');
  }

  async appendMany(messages: ChatMessage[]): Promise<void> {
    if (messages.length === 0) return;
    this.messages.push(...messages);
    await fs.appendFile(
      this.transcriptFile,
      messages.map((message) => `${JSON.stringify(message)}\n`).join(''),
      'utf8',
    );
  }

  /** 回退重跑：keepSession=false 时清空会话，让模型重新推理 */
  async reset(keepSession: boolean): Promise<void> {
    if (!keepSession) {
      this.messages = [];
      await fs.writeFile(this.transcriptFile, '', 'utf8');
    }
  }
}
