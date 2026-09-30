/**
 * 内部公告/招投标公告 —— 文章式公文 HTML 生成器。
 *
 * 设计（2026-09-21 重写）：与前台外部公告详情（portal/bidding/detail.vue 的 .docx 公文容器）对齐，
 * 输出“文章公文”格式，而不是把 payload 结构化字段堆成一张大表：
 *   · 标题居中黑体（h1.doc-title）；
 *   · 关键概要信息（发布单位/文号/项目/招标项目/标段/关键时间等）以文字行呈现，不建表；
 *   · 正文按段落（p.doc-para，首行缩进 2em、两端对齐）——仿宋字体、1.95 行高；
 *   · 内部公告附加单位红头/红线与落款（右对齐单位+日期）。
 * 安全：所有用户输入一律 HTML 转义，正文按纯文本段落化，不包含脚本/事件属性；
 * 页面内配合 v-dompurify-html（DOMPurify 白名单）二次净化，与前台外部公告同机制。
 * 产出：
 *   · noticeArticleBody() —— 正文片段（供页面 .docx 容器 v-html）；
 *   · convertNoticeToHtml() —— 完整 HTML 文档（供新窗口打印/另存 PDF）；
 *   · PDF：复用 notice/pdfGen.ts 的 generateNoticePdfBlob() 一键生成（对外最终件为盖章 PDF）。
 *
 * 四类公告分节（2026-09-22 定稿）：招标公告/变更公告/中标候选人公示/中标结果公示各有一套专属
 * 文书分节（noticeSections.ts 单一数据源），渲染在概要行与正文之间；类型切换后分节标题、
 * 字段序与数量肉眼可辨（对应设计稿 docs/四类公告布局设计与内部流转检查-2026-09-22.md）。
 * 政务报送字段（GOVERNMENT）不进分节——由平台/系统侧维护。
 * 三级编号（2026-09-23）：首节印「项目编号 → 项目名称 → 招标项目编号 → 标段（包）编号 → 标段（包）数量」，
 * 口径唯一定义在 noticeCodes.ts —— 这三种编号就是门户关注/投标流程跟踪的三级跟踪键，文书必须印全。
 */
import { buildNoticeSections } from './noticeSections';

export interface NoticeHtmlAttachment {
  name?: string;
  fileName?: string;
}

export interface NoticeHtmlInput {
  title?: string;
  noticeCode?: string;
  /** 公告根字段·真实项目编号（三级跟踪第①级；**不是**招标项目编号，见 noticeCodes.ts） */
  projectCode?: string;
  /** 公告根字段·项目名称快照（可空；空则回退数据集 PROJECT_NAME） */
  projectName?: string;
  noticeType?: string;
  publisherName?: string;
  publishTime?: number | string;
  content?: string;
  payload?: Record<string, unknown>;
  internalFlag?: boolean;
}

export interface NoticeHtmlOptions {
  mode?: 'pdf' | 'preview' | 'print';
}

/** 概要信息字段的中文标签（仅放“拟展示”的关键业务字段，避免英文 key 泄出；未知字段不入正文/概要）
 *  注：三级编号（项目编号/项目名称/招标项目编号/标段（包）编号）**不在此列** —— 它们由
 *  noticeCodes.buildCodeItems() 统一产出并落在文书首节（分节块），放这里会同一编号印两遍。 */
const SUMMARY_FIELDS: Array<[string, string]> = [
  ['TENDERER_NAME', '招标人'],
  ['UNIFIED_DEAL_CODE', '统一交易标识码'],
  ['FUND_SOURCE', '资金来源'],
  ['TENDER_CONTENT', '招标内容与范围'],
  ['DOC_GET_START_TIME', '文件获取开始'],
  ['DOC_GET_END_TIME', '文件获取截止'],
  ['BID_END_TIME', '投标递交截止'],
  ['FILE_DELIVER_END_TIME', '递交截止时间'],
  ['FILE_RECEIVE_ADDRESS', '文件获取地点'],
  ['NOMINAL_FEE', '标书费'],
];

