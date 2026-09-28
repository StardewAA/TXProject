/**
 * 书签占位模板回填（变量字典方案）：
 * 模板以 Word 原生书签（<w:bookmarkStart>/<w:bookmarkEnd> 同 id 成对）标记待填位置，
 * 打开文档不可见、不影响排版。回填只替换书签区域内的留白/示例 run，不动区域外内容。
 *
 * 实测模板（《投标文件_占位变量模板_版式保护版.docx》）的书签区域形态：
 *   1. 留白区：书签内是若干纯空白 run（下划线留白）→ 取值填进第一个空白 run；
 *   2. 空书签：书签内没有任何 run（表格数据格）→ 在区域内插入带段落格式的取值 run；
 *   3. 示例区：书签内是模板示例字（「无」「长期」「合格」、行号「1」、总承包服务费示例行）→ 整区替换为取值；
 *   4. 占位符区：示例用「ｘｘｘ」等占位字符 → 仅替换占位 run；
 *   5. 括号区：示例为「（  ）日历天」这类括号留白 → 仅替换括号对为（取值）；
 *   6. 标签区：书签只包住「××：」标签、留白紧随书签之后 → 填同段内紧随其后的前导空白；
 *   7. 日期骨架：区域非空白文本只含「年/月/日」且取值为完整中文日期 → 填值后吃掉骨架，避免残留「年月日」。
 * 书签 id 可能被模板脚本复用（如封面与目录共用 id 3-11），配对规则「每个 start 向后找第一个同 id 的 end」在此类文档上成立；
 * 同名书签会出现多处（bidder_name、bidder_name_2 …），每一处都独立回填；
 * _Toc / _GoBack 系统书签与表头标签格（「姓名」「年龄」等）一律不动。
 */
import { AppError } from '../errors.js';
import { unzip, zipStore } from './zip.js';

export interface BookmarkFillResult {
  buffer: Buffer;
  /** 成功回填的基础变量名（去重；同名 _N 变体按基础名归并） */
  filled: string[];
  /** 有取值但没有任何区域可填的书签名（如书签误包住表头标签） */
  skipped: string[];
}

const START_TAG_SOURCE = '<w:bookmarkStart\\b[^>]*?w:id="(\\d+)"[^>]*?w:name="([^"]*)"[^>]*?/?>';
/** rPr 可能是自闭合（<w:rPr/>）或成对标签，两种都要能匹配 */
const RUN_SOURCE =
  /<w:r(?:\s[^>]*)?>(?:<w:rPr\b[^>]*?\/>|<w:rPr>[\s\S]*?<\/w:rPr>)?<w:t(?:\s[^>]*)?>([\s\S]*?)<\/w:t><\/w:r>/;

/** 表头标签：书签误包住表头文字时不做回填，避免破坏表头 */
const BARE_LABELS = new Set([
  '姓名', '年龄', '学历', '职称', '职务', '拟在本合同任职',
  '性别', '专业', '毕业院校', '出生日期', '联系电话', '备注',
]);

/** 模板示例值形态（整区替换）：无/长期/合格/行号数字/总承包服务费示例行 */
const SAMPLE_PATTERNS: RegExp[] = [/^无$/, /^长期$/, /^合格$/, /^[0-9]{1,3}$/, /^发包/];

function escapeXml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

/** 去掉空白与下划线后的文本（留白判定用） */
function norm(text: string): string {
  return text.replace(/[\s\u3000_＿]/g, '');
}

function isBlankText(text: string): boolean {
  return norm(text).length === 0;
}

/** 完整中文日期：同时含 年/月/日 且带四位年份（数字或汉字数字） */
function isCompleteChineseDate(value: string): boolean {
  return (
    value.length <= 60 &&
    value.includes('年') &&
    value.includes('月') &&
    value.includes('日') &&
    /(?:19|20)\d{2}|[〇零一二三四五六七八九]{4}/.test(value)
  );
}

interface RunInfo {
  start: number;
  end: number;
  text: string;
  run: string;
}

/** 解析区域内全部含 <w:t> 的 run（下标相对区域起点） */
function parseRuns(seg: string): RunInfo[] {
  const runs: RunInfo[] = [];
  const re = new RegExp(RUN_SOURCE.source, 'g');
  let m: RegExpExecArray | null;
  while ((m = re.exec(seg)) !== null) {
    runs.push({ start: m.index, end: m.index + m[0].length, text: m[1], run: m[0] });
  }
  return runs;
}

