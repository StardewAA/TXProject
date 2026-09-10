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
