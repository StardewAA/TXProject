/**
 * 工作区会话的本地留存。
 *
 * 解决问题：刷新一下页面就把整轮 AI 任务重跑一遍、把提示重发一遍。
 * 用户只是刷新，代价却是等 20-40 秒、再烧一次模型调用，对话还会被冲掉重来。
 *
 * 存在 localStorage 而不是后端：这些内容只对当前这个浏览器有意义
 * （草稿、对话、待确认的建议），后端已经有正式的个人简历版本快照，
 * 为它单开一张表不值得。
 *
 * 读失败一律当成「没有留存」：隐私模式下 localStorage 会直接抛异常，
 * 但不能因为留不下会话就让工作区打不开。
 */

const PREFIX = 'ai-resume-ws';

/** 快照结构变了就升版本号，老数据自然失配失效，不用写迁移 */
const VERSION = 1;

interface Envelope {
  data: unknown;
  savedAt: number;
  v: number;
}

/**
 * 会话键。
 *
 * 按「入口 + 简历」分开存：同一份简历在诊断入口和优化入口是两条独立的对话，
 * 在诊断入口换一份简历也该是另一条，不能串。
 */
export function sessionKey(mode: string, resumeKey: string) {
  return `${PREFIX}:${VERSION}:${mode}:${resumeKey}`;
}

/** 读出留存；没有、版本不符、内容损坏都返回 undefined */
export function loadSession<T>(key: string): T | undefined {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return undefined;
    const parsed = JSON.parse(raw) as Envelope;
    if (!parsed || parsed.v !== VERSION) return undefined;
    return parsed.data as T;
  } catch {
    return undefined;
  }
}

/** 写入留存；配额满或存储被禁用时静默放弃，不能拖累编辑本身 */
export function saveSession(key: string, data: unknown) {
  try {
    const envelope: Envelope = { data, savedAt: Date.now(), v: VERSION };
    localStorage.setItem(key, JSON.stringify(envelope));
  } catch {
    /* 存不下就算了 */
  }
}

export function clearSession(key: string) {
  try {
    localStorage.removeItem(key);
  } catch {
    /* 忽略 */
  }
}
