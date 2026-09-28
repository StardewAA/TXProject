/**
 * MES 助手 - 单据导入解析
 *
 * 【新增模块：导入解析】
 * 1. 三种输入：上传 Word（.docx）/ 上传 Excel（.xlsx/.xls）/ 直接粘贴纯文本
 * 2. 自动识别单据类型（生产工单/领料单/入库单/采购单/检验单）：同义词 + 表格标题 + 字段标签综合打分
 * 3. 字段提取：按"标签：值"逐行匹配，标签做模糊映射（如"产品"→"产品名称"、"单号"→"工单编号"），
 *    完全对不上的字段忽略（留空），绝不编造数据
 * 4. 表格提取：定位"物料清单/工序明细/…"标题 → 识别表头行 → 逐行解析数据
 *    （支持 制表符/竖线/多空格/逗号 分隔，以及"物料编码：xxx 数量：120"内联写法）
 * 5. 仅做前端模板预览填充，不写任何后台数据库
 */
import * as XLSX from 'xlsx';

import type { BlankDocFormState, BlankDocSchema } from './blankDocSchema';
import { ALL_BLANK_SCHEMAS } from './blankDocSchema';

/** 解析结果 */
export interface DocParseResult {
  /** 是否解析成功（识别出单据类型且至少提取到一个字段/表格行） */
  ok: boolean;
  /** 命中的单据模板 */
  schema?: BlankDocSchema;
  /** 填充状态（可直接传给预览弹窗） */
  state?: BlankDocFormState;
  /** 给用户看的确认摘要（已识别单据 + 提取到的信息） */
  summary?: string;
  /** 未提取到的模板字段（提示用户可补充） */
  missing?: string[];
  /** 失败原因 */
  reason?: string;
}

/** 标签归一化：去空格/括号/冒号差异 */
function normLabel(s: string): string {
  return (s ?? '')
    .replace(/[\s（）()【】\[\]｜]/g, '')
    .replace(/[：:]/g, '')
    .replace(/\d+$/, '');
}

/** 标签相似度：完全相等 100；包含关系 60+短串长度；公共字符≥2 给基础分 */
function labelScore(a: string, b: string): number {
  const x = normLabel(a);
  const y = normLabel(b);
  if (!x || !y) return 0;
  if (x === y) return 100;
  if (x.includes(y) || y.includes(x)) return 60 + Math.min(x.length, y.length);
  let common = 0;
  const set = new Set(y.split(''));
  for (const ch of x) {
    if (set.has(ch)) common += 1;
  }
  return common >= 2 ? common * 10 : 0;
}

/** 把一行按可能的分隔符拆成单元格 */
function splitCells(line: string): string[] {
  return line
    .split(/\t+|\|+|｜+|,|，|、|\s{2,}/)
    .map((c) => c.trim())
    .filter((c) => c.length > 0);
}

/** 判断一行是否为"标签：值"结构，返回标签与值 */
function splitLabelValue(line: string): { label: string; value: string } | null {
  const m = line.match(/^\s*[-*•·\d.、()]*\s*([^：:]{2,16})[:：]\s*(.+?)\s*$/);
  if (!m) return null;
  return { label: m[1]!.trim(), value: m[2]!.trim() };
}

/** 从一段文本中提取某标签的值（含模糊标签匹配） */
function extractFieldValue(
  lines: string[],
  fieldLabel: string,
): { value: string; lineIndex: number } | null {
  let best: { value: string; lineIndex: number; score: number } | null = null;
  lines.forEach((line, lineIndex) => {
    const lv = splitLabelValue(line);
    if (!lv) return;
    // 两种形态：'标签：值' 或 '标签 值'（空格分隔，值不含冒号）
    const scoreColon = labelScore(lv.label, fieldLabel);
    let scoreSpace = 0;
    const m2 = line.match(
      new RegExp(`^\\s*[-*•·\\d.、()]*\\s*(${fieldLabel})\\s+(\\S[^：:]*)$`),
    );
    if (m2) scoreSpace = 80;
    const score = Math.max(scoreColon, scoreSpace);
    if (score >= 60 && (!best || score > best.score)) {
      best = { value: lv ? lv.value : (m2?.[2] ?? ''), lineIndex, score };
    }
  });
  return best ? { value: best.value, lineIndex: best.lineIndex } : null;
}

/** 文本行（统一首尾空白） */
function toLines(text: string): string[] {
  return text
    .replace(/\r\n?/g, '\n')
    .split('\n')
    .map((l) => l.trim());
}

/**
 * 单据类型识别：同义词 + 表格标题 + 字段标签 综合打分
 */
