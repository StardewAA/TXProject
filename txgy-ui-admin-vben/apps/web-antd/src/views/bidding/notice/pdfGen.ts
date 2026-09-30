/**
 * 公告版式 PDF 一键生成（零依赖）
 * ================= 方案 =================
 * 项目内无任何 PDF 生成库（pdfjs-dist 仅用于阅读预览），引入 html2pdf.js 需改 package.json（团队文件、需确认）。
 * 本工具零依赖达成「一键生成」：canvas 以系统字体排版公告版式（文号/标题/红头线/正文/落款/页码），
 *   逐页输出 JPEG（DCTDecode）→ 手写 PDF 容器（Image XObject，纯格式组装，无第三方库）。
 * 输出口径（08-公告公示 §3 对外公布件 = PDF）：公告版式文件 = 盖章 PDF（对外最终件）；
 *   本生成器产出图片型 PDF（矢量文本不可选中，与扫描/盖章件语义一致），文件上传后走强制 CA 认证（盖章）。
 *
 * 四类公告分节（2026-09-22 定稿）：与 HTML 生成器共用 noticeSections.ts 单一数据源——
 *   payload.sections 为已构建的文书分节（标题 + label/value 行），排版时以两列（label 列宽按
 *   同分节最大标签宽对齐）渲染在正文之前；分节内容 = 可展示字段（SOCIAL），政务报送字段不进版式。
 * ================= 用法 =================
 *   const blob = await generateNoticePdfBlob({ title, code, content, publisher, publishTime });
 *   const file = new File([blob], '公告版式.pdf', { type: 'application/pdf' });
 *   // 交给既有上传链路（uploadAttachment → fileId → 强制 CA 认证）即可。
 */

import type { NoticeSection } from './noticeSections';

export interface NoticePdfPayload {
  /** 公告/公示名称（版式主标题，可多行居中） */
  title: string;
  /** 文号（如 TXGY-2026-009，可空） */
  code?: string;
  /** 正文全文（按纯文本处理：换行分段，自动折行分页） */
  content?: string;
  /** 落款发布单位（右对齐加粗，可空） */
  publisher?: string;
  /** 落款时间（右对齐，可空） */
  publishTime?: string;
  /** 文书分节（四类公告分节，noticeSections.ts 单一数据源；可空——不传则版式仅标题+正文） */
  sections?: NoticeSection[];
}

/* GB/T 9704-2012 公文格式（公告版式 PDF）：
 *   A4 210×297mm @150dpi = 1240×1754 px（1mm ≈ 5.906px）；
 *   页边距：天头（上白边）37mm，订口（左白边）28mm → 版心 156mm×225mm ≈ 921×1329 px；
 *   字体：正文/表格一律 3 号仿宋体字（16pt ≈ 33px，28 字/行、22 行/面），标题 2 号小标宋（黑体近似）；
 *   文字黑色；字段以居中表格展示（表头与内容对齐，细黑线边框）。
 * PDF 物理纸张 = A4 点单位（595.276×841.89 pt = 210×297mm）；canvas 保持 150dpi 像素，
 * 经 Contents cm 变换映射到 A4 页面——打印/盖章纸型正确，清晰度不降。 */
const PAGE_W = 1240;
const PAGE_H = 1754;
const PDF_W = 595.28;
const PDF_H = 841.89;
const MARGIN_X = 165; // 订口 28mm ≈ 165.4px
const MARGIN_TOP = 219; // 天头 37mm ≈ 218.5px
const MARGIN_BOTTOM = 206; // 下白边 35mm ≈ 206.7px（297-37-225）
const MAX_W = 921; // 版心宽 156mm ≈ 921px
const MAX_H = PAGE_H - MARGIN_TOP - MARGIN_BOTTOM; // 版心高 225mm ≈ 1329px（22 行/面 ≈ 60px/行）

