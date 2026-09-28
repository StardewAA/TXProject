/**
 * 标书模板回填（方案2 + 方案3）：在保留模板全部样式（字体/表格/页眉页脚/目录域）的前提下，
 * 只编辑 word/document.xml，把「标签 + 下划线留白」替换为字段取值，并把已生成的章节正文
 * 插入到指定章节标题之后。
 */
import { renderMarkdownToBodyXml } from './docx.js';
import { AppError } from '../errors.js';
import { TEMPLATE_FIELDS } from './templateFields.js';
import { buildBookmarkValues, loadBookmarkDict } from './bookmarkFields.js';
import {
  assertSharedValueConsistency,
  fillBookmarksXml,
  hasFillBookmarks,
  listBookmarkNames,
  SHARED_VALUE_KEYS,
} from './bookmarkFill.js';
import { buildPricingBookmarkValues, type PricingData, type PricingTruncation } from './pricingMapping.js';
import { unzip, zipStore } from './zip.js';

export interface TemplateSection {
  /** 目标章节标题（段落拼接后的完整文字，如「六、施工组织设计」） */
  heading: string;
  /** 该章节要插入的 markdown 正文 */
  markdown: string;
}

export interface FillTemplateResult {
  buffer: Buffer;
  /** 成功回填的字段名（去重） */
  fields: string[];
  /** 书签模板模式下：有取值但无处可填的书签名（如书签误包住表头标签） */
  skippedBookmarks?: string[];
  /** 是否至少插入了一个章节正文 */
  inserted: boolean;
  /** 报价数据超出模板行数的截断明细（COPY_PRICING 通道） */
  pricingTruncated?: PricingTruncation[];
}

function escapeXml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

/** 留白 run 的匹配：<w:t ...> 仅空白或下划线（模板里两种留白风格：下划线空格 / 字面下划线）</w:t> */
const BLANK_T_SOURCE = '<w:t(?:\\s[^>]*)?>[ \\t\\r\\n_＿]*</w:t>';

/** 完整 run 的匹配：<w:r>…<w:t>…</w:t></w:r>（rPr 可选） */
const RUN_SOURCE = /<w:r(?:\s[^>]*)?>(?:<w:rPr>[\s\S]*?<\/w:rPr>)?<w:t(?:\s[^>]*)?>([\s\S]*?)<\/w:t><\/w:r>/;

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

/**
 * 完整日期取值下，删除紧随其后的「空白 run + 年 + 空白 run + 月 + 空白 run + 日」留白骨架。
 * 模板日期行形如「成立时间：____年____月____日」，回填只吃掉第一个留白 run 会残留「年 月 日」。
 * 骨架不完整或窗口内出现其它内容立即放弃，避免误删正文。
 */
function consumeDateSkeleton(xml: string, from: number): string {
  const WINDOW = 1600;
  const window = xml.slice(from, from + WINDOW);
  const runs: Array<{ start: number; end: number; text: string }> = [];
  const re = new RegExp(RUN_SOURCE.source, 'g');
  let m: RegExpExecArray | null;
  while ((m = re.exec(window)) !== null) {
    runs.push({ start: m.index, end: m.index + m[0].length, text: m[1] });
  }
  const norm = (text: string): string => text.replace(/[\s_＿]/g, '');
  const remove: Array<{ start: number; end: number }> = [];
  let consumed = 0;
  let pos = 0;
  while (pos < runs.length) {
    const batch: Array<{ start: number; end: number }> = [];
    let phase = 0; // 0=期待年 1=期待月 2=期待日 3=本段骨架完整
    let p = pos;
    while (p < runs.length) {
      const n = norm(runs[p].text);
      if (n === '') {
        batch.push(runs[p]);
        p += 1;
        continue;
      }
      if (phase === 0 && (n === '年' || n === '年月日')) {
        batch.push(runs[p]);
        p += 1;
        phase = n === '年月日' ? 3 : 1;
        if (phase === 3) break;
        continue;
      }
      if (phase === 1 && n === '月') {
        batch.push(runs[p]);
        p += 1;
        phase = 2;
        continue;
      }
      if (phase === 2 && n === '日') {
        batch.push(runs[p]);
        p += 1;
        phase = 3;
        break;
      }
      break; // 骨架中断
    }
    if (phase !== 3) break; // 不再有完整骨架
    remove.push(...batch);
    consumed += 1;
    pos = p;
  }
  if (consumed === 0) return xml;
  let out = window;
  for (const item of remove.reverse()) {
    out = out.slice(0, item.start) + out.slice(item.end);
  }
  return xml.slice(0, from) + out + xml.slice(from + WINDOW);
}

function firstBlank(text: string): RegExpExecArray | null {
  return new RegExp(BLANK_T_SOURCE).exec(text);
}

function lastBlank(text: string): RegExpExecArray | null {
  const re = new RegExp(BLANK_T_SOURCE, 'g');
  let m: RegExpExecArray | null;
  let last: RegExpExecArray | null = null;
  while ((m = re.exec(text)) !== null) last = m;
  return last;
}

