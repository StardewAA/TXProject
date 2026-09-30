/* VO 字段「业务规则」校验与归一化（唯一真源：voSpec.gen.ts 的 dtype 列 + 规范备注）。
 * 用途：项目主档（MainCardTable 行内编辑）保存前逐字段校验——不符合字段要求的字段不允许保存，并给出中文明示。
 *
 * 规则映射（dtype → 校验）：
 *   · C{n}            定长字符：长度必须 === n（如 C6 行政区划、C17 项目编号、C24 投资项目统一代码）
 *   · C..{n}          变长字符：长度 ≤ n
 *   · C..ul           自由文本：不限长度
 *   · N{n}            非负整数：位数 ≤ n（如 N1 标段招标次数）
 *   · N..{n},{d}      非负数字：小数位 ≤ d、整数位 ≤ n−d（如 N..20,2 → 整数 ≤18 位）
 *   · yyyyMMddHHmmss  时间：主档可编辑字段中无此类型（建立时间为系统生成），不校验
 *   · ''（空 dtype）   无规则（附件采集项等）
 *
 * 两处刻意的边界（改前先看这里）：
 *   1) 比例类字段（中文名含「比例」**且** dtype 为数值 N..）不按 dtype 判，改按比例语义判——见 isRatioField/parseRatio。
 *      口径（用户 2026-09-18 定稿）：`50%`、`0.5`、`50` 都合法，统一换算为百分数值提交（0.5 → 50、50% → 50）。
 *      分界点取 1：不带百分号且 ≤1 视为小数（0.5=50%），>1 视为百分数（50=50%）。
 *      自由文本的「出资比例」C..1000 不在比例校验内（见 isRatioField 注释）。
 *   2) 下拉框字段（codeTables.ts 有登记）不做长度/格式校验——选项集本身已约束取值。
 *      原因：规范 dtype 与选项集存在不一致（如 BID_SECTION_TYPE 为 C2 定长 2 位，但选项值是 1 位代码），
 *      若对下拉框强套定长会直接导致页面无法保存。下拉框取值由其 options 保证。 */

import { voRequiredCodes, type VoField } from './voSpec.gen';

export interface DtypeRule {
  kind: 'char' | 'none' | 'num' | 'time';
  /** 定长字符长度（C{n}） */
  exact?: number;
  /** 最长字符数（C..{n}）或数值最大精度（N..{n},{d} 的 n） */
  max?: number;
  /** 小数位（N..{n},{d} 的 d；N{n} 为 0） */
  scale?: number;
}

/** 解析 VO 规范「数据类型及格式」列。无法识别的写法按「无规则」处理，不误拦。 */
export function parseDtype(dtype?: null | string): DtypeRule {
  const t = (dtype ?? '').trim();
  if (!t) return { kind: 'none' };
  if (/yyyyMMddHHmmss/i.test(t)) return { kind: 'time' };
  if (/^C\.\.ul$/i.test(t)) return { kind: 'char' };
  let m = /^C\.\.(\d+)$/.exec(t);
  if (m) return { kind: 'char', max: Number(m[1]) };
  m = /^C(\d+)$/.exec(t);
  if (m) return { kind: 'char', exact: Number(m[1]) };
  m = /^N(?:\.\.)?(\d+)(?:,(\d+))?$/.exec(t);
  if (m) return { kind: 'num', max: Number(m[1]), scale: m[2] ? Number(m[2]) : 0 };
  return { kind: 'none' };
}

/* dtype 之外的字符集规则：仅收「规范备注里显式点名国家标准」的字段，便于日后增删。
 * 注意：这里不放长度规则（长度一律由 dtype 决定），只补字符集。
 * hint = 输入框内的格式提示（简短、给示例）；msg = 校验失败时的报错（说明依据）。 */
const CHARSET_RULES: Record<string, { hint: string; msg: string; re: RegExp }> = {
  REGION_CODE: { re: /^\d{6}$/, hint: '6 位数字，如 610113', msg: '须为 6 位数字（GB/T 2260-2007 行政区划代码）' },
  TRA_PLACE_CODE: { re: /^\d{6}$/, hint: '6 位数字，如 610113', msg: '须为 6 位数字（GB/T 2260-2007 行政区划代码）' },
  INDUSTRIES_TYPE: { re: /^[A-Z]\d{2}$/, hint: '如 A01（字母 + 2 位数字）', msg: '须为 1 位行业门类字母 + 2 位大类数字（GB/T 4754-2017），如 A01' },
};