const CODE_FONT = '32px "FangSong", "仿宋_GB2312", "SimSun", "Songti SC", serif';
const TITLE_FONT = 'bold 46px "SimHei", "Microsoft YaHei", "Noto Sans SC", sans-serif'; // 2号小标宋（黑体近似）
const BODY_FONT = '32px "FangSong", "仿宋_GB2312", "SimSun", "Songti SC", serif'; // 3号仿宋
const FOOT_FONT = 'bold 32px "FangSong", "仿宋_GB2312", "SimSun", "Songti SC", serif';
const PAGE_NO_FONT = '20px "SimSun", "Songti SC", serif';
const BODY_LINE = 60; // 22 行/面 ≈ 1329/22
const TITLE_LINE = 64;
const HEAD_RED = '#b02418';
const SEC_HEAD_FONT = 'bold 32px "SimHei", "Microsoft YaHei", "Noto Sans SC", sans-serif'; // 分节标题：3号黑体
const SEC_LABEL_FONT = 'bold 30px "FangSong", "仿宋_GB2312", "SimSun", "Songti SC", serif'; // 表头列：仿宋加粗
const SEC_VALUE_FONT = '30px "FangSong", "仿宋_GB2312", "SimSun", "Songti SC", serif'; // 内容列：3号仿宋
const SEC_LINE = 54; // 表格基础行高
const VALUE_LINE = 44; // 值折行行高
const INDENT = 64; // 正文首行缩进 2 字符（2 × 32px）

interface RenderLine {
  kind: 'body' | 'foot' | 'sec-head' | 'sec-row';
  text: string;
  bold?: boolean;
  /** 分节字段行：标签（左列，居中） */
  label?: string;
  /** 分节字段行：值（右列，居中） */
  value?: string;
  /** 分节字段行：同分节最大标签宽（两列对齐基准） */
  labelW?: number;
  /** 分节字段行：值单元格折行后的行（居中多行排版） */
  valueLines?: string[];
  /** 分节字段行：动态行高（值多行时按行数伸展） */
  h?: number;
  /** 正文首行缩进标记（公文正文首行缩进 2 字） */
  indent?: boolean;
}

const enc = new TextEncoder();

/** HTML 去标签 + 实体还原为纯文本（正文可能带标记语言；与打印版式一致只取可见文本） */
function stripHtml(src: string): string {
  return (src || '')
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/(p|div|li|tr|h[1-6])>/gi, '\n')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;/gi, "'")
    .replace(/[ \t]+/g, ' ')
    .replace(/\n{3,}/g, '\n\n');
}

/** 正文段落折行：首行可用宽度减缩进（maxWidth - indent），后续行满宽（公文首行缩进 2 字） */
function wrapParagraph(
  ctx: CanvasRenderingContext2D,
  text: string,
  maxWidth: number,
  indent: number,
): Array<{ text: string; indent: boolean }> {
  const out: Array<{ text: string; indent: boolean }> = [];
  let cur = '';
  let first = true;
  const avail = () => maxWidth - (first ? indent : 0);
  for (const ch of text) {
    const test = cur + ch;
    if (cur && ctx.measureText(test).width > avail()) {
      out.push({ text: cur, indent: first });
      cur = ch;
      first = false;
    } else {
      cur = test;
    }
  }
  if (cur) out.push({ text: cur, indent: first });
  return out;
}

/** 按 measureText 折行（逐字检查；中文/英文均安全） */
function wrapLine(ctx: CanvasRenderingContext2D, text: string, maxWidth: number): string[] {
  const lines: string[] = [];
  let cur = '';
  for (const ch of text) {
    const test = cur + ch;
    if (cur && ctx.measureText(test).width > maxWidth) {
      lines.push(cur);
      cur = ch;
    } else {
      cur = test;
    }
  }
  if (cur) lines.push(cur);
  return lines;
}

