/**
 * 招投标全域**数值展示统一实现**（文件大小 / 百分比）。
 *
 * 起因（用户 2026-09-14"数据胡乱展示"，与 utils/datetime.ts 同一类问题）：
 * 实测**文件大小格式化有 8 处、5 种口径**，同一个附件在不同页面显示不一样：
 *   · cert-files.vue / NoticeWorkbench.vue / NoticeHtmlView.vue：B / KB(1 位) / **MB(2 位)**
 *   · crawlExternal/console/modules/task-chain.vue：B / KB(1 位) / **MB(1 位)**   ← 与上面不同
 *   · vo/AttachmentList.vue / vo/AttachmentsPanel.vue：**只有 KB(0 位)**，1.5MB 的附件显示成 "1536 KB"
 *   · noticeExternal/modules/detail.vue：**Math.round(size/1024) + ' KB'**（又一种）
 *   · portal/bidding/detail.vue：B / KB(0 位, Math.round) / **MB(1 位)**（门户又一套）
 * 于是"这个 PDF 到底多大"在不同页面读数不一致。
 *
 * 统一口径（**选 MB 2 位**的理由：8 处里 3 处已是该口径且最精确；KB 整数会让 1.5MB 显示成 1536 KB，
 * 可读性反而差；门户/采集域改为跟随，读数在全域一致）：
 *   · < 1024 B        → 整数 B
 *   · < 1024 KB       → 1 位小数 KB
 *   · < 1024 MB       → 2 位小数 MB
 *   · 其余            → 2 位小数 GB
 * 空值/负数默认 '—'；需要专门文案时传 fallback（如 PDF 面板的"大小未知"，因为它要解释原因）。
 */

/** 文件大小：字节数 → 'B/KB/MB/GB'（口径见文件头；非数字或负值返回 fallback） */
export function fmtBytes(v: null | number | string | undefined, fallback = '—'): string {
  if (v === null || v === undefined || v === '') return fallback;
  const n = typeof v === 'number' ? v : Number(v);
  if (!Number.isFinite(n) || n < 0) return fallback;
  if (n < 1024) return `${Math.round(n)} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(2)} MB`;
  return `${(n / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

/**
 * 比率（0~1）→ 百分比文案。后端 AI 置信度/匹配度一律下发比率，各页自己 `* 100` 容易漏乘或重复乘。
 * @param ratio 0~1 的比率（也容忍 0~100 的已百分数：>1 时按已百分数处理，避免"1.2%"这种错读数）
 */
export function fmtRatioPercent(ratio: null | number | string | undefined, digits = 0, fallback = '—'): string {
  if (ratio === null || ratio === undefined || ratio === '') return fallback;
  const n = typeof ratio === 'number' ? ratio : Number(ratio);
  if (!Number.isFinite(n)) return fallback;
  const pct = n > 1 ? n : n * 100;
  return `${pct.toFixed(digits)}%`;
}

/** 已是百分数（0~100）→ 百分比文案（a-progress 的 percent 之类由组件自己渲染，勿套用） */
export function fmtPercentValue(value: null | number | string | undefined, digits = 0, fallback = '—'): string {
  if (value === null || value === undefined || value === '') return fallback;
  const n = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(n) ? `${n.toFixed(digits)}%` : fallback;
}
