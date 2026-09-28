/* 电子证照域展示派生（列表页 certificate/index.vue 与详情页 certificate/detail/index.vue 共用一份）
 *
 * 为什么单独成文件：详情从抽屉改为独立页后，有效期文案 / 编号掩码 / 状态圆点色会同时被两个页面用到，
 * 复制两份必然漂移（本仓既有口径：同一能力只留一处实现）。
 *
 * 口径（契约 §2.1④）：前端**不得**拿 validTo 与本地时间比较派生"临期/已过期"——
 * 该结论由后端按 cert.expiring.window.days / cert.alert.legal.floor.days 计算后写入 certStatus，
 * 前端只做"字典 → 展示"的映射。
 */
import type { CertificateApi } from '#/api/bidding/certificate';

import { fmtDate } from './format';

/** 状态圆点兜底色：沿用主题令牌，深浅色自适应（原为固定十六进制，深色下深蓝几乎看不见） */
export const DOT_FALLBACK = 'hsl(var(--muted-foreground))';

/** 字典 tagType/color → 主题令牌（不自造颜色语义） */
export const TAG_HEX: Record<string, string> = {
  blue: 'hsl(var(--primary))',
  default: DOT_FALLBACK,
  error: 'hsl(var(--destructive))',
  green: 'var(--ind-ok, hsl(var(--success)))',
  orange: 'var(--ind-warn, hsl(var(--warning)))',
  red: 'hsl(var(--destructive))',
};

/** 标签色 → 圆点色 */
export function dotColor(tagColor?: string): string {
  return TAG_HEX[tagColor ?? 'default'] ?? DOT_FALLBACK;
}

/** 有效期区间文案；无起止即未知 */
export function validityText(r: Pick<CertificateApi.Certificate, 'validFrom' | 'validTo'>): string {
  if (!r.validFrom && !r.validTo) return '—';
  return `${fmtDate(r.validFrom)} ~ ${r.validTo ? fmtDate(r.validTo) : '长期'}`;
}

/** 长期有效：仅依据后端字段是否为空，不做日期推算 */
export function isLongTerm(r: Pick<CertificateApi.Certificate, 'validTo'>): boolean {
  return !r.validTo;
}

/** 证书编号掩码（列表默认掩码；详情页由开关控制） */
export function maskCertNo(no?: string, mask = true): string {
  if (!no) return '—';
  if (!mask || no.length <= 8) return no;
  return `${no.slice(0, 4)}${'*'.repeat(Math.min(6, no.length - 8))}${no.slice(-4)}`;
}