/** 排版：返回标题行 + 扁平渲染行 + 首页头部高度 */
function layout(
  ctx: CanvasRenderingContext2D,
  payload: NoticePdfPayload,
): { titleLines: string[]; lines: RenderLine[]; headH: number } {
  // 标题折行必须先切到标题字体（46px）：此前用调用残留字体测量，长标题折行偏窄、渲染溢出版心（用户 2026-10-02 反馈）
  ctx.font = TITLE_FONT;
  const titleLines = wrapLine(ctx, payload.title.trim() || '公告', MAX_W);
  const codeH = payload.code?.trim() ? 34 : 0;
  const headH = codeH + titleLines.length * TITLE_LINE + 52;

  const lines: RenderLine[] = [];
  // 版式结构（用户 2026-10-02 定稿）：上方 标题/文号 → 中间 正文 → 下方 关键字段表格 → 落款
  ctx.font = '32px "FangSong", "SimSun", "Songti SC", serif'; // 正文测量字体（3号仿宋）
  const strip = stripHtml(payload.content || '').trim();
  const paras = strip ? strip.split('\n') : ['（正文待补充）'];
  let first = true;
  for (const para of paras) {
    const p = para.trim();
    if (!p) continue;
    if (!first) lines.push({ kind: 'body', text: '' }); // 段落间距
    first = false;
    for (const w of wrapParagraph(ctx, p, MAX_W, INDENT)) {
      lines.push({ kind: 'body', text: w.text, indent: w.indent });
    }
  }
  // 下方 关键字段表格（noticeSections.ts 单一数据源）：节标题（黑体居中）+ 两列居中表格（label | value，细黑线边框，值折行居中）
  ctx.font = SEC_VALUE_FONT; // 以值字体测量折行宽（与渲染一致）
  for (const sec of payload.sections ?? []) {
    if (!sec.title?.trim() && !(sec.items ?? []).length) continue;
    if (sec.title?.trim()) lines.push({ kind: 'sec-head', text: sec.title.trim() });
    ctx.font = SEC_LABEL_FONT; // 以标签字体测量列宽，与渲染一致
    const labelW = Math.max(...(sec.items ?? []).map((it) => ctx.measureText(it.label).width), 0) + 28;
    ctx.font = SEC_VALUE_FONT; // 值折行宽度
    const valueW = MAX_W - labelW - 40;
    for (const it of sec.items ?? []) {
      const valueLines = it.value ? wrapLine(ctx, it.value, Math.max(80, valueW)) : [''];
      const h = Math.max(SEC_LINE, valueLines.length * VALUE_LINE + 14);
      lines.push({ kind: 'sec-row', text: '', label: it.label, value: it.value, labelW, valueLines, h });
    }
  }
  if (payload.publisher?.trim()) lines.push({ kind: 'foot', text: payload.publisher.trim(), bold: true });
  if (payload.publishTime?.trim()) lines.push({ kind: 'foot', text: payload.publishTime.trim() });
  return { titleLines, lines, headH };
}

/** 每页可用像素高度（首页扣除头部高度） */
function capacityOf(headH: number, pageIndex: number): number {
  const usable = MAX_H - (pageIndex === 0 ? headH : 0);
  return Math.max(120, usable);
}

/** 分页：尾部连续 foot 行优先与正文末段同页（放不下则独占新页顶部） */
/** 单行占高：表格行按动态 h（值多行伸展），分节标题/正文按固定行高 */
function lineH(row: RenderLine): number {
  if (row.kind === 'sec-row') return row.h ?? SEC_LINE;
  if (row.kind === 'sec-head') return SEC_LINE;
  return BODY_LINE;
}

function paginate(lines: RenderLine[], headH: number): RenderLine[][] {
  const pages: RenderLine[][] = [];
  let cur: RenderLine[] = [];
  let pageIndex = 0;
  const next = (): void => {
    if (cur.length) pages.push(cur);
    cur = [];
    pageIndex += 1;
  };
  let usedH = 0;
  const cap = (): number => capacityOf(headH, pageIndex);
  for (let i = 0; i < lines.length; i++) {
    const row = lines[i]!;
    if (row.kind === 'foot') {
      let j = i;
      while (j < lines.length && lines[j]!.kind === 'foot') j += 1;
      const footH = (j - i) * BODY_LINE;
      if (usedH + footH > cap() && cur.length > 0) {
        next();
        usedH = 0;
      }
      for (let k = i; k < j; k += 1) cur.push(lines[k]!);
      usedH += footH;
      i = j - 1;
      continue;
    }
    // 分节标题不孤立页底：与本表格行（后续连续 sec-row 至少首行）同页，跨页则整块后移（公文排版规范）
    if (row.kind === 'sec-head' && i + 1 < lines.length && lines[i + 1]!.kind === 'sec-row') {
      const blockH = lineH(row) + lineH(lines[i + 1]!);
      if (usedH + blockH > cap() && cur.length > 0) {
        next();
        usedH = 0;
      }
      cur.push(row);
      usedH += lineH(row);
      continue;
    }
    if (usedH + lineH(row) > cap()) {
      next();
      usedH = 0;
    }
    cur.push(row);
    usedH += lineH(row);
  }
  if (cur.length) pages.push(cur);
  if (!pages.length) pages.push([]);
  return pages;
}

