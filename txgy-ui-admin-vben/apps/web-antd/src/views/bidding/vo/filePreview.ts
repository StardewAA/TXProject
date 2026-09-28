/**
 * 文件预览 · 多格式渲染纯函数集（2026-10-01，调查报告方案 A 落地）
 * ---------------------------------------------------------------------------
 * 【背景】PdfPreview.vue 原先只能预览 PDF/图片/UTF-8 文本，其余类型只能"新窗口打开/下载"。
 *  真实数据 96%+ 是 PDF+图片，但库里还有 docx(15)/csv(8)/txt(35 可能 GBK) 等 ——
 *  本模块提供按扩展名分发 + 字节级渲染的纯逻辑（vitest 直测，不含 DOM）。
 *
 * 【渲染矩阵】
 *  kind      | 渲染方式
 *  pdf/image | 不改：iframe / <img>（原生）
 *  text/csv  | 字节解码（UTF-8 优先，乱码回退 GB18030）→ <pre>（原 iframe 对 GBK 会乱码）
 *  docx      | 字节 → mammoth convertToHtml → HTML（training 资源页同款先例）
 *  xlsx/xls  | 字节 → SheetJS read + sheet_to_html → HTML 表格（只读、行/列截断、无公式计算）
 *  other     | zip/rar/pptx 等不入表 → 组件兜底"新窗口打开/下载"
 *
 * 【理由】详见 docs/模块设计与规范/调查报告-主流文件在线预览能力与成本评估-2026-10-01.md：
 *  纯前端方案、后端零改动；SheetJS 锁定 0.18.5（CE 最后 npm 版，后续转收费 CDN）。
 * ---------------------------------------------------------------------------
 */
export type PreviewKind =
  | 'csv'
  | 'docx'
  | 'image'
  | 'other'
  | 'pdf'
  | 'text'
  | 'xlsx';

/** 图片扩展名 */
const IMAGE_RE = /\.(bmp|gif|jpe?g|png|svg|webp)$/i;
/** 纯文本类（浏览器 iframe/<pre> 可渲染；UTF-8 或 GBK 均可解码） */
const TEXT_RE = /\.(html?|json|log|md|sig\.txt|txt|xml)$/i;

/** 按文件名/URL 判定渲染分类（大小写不敏感；未知扩展名 → other，走"新窗口/下载"兜底） */
export function detectPreviewKind(fileName?: null | string): PreviewKind {
  const name = (fileName ?? '').trim();
  const lower = name.toLowerCase();
  if (!lower) return 'other';
  if (lower.endsWith('.pdf')) return 'pdf';
  if (IMAGE_RE.test(name)) return 'image';
  if (lower.endsWith('.docx') || lower.endsWith('.docm')) return 'docx';
  if (lower.endsWith('.xlsx') || lower.endsWith('.xls') || lower.endsWith('.xlsm')) return 'xlsx';
  if (lower.endsWith('.csv')) return 'csv';
  if (TEXT_RE.test(name)) return 'text';
  return 'other';
}

/** HTML 转义（文本类渲染进 <pre v-html> 前必须转义，防文件内容注入） */
export function escHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

/**
 * 文本字节 → 字符串：先按 UTF-8 解码；若出现替换符（U+FFFD，说明字节非法/非 UTF-8），
 * 回退按 GB18030（兼容 GBK/GB2312，国产系统导出常用）解码 —— 解决 iframe 对 GBK txt/csv 乱码问题。
 */
export function decodeText(bytes: Uint8Array): string {
  const utf8 = new TextDecoder('utf-8', { fatal: false }).decode(bytes);
  if (!utf8.includes('\uFFFD')) return utf8;
  try {
    return new TextDecoder('gb18030').decode(bytes);
  } catch {
    return utf8; // 环境无 gb18030 支持时退回 UTF-8 结果
  }
}

/** 表格类字节 → HTML table（SheetJS 只读渲染：无公式计算、无宏；行/列超限截断并注明） */
export async function sheetToHtmlTable(
  bytes: ArrayBuffer,
  options: { maxCols?: number; maxRows?: number } = {},
): Promise<{ html: string; rows: number; truncated: boolean }> {
  const { maxCols = 50, maxRows = 2000 } = options;
  const XLSX = await loadXlsx();
  const wb = XLSX.read(bytes, { type: 'array' });
  const sheet = wb.Sheets[wb.SheetNames[0]];
  if (!sheet) return { html: '<p class="pv-blank">该工作簿没有工作表</p>', rows: 0, truncated: false };
  const ref = sheet['!ref'];
  if (!ref) return { html: '<p class="pv-blank">工作表为空</p>', rows: 0, truncated: false };
  const range = XLSX.utils.decode_range(ref);
  const totalRows = range.e.r - range.s.r + 1;
  const totalCols = range.e.c - range.s.c + 1;
  const truncated = totalRows > maxRows || totalCols > maxCols;
  // 按截断后范围生成 HTML（sheet_to_html 支持 range 选项）
  const html = XLSX.utils.sheet_to_html(sheet, {
    header: '',
    footer: '',
    id: '',
    editable: false,
    raw: true,
    range: {
      s: { r: range.s.r, c: range.s.c },
      e: { r: Math.min(range.e.r, range.s.r + maxRows - 1), c: Math.min(range.e.c, range.s.c + maxCols - 1) },
    },
  });
  return { html, rows: Math.min(totalRows, maxRows), truncated };
}

/** docx 字节 → HTML（mammoth，training 资源页同款；复杂版式有损，预览"近似"） */
export async function docxToHtml(bytes: ArrayBuffer): Promise<{ html: string }> {
  const mammoth = await loadMammoth();
  const result = await mammoth.convertToHtml({ arrayBuffer: bytes });
  return { html: result.value || '<p class="pv-blank">文档内容为空</p>' };
}

/** 懒加载 SheetJS（仅首次预览表格类时加载） */
let xlsxPromise: null | Promise<typeof import('xlsx')> = null;
function loadXlsx(): Promise<typeof import('xlsx')> {
  xlsxPromise ??= import('xlsx');
  return xlsxPromise;
}

/** 懒加载 mammoth.browser（仅首次预览 docx 时加载） */
let mammothPromise: null | Promise<any> = null;
function loadMammoth(): Promise<any> {
  mammothPromise ??= import('mammoth/mammoth.browser').then((m) => m.default ?? m);
  return mammothPromise;
}