function replaceBlankTag(tag: string, value: string): string {
  const openEnd = tag.indexOf('>') + 1;
  const open = tag.slice(0, openEnd);
  const withSpace = open.includes('xml:space') ? open : '<w:t xml:space="preserve">';
  return `${withSpace}${escapeXml(value)}</w:t>`;
}

/** 定位第 occurrence 次出现的 label，返回其起始下标（找不到返回 -1） */
function locate(xml: string, label: string, occurrence: number): number {
  let idx = -1;
  let from = 0;
  for (let i = 0; i < occurrence; i += 1) {
    idx = xml.indexOf(label, from);
    if (idx < 0) return -1;
    from = idx + label.length;
  }
  return idx;
}

/** 标签后 300 字符内的首个 run（含可选 rPr） */
const FIRST_RUN_AFTER = new RegExp('^[\\s\\S]{0,300}?(' + RUN_SOURCE.source + ')');

/**
 * 混排留白：模板个别行把示例字与下划线空白放在同一 run 里（如「经营期限：____长期____」）。
 * 此时把该 run 整体替换为取值（示例字一并移除）；纯空白 run / 长正文 run / 纯标点 run 不属于此形态，交由常规路径。
 */
function fillMixedAfterLabel(
  xml: string,
  label: string,
  value: string,
  occurrence: number,
): { xml: string; hit: boolean; filledEnd?: number } {
  const idx = locate(xml, label, occurrence);
  if (idx < 0) return { xml, hit: false };
  const tail = xml.slice(idx + label.length);
  const m = FIRST_RUN_AFTER.exec(tail);
  if (!m) return { xml, hit: false };
  const run = m[1];
  const text = m[2];
  const nonBlank = text.replace(/[\s_＿]/g, '');
  if (nonBlank.length === 0 || nonBlank.length > 20 || /^[：:；;、，,．。.（）()]+$/.test(nonBlank)) {
    return { xml, hit: false };
  }
  const tStart = run.indexOf('<w:t');
  const tOpenEnd = run.indexOf('>', tStart) + 1;
  const withSpace = run.slice(tStart, tOpenEnd).includes('xml:space')
    ? run.slice(tStart, tOpenEnd)
    : '<w:t xml:space="preserve">';
  const filledRun =
    run.slice(0, tStart) + withSpace + escapeXml(value) + '</w:t>' + run.slice(run.indexOf('</w:t>') + '</w:t>'.length);
  // m.index 是正则整体起点（前缀含标签 run 的收尾标签）；run 真正占用的区间是 [m.index + 前缀长, + run.length)
  const prefixLength = m[0].length - run.length;
  const filledEnd = idx + label.length + m.index + prefixLength + filledRun.length;
  return {
    xml:
      xml.slice(0, idx + label.length) +
      tail.slice(0, m.index + prefixLength) +
      filledRun +
      tail.slice(m.index + m[0].length),
    hit: true,
    filledEnd,
  };
}

/** 留白紧跟锚点之后：锚点 + 下一个空白 run */
function fillAfterLabel(xml: string, label: string, value: string, occurrence: number): { xml: string; hit: boolean } {
  const mixed = fillMixedAfterLabel(xml, label, value, occurrence);
  if (mixed.hit) {
    // 混排留白已整体替换；完整日期取值下仍要清理紧随的「年 月 日」骨架（防模板另设骨架行）
    const next = isCompleteChineseDate(value) ? consumeDateSkeleton(mixed.xml, mixed.filledEnd!) : mixed.xml;
    return { xml: next, hit: true };
  }
  const idx = locate(xml, label, occurrence);
  if (idx < 0) return { xml, hit: false };
  const tail = xml.slice(idx + label.length);
  const m = firstBlank(tail);
  if (!m) return { xml, hit: false };
  const filledTag = replaceBlankTag(m[0], value);
  const replaced = tail.slice(0, m.index) + filledTag + tail.slice(m.index + m[0].length);
  let next = xml.slice(0, idx + label.length) + replaced;
  if (isCompleteChineseDate(value)) {
    // 完整日期下移除模板残留的「____年____月____日」骨架（2026-09-21：成立时间填值后残留年月日）
    const filledEnd = idx + label.length + m.index + filledTag.length;
    next = consumeDateSkeleton(next, filledEnd);
  }
  return { xml: next, hit: true };
}

/** 留白在锚点之前：锚点之前的最后一个空白 run */
function fillBeforeLabel(xml: string, label: string, value: string, occurrence: number): { xml: string; hit: boolean } {
  const idx = locate(xml, label, occurrence);
  if (idx < 0) return { xml, hit: false };
  const head = xml.slice(0, idx);
  const last = lastBlank(head);
  if (!last) return { xml, hit: false };
  const replaced = replaceBlankTag(last[0], value);
  const next = head.slice(0, last.index) + replaced + head.slice(last.index + last[0].length) + xml.slice(idx);
  return { xml: next, hit: true };
}