/** 渲染单页为 JPEG */
function renderPage(
  payload: NoticePdfPayload,
  titleLines: string[],
  pageLines: RenderLine[],
  pageIndex: number,
  pageCount: number,
  headH: number,
): Promise<Uint8Array> {
  const canvas = document.createElement('canvas');
  canvas.width = PAGE_W;
  canvas.height = PAGE_H;
  const ctx = canvas.getContext('2d');
  if (!ctx) return Promise.reject(new Error('浏览器不支持 Canvas（无法生成版式 PDF）'));
  ctx.fillStyle = '#ffffff';
  ctx.fillRect(0, 0, PAGE_W, PAGE_H);

  let y = MARGIN_TOP;
  if (pageIndex === 0) {
    if (payload.code?.trim()) {
      ctx.font = CODE_FONT;
      ctx.textAlign = 'center';
      ctx.fillStyle = '#000';
      ctx.fillText(payload.code.trim(), PAGE_W / 2, y);
      y += 34;
    }
    ctx.font = TITLE_FONT;
    ctx.textAlign = 'center';
    ctx.fillStyle = '#000';
    for (const line of titleLines) {
      ctx.fillText(line, PAGE_W / 2, y + TITLE_LINE * 0.72);
      y += TITLE_LINE;
    }
    y += 12;
    ctx.strokeStyle = HEAD_RED;
    ctx.lineWidth = 3;
    ctx.beginPath();
    ctx.moveTo(MARGIN_X + 40, y);
    ctx.lineTo(PAGE_W - MARGIN_X - 40, y);
    ctx.stroke();
    const bodyTop = MARGIN_TOP + headH;
    if (y < bodyTop) y = bodyTop;
    else y += 30;
  }

  ctx.textAlign = 'left';
  for (const line of pageLines) {
    if (y > PAGE_H - MARGIN_BOTTOM - BODY_LINE) break;
    if (!line.text && line.kind !== 'sec-row') {
      y += BODY_LINE * 0.55;
      continue;
    }
    if (line.kind === 'sec-head') {
      // 分节标题：3号黑体居中，公文小节标题（一、二、…）
      ctx.textAlign = 'center';
      ctx.font = SEC_HEAD_FONT;
      ctx.fillStyle = '#000';
      ctx.fillText(line.text, PAGE_W / 2, y + SEC_LINE * 0.72);
      y += SEC_LINE;
    } else if (line.kind === 'sec-row') {
      // 居中表格行：左列字段名（仿宋加粗）/ 右列值（仿宋，值折行居中）
      const labelW = line.labelW ?? 220;
      const rowH = line.h ?? SEC_LINE;
      const vLines = line.valueLines && line.valueLines.length ? line.valueLines : ['—'];
      // 细黑线表格边框（上下左右 + 中竖线）
      ctx.strokeStyle = '#000';
      ctx.lineWidth = 1;
      ctx.strokeRect(MARGIN_X, y, MAX_W, rowH);
      ctx.beginPath();
      ctx.moveTo(MARGIN_X + labelW, y);
      ctx.lineTo(MARGIN_X + labelW, y + rowH);
      ctx.stroke();
      // 左列：字段名居中（垂直居中）
      ctx.textAlign = 'center';
      ctx.font = SEC_LABEL_FONT;
      ctx.fillStyle = '#000';
      ctx.fillText(line.label ?? '', MARGIN_X + labelW / 2, y + rowH / 2 + 11);
      // 右列：值居中（多行垂直居中）
      ctx.font = SEC_VALUE_FONT;
      const vc = MARGIN_X + labelW + (MAX_W - labelW) / 2;
      const blockH = vLines.length * VALUE_LINE;
      let vy = y + (rowH - blockH) / 2 + VALUE_LINE * 0.72;
      for (const vl of vLines) {
        ctx.fillText(vl, vc, vy);
        vy += VALUE_LINE;
      }
      y += rowH;
    } else if (line.kind === 'foot') {
      ctx.textAlign = 'right';
      ctx.font = line.bold ? FOOT_FONT : BODY_FONT;
      ctx.fillStyle = '#000';
      ctx.fillText(line.text, PAGE_W - MARGIN_X, y);
      y += BODY_LINE;
      ctx.textAlign = 'left';
    } else {
      // 正文：3号仿宋，首行缩进 2 字
      ctx.font = BODY_FONT;
      ctx.fillStyle = '#000';
      ctx.fillText(line.text, MARGIN_X + (line.indent ? INDENT : 0), y);
      y += BODY_LINE;
    }
  }
  if (pageCount > 1) {
    ctx.font = PAGE_NO_FONT;
    ctx.textAlign = 'center';
    ctx.fillStyle = '#000';
    ctx.fillText('第 ' + (pageIndex + 1) + ' 页 / 共 ' + pageCount + ' 页', PAGE_W / 2, PAGE_H - 44);
  }
  return new Promise<Uint8Array>((resolve, reject) => {
    canvas.toBlob(
      (b) => {
        if (!b) {
          reject(new Error('canvas 编码失败'));
          return;
        }
        b.arrayBuffer()
          .then((ab) => resolve(new Uint8Array(ab)))
          .catch(reject);
      },
      'image/jpeg',
      0.92,
    );
  });
}