/** 约定提示（dtype 推导不出来、需逐字段维护的自由文本字段） */
const CONVENTION_HINTS: Record<string, string> = {
  // 出资比例：规范 dtype 为 C..1000 自由文本，允许写多个出资方，故用示例说明写法
  CONTRIBUTION_SCALE: '如：国有资金 60%；银行贷款 40%',
  // 投资项目统一代码：提示来源平台（《后端变更请求-编号体系归位》§三 已要求前端在 placeholder 注明来源）
  INVEST_PROJECT_CODE: '24 位，来自投资项目在线审批监管平台',
};

/** 业务必填覆盖表——规范「必填」列标否、但业务上必填的字段（按规范码）。
 *  取「后端已强制 + 用户已裁定」的口径，**改前先回契约核对**，不要凭印象增删。
 *  · INVEST_PROJECT_CODE（24 位投资项目统一代码）：《后端变更请求-编号体系归位》§二/§六 ——
 *    用户裁定它是唯一需要用户输入的编号（社会公开）。
 *    草稿宽松（2026-09-27 契约29 §3.4）后：后端 save 仅对**非草稿**强制它（草稿创建豁免、
 *    立项审批通过后登记），前端新建态（MainCardTable createMode）整体豁免必填，此处覆盖表
 *    继续服务「编辑非草稿主档」场景；规范 ProjectVO 序号 5 的 required=false 只表示
 *    「是否固定资产=1 时必填」，与此处口径不同。 */
const REQUIRED_OVERRIDES: ReadonlySet<string> = new Set<string>(['INVEST_PROJECT_CODE']);

/** 比例类字段：字段中文名含「比例」**且** dtype 为数值（N..）。
 *  刻意排除自由文本的「出资比例」(CONTRIBUTION_SCALE，规范 dtype C..1000)：规范允许它填
 *  「国有资金 60%；银行贷款 40%」这类描述，强套比例格式会拒掉合法内容、违反 VO 数据规范，
 *  且归一化会把这类文本改坏。它按 dtype 走「不超 1000 字」的自由文本校验。 */
export function isRatioField(f: Pick<VoField, 'dtype' | 'label'>): boolean {
  return /比例/.test(f.label ?? '') && parseDtype(f.dtype).kind === 'num';
}

export interface RatioParse {
  ok: boolean;
  /** 归一化后的百分数值：0.5 → 50、50% → 50、50 → 50 */
  percent?: number;
  msg?: string;
}

const RATIO_HINT = '比例格式不正确：请填百分数或小数，如 50% 或 0.5';

/** 解析比例写法并归一化为百分数值（不带百分号且 ≤1 视为小数，>1 视为百分数）。 */
export function parseRatio(raw: string): RatioParse {
  const s = (raw ?? '').trim();
  const hasPercent = /[%％]$/.test(s);
  const body = hasPercent ? s.slice(0, -1).trim() : s;
  if (!/^\d+(\.\d+)?$/.test(body)) return { ok: false, msg: RATIO_HINT };
  const n = Number(body);
  if (!Number.isFinite(n)) return { ok: false, msg: RATIO_HINT };
  // 带 % 或裸数值 >1 → 已按百分数理解；裸数值 ≤1 → 按小数理解，×100 换算
  const asPercent = hasPercent || n > 1;
  const percent = asPercent ? n : n * 100;
  if (percent < 0 || percent > 100) return { ok: false, msg: '比例须在 0–100（或 0–1 的小数）之间' };
  // 小数位按「换算后的百分数」计：小数写法 ×100 相当于放大 2 位，故容差 +2
  const bodyDec = (body.split('.')[1] ?? '').length;
  const percentDec = asPercent ? bodyDec : Math.max(bodyDec - 2, 0);
  if (percentDec > 2) return { ok: false, msg: '比例小数位不能超过 2 位（如 33.33%）' };
  return { ok: true, percent: Number(percent.toFixed(2)) };
}

