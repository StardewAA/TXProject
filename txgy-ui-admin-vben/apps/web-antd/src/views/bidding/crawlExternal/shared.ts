import { fmtDateTime } from '#/utils/datetime';

/** 采集域时间展示：委托 utils/datetime 的唯一实现（口径 'YYYY-MM-DD HH:mm:ss' / 空值 '—'） */
export const fmtDt = (v?: null | number | string): string => fmtDateTime(v, '—');

export type TagOf = { color: string; text: string; };

/** 采集任务状态 */
export const TASK_STATUS_MAP: Record<string, TagOf> = {
  PENDING: { text: '排队中', color: '#8c8c8c' },
  RUNNING: { text: '执行中', color: '#1677ff' },
  SUCCESS: { text: '成功', color: '#52c41a' },
  FAILED: { text: '失败(死信)', color: '#ff4d4f' },
  SKIPPED: { text: '跳过', color: '#d46b08' },
};

/** 采集批次状态 */
export const JOB_STATUS_MAP: Record<string, TagOf> = {
  RUNNING: { text: '执行中', color: '#1677ff' },
  SUCCESS: { text: '成功', color: '#52c41a' },
  PARTIAL: { text: '部分失败', color: '#d46b08' },
  FAILED: { text: '失败', color: '#ff4d4f' },
};

/** 批次触发类型 */
export const TRIGGER_MAP: Record<string, TagOf> = {
  SCHEDULE: { text: '定时', color: '#13c2c2' },
  MANUAL: { text: '手动', color: '#2f54eb' },
  REPAIR: { text: '修复补采', color: '#722ed1' },
};

/** 任务状态筛选项 */
export const TASK_STATUS_OPTIONS = Object.keys(TASK_STATUS_MAP).map((k) => ({ label: TASK_STATUS_MAP[k]!.text, value: k }));
/** 批次状态筛选项 */
export const JOB_STATUS_OPTIONS = Object.keys(JOB_STATUS_MAP).map((k) => ({ label: JOB_STATUS_MAP[k]!.text, value: k }));
/** 触发类型筛选项 */
export const TRIGGER_OPTIONS = Object.keys(TRIGGER_MAP).map((k) => ({ label: TRIGGER_MAP[k]!.text, value: k }));

/** 标签取色（undefined → null 显示 —） */
export function tagOf(map: Record<string, TagOf>, v?: string): null | TagOf {
  const c = v ? map[v] : undefined;
  return c ? { text: c.text, color: c.color } : null;
}

/** 批次汇总 JSON → 总/成/败 摘要（解析失败回原文截断） */
export function jobSummaryText(summaryJson?: string): string {
  if (!summaryJson) return '—';
  try {
    const s = JSON.parse(summaryJson) as Record<string, number>;
    const parts: string[] = [];
    if (typeof s.total === 'number') parts.push('总 ' + s.total);
    if (typeof s.success === 'number') parts.push('成 ' + s.success);
    if (typeof s.failed === 'number') parts.push('败 ' + s.failed);
    if (typeof s.manual === 'number') parts.push('转人工 ' + s.manual);
    return parts.length ? parts.join(' / ') : summaryJson;
  } catch {
    return summaryJson.length > 60 ? summaryJson.slice(0, 60) + '…' : summaryJson;
  }
}