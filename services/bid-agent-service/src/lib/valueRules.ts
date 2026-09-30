/** DERIVE 通道的确定性换算（§4.2 字段值规划：前两类完全不经模型） */

const CN_DIGITS = ['零', '壹', '贰', '叁', '肆', '伍', '陆', '柒', '捌', '玖'];
const CN_UNITS = ['', '拾', '佰', '仟'];
const CN_SECTIONS = ['', '万', '亿', '万亿'];

export function toChineseAmount(amountText: string): string | undefined {
  const cleaned = amountText.replace(/[,\s¥￥元]/g, '');
  if (!/^\d+(\.\d{1,2})?$/.test(cleaned)) return undefined;
  const [intPart, decPart = ''] = cleaned.split('.');
  if (intPart === undefined) return undefined;

  const intValue = BigInt(intPart);
  if (intValue === 0n && !decPart) return '零元整';

  const sections: number[] = [];
  let rest = intPart;
  while (rest.length > 0) {
    sections.unshift(Number(rest.slice(-4)));
    rest = rest.slice(0, -4);
  }

  let result = '';
  let zeroPending = false;
  for (let i = 0; i < sections.length; i += 1) {
    const section = sections[i] ?? 0;
    const sectionPos = sections.length - 1 - i;
    let sectionText = '';
    let zeroInSection = false;
    for (let pos = 3; pos >= 0; pos -= 1) {
      const digit = Math.floor(section / 10 ** pos) % 10;
      if (digit === 0) {
        zeroInSection = true;
        continue;
      }
      if (zeroPending || (zeroInSection && sectionText)) sectionText += '零';
      zeroPending = false;
      zeroInSection = false;
      sectionText += `${CN_DIGITS[digit]}${CN_UNITS[pos]}`;
    }
    if (sectionText) {
      result += `${sectionText}${CN_SECTIONS[sectionPos] ?? ''}`;
      zeroPending = false;
    } else if (result) {
      zeroPending = true;
    }
  }

  const integer = result ? `${result}元` : '零元';
  if (!decPart) return `${integer}整`;

  const jiao = decPart[0];
  const fen = decPart[1];
  let decimals = '';
  if (jiao && jiao !== '0') decimals += `${CN_DIGITS[Number(jiao)]}角`;
  else if (fen && fen !== '0') decimals += '零';
  if (fen && fen !== '0') decimals += `${CN_DIGITS[Number(fen)]}分`;
  return decimals ? `${integer}${decimals}` : `${integer}整`;
}

export function parseDate(text: string): Date | undefined {
  const match = text.match(/(\d{4})[-/年.](\d{1,2})[-/月.](\d{1,2})/);
  if (!match) return undefined;
  const date = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3])));
  return Number.isNaN(date.getTime()) ? undefined : date;
}

export function formatDate(date: Date): string {
  const y = date.getUTCFullYear();
  const m = `${date.getUTCMonth() + 1}`.padStart(2, '0');
  const d = `${date.getUTCDate()}`.padStart(2, '0');
  return `${y}年${m}月${d}日`;
}

export function shiftDays(text: string, days: number): string | undefined {
  const date = parseDate(text);
  if (!date) return undefined;
  return formatDate(new Date(date.getTime() + days * 86_400_000));
}

export function diffDays(startText: string, endText: string): number | undefined {
  const start = parseDate(startText);
  const end = parseDate(endText);
  if (!start || !end) return undefined;
  return Math.round((end.getTime() - start.getTime()) / 86_400_000);
}

const NUMERIC_PATTERN = /-?\d[\d,]*(\.\d+)?/g;

export function extractNumbers(text: string): string[] {
  return (text.match(NUMERIC_PATTERN) ?? []).map((value) => value.replace(/,/g, ''));
}

export function normalizeName(name: string): string {
  return name.replace(/[\s:：（）()【】\[\]/、-]/g, '').toLowerCase();
}

export function isAmountLike(text: string): boolean {
  return /-?\d[\d,]*(\.\d+)?/.test(text.replace(/[¥￥元万元%％]/g, ''));
}

export interface NumberHit {
  /** 原始文本片段（含千分位） */
  raw: string;
  /** 归一化后的数值文本（去千分位） */
  normalized: string;
  /** 在原文中的起始下标 */
  index: number;
}

/** 扫描文本中的全部数字片段，保留位置信息 */
export function scanNumbers(text: string): NumberHit[] {
  const hits: NumberHit[] = [];
  const pattern = /-?\d[\d,]*(?:\.\d+)?/g;
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(text)) !== null) {
    hits.push({
      raw: match[0],
      normalized: match[0].replace(/,/g, ''),
      index: match.index,
    });
  }
  return hits;
}

/**
 * 判定一个数字是否只是"结构编号"（条款号/章节号/列表序号）。
 * A5：此前用「整数且绝对值 < 10 一律放行」的粗粒度规则，会把"3 人"这类小整数事实一并放过。
 * 这里改为按上下文判定，只有明确的编号形态才排除在事实校验之外。
 */
export function isStructuralNumber(text: string, hit: NumberHit): boolean {
  const before = text.slice(0, hit.index);
  const after = text.slice(hit.index + hit.raw.length);
  // 层级编号：1.2 / 3.1.2 / 4.4.1
  if (/^\d+(\.\d+)+$/.test(hit.raw)) return true;
  // 行首列表或条款序号：1. / 2、 / 3) / 4）
  const atLineStart = /(^|\n)[ \t>#*\-（(【\[]*$/.test(before);
  if (atLineStart && /^([.、)）]|\s*$)/.test(after)) return true;
  // 第 3 章 / 第 5 条 / 第 2 款
  if (/第\s*$/.test(before) && /^[、\s]*[章节条款项]/.test(after)) return true;
  return false;
}

/** 正文中的"事实性数字"（排除结构编号），返回归一化文本 */
export function factualNumbers(text: string): string[] {
  return scanNumbers(text)
    .filter((hit) => !isStructuralNumber(text, hit))
    .map((hit) => hit.normalized);
}

/** 从多段文本中收集"出现过的数字"集合，用于集合级比对（替代子串匹配） */
export function numberSet(texts: Array<string | undefined>): Set<string> {
  const values = new Set<string>();
  for (const text of texts) {
    if (!text) continue;
    for (const hit of scanNumbers(text)) values.add(hit.normalized);
  }
  return values;
}

/**
 * 中文 2-gram 重合度：用于判断一段结论与其引用证据是否讨论同一件事（A6）。
 * 纯确定性计算，不引入模型调用。
 */
export function bigramOverlap(claim: string, evidence: string): number {
  const tokens = new Set<string>();
  const cleaned = claim.replace(/[\s，。；：、（）()【】\[\]「」"'’“”]/g, '');
  for (let i = 0; i + 1 < cleaned.length; i += 1) {
    const gram = cleaned.slice(i, i + 2);
    if (/^[\u4e00-\u9fa5]{2}$/.test(gram)) tokens.add(gram);
  }
  for (const word of claim.match(/[A-Za-z]{3,}|\d+(?:\.\d+)?/g) ?? []) tokens.add(word);
  if (tokens.size === 0) return 1;
  let shared = 0;
  for (const token of tokens) {
    if (evidence.includes(token)) shared += 1;
  }
  return shared / tokens.size;
}