/** 组装多页 PDF 容器（每页 = Page + Contents + Image XObject / DCTDecode） */
function assemblePdf(pages: Uint8Array[]): Blob {
  const parts: Uint8Array[] = [];
  const offsets: number[] = [];
  let pos = 0;
  const push = (s: string): void => {
    const b = enc.encode(s);
    parts.push(b);
    pos += b.length;
  };
  const pushBin = (b: Uint8Array): void => {
    parts.push(b);
    pos += b.length;
  };
  const beginObj = (n: number) => {
    offsets[n] = pos;
    push(n + ' 0 obj\n');
  };
  const endObj = () => push('endobj\n');

  push('%PDF-1.4\n%txgy-bidding-notice\n');
  const count = pages.length;
  beginObj(1);
  push('<< /Type /Catalog /Pages 2 0 R >>\n');
  endObj();
  beginObj(2);
  push('<< /Type /Pages /Count ' + count + ' /Kids [');
  for (let i = 0; i < count; i += 1) push(' ' + (3 + i * 3) + ' 0 R');
  push(' ] >>\n');
  endObj();
  for (let i = 0; i < count; i += 1) {
    const pageNum = 3 + i * 3;
    const contNum = pageNum + 1;
    const imgNum = pageNum + 2;
    const stream = 'q\n' + PDF_W + ' 0 0 ' + PDF_H + ' 0 0 cm\n/Im0 Do\nQ';
    const streamBytes = enc.encode(stream);
    const img = pages[i]!;
    beginObj(pageNum);
    push('<< /Type /Page /Parent 2 0 R /MediaBox [ 0 0 ' + PDF_W + ' ' + PDF_H + ' ]');
    push(' /Resources << /XObject << /Im0 ' + imgNum + ' 0 R >> >>');
    push(' /Contents ' + contNum + ' 0 R >>\n');
    endObj();
    beginObj(contNum);
    push('<< /Length ' + streamBytes.length + ' >>\nstream\n');
    pushBin(streamBytes);
    push('\nendstream\n');
    endObj();
    beginObj(imgNum);
    push('<< /Type /XObject /Subtype /Image /Width ' + PAGE_W + ' /Height ' + PAGE_H);
    push(' /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ' + img.length + ' >>\nstream\n');
    pushBin(img);
    push('\nendstream\n');
    endObj();
  }
  const xrefPos = pos;
  const objTotal = 2 + count * 3;
  let xref = 'xref\n0 ' + (objTotal + 1) + '\n';
  xref += '0000000000 65535 f \n';
  for (let n = 1; n <= objTotal; n += 1) {
    xref += String(offsets[n] ?? 0).padStart(10, '0') + ' 00000 n \n';
  }
  push(xref);
  push('trailer\n<< /Size ' + (objTotal + 1) + ' /Root 1 0 R >>\nstartxref\n' + xrefPos + '\n%%EOF\n');
  return new Blob(parts as unknown as BlobPart[], { type: 'application/pdf' });
}

/** 一键生成公告版式 PDF（图片型 PDF；上传后走强制 CA 认证链路） */
export async function generateNoticePdfBlob(payload: NoticePdfPayload): Promise<Blob> {
  if (!payload.title?.trim()) throw new Error('公告名称不能为空');
  await document.fonts.ready; // 等系统字体就绪，保证折行宽度准确
  const measure = document.createElement('canvas').getContext('2d');
  if (!measure) throw new Error('浏览器不支持 Canvas（无法生成版式 PDF）');
  measure.font = BODY_FONT;
  const { titleLines, lines, headH } = layout(measure, payload);
  const pages = paginate(lines, headH);
  const jpegs: Uint8Array[] = [];
  for (let i = 0; i < pages.length; i += 1) {
    jpegs.push(await renderPage(payload, titleLines, pages[i]!, i, pages.length, headH));
  }
  return assemblePdf(jpegs);
}
