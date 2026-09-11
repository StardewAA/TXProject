/* 时间/日期展示与表单转换（电子证照模块统一口径）
 * 背景（实测后端）：平台 Jackson 配置把 **LocalDateTime 序列化为 epoch 毫秒**（无 @JsonFormat），
 * 直接插值会渲染出 1757654321000 这类数字；LocalDate 则仍是 'yyyy-MM-dd' 字符串。
 * 因此：所有时间列必须过 fmtDateTime/fmtDate；所有 LocalDateTime 入参必须发毫秒（dateTimeToMs）。 */
import dayjs from 'dayjs';

type TimeLike = null | number | string | undefined;

function toDayjs(v: TimeLike) {
  if (v === null || v === undefined || v === '') return null;
  const val = typeof v === 'string' && /^\d+$/.test(v) ? Number(v) : v;
  const d = dayjs(val as number | string);
  return d.isValid() ? d : null;
}

/** LocalDateTime（毫秒或字符串）→ 'YYYY-MM-DD HH:mm:ss' */
export function fmtDateTime(v: TimeLike, fallback = '—'): string {
  return toDayjs(v)?.format('YYYY-MM-DD HH:mm:ss') ?? fallback;
}

/** LocalDate / LocalDateTime → 'YYYY-MM-DD' */
export function fmtDate(v: TimeLike, fallback = '—'): string {
  return toDayjs(v)?.format('YYYY-MM-DD') ?? fallback;
}

/** 表单回显：毫秒 → 'YYYY-MM-DD HH:mm:ss'（a-date-picker value-format 用） */
export function msToDateTime(v: TimeLike): string | undefined {
  return toDayjs(v)?.format('YYYY-MM-DD HH:mm:ss') ?? undefined;
}

/** 表单提交：'YYYY-MM-DD HH:mm:ss' → epoch 毫秒（后端 LocalDateTime 入参要求） */
export function dateTimeToMs(v: TimeLike): number | undefined {
  const d = toDayjs(v);
  return d ? d.valueOf() : undefined;
}