export function escapeHtml(value: unknown): string {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function text(value: unknown, fallback = ''): string {
  return escapeHtml(value ?? fallback);
}

function hasText(value: unknown): boolean {
  return value !== undefined && value !== null && String(value).trim() !== '';
}

/** 正文 → 段落（保留换行，空值给占位） */
function paragraphs(value?: string): string {
  const raw = String(value ?? '').trim();
  if (!raw) return '<p class="doc-para doc-empty">（正文待补充）</p>';
  return raw
    .split(/\r?\n\s*\r?\n/)
    .map((p) => `<p class="doc-para">${text(p).replaceAll('\n', '<br/>')}</p>`)
    .join('');
}

function formatTime(value?: number | string): string {
  if (!hasText(value)) return '';
  const date = /^\d+$/.test(String(value)) ? new Date(Number(value)) : new Date(String(value));
  if (Number.isNaN(date.getTime())) return text(value);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}年${pad(date.getMonth() + 1)}月${pad(date.getDate())}日`;
}

/** payload 关键信息 → 概要文字行（每行“标签：值”；不建表格，避免“除了正文全放表格”） */
function summaryLead(payload?: Record<string, unknown>): Array<{ label: string; value: string }> {
  if (!payload) return [];
  const out: Array<{ label: string; value: string }> = [];
  for (const [key, label] of SUMMARY_FIELDS) {
    const raw = payload[key];
    if (hasText(raw)) out.push({ label, value: String(raw).replace(/[;；]$/, '') });
  }
  return out;
}

/** 文章正文片段 —— 供页面 .docx 公文容器 v-dompurify-html 渲染（与前台外部公告同效果） */
export function noticeArticleBody(input: NoticeHtmlInput): string {
  const lines: string[] = [];
  if (input.title?.trim()) lines.push(`<h1 class="doc-title">${text(input.title)}</h1>`);
  if (input.noticeCode?.trim()) lines.push(`<div class="doc-code">${text(input.noticeCode)}</div>`);
  const lead = summaryLead(input.payload);
  if (lead.length) {
    const leadHtml = lead
      .map((x) => `${text(x.label)}：<strong class="doc-lead-v">${text(x.value)}</strong>`)
      .join('<span class="doc-lead-sep">　</span>');
    lines.push(`<p class="doc-lead">${leadHtml}</p>`);
  }
  // 四类公告文书分节（单一数据源 noticeSections.ts；政务字段不进分节）；
  // 首节为三级编号块（项目编号/项目名称/招标项目编号/标段编号），root 传入公告根字段
  const sections = buildNoticeSections(input.noticeType, input.payload, {
    projectCode: input.projectCode,
    projectName: input.projectName,
  });
  if (sections.length) {
    for (const sec of sections) {
      const rows = sec.items
        .map((it) => `<div class="doc-sec-row"><span class="doc-sec-k">${text(it.label)}</span><span class="doc-sec-v">${text(it.value)}</span></div>`)
        .join('');
      lines.push(`<section class="doc-sec"><h2 class="doc-sec-t">${text(sec.title)}</h2>${rows}</section>`);
    }
  }
  lines.push(`<div class="doc-body">${paragraphs(input.content)}</div>`);
  return lines.join('\n');
}

/** 完整公文 HTML 文档 —— 新窗口打印 / 另存 PDF（含红头/红线/落款） */
export function convertNoticeToHtml(input: NoticeHtmlInput, _options: NoticeHtmlOptions = {}): string {
  const internal = input.internalFlag === true;
  const publisher = text(input.publisherName || '发布单位');
  const date = formatTime(input.publishTime);
  const redHead = internal ? `<div class="doc-red-head">${publisher}</div>` : '';
  const signDate = date ? `<div class="doc-foot-date">${date}</div>` : '';
  const body = noticeArticleBody(input);
  const modeClass = _options.mode ? ` doc-${_options.mode}` : '';

  return `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"/><title>${text(input.title || '公告')}</title><style>
@page{size:A4;margin:25mm 26mm 25mm 28mm}
.doc${modeClass}{color:#111;font-family:"仿宋_GB2312","FangSong_GB2312","仿宋","FangSong","STFangsong","SimSun",serif;font-size:16px;line-height:1.95}
.doc${modeClass} *{box-sizing:border-box}
.doc-red-head{padding:6px 0 8px;color:#c40000;font-family:"SimHei","黑体",sans-serif;font-size:24px;font-weight:700;letter-spacing:4px;text-align:center;border-bottom:2px solid #c40000}
.doc-code{text-align:center;margin:14px 0 6px;font-size:13px;color:#555}
.doc-title{text-align:center;margin:4px 0 20px;font-family:"SimHei","黑体","SimSun",serif;font-size:23px;font-weight:700;letter-spacing:2px;line-height:1.6}
.doc-lead{margin:4px 0 16px;font-size:14px;line-height:1.9;color:#444;text-align:justify}
.doc-lead-sep{display:inline-block;width:.6em}
.doc-sec{margin:14px 0 18px}
.doc-sec-t{font-family:"SimHei","黑体",sans-serif;font-size:17px;font-weight:700;margin:0 0 8px;padding:4px 0 4px 10px;border-left:4px solid #b02418;line-height:1.5}
.doc-sec-row{display:flex;gap:12px;padding:3px 0;font-size:15px;line-height:1.8;text-align:justify}
.doc-sec-k{flex:0 0 9.5em;color:#333;font-weight:600}
.doc-sec-v{flex:1;color:#111}
.doc-body p.doc-para{margin:0 0 2px;text-indent:2em;text-align:justify;line-height:1.95}
.doc-body p.doc-empty{text-indent:0;color:#999}
.doc-body h1,.doc-body h2,.doc-body h3{font-family:"SimHei","黑体",sans-serif;font-weight:700;margin:18px 0 4px;line-height:1.7}
.doc-foot{margin-top:32px;text-align:right;font-size:14px}
.doc-foot-date{margin-top:2px}
</style></head><body><main class="doc${modeClass}">${redHead}${body}<footer class="doc-foot"><div class="doc-foot-date">${publisher}</div>${signDate}</footer></main></body></html>`;
}