/** 单字段校验：返回中文错误文案；空值不在此判（必填由调用方判）。 */
export function validateField(f: VoField, raw: string): null | string {
  const s = (raw ?? '').trim();
  if (!s) return null;

  if (isRatioField(f)) {
    const r = parseRatio(s);
    return r.ok ? null : (r.msg ?? RATIO_HINT);
  }

  const cs = CHARSET_RULES[f.code];
  if (cs && !cs.re.test(s)) return cs.msg;

  const rule = parseDtype(f.dtype);
  if (rule.kind === 'char') {
    const len = [...s].length;
    if (rule.exact !== undefined && len !== rule.exact) return `须恰好 ${rule.exact} 位字符（当前 ${len} 位）`;
    if (rule.max !== undefined && len > rule.max) return `不能超过 ${rule.max} 个字符（当前 ${len} 个）`;
    return null;
  }
  if (rule.kind === 'num') {
    const scale = rule.scale ?? 0;
    if (!/^\d+(\.\d+)?$/.test(s)) {
      return scale === 0 ? '须为非负整数（不含单位、千分位、正负号）' : '须为非负数字（不含单位、千分位、正负号）';
    }
    const [intRaw = '', dec = ''] = s.split('.');
    if (scale === 0 && dec !== '') return '须为非负整数（不含小数位）';
    if (dec.length > scale) return `小数位不能超过 ${scale} 位（当前 ${dec.length} 位）`;
    const intDigits = intRaw.replace(/^0+(?=\d)/, '').length;
    const maxInt = rule.max === undefined ? undefined : Math.max(rule.max - scale, 0);
    if (maxInt !== undefined && intDigits > maxInt) return `整数位不能超过 ${maxInt} 位（超出 ${rule.max} 位精度上限）`;
    return null;
  }
  return null;
}

/** 提交值归一化：比例字段换算为百分数值，其余仅去首尾空格。 */
export function normalizeFieldValue(f: VoField | undefined, raw: string): string {
  const s = (raw ?? '').trim();
  if (!s || !f) return s;
  if (isRatioField(f)) {
    const r = parseRatio(s);
    return r.ok && r.percent !== undefined ? String(r.percent) : s;
  }
  return s;
}

/** 比例字段的显示形式：失焦时把输入统一成「百分数值 + %」（0.5 → 50%、50 → 50%、50% → 50%）。
 *  仅供界面回显用，提交仍走 normalizeFieldValue（存数值 50）。无法解析时原样返回，交给校验去报错。 */
export function ratioDisplayValue(f: VoField | undefined, raw: string): string {
  const s = (raw ?? '').trim();
  if (!s || !f || !isRatioField(f)) return s;
  const r = parseRatio(s);
  return r.ok && r.percent !== undefined ? `${r.percent}%` : s;
}

/** 字段「输入格式提示」：与 validateField 同源推导，保证提示与校验永远一致。
 *  返回空串表示该字段无格式要求（调用方回退为「请输入」）。 */
export function fieldHint(f: VoField): string {
  const conv = CONVENTION_HINTS[f.code];
  if (conv) return conv;
  if (isRatioField(f)) return '如 50% 或 0.5';
  const cs = CHARSET_RULES[f.code];
  if (cs) return cs.hint;
  const rule = parseDtype(f.dtype);
  if (rule.kind === 'char') {
    if (rule.exact !== undefined) return `恰好 ${rule.exact} 位字符`;
    if (rule.max !== undefined) return `不超过 ${rule.max} 个字符`;
    return '';
  }
  if (rule.kind === 'num') {
    const scale = rule.scale ?? 0;
    if (scale === 0) {
      const maxInt = rule.max === undefined ? undefined : Math.max(rule.max, 0);
      return maxInt === undefined ? '非负整数' : `非负整数，最多 ${maxInt} 位`;
    }
    return `数字，最多 ${scale} 位小数`;
  }
  return '';
}

/** 字段是否必填：规范「必填=是」，或落在业务必填覆盖表 REQUIRED_OVERRIDES 内。 */
export function isRequiredField(vo: string, code: string): boolean {
  return voRequiredCodes(vo).includes(code) || REQUIRED_OVERRIDES.has(code);
}
