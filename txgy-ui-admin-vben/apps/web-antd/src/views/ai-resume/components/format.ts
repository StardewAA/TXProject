/**
 * AI 简历工作区里几个组件共用的小格式化。
 */

/**
 * 把后端的时间字段转成可展示的文本。
 *
 * 不能直接 `String(value).slice(...)`：LocalDateTime 在不同序列化配置下会变成
 * `[2026, 9, 27, 21, 36, 15]` 这样的数组，数组没有 slice 之外的问题但也没有
 * 可用的字符串形态，直接切会抛 `slice is not a function` 把整个列表渲染打断。
 *
 * @param value 接口返回的原始值
 * @param withTime 是否带上时分；列表卡片只到分钟，够用
 */
const pad = (input: number) => String(input ?? 0).padStart(2, '0');

function fromDate(date: Date, withTime: boolean): string {
  const day = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  return withTime
    ? `${day} ${pad(date.getHours())}:${pad(date.getMinutes())}`
    : day;
}

export function formatDateTime(value: unknown, withTime = true): string {
  if (value === null || value === undefined || value === '') return '';

  if (typeof value === 'string') {
    return withTime ? value.slice(0, 16).replace('T', ' ') : value.slice(0, 10);
  }

  // 另一个常见形态：epoch 毫秒。直接 String() 会变成 1789433071000 这种数字串，
  // 列表上看着像个错误编号而不是时间。
  if (typeof value === 'number') {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? String(value) : fromDate(date, withTime);
  }

  if (Array.isArray(value)) {
    const [year, month, day, hour, minute] = value as number[];
    if (!year) return '';
    const date = `${year}-${pad(month)}-${pad(day)}`;
    return withTime ? `${date} ${pad(hour)}:${pad(minute)}` : date;
  }

  if (value instanceof Date) {
    return fromDate(value, withTime);
  }

  return String(value);
}
