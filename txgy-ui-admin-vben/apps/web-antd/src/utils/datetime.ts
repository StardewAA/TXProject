/**
 * 招投标全域**时间展示统一实现**（唯一解析口径）。
 *
 * 为什么要有这个文件：平台 Jackson 把 LocalDateTime 序列化为 **epoch 毫秒**（无 @JsonFormat），
 * LocalDate 仍是 'yyyy-MM-dd' 字符串；而时间字段在出参里可能是
 * `number(epoch 毫秒)` / `'1757654321000'(13 位串)` / `'2026-09-14 14:03:02'` / ISO 串 四种形态。
 * 2026-09-14 实测：前端有 **4 套并行实现**（crawlExternal/shared.ts:fmtDt、certificate/format.ts:fmtDateTime、
 * portal/bidding/format.ts:fmtDT、noticeExternal/data.ts:fmtT 与 NoticeWorkbench.vue 内的同名 fmtT），
 * 解析逻辑各写一遍、空值口径还不一样 —— 于是**同一个后端字段在后台显示 '—'、在门户显示空白**，
 * 且 personnel 页把 verifiedAt 直接插值（页面出现 1757654321000 这种数字）。
 *
 * 统一后的分工：**解析只有这一份**（toDate），显示保留三个有据可查的预设（见下），差异必须写在这里、不许各页自造：
 *   · fmtDateTime        'YYYY-MM-DD HH:mm:ss'，空值 '—'  → 后台台账口径（要秒、要"确实没有"的显式占位）
 *   · fmtDateTimeMinutes 'YYYY-MM-DD HH:mm'，   空值 '—'  → 后台列表的紧凑口径（可选）
 *   · fmtDate            'YYYY-MM-DD'，         空值 '—'  → 日期型字段（有效期、生效日）
 * 门户面向公众的页面继续用**空串**占位（留白比"—"干净）：传 fallback='' 即可，不要另写实现。
 */
import dayjs from 'dayjs';

export type TimeLike = null | number | string | undefined;

/** 后端下发"无值"的几种历史形态（门户 cleanT 实测：字符串 "NULL"/"null"/"N/A"/"-"） */
const NULLISH = /^(?:NULL|null|N\/A|-)$/;

/**
 * 唯一解析入口：兼容 epoch 毫秒 / 13 位数字串 / 'yyyy-MM-dd HH:mm(:ss)' / ISO；非法返回 null。
 * 两条来自实际数据的规则（**上提到这里，避免各域重写**）：
 *   · **0 / 负值 = 未填**——演示库存在 publishTime=0，若按 1970-01-01 渲染就是假数据（门户 format.ts 实测发现）；
 *   · 字符串 "NULL"/"null"/"N/A"/"-" = 无值（历史脏数据）。
 */
export function toDate(v: TimeLike): Date | null {
  if (v === null || v === undefined || v === '') return null;
  if (typeof v === 'number') return v <= 0 ? null : (dayjs(v).isValid() ? dayjs(v).toDate() : null);
  const s = v.trim();
  if (NULLISH.test(s)) return null;
  const raw = /^\d+$/.test(s) ? Number(s) : (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}(:\d{2})?$/.test(s) ? s.replace(' ', 'T') : s);
  if (typeof raw === 'number' && raw <= 0) return null;
  const d = dayjs(raw as number | string);
  return d.isValid() ? d.toDate() : null;
}

/** 'YYYY-MM-DD HH:mm:ss'（后台台账口径；非法值原样回显，便于发现脏数据而不是显示成 '—' 掩盖） */
export function fmtDateTime(v: TimeLike, fallback = '—'): string {
  const d = toDate(v);
  if (!d) return v === null || v === undefined || v === '' ? fallback : String(v).slice(0, 19).replace('T', ' ');
  return dayjs(d).format('YYYY-MM-DD HH:mm:ss');
}

/** 'YYYY-MM-DD HH:mm'（紧凑口径；门户用它并配 fallback='' 留白） */
export function fmtDateTimeMinutes(v: TimeLike, fallback = '—'): string {
  const d = toDate(v);
  if (!d) return v === null || v === undefined || v === '' ? fallback : String(v).slice(0, 19).replace('T', ' ');
  return dayjs(d).format('YYYY-MM-DD HH:mm');
}

/** 'YYYY-MM-DD'（日期型字段；LocalDate 串与 LocalDateTime 毫秒都吃） */
export function fmtDate(v: TimeLike, fallback = '—'): string {
  const d = toDate(v);
  if (!d) return v === null || v === undefined || v === '' ? fallback : String(v).slice(0, 10);
  return dayjs(d).format('YYYY-MM-DD');
}

/** 表单回显：epoch 毫秒 → 'YYYY-MM-DD HH:mm:ss'（配 a-date-picker 的 value-format） */
export function msToDateTime(v: TimeLike): string | undefined {
  const d = toDate(v);
  return d ? dayjs(d).format('YYYY-MM-DD HH:mm:ss') : undefined;
}

/** 表单提交：'YYYY-MM-DD HH:mm:ss' → epoch 毫秒（后端 LocalDateTime 入参要求） */
export function dateTimeToMs(v: TimeLike): number | undefined {
  const d = toDate(v);
  return d ? d.valueOf() : undefined;
}
