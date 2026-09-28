/**
 * 回填产物五项审计（改进版落地方案 P0 冒烟标准）：
 *   1. XML 合法性：document.xml 标签配对平衡；
 *   2. 书签配对：每个 bookmarkStart 都有同 id 的 bookmarkEnd，id 不重复；
 *   3. 表格几何：产物与模板逐表比对行数、逐行比对单元格数（版式保护成立）；
 *   4. 字号审计：表格内字号上限 24 磅（防表8/表9 曾出现的 36 磅缺陷回归）；
 *   5. 回填到位 + 占位符残留：每个有取值的书签，取值都出现在其出现位置（区域+后 400 字符窗口），
 *      且产物中不再残留 ｘｘｘ/xxx 占位符 run。
 *
 * 用法：node tools/audit-filled-docx.mjs <模板.docx> <回填产物.docx> <取值.json>
 * 也可被 fill-all-bookmarks.mjs 以模块方式引用（runAudits）。
 */
import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';
import { unzip } from '../dist/lib/zip.js';
import { assertSharedValueConsistency } from '../dist/lib/bookmarkFill.js';

const here = path.dirname(fileURLToPath(import.meta.url));

function documentXml(buffer) {
  const entries = unzip(buffer);
  const doc = entries.find((entry) => entry.name === 'word/document.xml');
  if (!doc) throw new Error('产物缺少 word/document.xml');
  return doc.data.toString('utf8');
}

/** 审计 1：标签配对平衡（按标签名统计开闭次数，自闭合不计） */
function auditXmlBalanced(xml) {
  const counts = new Map();
  const re = /<\/?(w:[a-zA-Z]+)(?:\s[^>]*?)?>/g;
  let m;
  while ((m = re.exec(xml)) !== null) {
    const tag = m[1];
    const isClose = xml[m.index + 1] === '/';
    if (!isClose && m[0].endsWith('/>')) continue; // 自闭合 <w:sz .../>
    counts.set(tag, (counts.get(tag) ?? 0) + (isClose ? -1 : 1));
  }
  const unbalanced = [...counts.entries()].filter(([, count]) => count !== 0);
  return { ok: unbalanced.length === 0, unbalanced };
}

/** 审计 2：书签配对。模板脚本会复用书签 id（如封面与目录共用 id，见 bookmarkFill.ts 注释），
 * 因此 id 重复不判失败，只保证「每个 start id 都能找到同 id 的 end、每个 end id 都有 start」。 */
function auditBookmarkPairs(xml) {
  const startIds = [];
  const startRe = /<w:bookmarkStart\b[^>]*?w:id="(\d+)"[^>]*?\/?>/g;
  let m;
  while ((m = startRe.exec(xml)) !== null) startIds.push(m[1]);
  const endIds = [];
  const endRe = /<w:bookmarkEnd\b[^>]*?w:id="(\d+)"[^>]*?\/?>/g;
  while ((m = endRe.exec(xml)) !== null) endIds.push(m[1]);
  const startSet = new Set(startIds);
  const endSet = new Set(endIds);
  const missingEnd = startIds.filter((id) => !endSet.has(id));
  const missingStart = endIds.filter((id) => !startSet.has(id));
  return {
    ok: missingEnd.length === 0 && missingStart.length === 0,
    starts: startIds.length,
    ends: endIds.length,
    missingEnd,
    missingStart,
    duplicateStarts: startIds.length !== startSet.size,
  };
}

/** 提取全部表格块（按出现顺序），返回每表 {rows, cellsPerRow} */
function tableShapes(xml) {
  const shapes = [];
  const tblRe = /<w:tbl(?:\s[^>]*)?>[\s\S]*?<\/w:tbl>/g;
  let m;
  while ((m = tblRe.exec(xml)) !== null) {
    const rows = [];
    const trRe = /<w:tr(?:\s[^>]*)?>[\s\S]*?<\/w:tr>/g;
    let tr;
    while ((tr = trRe.exec(m[0])) !== null) {
      rows.push((tr[0].match(/<w:tc(?:\s[^>]*)?>/g) ?? []).length);
    }
    shapes.push(rows);
  }
  return shapes;
}

/** 审计 3：表格几何逐表比对 */
function auditTableGeometry(templateXml, productXml) {
  const templateShapes = tableShapes(templateXml);
  const productShapes = tableShapes(productXml);
  const mismatches = [];
  if (templateShapes.length !== productShapes.length) {
    mismatches.push(`表格数不一致：模板 ${templateShapes.length} / 产物 ${productShapes.length}`);
  }
  const n = Math.min(templateShapes.length, productShapes.length);
  for (let i = 0; i < n; i += 1) {
    const a = templateShapes[i];
    const b = productShapes[i];
    if (a.length !== b.length) {
      mismatches.push(`表 ${i + 1} 行数不一致：模板 ${a.length} / 产物 ${b.length}`);
      continue;
    }
    for (let r = 0; r < a.length; r += 1) {
      if (a[r] !== b[r]) {
        mismatches.push(`表 ${i + 1} 第 ${r + 1} 行单元格数不一致：模板 ${a[r]} / 产物 ${b[r]}`);
      }
    }
  }
  return { ok: mismatches.length === 0, tables: templateShapes.length, mismatches };
}

