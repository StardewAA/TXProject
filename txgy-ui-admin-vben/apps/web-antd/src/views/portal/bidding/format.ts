/**
 * 展示工具：时间（epoch 毫秒/字符串兼容，0/空视为无值）、空值清洗、
 * 公告正文智能排版（遗留拍平正文兜底）、正文结构化块 → HTML（02 §4 schema）。
 */
import type { NoticeBodyBlock } from './types';

const pad = (n: number) => String(n).padStart(2, '0');

function toDate(v: number | string | null | undefined): Date | null {
  if (v === null || v === undefined || v === '') return null;
  if (typeof v === 'number') {
    // 0/负值 = 未填（演示库存在 publishTime=0），不渲染成 1970
    if (v <= 0) return null;
    const d = new Date(v);
    return Number.isNaN(+d) ? null : d;
  }
  // 'YYYY-MM-DD HH:mm' / 'YYYY-MM-DD HH:mm:ss' / ISO
  const t = /^\d{4}-\d{2}-\d{2}( \d{2}:\d{2}(:\d{2})?)?$/.test(v)
    ? v.replace(' ', 'T')
    : v;
  const d = new Date(t);
  return Number.isNaN(+d) ? null : d;
}

/** 日期时间：YYYY-MM-DD HH:mm（无值返回空串） */
export function fmtDT(v: number | string | null | undefined): string {
  const d = toDate(v);
  if (!d) return '';
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** 仅日期：YYYY-MM-DD（有效期区间用） */
export function fmtDate(v: number | string | null | undefined): string {
  const d = toDate(v);
  if (!d) return '';
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** 后端空值可能以字符串 "NULL"/"null" 下发（历史脏数据），统一还原为空 */
const NULL_RE = /^(NULL|null|N\/A|-)$/i;
export function cleanT(v: unknown): string {
  if (v === null || v === undefined) return '';
  const s = String(v).trim();
  return NULL_RE.test(s) ? '' : s;
}

/** 多值字段（标段等）可能是数组、字符串 "NULL" 或逗号/顿号/分号分隔串，统一归一化为非空数组 */
export function toCleanList(v: unknown): string[] {
  if (v === null || v === undefined) return [];
  const list = Array.isArray(v) ? v.map(String) : String(v).split(/[,，、;；\s]+/);
  return list.map(cleanT).filter(Boolean);
}

/* ---------- HTML 转义 / 净化安全渲染 ---------- */

/** 文本 → HTML 转义（所有来自后端/结构化块的文本先转义再进入 v-dompurify-html） */
export function escapeHtml(s: string | number | null | undefined): string {
  return String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

const esc = escapeHtml;

/* ---------- 公告正文智能排版 ----------
 * 真实数据（EBIDDING 等外部源）拍平正文曾为「一个 <h1> + 一整段无换行的连续文字」，
 * 原文直接 v-html 会是一大坨。这里仅在“单一长段落、无表格/图片/多段落结构”时
 * 做纯文本重排：按中文序号（一、二、…）与既有换行切段，恢复公文的段落版式。
 * 结构化 HTML（含 table/img/h2/多个 p）不受影响，保持原 HTML + 公文 CSS；
 * 结构化块（bodyBlocksJson）走 blocksToHtml，不经本函数。
 */
const SEQ_SPLIT_RE = /[一二三四五六七八九十]{1,3}、/g;
// 允许切分的后续章节关键词：防止把“共7个标段：标段一、标段二…”误切
const SEQ_HEAD_RE =
  /^[一二三四五六七八九十]{1,3}、\s*(招标条件|项目概况|招标范围|标段划分|投标人|资格|招标文件|投标文件|开标|评标|定标|中标|成交|结果|公示|公告|异议|发布|媒介|联系|监督|其他|合同|验收|结算|支付)/;
const PUNC_END_RE = /[。：；！？）】」』]\s*$/;

function decodeEntities(s: string): string {
  return s
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;|&apos;/gi, "'")
    .replace(/&mdash;/gi, '—')
    .replace(/&ndash;/gi, '–')
    .replace(/&hellip;/gi, '…')
    .replace(/&#(\d+);/g, (_m, n) => String.fromCodePoint(Number(n)))
    .replace(/&#x([0-9a-f]+);/gi, (_m, n) => String.fromCodePoint(parseInt(n, 16)));
}

/** 首段若以公告标题开头（真实数据常见整句回显），裁掉重复部分，避免标题下再叠一遍。
 * 仅处理“完整标题重复”或“标题前缀残缺回显且后跟标点”两种情况，防止误删正常正文开头。 */
function stripLeadTitle(line: string, title: string): string {
  if (!title) return line;
  const norm = (s: string) => s.replace(/\s+/g, '').replace(/[/／]/g, '');
  const tn = norm(title);
  if (!tn) return line;
  let s = line.trim();
  // ① 整行就是标题重复 → 整行删除
  if (norm(s) === tn) return '';
  // ② 行以完整标题开头 → 裁掉该标题（允许空格/斜杠差异）
  for (let k = 0; k < 4; k += 1) {
    let i = 0;
    let j = 0;
    while (i < s.length && j < tn.length) {
      const ch = s[i]!;
      if (/\s|[/／]/.test(ch)) {
        i += 1;
        continue;
      }
      if (ch !== tn[j]) break;
      i += 1;
      j += 1;
    }
    if (j < tn.length || j === 0) break;
    s = s.slice(i).trim();
    if (!s) return '';
  }
  // ③ 剩余以标题前缀回显开头且后跟标点（如“…维修项目（项目编号…”）→ 仅裁回显前缀
  let i2 = 0;
  let j2 = 0;
  while (i2 < s.length && j2 < tn.length) {
    const ch = s[i2]!;
    if (/\s|[/／]/.test(ch)) {
      i2 += 1;
      continue;
    }
    if (ch !== tn[j2]) break;
    i2 += 1;
    j2 += 1;
  }
  if (j2 >= 12) {
    const rest = s.slice(i2);
    if (rest && /^[（(：:\s，,。.；;]/.test(rest[0]!)) {
      s = rest.trim();
    }
  }
  return s;
}

/** 把超长段按中文序号切成若干段落；仅在章节词前切，避免误切“标段一、二” */
function splitSeq(line: string): string[] {
  const out: string[] = [];
  let last = 0;
  let m: RegExpExecArray | null;
  SEQ_SPLIT_RE.lastIndex = 0;
  while ((m = SEQ_SPLIT_RE.exec(line))) {
    const idx = m.index;
    if (idx === 0) {
      last = idx;
      continue;
    }
    const head = line.slice(idx);
    const prev = line[idx - 1];
    const okBoundary =
      PUNC_END_RE.test(line.slice(0, idx)) || (prev && !/[段号包件]/.test(prev));
    if (okBoundary && SEQ_HEAD_RE.test(head)) {
      if (idx > last) out.push(line.slice(last, idx).trim());
      last = idx;
    }
  }
  if (last < line.length) {
    const rest = line.slice(last).trim();
    if (rest) out.push(rest);
  }
  return out;
}

/**
 * 公告正文“公文化”智能排版（遗留拍平正文兜底；bodyBlocks/contentHtmlSafe 结构化时原样返回）。
 * 返回可安全 v-html 的片段。
 */
export function prettyNoticeHtml(html: string | null | undefined): string {
  if (!html) return '';
  // 含表格/图片/列表/多级标题 → 结构化内容，保留原 HTML（公文 CSS 已覆盖）
  if (/<(?:table|img|ul|ol|li|h[2-6])[ >]/i.test(html)) return html;

  const h1m = html.match(/<h1[^>]*>([\s\S]*?)<\/h1>/i);
  const title = h1m ? decodeEntities(h1m[1]!.replace(/<[^>]+>/g, '')).trim() : '';
  const body = html.replace(/<h1[^>]*>[\s\S]*?<\/h1>/i, '');

  const lines = decodeEntities(
    body.replace(/<br\s*\/?>/gi, '\n').replace(/<[^>]+>/g, '\n'),
  )
    .split('\n')
    .map((t) => t.trim())
    .filter(Boolean);
  if (!lines.length) return html;

  // 只对“存在超长连续文本”的正文做公文化重排；短段落结构（多行排布的正文）原样展示
  const maxLen = Math.max(...lines.map((l) => l.length));
  const totalLen = lines.reduce((n, l) => n + l.length, 0);
  if (maxLen < 260 || totalLen < 400) return html;

  const paras: string[] = [];
  lines.forEach((line, idx) => {
    if (idx === 0) line = stripLeadTitle(line, title);
    if (!line) return;
    if (line.length > 140) {
      paras.push(...splitSeq(line));
    } else {
      paras.push(line);
    }
  });
  const parts: string[] = [];
  if (title) parts.push(`<h1 class="doc-title">${esc(title)}</h1>`);
  for (const p of paras) {
    if (p) parts.push(`<p class="doc-para">${esc(p)}</p>`);
  }
  return parts.join('\n');
}

/* ---------- 正文结构化块 → 安全 HTML（02 §4：heading/paragraph/table/list） ---------- */

function renderBlock(b: NoticeBodyBlock): string {
  switch (b.type) {
    case 'heading': {
      const lv = Number.isInteger(b.level) ? Math.min(6, Math.max(1, b.level)) : 2;
      const text = b.text ?? '';
      return text ? `<h${lv}>${esc(text)}</h${lv}>` : '';
    }
    case 'paragraph': {
      const text = (b.text ?? '').trim();
      return text ? `<p class="doc-para">${esc(text)}</p>` : '';
    }
    case 'table': {
      const cap = b.caption ? `<caption>${esc(b.caption)}</caption>` : '';
      const head = Array.isArray(b.headers) && b.headers.length
        ? `<thead><tr>${b.headers.map((h) => `<th>${esc(h)}</th>`).join('')}</tr></thead>`
        : '';
      const rows = Array.isArray(b.rows) ? b.rows : [];
      const body = rows.length
        ? `<tbody>${rows
            .map(
              (r) =>
                `<tr>${(Array.isArray(r) ? r : []).map((c) => `<td>${esc(c)}</td>`).join('')}</tr>`,
            )
            .join('')}</tbody>`
        : '';
      if (!head && !body) return '';
      return `<table>${cap}${head}${body}</table>`;
    }
    case 'list': {
      const items = Array.isArray(b.items) ? b.items : [];
      if (!items.length) return '';
      const tag = b.ordered ? 'ol' : 'ul';
      return `<${tag}>${items.map((it) => `<li>${esc(it)}</li>`).join('')}</${tag}>`;
    }
    default:
      return '';
  }
}

/**
 * 正文结构化块 → 可安全 v-html 的 HTML（文本一律先转义，v-dompurify-html 二次净化兜底）。
 * 空数组/非块内容返回 ''。
 */
export function blocksToHtml(blocks: NoticeBodyBlock[] | null | undefined): string {
  if (!blocks || !blocks.length) return '';
  return blocks.map(renderBlock).filter(Boolean).join('\n');
}