/** 替换 run 内 <w:t> 的文本（保留 run 的 rPr 等其余属性，确保留白下划线等格式不变） */
function replaceRunText(run: string, value: string): string {
  const tStart = run.indexOf('<w:t');
  const tOpenEnd = run.indexOf('>', tStart) + 1;
  const open = run.slice(tStart, tOpenEnd).includes('xml:space')
    ? run.slice(tStart, tOpenEnd)
    : '<w:t xml:space="preserve">';
  return run.slice(0, tStart) + open + escapeXml(value) + '</w:t>' + run.slice(run.indexOf('</w:t>') + 6);
}

/** 空书签插入取值 run 时，取段落标记（pPr 内 rPr）的字体格式 */
function extractParaRPr(seg: string): string {
  const pPr = /<w:pPr>([\s\S]*?)<\/w:pPr>/.exec(seg);
  if (!pPr) return '';
  const rPr = /<w:rPr>([\s\S]*?)<\/w:rPr>/.exec(pPr[1]);
  return rPr && rPr[1].trim().length > 0 ? `<w:rPr>${rPr[1]}</w:rPr>` : '';
}

/**
 * 零长度书签的段落属性在书签 start 之前（书签紧随 pPr 之后），区域内取不到，
 * 向前找所属段落的段落标记格式（w:pPr/w:rPr）；最近 pPr 之后若已出现 </w:p>，
 * 说明它属于更早的段落，放弃（宁可无格式，不可串段）。
 */
function extractParaMarkRPr(xml: string, startEnd: number): string {
  const prefix = xml.slice(0, startEnd);
  const pPrIdx = prefix.lastIndexOf('<w:pPr>');
  if (pPrIdx < 0) return '';
  if (prefix.lastIndexOf('</w:p>') > pPrIdx) return '';
  const rPr = /<w:rPr>([\s\S]*?)<\/w:rPr>/.exec(prefix.slice(pPrIdx));
  return rPr && rPr[1].trim().length > 0 ? `<w:rPr>${rPr[1]}</w:rPr>` : '';
}

/**
 * 回填单个书签区域（start 标签结尾与 end 标签开头之间的内容）。
 * 按区域形态逐级匹配，命中后返回新 xml；无形态可匹配则返回原 xml（hit=false）。
 */
