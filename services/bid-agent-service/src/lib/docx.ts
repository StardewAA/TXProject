/**
 * 零依赖 docx 生成：把「章节目录 + 每章 markdown 草稿」排版为可直接交付的 Word 文档。
 * 样式约定（中文标书常用）：
 *   封面黑体居中大字；章标题黑体小二；节标题黑体四号；正文宋体小四、1.5 倍行距、首行缩进 2 字符。
 */
import { zipStore } from './zip.js';

export interface DocChapter {
  title: string;
  md: string;
  /** 待复核：草稿因检索未命中等原因留白。一键生成无人工确认闸门，交付件中对这类章节做显著标注 */
  review?: boolean;
}

export interface CoverInfo {
  projectId?: string;
  projectName?: string;
  org?: string;
  subTitle?: string;
}

type Block =
  | { t: 'heading'; level: number; text: string }
  | { t: 'para'; text: string }
  | { t: 'bullet'; text: string }
  | { t: 'quote'; text: string }
  | { t: 'hr' }
  | { t: 'pagebreak' }
  | { t: 'table'; header: string[]; rows: string[][] };

const xml = (s: string): string =>
  s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/\u00a0/g, ' ');

export function parseMarkdown(md: string): Block[] {
  const lines = md.split(/\r?\n/);
  const blocks: Block[] = [];
  let i = 0;
  while (i < lines.length) {
    const line = lines[i]!.replace(/\u0000/g, '');
    const trimmed = line.trim();
    if (!trimmed) { i++; continue; }
    if (/^#{1,6}\s/.test(trimmed)) {
      const level = trimmed.match(/^#{1,6}/)![0].length;
      blocks.push({ t: 'heading', level, text: inline(trimmed.replace(/^#{1,6}\s*/, '')) });
      i++;
      continue;
    }
    if (/^\s*[-*]\s+/.test(trimmed)) {
      blocks.push({ t: 'bullet', text: inline(trimmed.replace(/^\s*[-*]\s+/, '')) });
      i++;
      continue;
    }
    if (/^\s*>\s?/.test(trimmed)) {
      blocks.push({ t: 'quote', text: inline(trimmed.replace(/^\s*>\s?/, '')) });
      i++;
      continue;
    }
    if (/^\s*([-*_])\1\1+\s*$/.test(trimmed)) {
      blocks.push({ t: 'hr' });
      i++;
      continue;
    }
    if (trimmed === '\\page' || trimmed === '<<<PAGEBREAK>>>') {
      blocks.push({ t: 'pagebreak' });
      i++;
      continue;
    }
    // markdown 表格
    if (/^\|.+\|$/.test(trimmed) && i + 1 < lines.length && /^\|[\s:|-]+\|$/.test(lines[i + 1]!.trim())) {
      const header = splitRow(trimmed);
      i += 2;
      const rows: string[][] = [];
      while (i < lines.length && /^\|.+\|$/.test(lines[i]!.trim())) {
        rows.push(splitRow(lines[i]!.trim()));
        i++;
      }
      blocks.push({ t: 'table', header, rows });
      continue;
    }
    blocks.push({ t: 'para', text: inline(trimmed) });
    i++;
  }
  return blocks;
}

function splitRow(line: string): string[] {
  return line
    .replace(/^\|/, '')
    .replace(/\|$/, '')
    .split('|')
    .map((c) => inline(c.trim()));
}

function inline(text: string): string {
  return text
    .replace(/\*\*([^*]+)\*\*/g, '<b>$1</b>')
    .replace(/(^|[^*])\*([^*\n]+)\*/g, '$1<i>$2</i>')
    .replace(/`([^`]+)`/g, '<mono>$1</mono>');
}

/* ---------------- OOXML 部件 ---------------- */

/** pPr 子元素按 ECMA-376 顺序输出，避免 Word 拒绝 */
function paraXml(
  runs: Array<{ t: string; b?: boolean; sz?: string; font?: string; color?: string }>,
  opts: {
    style?: string;
    jc?: string;
    spacingAfter?: string;
    firstLine?: string;
    keepNext?: boolean;
    spacingBefore?: string;
  } = {},
): string {
  const parts: Array<string | null> = [
    opts.style ? `<w:pStyle w:val="${opts.style}"/>` : null,
    opts.keepNext ? '<w:keepNext/>' : null,
    (() => {
      const attrs: string[] = [];
      if (opts.spacingBefore) attrs.push(`w:before="${opts.spacingBefore}"`);
      attrs.push(`w:after="${opts.spacingAfter || '120'}"`);
      attrs.push('w:line="360" w:lineRule="auto"');
      return `<w:spacing ${attrs.join(' ')}/>`;
    })(),
    opts.firstLine ? `<w:ind w:firstLineChars="${opts.firstLine}"/>` : null,
    opts.jc ? `<w:jc w:val="${opts.jc}"/>` : null,
  ];
  const pPr = parts.filter((x): x is string => x != null).join('');
  const runsXml = runs
    .map((r) => {
      const props: string[] = [];
      if (r.font) props.push(`<w:rFonts w:ascii="${r.font}" w:eastAsia="${r.font}" w:hAnsi="${r.font}"/>`);
      if (r.sz) props.push(`<w:sz w:val="${r.sz}"/><w:szCs w:val="${r.sz}"/>`);
      if (r.b) props.push('<w:b/>');
      if (r.color) props.push(`<w:color w:val="${r.color}"/>`);
      return `<w:r>${props.length ? `<w:rPr>${props.join('')}</w:rPr>` : ''}<w:t xml:space="preserve">${xml(r.t)}</w:t></w:r>`;
    })
    .join('');
  return `<w:p>${pPr ? `<w:pPr>${pPr}</w:pPr>` : ''}${runsXml}</w:p>`;
}

function contentPara(text: string, level: number): string {
  // 解析 <b>/<i>/<mono> 行内标签（已在 inline 转义）
  const tokens: Array<{ t: string; b?: boolean; i?: boolean; mono?: boolean }> = [];
  const re = /(<\/?(?:b|i|mono)>)/g;
  const parts = text.split(re);
  let bold = false;
  let italic = false;
  let mono = false;
  for (const part of parts) {
    if (part === '<b>') { bold = true; continue; }
    if (part === '</b>') { bold = false; continue; }
    if (part === '<i>') { italic = true; continue; }
    if (part === '</i>') { italic = false; continue; }
    if (part === '<mono>') { mono = true; continue; }
    if (part === '</mono>') { mono = false; continue; }
    if (part) tokens.push({ t: part, b: bold, i: italic, mono });
  }
  const runs = tokens.map((tk) => ({
    t: tk.t,
    b: tk.b,
    mono: tk.mono,
  }));
  const sizes = { 1: '36', 2: '32', 3: '28', 4: '24' } as Record<number, string>;
  const fonts = { 1: '黑体', 2: '黑体', 3: '黑体', 4: '黑体' } as Record<number, string>;
  const jc = level === 1 ? 'center' : undefined;
  return paraXml(runs, {
    style: `Heading${Math.min(level, 4)}`,
    jc,
    keepNext: true,
  });
}

function normalPara(runs: Array<{ t: string; b?: boolean; mono?: boolean }>): string {
  return paraXml(
    runs.map((r) => ({ t: r.t, b: r.b, mono: r.mono, font: '宋体', sz: '24' })),
    { firstLine: '200' },
  );
}

function bulletPara(text: string): string {
  return `<w:p><w:pPr><w:pStyle w:val="ListParagraph"/><w:spacing w:after="40" w:line="360" w:lineRule="auto"/><w:ind w:left="720" w:leftChars="200" w:hanging="360" w:hangingChars="100"/></w:pPr><w:r><w:rPr><w:rFonts w:ascii="宋体" w:eastAsia="宋体" w:hAnsi="宋体"/><w:sz w:val="24"/></w:rPr><w:t>• </w:t></w:r><w:r><w:rPr><w:rFonts w:ascii="宋体" w:eastAsia="宋体" w:hAnsi="宋体"/><w:sz w:val="24"/></w:rPr><w:t xml:space="preserve">${xml(stripInline(text))}</w:t></w:r></w:p>`;
}

function stripInline(text: string): string {
  return text.replace(/<[^>]+>/g, '');
}

function quotePara(text: string): string {
  return `<w:p><w:pPr><w:spacing w:after="80" w:line="360" w:lineRule="auto"/><w:ind w:left="420"/></w:pPr><w:r><w:rPr><w:i/><w:rFonts w:ascii="楷体" w:eastAsia="楷体" w:hAnsi="楷体"/><w:sz w:val="24"/></w:rPr><w:t xml:space="preserve">${xml(stripInline(text))}</w:t></w:r></w:p>`;
}

function tableXml(header: string[], rows: string[][]): string {
  const cell = (text: string, bold: boolean) =>
    `<w:tc><w:tcPr><w:tcW w:w="0" w:type="auto"/><w:shd w:val="clear" w:color="auto" w:fill="${bold ? 'D9E2F3' : 'FFFFFF'}"/></w:tcPr><w:p><w:pPr><w:spacing w:after="20" w:line="300" w:lineRule="auto"/><w:jc w:val="left"/></w:pPr><w:r><w:rPr><w:rFonts w:ascii="宋体" w:eastAsia="宋体" w:hAnsi="宋体"/><w:sz w:val="20"/><w:szCs w:val="20"/>${bold ? '<w:b/>' : ''}</w:rPr><w:t xml:space="preserve">${xml(stripInline(text))}</w:t></w:r></w:p></w:tc>`;
  const cols = Math.max(header.length, ...rows.map((r) => r.length), 1);
  const pad = (arr: string[]) => {
    const a = [...arr];
    while (a.length < cols) a.push('');
    return a;
  };
  const tr = (cells: string[], bold: boolean) =>
    `<w:tr>${cells.map((c) => cell(c, bold)).join('')}</w:tr>`;
  return `<w:tbl><w:tblPr><w:tblW w:w="9600" w:type="dxa"/><w:tblBorders>${['top', 'left', 'bottom', 'right', 'insideH', 'insideV']
    .map((b) => `<w:${b} w:val="single" w:sz="4" w:color="999999"/>`)
    .join('')}</w:tblBorders><w:tblLook w:val="04A0"/></w:tblPr><w:tblGrid>${Array(cols)
    .fill('<w:gridCol w:w="0"/>')
    .join('')}</w:tblGrid>${tr(pad(header), true)}${rows.map((r) => tr(pad(r), false)).join('')}</w:tbl><w:p><w:pPr><w:spacing w:after="60"/></w:pPr></w:p>`;
}

function emptyPara(lines = 1): string {
  return Array(lines).fill('<w:p/>').join('');
}

/* ---------------- 目录与页脚（B9） ---------------- */

/** 目录域：Word 打开后按提示“更新域”即可生成带页码的目录（Heading1-3 参与） */
function tocField(): string {
  const run = (inner: string): string =>
    `<w:r><w:rPr><w:rFonts w:ascii="宋体" w:eastAsia="宋体" w:hAnsi="宋体"/><w:sz w:val="24"/></w:rPr>${inner}</w:r>`;
  return [
    '<w:p><w:pPr><w:pStyle w:val="Heading1"/><w:jc w:val="center"/><w:spacing w:after="240" w:line="360" w:lineRule="auto"/></w:pPr>',
    run('<w:t>目　录</w:t>'),
    '</w:p>',
    '<w:p><w:pPr><w:spacing w:after="120" w:line="360" w:lineRule="auto"/></w:pPr>',
    run('<w:fldChar w:fldCharType="begin"/>'),
    run('<w:instrText xml:space="preserve"> TOC \\o "1-3" \\h \\z \\u </w:instrText>'),
    run('<w:fldChar w:fldCharType="separate"/>'),
    run('<w:t>（目录尚未生成：在 Word 中全选后按 F9，或右键“更新域”）</w:t>'),
    run('<w:fldChar w:fldCharType="end"/>'),
    '</w:p>',
  ].join('');
}

/** 页脚：居中“第 N 页”（PAGE 域） */
function footerXml(): string {
  const props = '<w:rPr><w:rFonts w:ascii="宋体" w:eastAsia="宋体" w:hAnsi="宋体"/><w:sz w:val="20"/><w:szCs w:val="20"/></w:rPr>';
  const run = (inner: string): string => `<w:r>${props}${inner}</w:r>`;
  return (
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n`
    + `<w:ftr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">`
    + '<w:p><w:pPr><w:jc w:val="center"/><w:spacing w:after="0"/></w:pPr>'
    + run('<w:t xml:space="preserve">第 </w:t>')
    + run('<w:fldChar w:fldCharType="begin"/>')
    + run('<w:instrText xml:space="preserve"> PAGE </w:instrText>')
    + run('<w:fldChar w:fldCharType="separate"/>')
    + run('<w:t>1</w:t>')
    + run('<w:fldChar w:fldCharType="end"/>')
    + run('<w:t xml:space="preserve"> 页</w:t>')
    + '</w:p></w:ftr>'
  );
}

/** 打开文档时自动更新域（目录/页码），避免交付件显示占位文本 */
function settingsXml(): string {
  return (
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n`
    + `<w:settings xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">`
    + '<w:updateFields w:val="true"/>'
    + '</w:settings>'
  );
}

/* ---------------- 文档组装 ---------------- */

export function buildDocx(opts: { cover: CoverInfo; chapters: DocChapter[] }): Buffer {
  const body: string[] = [];
  const now = new Date();
  const dateText = `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日`;

  // 封面
  body.push(emptyPara(4));
  if (opts.cover.projectName) {
    body.push(paraXml([{ t: opts.cover.projectName, b: true, font: '黑体', sz: '36' }], { jc: 'center' }));
    body.push(emptyPara(1));
  }
  body.push(paraXml([{ t: '投  标  文  件', b: true, font: '黑体', sz: '56' }], { jc: 'center' }));
  body.push(emptyPara(1));
  if (opts.cover.subTitle) {
    body.push(paraXml([{ t: opts.cover.subTitle, font: '宋体', sz: '30' }], { jc: 'center' }));
    body.push(emptyPara(1));
  }
  body.push(emptyPara(2));
  body.push(paraXml([{ t: opts.cover.org || '（投标人名称）', b: true, font: '宋体', sz: '30' }], { jc: 'center' }));
  body.push(paraXml([{ t: dateText, font: '宋体', sz: '30' }], { jc: 'center' }));
  body.push(emptyPara(2));
  if (opts.cover.projectId) {
    body.push(paraXml([{ t: `项目编号：${opts.cover.projectId}`, font: '宋体', sz: '24' }], { jc: 'center' }));
  }
  body.push('<w:p><w:pPr><w:spacing w:before="240" w:after="120"/></w:pPr><w:r><w:br w:type="page"/></w:r></w:p>');

  // 目录页（B9）：生成 TOC 域，正式交付前在 Word 中更新域即可
  body.push(tocField());
  body.push('<w:p><w:r><w:br w:type="page"/></w:r></w:p>');

  // 章节
  for (const ch of opts.chapters) {
    const blocks = parseMarkdown(ch.md);
    // 章标题挂 Heading1：既保持原有视觉样式，又能被目录域收集；待复核章节显著标注（一键生成无人工确认闸门）
    const chapterTitle = ch.review ? `【待复核】${ch.title}` : ch.title;
    body.push(paraXml([{ t: chapterTitle, b: true, font: '黑体', sz: '32' }], { style: 'Heading1', jc: 'center', keepNext: true, spacingAfter: '240' }));
    // 去掉每个草稿最顶部的重复标题（草稿常以 '# 标题' 开头）
    let firstHeadingSkipped = false;
    for (const blk of blocks) {
      if (!firstHeadingSkipped && blk.t === 'heading') {
        // 若首个标题与章名明显相同则跳过；否则作为正文小节
        if (stripInline(blk.text) === ch.title.trim()) { firstHeadingSkipped = true; continue; }
      }
      if (blk.t === 'heading') {
        body.push(contentPara(stripInline(blk.text), Math.max(2, Math.min(blk.level, 4))));
      } else if (blk.t === 'para') {
        body.push(normalPara([{ t: stripInline(blk.text), mono: false }]));
      } else if (blk.t === 'bullet') {
        body.push(bulletPara(blk.text));
      } else if (blk.t === 'quote') {
        body.push(quotePara(blk.text));
      } else if (blk.t === 'table') {
        body.push(tableXml(blk.header, blk.rows));
      } else if (blk.t === 'pagebreak') {
        body.push('<w:p><w:r><w:br w:type="page"/></w:r></w:p>');
      }
    }
    body.push(emptyPara(1));
  }

  const documentXml =
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n` +
    `<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">` +
    `<w:body>${body.join('')}<w:sectPr><w:footerReference w:type="default" r:id="rId2"/><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="720" w:footer="720" w:gutter="0"/></w:sectPr></w:body></w:document>`;

  const stylesXml =
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n` +
    `<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">` +
    `<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Times New Roman" w:eastAsia="宋体" w:hAnsi="Times New Roman"/><w:sz w:val="24"/><w:szCs w:val="24"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="120" w:line="360" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>` +
    `<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style>` +
    `<w:style w:type="paragraph" w:styleId="ListParagraph"><w:name w:val="ListParagraph"/><w:basedOn w:val="Normal"/><w:ind w:left="720"/></w:style>` +
    [1, 2, 3, 4]
      .map(
        (lv) =>
          `<w:style w:type="paragraph" w:styleId="Heading${lv}"><w:name w:val="heading ${lv}"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/><w:pPr><w:outlineLvl w:val="${lv - 1}"/><w:spacing w:before="${lv === 1 ? 360 : 240}" w:after="${lv === 1 ? 240 : 120}" w:line="360" w:lineRule="auto"/></w:pPr><w:rPr><w:rFonts w:ascii="黑体" w:eastAsia="黑体" w:hAnsi="黑体"/><w:b/><w:color w:val="000000"/><w:sz w:val="${lv === 1 ? 32 : lv === 2 ? 28 : lv === 3 ? 24 : 22}"/><w:szCs w:val="${lv === 1 ? 32 : lv === 2 ? 28 : lv === 3 ? 24 : 22}"/></w:rPr></w:style>`,
      )
      .join('') +
    `</w:styles>`;

  const contentTypes =
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n` +
    `<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">` +
    `<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>` +
    `<Default Extension="xml" ContentType="application/xml"/>` +
    `<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>` +
    `<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>` +
    `<Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>` +
    `<Override PartName="/word/settings.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml"/>` +
    `<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>` +
    `<Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>` +
    `</Types>`;

  const rootRels =
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n` +
    `<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">` +
    `<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>` +
    `<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>` +
    `<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>` +
    `</Relationships>`;

  const docRels =
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n` +
    `<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">` +
    `<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>` +
    `<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/>` +
    `<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/settings" Target="settings.xml"/>` +
    `</Relationships>`;

  const coreXml =
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n` +
    `<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">` +
    `<dc:title>${xml(opts.cover.projectName || '投标文件')}</dc:title>` +
    `<dc:creator>bid-agent-service</dc:creator>` +
    `<cp:lastModifiedBy>bid-agent-service</cp:lastModifiedBy>` +
    `<dcterms:created xsi:type="dcterms:W3CDTF">${now.toISOString()}</dcterms:created>` +
    `<dcterms:modified xsi:type="dcterms:W3CDTF">${now.toISOString()}</dcterms:modified>` +
    `</cp:coreProperties>`;

  const appXml =
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n` +
    `<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">` +
    `<Application>bid-agent-service</Application></Properties>`;

  return zipStore([
    { name: '[Content_Types].xml', data: Buffer.from(contentTypes, 'utf8') },
    { name: '_rels/.rels', data: Buffer.from(rootRels, 'utf8') },
    { name: 'docProps/core.xml', data: Buffer.from(coreXml, 'utf8') },
    { name: 'docProps/app.xml', data: Buffer.from(appXml, 'utf8') },
    { name: 'word/document.xml', data: Buffer.from(documentXml, 'utf8') },
    { name: 'word/_rels/document.xml.rels', data: Buffer.from(docRels, 'utf8') },
    { name: 'word/styles.xml', data: Buffer.from(stylesXml, 'utf8') },
    { name: 'word/footer1.xml', data: Buffer.from(footerXml(), 'utf8') },
    { name: 'word/settings.xml', data: Buffer.from(settingsXml(), 'utf8') },
  ]);
}

/**
 * 把一段 markdown 草稿渲染为 OOXML 正文片段（不含 <w:body> 包裹），
 * 用于把已生成的章节正文插入到招标方/标书模板的指定位置（方案3：模板当骨架）。
 * 样式约定与 buildDocx 完全一致，保证插入后的正文与预览件观感统一。
 */
export function renderMarkdownToBodyXml(md: string): string {
  const parts: string[] = [];
  for (const blk of parseMarkdown(md)) {
    if (blk.t === 'heading') {
      parts.push(contentPara(stripInline(blk.text), Math.max(2, Math.min(blk.level, 4))));
    } else if (blk.t === 'para') {
      parts.push(normalPara([{ t: stripInline(blk.text), mono: false }]));
    } else if (blk.t === 'bullet') {
      parts.push(bulletPara(blk.text));
    } else if (blk.t === 'quote') {
      parts.push(quotePara(blk.text));
    } else if (blk.t === 'table') {
      parts.push(tableXml(blk.header, blk.rows));
    } else if (blk.t === 'pagebreak') {
      parts.push('<w:p><w:r><w:br w:type="page"/></w:r></w:p>');
    }
  }
  return parts.join('');
}
