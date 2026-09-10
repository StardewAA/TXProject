/* VO 字段 → 控件推断（依据 dtype 与规范备注），供 VO 表单统一渲染。
 * 规则来自规范"数据类型及格式"列：C1+是否=是/否代码 → 布尔；yyyyMMddHHmmss → 时间；N.. → 数字/金额；C..ul → 长文本。
 * 政务公开（pub=GOVERNMENT）字段=报送平台/监管的结构化参数，企业表单默认不收集，集中折叠展示（见 VoParamsPanel）。*/
import type { VoField } from './voSpec.gen';

export type VoControl = 'amount' | 'boolean' | 'multiline' | 'number' | 'text' | 'time' | 'url';

/** 是否 1/0 代码字段（参考 1.10.2 是否代码） */
export function isYesNoField(f: VoField): boolean {
  const t = f.dtype.trim();
  if (t !== 'C1' && t !== 'C..20') return false;
  return /是否/.test(f.label) || /是否代码/.test(f.note);
}

export function voControl(f: VoField): VoControl {
  const t = f.dtype.trim();
  if (isYesNoField(f)) return 'boolean';
  if (/yyyyMMddHHmmss/i.test(t)) return 'time';
  if (/^(N\.|N$)/.test(t)) return /比例|金额|价|费|资金|面积/i.test(f.label) ? 'amount' : 'number';
  if (/ul$|C\.\.ul/.test(t)) return 'multiline';
  if (/URL|url/.test(f.code) || /地址|网址/.test(f.label)) return 'url';
  return 'text';
}

/** 政务公开字段（报送参数：平台码/时间戳/责任人/统一交易标识码等） */
export function isGovernment(f: VoField): boolean {
  return f.pub === 'GOVERNMENT';
}

export interface VoPublicSplit {
  social: VoField[];
  government: VoField[];
}
export function splitByPublic(fields: VoField[]): VoPublicSplit {
  return {
    social: fields.filter((f) => !isGovernment(f)),
    government: fields.filter(isGovernment),
  };
}

/** 是否/否选项（规范 1.10.2 是否代码：1 是 / 0 否） */
export const YES_NO_OPTIONS = [
  { value: '1', label: '是（1）' },
  { value: '0', label: '否（0）' },
];

/** dtype 展示（规范列原文；空则显示 —） */
export function dtypeText(f: VoField): string {
  return f.dtype.trim() || '—';
}

/** 时间提交格式（后端联调口径 yyyy-MM-ddTHH:mm:ss；规范原列 yyyyMMddHHmmss，二者按后端文档取 ISO 提交） */
export function nowIsoLocal(): string {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, '0');
  return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) + 'T' + p(d.getHours()) + ':' + p(d.getMinutes()) + ':00';
}

/** 平台/系统注入字段（企业不填，由发布工具/交易平台或系统生成）
 * 重要口径纠正：VO 的「公开范围」是**披露范围**（政务公开=仅向政务平台报送、不对外公开），
 * 不是"谁填报"。因此不能按 pub 隐藏字段——像 IS_MARGIN（是否缴纳投标保证金，必填=是）、
 * DOC_TYPE（招标文件类型）、CLARIFY_NUM（澄清次数）都是企业必须填、但只报政务的字段。
 * 只有下列平台标识码/时间戳/附件关联标识号才是平台与系统维护。 */
export const PLATFORM_MAINTAINED_CODES: ReadonlySet<string> = new Set<string>([
  'UNIFIED_DEAL_CODES',
  'UNIFIED_DEAL_CODE',
  'FROM_TRADE_PLAT_CODE',
  'PLATFORM_CODE',
  'PUB_SERVICE_PLAT_CODE',
  'TRADE_PLAT',
  'PUB_SERVICE_PLAT',
  'SUBMIT_TIMESTAMP',
  'DATA_TIMESTAMP',
  'ATTACHMENT_CODE',
]);

/** 是否平台/系统注入字段（企业表单不收集；附件关联标识号由附件面板生成） */
export function isPlatformMaintained(f: VoField): boolean {
  return PLATFORM_MAINTAINED_CODES.has(f.code);
}