/** 审计 4：表格内字号（w:sz 半磅值）上限 48（24 磅）；返回表格内字号分布 */
function auditFontSizes(productXml) {
  const sizes = new Set();
  const tblRe = /<w:tbl(?:\s[^>]*)?>[\s\S]*?<\/w:tbl>/g;
  let m;
  while ((m = tblRe.exec(productXml)) !== null) {
    const szRe = /<w:sz\b[^>]*?w:val="(\d+)"/g;
    let sz;
    while ((sz = szRe.exec(m[0])) !== null) sizes.add(Number(sz[1]));
  }
  const oversize = [...sizes].filter((size) => size > 48).sort((a, b) => a - b);
  return { ok: oversize.length === 0, sizes: [...sizes].sort((a, b) => a - b), oversize };
}

/** 审计 5a：占位符 run 残留（ｘｘｘ / xxx / XX 等占位字符连续出现） */
function auditPlaceholderResidue(productXml) {
  const hits = [];
  const runRe = /<w:t(?:\s[^>]*)?>([\s\S]*?)<\/w:t>/g;
  let m;
  while ((m = runRe.exec(productXml)) !== null) {
    if (/[ｘxX×]{2,}/.test(m[1])) hits.push(m[1]);
  }
  return { ok: hits.length === 0, hits: hits.slice(0, 10) };
}

/** 审计 5b：回填到位——每个取值都出现在其书签出现位置（区域 + 后 400 字符窗口） */
function auditFillInPlace(productXml, values) {
  const names = Object.keys(values).filter((name) => values[name] && values[name].trim());
  const issues = assertSharedValueConsistency(productXml, values, names);
  return { ok: issues.length === 0, checked: names.length, issues };
}

/**
 * 执行五项审计。values 为「书签名 → 取值」全量表（含 _N 变体展开）。
 * 返回 { ok, results }，results 为各项审计明细。
 */
export function runAudits(templateBuffer, productBuffer, values, log = console.log) {
  const templateXml = documentXml(templateBuffer);
  const productXml = documentXml(productBuffer);
  const results = {
    xmlBalanced: auditXmlBalanced(productXml),
    bookmarkPairs: auditBookmarkPairs(productXml),
    tableGeometry: auditTableGeometry(templateXml, productXml),
    fontSizes: auditFontSizes(productXml),
    placeholderResidue: auditPlaceholderResidue(productXml),
    fillInPlace: auditFillInPlace(productXml, values),
  };
  const labels = {
    xmlBalanced: '1. XML 合法性（标签配对）',
    bookmarkPairs: '2. 书签配对',
    tableGeometry: '3. 表格几何（逐表逐行比对模板）',
    fontSizes: '4. 表格字号上限（≤24 磅）',
    placeholderResidue: '5a. 占位符残留',
    fillInPlace: '5b. 回填到位（取值出现在每处书签位置）',
  };
  for (const [key, result] of Object.entries(results)) {
    const status = result.ok ? '通过' : '失败';
    log(`[${status}] ${labels[key]}`);
    if (key === 'xmlBalanced' && !result.ok) log(`  未配对标签：${JSON.stringify(result.unbalanced.slice(0, 5))}`);
    if (key === 'bookmarkPairs' && !result.ok) {
      log(`  start ${result.starts} / end ${result.ends}；缺 end ${result.missingEnd.length}、缺 start ${result.missingStart.length}（id 复用 ${result.duplicateStarts} 为模板正常形态）`);
    }
    if (key === 'tableGeometry') log(`  表格数 ${result.tables}；不一致：${result.mismatches.length ? result.mismatches.slice(0, 5).join('；') : '无'}`);
    if (key === 'fontSizes') log(`  表格内字号：${result.sizes.join('、')}；超标：${result.oversize.length ? result.oversize.join('、') : '无'}`);
    if (key === 'placeholderResidue' && !result.ok) log(`  残留：${JSON.stringify(result.hits)}`);
    if (key === 'fillInPlace') {
      log(`  检查 ${result.checked} 个变量；未到位：${result.issues.length ? JSON.stringify(result.issues.slice(0, 5)) : '无'}`);
    }
  }
  return { ok: Object.values(results).every((result) => result.ok), results };
}

function main() {
  const [templateArg, productArg, valuesArg] = process.argv.slice(2);
  if (!templateArg || !productArg || !valuesArg) {
    console.error('用法：node tools/audit-filled-docx.mjs <模板.docx> <回填产物.docx> <取值.json>');
    process.exit(2);
  }
  const template = fs.readFileSync(path.resolve(here, '..', templateArg));
  const product = fs.readFileSync(path.resolve(here, '..', productArg));
  const values = JSON.parse(fs.readFileSync(path.resolve(here, '..', valuesArg), 'utf8'));
  const { ok } = runAudits(template, product, values);
  process.exit(ok ? 0 : 1);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main();
}