function fillRegion(xml: string, startEnd: number, endStart: number, value: string): { xml: string; hit: boolean } {
  const seg = xml.slice(startEnd, endStart);
  const runs = parseRuns(seg);
  const joined = norm(runs.map((r) => r.text).join(''));
  const firstBlankIdx = runs.findIndex((r) => isBlankText(r.text));
  const placeholderIdx = runs.findIndex((r) => /^[ｘxX×]+$/.test(norm(r.text)));
  const bracketIdx = runs.findIndex((r) => /（\s*）|\(\s*\)/.test(r.text));
  const labelOnly = firstBlankIdx < 0 && placeholderIdx < 0 && bracketIdx < 0 && /[：:]\s*$/.test(joined);

  // 形态 2：空书签 → 在区域内插入取值 run（段落标记格式）
  if (runs.length === 0) {
    const rPr = extractParaRPr(seg) || extractParaMarkRPr(xml, startEnd);
    const run = `<w:r>${rPr}<w:t xml:space="preserve">${escapeXml(value)}</w:t></w:r>`;
    return { xml: xml.slice(0, endStart) + run + xml.slice(endStart), hit: true };
  }

  // 形态 4：占位符 run（ｘｘｘ 等）→ 仅替换该 run
  if (placeholderIdx >= 0) {
    const r = runs[placeholderIdx];
    const next = seg.slice(0, r.start) + replaceRunText(r.run, value) + seg.slice(r.end);
    return { xml: xml.slice(0, startEnd) + next + xml.slice(endStart), hit: true };
  }

  // 形态 5：括号留白「（  ）日历天」→ 仅替换括号对
  if (bracketIdx >= 0) {
    const r = runs[bracketIdx];
    const text = r.text.replace(/（\s*）|\(\s*\)/, `（${value}）`);
    const next = seg.slice(0, r.start) + replaceRunText(r.run, text) + seg.slice(r.end);
    return { xml: xml.slice(0, startEnd) + next + xml.slice(endStart), hit: true };
  }

  // 形态 3：示例区（无/长期/合格/行号/服务费示例行）→ 整区替换为取值
  if (runs.length === 1 && !BARE_LABELS.has(joined) && SAMPLE_PATTERNS.some((p) => p.test(joined))) {
    return {
      xml: xml.slice(0, startEnd) + replaceRunText(runs[0].run, value) + xml.slice(endStart),
      hit: true,
    };
  }

  // 形态 9：骨架句单 run（中文 + 内嵌长留白，如「年毕业于  学校  专业」）→ 整区替换；
  // 排除含冒号（标签）、括号尾（标签链，由形态 8 处理）、年月日（日期骨架）的 run
  if (runs.length === 1) {
    const raw = runs[0].text;
    if (
      /[一-龥]/.test(raw) &&
      /[\s　_＿]{4,}/.test(raw) &&
      !/[：:]/.test(raw) &&
      !/[（(][^）)]*[）)]\s*$/.test(raw) &&
      !/年\s*月\s*日/.test(raw)
    ) {
      return {
        xml: xml.slice(0, startEnd) + replaceRunText(runs[0].run, value) + xml.slice(endStart),
        hit: true,
      };
    }
  }

  // 形态 8：标签链单 run（「____（盖章）」「：____（盖章）」）→ 只填 run 内留白段（行首留白，或冒号与括号组之间）
  if (runs.length === 1) {
    const text = runs[0].text;
    const chain = norm(text);
    if (/^[：:]?[（(].*[）)]$/.test(chain)) {
      const paren = text.search(/[（(][^）)]*[）)]/);
      const colon = /^[\s　_＿]*[：:]/.test(text) ? text.search(/[：:]/) : -1;
      const blankFrom = colon >= 0 ? colon + 1 : 0;
      const blank = text.slice(blankFrom, paren < 0 ? text.length : paren);
      if (/^[\s　_＿]+$/.test(blank)) {
        const filledText =
          text.slice(0, blankFrom) + value + text.slice(blankFrom + blank.length);
        const filled = replaceRunText(runs[0].run, filledText);
        const next = seg.slice(0, runs[0].start) + filled + seg.slice(runs[0].end);
        return { xml: xml.slice(0, startEnd) + next + xml.slice(endStart), hit: true };
      }
    }
  }

  // 形态 6：标签区「××：」→ 填同段内紧随书签之后的前导空白（不得越过下一个书签标签）
  if (labelOnly) {
    const paraEnd = xml.indexOf('</w:p>', endStart);
    const nextTag = xml.indexOf('<w:bookmark', endStart + 1); // 跳过当前 bookmarkEnd 标签本身
    const windowEnd = Math.min(
      paraEnd < 0 ? xml.length : paraEnd,
      nextTag < 0 ? xml.length : nextTag,
      endStart + 800,
    );
    const window = xml.slice(endStart, windowEnd);
    const re = new RegExp(RUN_SOURCE.source, 'g');
    const m = re.exec(window);
    if (m) {
      const run = m[0];
      const text = m[1];
      const lead = /^[\s\u3000_＿]+/.exec(text);
      const newText = isBlankText(text) ? value : lead ? value + text.slice(lead[0].length) : null;
      if (newText !== null) {
        const replaced = window.slice(0, m.index) + replaceRunText(run, newText) + window.slice(m.index + run.length);
        return { xml: xml.slice(0, endStart) + replaced + xml.slice(endStart + window.length), hit: true };
      }
    }
    // 同段无 run：下一个空段落（如「投标人名称（盖章）：」后的签名行）插入取值 run；
    // 不得跨表格单元格（</w:tc>），避免插进无关格子
    if (paraEnd >= 0) {
      const nextParaEnd = xml.indexOf('</w:p>', paraEnd + 6);
      const cellEnd = xml.indexOf('</w:tc>', paraEnd);
      if (nextParaEnd >= 0 && nextParaEnd < paraEnd + 2500 && (cellEnd < 0 || cellEnd > nextParaEnd)) {
        const nextPara = xml.slice(paraEnd + 6, nextParaEnd);
        const nextRuns = new RegExp(RUN_SOURCE.source, 'g').exec(nextPara);
        if (!nextRuns) {
          const rPr = extractParaRPr(nextPara);
          const run = `<w:r>${rPr}<w:t xml:space="preserve">${escapeXml(value)}</w:t></w:r>`;
          return {
            xml: xml.slice(0, nextParaEnd) + run + xml.slice(nextParaEnd),
            hit: true,
          };
        }
      }
    }
    return { xml, hit: false };
  }

  // 形态 1/7：留白区 → 填第一个空白 run；完整中文日期取值下吃掉区域内「年/月/日」骨架
  if (firstBlankIdx >= 0) {
    const r = runs[firstBlankIdx];
    let next = seg.slice(0, r.start) + replaceRunText(r.run, value);
    if (isCompleteChineseDate(value) && joined.length > 0 && /^[年月日]+$/.test(joined)) {
      // 骨架已随取值冗余，其余 run（骨架字 + 分隔留白）一并移除
    } else {
      next += seg.slice(r.end);
    }
    return { xml: xml.slice(0, startEnd) + next + xml.slice(endStart), hit: true };
  }

  return { xml, hit: false };
}