/** 在段落拼接文字等于 headingText 的段落之后插入 fragment（段落级，不受 run 拆分影响） */
function insertAfterParagraph(xml: string, headingText: string, fragment: string): string {
  const target = headingText.trim();
  const paraRe = /<w:p(?:\s[^>]*)?>[\s\S]*?<\/w:p>/g;
  let m: RegExpExecArray | null;
  while ((m = paraRe.exec(xml)) !== null) {
    let text = '';
    const tRe = /<w:t[^>]*>([\s\S]*?)<\/w:t>/g;
    let tm: RegExpExecArray | null;
    while ((tm = tRe.exec(m[0])) !== null) text += tm[1];
    if (text.trim() === target) {
      return xml.slice(0, m.index + m[0].length) + fragment + xml.slice(m.index + m[0].length);
    }
  }
  return xml;
}

/**
 * 回填标书模板。templateBuffer 为原始模板 docx；values 为标准字段名 → 取值；
 * sections 为要插入的章节正文（按标题定位插入点）；pricing 为报价数据（COPY_PRICING 通道，
 * 按 bookmark-dict.json 的 table/row/col 展开为书签名取值，覆盖标准字段通道的同名值）。
 * 返回新的 docx 二进制。
 */
export function fillTemplate(
  templateBuffer: Buffer,
  opts: { values: Map<string, string>; sections?: TemplateSection[]; pricing?: PricingData },
): FillTemplateResult {
  const entries = unzip(templateBuffer);
  const docEntry = entries.find((entry) => entry.name === 'word/document.xml');
  if (!docEntry) {
    // 模板文件结构不合法属于入参问题，返回 400 validation 而非 500
    throw new AppError('模板缺少 word/document.xml，无法回填', { category: 'validation', status: 400 });
  }

  let xml = docEntry.data.toString('utf8');
  const filled: string[] = [];
  let skippedBookmarks: string[] | undefined;
  let bookmarkValues: Record<string, string> | undefined;
  let pricingTruncated: PricingTruncation[] | undefined;

  // 书签占位模板（变量字典方案）：书签名 → 取值回填，不走「标签+留白」锚点，
  // 避免锚点规则在书签模板上误填（两套规则会填同一处留白，且锚点无法覆盖全部书签字段）
  if (hasFillBookmarks(xml)) {
    bookmarkValues = buildBookmarkValues(opts.values);
    if (opts.pricing) {
      const dict = loadBookmarkDict();
      if (dict) {
        const pricingResult = buildPricingBookmarkValues(
          dict,
          opts.pricing,
          new Set(listBookmarkNames(templateBuffer)),
        );
        // 报价数据为权威来源：覆盖标准字段通道的同名取值（废标红线断言随后校验全处一致）
        Object.assign(bookmarkValues, pricingResult.values);
        pricingTruncated = pricingResult.truncated.length > 0 ? pricingResult.truncated : undefined;
      }
    }
    const result = fillBookmarksXml(xml, bookmarkValues);
    xml = result.xml;
    filled.push(...result.filled);
    skippedBookmarks = result.skipped.length > 0 ? result.skipped : undefined;
  } else {
    for (const spec of TEMPLATE_FIELDS) {
      const value = opts.values.get(spec.name);
      if (!value) continue;
      for (const anchor of spec.anchors) {
        const occurrence = anchor.occurrence ?? 1;
        const result =
          anchor.dir === 'before'
            ? fillBeforeLabel(xml, anchor.text, value, occurrence)
            : fillAfterLabel(xml, anchor.text, value, occurrence);
        if (result.hit) {
          xml = result.xml;
          filled.push(spec.name);
        }
      }
    }
  }

  let inserted = false;
  for (const section of opts.sections ?? []) {
    const fragment = renderMarkdownToBodyXml(section.markdown);
    const next = insertAfterParagraph(xml, section.heading, fragment);
    if (next !== xml) {
      xml = next;
      inserted = true;
    }
  }

  // 废标红线一致性断言（交付前固定校验）：关键共享变量在每一处出现位置都必须回填到统一取值
  if (bookmarkValues) {
    const issues = assertSharedValueConsistency(xml, bookmarkValues, SHARED_VALUE_KEYS);
    if (issues.length > 0) {
      throw new AppError('书签回填一致性校验失败（废标红线）：关键共享变量存在未回填到统一取值的位置', {
        category: 'validation',
        status: 422,
        details: { issues },
      });
    }
  }

  const rebuilt = entries.map((entry) =>
    entry.name === 'word/document.xml' ? { name: entry.name, data: Buffer.from(xml, 'utf8') } : entry,
  );
  return {
    buffer: zipStore(rebuilt),
    fields: [...new Set(filled)],
    skippedBookmarks,
    inserted,
    pricingTruncated,
  };
}