function detectSchema(lines: string[]): {
  schema: BlankDocSchema;
  score: number;
} {
  let best: BlankDocSchema = ALL_BLANK_SCHEMAS[0]!;
  let bestScore = -1;
  for (const schema of ALL_BLANK_SCHEMAS) {
    let score = 0;
    const joined = lines.join('\n');
    for (const alias of schema.aliases) {
      if (joined.includes(alias)) score += 10 + alias.length;
    }
    for (const table of schema.tables) {
      if (lines.some((l) => l.includes(table.title))) score += 15;
    }
    for (const label of schema.fields) {
      if (lines.some((l) => l.replace(/\s/g, '').startsWith(`${label}:`))) {
        score += 8;
      }
    }
    if (score > bestScore) {
      bestScore = score;
      best = schema;
    }
  }
  return { schema: best, score: bestScore };
}

/**
 * 核心：把文本解析为 单据模板 + 填充状态
 * @returns ok=false 时带 reason；ok=true 时带 schema/state/summary/missing
 */
export function parseDocFromText(text: string): DocParseResult {
  const lines = toLines(text);
  if (lines.filter((l) => l).length === 0) {
    return { ok: false, reason: '内容为空' };
  }

  // 1. 识别单据类型
  const { schema, score } = detectSchema(lines);
  if (score < 10) {
    return {
      ok: false,
      reason: '无法识别单据类型（未命中生产工单/领料单/入库单/采购单/检验单的任何特征）',
    };
  }

  // 2. 提取基础字段（含模糊映射；未命中的留空，绝不编造）
  const fieldValues: Record<string, string> = {};
  for (const label of schema.fields) {
    const hit = extractFieldValue(lines, label);
    if (hit) fieldValues[label] = hit.value;
  }
  const subtitleValues: string[] = schema.subtitleFields.map((label) => {
    const hit = extractFieldValue(lines, label);
    return hit?.value ?? '';
  });
  // 副标题字段与基础字段可能重复（如工单编号），去重填充
  for (let i = 0; i < schema.subtitleFields.length; i += 1) {
    const label = schema.subtitleFields[i]!;
    if (!subtitleValues[i] && fieldValues[label]) {
      subtitleValues[i] = fieldValues[label]!;
    }
    if (!fieldValues[label] && subtitleValues[i]) {
      fieldValues[label] = subtitleValues[i]!;
    }
  }

  // 3. 提取表格：定位标题 → 识别表头行 → 逐行解析
  const tables: Record<string, string[][]> = {};
  const tableSummary: string[] = [];
  const usedLineIdx = new Set<number>();
  for (const table of schema.tables) {
    const titleIdx = lines.findIndex((l) => l.includes(table.title));
    if (titleIdx < 0) continue;
    // 在标题后 3 行内找表头行（命中 ≥ 一半表头的行）
    let headerIdx = -1;
    let headerMap: number[] = [];
    for (let i = titleIdx + 1; i <= Math.min(titleIdx + 3, lines.length - 1); i += 1) {
      const cells = splitCells(lines[i]!);
      const map = table.headers.map((h) =>
        cells.findIndex((c) => labelScore(c, h) >= 60),
      );
      if (map.filter((x) => x >= 0).length >= Math.ceil(table.headers.length / 2)) {
        headerIdx = i;
        headerMap = map;
        break;
      }
    }
    if (headerIdx < 0) continue;
    usedLineIdx.add(headerIdx);
    // 数据行：紧随表头，直到连续 2 行解析失败 / 出现下一个表格标题 / 结束
    const rows: string[][] = [];
    let failStreak = 0;
    for (let i = headerIdx + 1; i < lines.length; i += 1) {
      const line = lines[i]!;
      if (!line) {
        failStreak += 1;
        if (failStreak >= 2) break;
        continue;
      }
      if (schema.tables.some((t) => line.includes(t.title))) break;
      const lv = splitLabelValue(line);
      if (lv && labelScore(lv.label, table.headers[0] ?? '') < 30) {
        // 出现新的"字段：值"行，视为离开表格区域
        break;
      }
      const cells = splitCells(line);
      if (cells.length === 0) {
        failStreak += 1;
        if (failStreak >= 2) break;
        continue;
      }
      // 单元格 → 表头列映射
      const row: string[] = table.headers.map(() => '');
      let filled = 0;
      if (cells.length === table.headers.length) {
        cells.forEach((c, ci) => {
          row[ci] = c;
          if (c) filled += 1;
        });
      } else {
        // 内联"物料编码：xxx 数量：120"或乱序列：按标签模糊映射
        let inlineMatched = false;
        cells.forEach((c) => {
          const lv2 = splitLabelValue(c);
          if (!lv2) return;
          const hi = table.headers.findIndex(
            (h) => labelScore(lv2.label, h) >= 60,
          );
          if (hi >= 0) {
            row[hi] = lv2.value;
            inlineMatched = true;
            filled += 1;
          }
        });
        if (!inlineMatched && cells.length === row.length - 1 && /^\d+$/.test(cells[0] ?? '')) {
          // 首列序号 + 其余顺序对应
          row[0] = cells[0]!;
          cells.slice(1).forEach((c, ci) => {
            row[ci + 1] = c;
            if (c) filled += 1;
          });
        }
      }
      if (filled > 0) {
        rows.push(row);
        usedLineIdx.add(i);
        failStreak = 0;
      } else {
        failStreak += 1;
        if (failStreak >= 2) break;
      }
      if (rows.length >= 30) break; // 防御：超长表格截断
    }
    if (rows.length > 0) {
      tables[table.title] = rows;
      tableSummary.push(`${table.title} ${rows.length} 行`);
    }
  }

  // 4. 组装填充状态
  const state: BlankDocFormState = {
    subtitle: subtitleValues,
    fields: fieldValues,
    tables,
  };

  // 5. 成功判定：至少提取到一个字段值或一行表格数据
  const filledFieldCount = Object.values(fieldValues).filter((v) => v).length;
  const tableRowCount = Object.values(tables).reduce((n, r) => n + r.length, 0);
  if (filledFieldCount === 0 && tableRowCount === 0) {
    return {
      ok: false,
      reason: `已按「${schema.title}」解析，但没有提取到任何字段值或表格数据，请检查格式`,
    };
  }

  // 6. 摘要与缺失字段
  const summaryLines: string[] = [];
  for (const label of schema.fields) {
    if (fieldValues[label]) summaryLines.push(`${label}：${fieldValues[label]}`);
  }
  if (tableSummary.length > 0) summaryLines.push(tableSummary.join('；'));
  const missing = schema.fields.filter((label) => !fieldValues[label]);

  return {
    ok: true,
    schema,
    state,
    summary: summaryLines.join('\n'),
    missing,
  };
}

