/* 时间/日期展示与表单转换（电子证照模块统一口径）
 * 背景（实测后端）：平台 Jackson 配置把 **LocalDateTime 序列化为 epoch 毫秒**（无 @JsonFormat），
 * 直接插值会渲染出 1757654321000 这类数字；LocalDate 则仍是 'yyyy-MM-dd' 字符串。
 * 因此：所有时间列必须过 fmtDateTime/fmtDate；所有 LocalDateTime 入参必须发毫秒（dateTimeToMs）。 */
import { dateTimeToMs, msToDateTime, fmtDate as sharedFmtDate, fmtDateTime as sharedFmtDateTime } from '#/utils/datetime';

type TimeLike = null | number | string | undefined;

/* 2026-09-14：解析与格式化改为**委托 utils/datetime**（此前本文件自带一份 dayjs 解析，是全域 4 套实现之一）。
 * 保留本域导出名（fmtDateTime/fmtDate/msToDateTime/dateTimeToMs），避免 6 个页面改导入。 */

/** LocalDateTime（毫秒或字符串）→ 'YYYY-MM-DD HH:mm:ss' */
export const fmtDateTime = (v: TimeLike, fallback = '—') => sharedFmtDateTime(v, fallback);

/** LocalDate / LocalDateTime → 'YYYY-MM-DD' */
export const fmtDate = (v: TimeLike, fallback = '—') => sharedFmtDate(v, fallback);

/** 表单回显：毫秒 → 'YYYY-MM-DD HH:mm:ss'（a-date-picker value-format 用） */
export { dateTimeToMs, msToDateTime };
