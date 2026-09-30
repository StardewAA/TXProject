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


/**
 * 错误 → 可读文本（采集域统一取后端 msg）。
 * 背景：请求器在业务 code 非 0 时抛普通对象（Object.assign({}, response)），
 * 旧写法 e instanceof Error ? e.message : String(e) 会显示 "[object Object]"
 * （见 task-chain 段6 产出定位缺陷修复）。此处递归取 msg，兜底 JSON 截断，绝不回显对象字面量。
 */
export function crawlErrMsg(error: unknown): string {
  const pick = (v: unknown, depth: number): string => {
    if (v == null) return '';
    if (typeof v === 'string') return v;
    if (typeof v === 'number' || typeof v === 'boolean') return String(v);
    if (v instanceof Error) return v.message || '';
    if (depth > 3) return '';
    if (typeof v === 'object') {
      const o = v as Record<string, unknown>;
      for (const k of ['msg', 'message', 'errorMsg', 'errorMessage']) {
        const s = pick(o[k], depth + 1);
        if (s) return s;
      }
      if (o.response && typeof o.response === 'object') {
        const s = pick((o.response as Record<string, unknown>).data, depth + 1);
        if (s) return s;
      }
      if (o.data && typeof o.data === 'object') {
        const s = pick((o.data as Record<string, unknown>).msg, depth + 1);
        if (s) return s;
      }
      try {
        const j = JSON.stringify(v);
        return j && j.length <= 120 ? j : j ? j.slice(0, 117) + '…' : '';
      } catch {
        return '';
      }
    }
    return String(v);
  };
  const s = pick(error, 0);
  return s || '接口异常';
}

/**
 * 入口/栏目 URL 配置项（entry_urls_json 数组元素）。
 * 兼容两种源形态：
 *  · 对象数组（主流）：{ name, parser, listUrl 或 siteId/categoryId 等平台参数, maxPages, detailParser, lookbackDays, fullLookbackDays }
 *  · 纯字符串数组（存量）：["/zhaobiao/gglist?p=1"]
 * 附加字段（如 siteId/categoryId/pageSize）原样保留，不做裁剪。
 */
export interface EntryUrlItem {
  name?: string;
  parser?: string;
  url?: string;
  listUrl?: string;
  maxPages?: number;
  detailParser?: string;
  lookbackDays?: number;
  fullLookbackDays?: number;
  [k: string]: unknown;
}

/** entry_urls_json → 栏目对象数组（字符串元素转 {url}；解析失败返回 []） */
export function parseEntryUrls(raw?: string): EntryUrlItem[] {
  if (!raw) return [];
  try {
    const v = JSON.parse(raw);
    if (!Array.isArray(v)) return [];
    return v.map((x) => {
      if (typeof x === 'string') return { url: x } as EntryUrlItem;
      if (x && typeof x === 'object') return x as EntryUrlItem;
      return {};
    });
  } catch {
    return [];
  }
}

/** 栏目对象 → 入口 URL（listUrl 优先，兼容旧 url 字段） */
export function entryUrlOf(item: EntryUrlItem): string {
  return String(item.listUrl ?? item.url ?? '');
}

/** 栏目对象数组 → entry_urls_json（空数组返回空串=留空从首页发现；对象原样序列化） */
export function formatEntryUrls(items: EntryUrlItem[]): string {
  if (!items || items.length === 0) return '';
  return JSON.stringify(items);
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