/** 从 Word/Excel 文件提取纯文本（供 parseDocFromText 使用） */
export async function extractTextFromFile(file: File): Promise<string> {
  const name = file.name.toLowerCase();
  const arrayBuffer = await file.arrayBuffer();
  if (name.endsWith('.docx')) {
    // Word：mammoth 提取正文文本
    const mammoth = await import('mammoth');
    const result = await mammoth.extractRawText({ arrayBuffer });
    return result.value ?? '';
  }
  if (name.endsWith('.xlsx') || name.endsWith('.xls')) {
    // Excel：每个 Sheet 转为文本行，行内单元格用制表符连接（便于按列解析）
    const workbook = XLSX.read(arrayBuffer, { type: 'array' });
    const out: string[] = [];
    for (const sheetName of workbook.SheetNames) {
      const sheet = workbook.Sheets[sheetName]!;
      const rows = XLSX.utils.sheet_to_json<unknown[]>(sheet, {
        header: 1,
        blankrows: false,
        defval: '',
      });
      out.push(`【${sheetName}】`);
      for (const row of rows) {
        const cells = (row as unknown[]).map((c) =>
          c === null || c === undefined ? '' : String(c).trim(),
        );
        if (cells.some((c) => c)) out.push(cells.join('\t'));
      }
    }
    return out.join('\n');
  }
  if (name.endsWith('.doc')) {
    throw new Error('旧版 .doc 格式暂不支持，请在 Word 中另存为 .docx 后重新上传');
  }
  throw new Error('仅支持 .docx / .xlsx / .xls 文件');
}

/** 从文件解析单据（提取文本 → 复用文本解析） */
export async function parseDocFromFile(file: File): Promise<DocParseResult> {
  try {
    const text = await extractTextFromFile(file);
    return parseDocFromText(text);
  } catch (e: any) {
    return { ok: false, reason: e?.message ?? '文件读取失败' };
  }
}

/** 判断一段文本"是否像单据内容"（用于决定走解析还是普通问答） */
export function looksLikeDocText(text: string): boolean {
  const lines = toLines(text).filter((l) => l);
  if (lines.length < 2) return false;
  const labelLines = lines.filter((l) => splitLabelValue(l)).length;
  const hasTableTitle = ALL_BLANK_SCHEMAS.some((s) =>
    s.tables.some((t) => lines.some((l) => l.includes(t.title))),
  );
  return labelLines >= 2 || hasTableTitle;
}