/** 去掉尾部 _数字 / _appendix 后缀：bidder_name_2 → bidder_name；duration_days_appendix → duration_days */
function stripVariantSuffix(name: string): string {
  return name.replace(/_\d+$/, '').replace(/_appendix$/, '');
}

/**
 * 在 document.xml 上执行书签回填。
 * values 的键可以是基础变量名（bidder_name）或带行号/序号的完整书签名（bill_2_code）；
 * 基础变量名会自动回填同名 _N 变体（bidder_name_2、date_year_2 …）。
 * 变体归并只在「去后缀后的基础名本身也是模板书签」时生效（bidder_name_2 → bidder_name），
 * 行号命名（cost_breakdown_item_1、appendix_clause_no_1）不受影响。
 */
export function fillBookmarksXml(
  xml: string,
  values: Record<string, string>,
): { xml: string; filled: string[]; skipped: string[] } {
  // 第一遍：收集模板全部非系统书签名（变体归并判定依据）与候选名
  const templateNames = new Set<string>();
  const candidates: string[] = [];
  const seen = new Set<string>();
  const startRe = new RegExp(START_TAG_SOURCE, 'g');
  let m: RegExpExecArray | null;
  while ((m = startRe.exec(xml)) !== null) {
    const name = m[2];
    if (name.startsWith('_')) continue;
    templateNames.add(name);
    if (seen.has(name)) continue;
    seen.add(name);
    candidates.push(name);
  }

  const filled = new Set<string>();
  const skipped: string[] = [];
  for (const name of candidates) {
    const stripped = stripVariantSuffix(name);
    const base = stripped !== name && templateNames.has(stripped) ? stripped : name;
    const value = values[name] ?? values[base];
    if (value === undefined || value.trim() === '') continue;
    // 同名书签可能多处出现：收集全部标签位置后倒序回填（前面的编辑不影响后面已收集的位置）
    const positions: number[] = [];
    const nameRe = new RegExp('<w:bookmarkStart\\b[^>]*?w:name="' + escapeRegex(name) + '"[^>]*?/?>', 'g');
    let tag: RegExpExecArray | null;
    while ((tag = nameRe.exec(xml)) !== null) positions.push(tag.index);

    let anyHit = false;
    for (const pos of positions.reverse()) {
      const idMatch = /w:id="(\d+)"/.exec(xml.slice(pos, pos + 300));
      if (!idMatch) continue;
      const endRe = new RegExp('<w:bookmarkEnd\\b[^>]*?w:id="' + idMatch[1] + '"[^>]*?/?>');
      const end = endRe.exec(xml.slice(pos + 1));
      if (!end) continue;
      const result = fillRegion(xml, pos + tagLength(xml, pos), pos + 1 + end.index, value);
      if (result.hit) {
        xml = result.xml;
        anyHit = true;
      }
    }
    if (anyHit) filled.add(base);
    else skipped.push(name);
  }

  return { xml, filled: [...filled], skipped };
}

/** 书签标签起点到标签结束（/> 或 >）的长度 */
function tagLength(xml: string, pos: number): number {
  const close = xml.indexOf('>', pos);
  return close < 0 ? 0 : close - pos + 1;
}

function escapeRegex(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/** 模板是否使用书签占位（存在任一非系统书签） */
export function hasFillBookmarks(xml: string): boolean {
  const re = new RegExp(START_TAG_SOURCE, 'g');
  let m: RegExpExecArray | null;
  while ((m = re.exec(xml)) !== null) {
    if (!m[2].startsWith('_')) return true;
  }
  return false;
}

/** 列出模板内全部非系统书签名（用于编写取值 JSON / 排查） */
export function listBookmarkNames(buffer: Buffer): string[] {
  const entries = unzip(buffer);
  const docEntry = entries.find((entry) => entry.name === 'word/document.xml');
  if (!docEntry) {
    throw new AppError('模板缺少 word/document.xml，无法解析书签', { category: 'validation', status: 400 });
  }
  const xml = docEntry.data.toString('utf8');
  const names: string[] = [];
  const seen = new Set<string>();
  const re = new RegExp(START_TAG_SOURCE, 'g');
  let m: RegExpExecArray | null;
  while ((m = re.exec(xml)) !== null) {
    if (m[2].startsWith('_') || seen.has(m[2])) continue;
    seen.add(m[2]);
    names.push(m[2]);
  }
  return names;
}

/** 书签模板回填入口：templateBuffer 为模板 docx，返回回填后的 docx 二进制 */
export function fillBookmarkTemplate(buffer: Buffer, values: Record<string, string>): BookmarkFillResult {
  const entries = unzip(buffer);
  const docEntry = entries.find((entry) => entry.name === 'word/document.xml');
  if (!docEntry) {
    throw new AppError('模板缺少 word/document.xml，无法回填', { category: 'validation', status: 400 });
  }

  const { xml, filled, skipped } = fillBookmarksXml(docEntry.data.toString('utf8'), values);
  const rebuilt = entries.map((entry) =>
    entry.name === 'word/document.xml' ? { name: entry.name, data: Buffer.from(xml, 'utf8') } : entry,
  );
  return { buffer: zipStore(rebuilt), filled, skipped };
}

/** 废标红线：报价/投标人/工期等共享变量在文档多处出现（同名或 _N 变体），任一出现处取值不一致即废标 */
export const SHARED_VALUE_KEYS: readonly string[] = [
  'bid_total',
  'bid_total_upper',
  'bidder_name',
  'project_name',
  'date_year',
  'date_month',
  'date_day',
  'duration_days',
  'quality_standard',
];

interface BookmarkRegion {
  start: number;
  end: number;
  /** 区域（start 标签结尾到 end 标签开头）内可见文本 */
  text: string;
}

/** 提取指定书签名全部出现处的区域区间与区域文本（回填后调用，text 为回填结果） */
export function collectBookmarkRegions(xml: string, name: string): BookmarkRegion[] {
  const regions: BookmarkRegion[] = [];
  const nameRe = new RegExp('<w:bookmarkStart\\b[^>]*?w:name="' + escapeRegex(name) + '"[^>]*?/?>', 'g');
  let tag: RegExpExecArray | null;
  while ((tag = nameRe.exec(xml)) !== null) {
    const idMatch = /w:id="(\d+)"/.exec(xml.slice(tag.index, tag.index + 300));
    if (!idMatch) continue;
    const endRe = new RegExp('<w:bookmarkEnd\\b[^>]*?w:id="' + idMatch[1] + '"[^>]*?/?>');
    const end = endRe.exec(xml.slice(tag.index + 1));
    if (!end) continue;
    const start = tag.index + tagLength(xml, tag.index);
    const endStart = tag.index + 1 + end.index;
    let text = '';
    const tRe = /<w:t(?:\s[^>]*)?>([\s\S]*?)<\/w:t>/g;
    let m: RegExpExecArray | null;
    while ((m = tRe.exec(xml.slice(start, endStart))) !== null) text += m[1];
    regions.push({ start, end: endStart, text });
  }
  return regions;
}

export interface ConsistencyIssue {
  name: string;
  /** 各出现处区域文本（未命中期望值的位置会在最后一条标注） */
  values: string[];
}

/**
 * 一致性断言（交付前固定校验）：关键共享变量的取值必须出现在每一处书签出现位置。
 * 检查窗口 = 书签区域 + 区域后 400 字符，覆盖「标签区」形态（形态 6，取值填在书签之外的紧随留白）。
 * 回填按基础名统一取值，正常路径下必然一致；断言把该隐式保证升级为显式校验，
 * 防模板修订/未来按出现处差异化回填引入的报价不一致（废标）。
 */
export function assertSharedValueConsistency(
  xml: string,
  values: Record<string, string>,
  names: readonly string[],
): ConsistencyIssue[] {
  const issues: ConsistencyIssue[] = [];
  const normText = (text: string): string => text.replace(/[\s　_＿]/g, '');
  for (const name of names) {
    const value = values[name];
    const valueNorm = value ? normText(value) : '';
    if (!valueNorm) continue;
    const regions = collectBookmarkRegions(xml, name);
    if (regions.length === 0) continue;
    const texts = regions.map((region) => normText(region.text));
    for (let i = 0; i < regions.length; i += 1) {
      const windowText = normText(xml.slice(regions[i]!.start, regions[i]!.end + 400));
      if (!windowText.includes(valueNorm)) {
        issues.push({
          name,
          values: [...texts, `（第 ${i + 1} 处出现位置未命中期望值 ${valueNorm}）`],
        });
      }
    }
  }
  return issues;
}
