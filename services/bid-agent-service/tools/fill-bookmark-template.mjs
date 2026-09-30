/**
 * 书签占位模板回填 CLI（变量字典方案）：
 *   查看书签：node tools/fill-bookmark-template.mjs --inspect <模板.docx>
 *   回填模板：node tools/fill-bookmark-template.mjs <模板.docx> <取值.json> [输出.docx]
 *
 * 取值 JSON 形如 {"bid_no": "XXXX-2026-001", "bidder_name": "XX建设有限公司", ...}，
 * 基础变量名会自动回填同名 _N 变体（bidder_name_2、date_year_2 …），
 * 表格明细行用完整书签名（bill_2_code、material_1_unit_price …）。
 * 输出缺省为模板同目录 <原名>-已回填.docx。
 */
import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';
import { fillBookmarkTemplate, listBookmarkNames } from '../dist/lib/bookmarkFill.js';

const here = path.dirname(fileURLToPath(import.meta.url));

function usage() {
  console.error('用法：');
  console.error('  node tools/fill-bookmark-template.mjs --inspect <模板.docx>');
  console.error('  node tools/fill-bookmark-template.mjs <模板.docx> <取值.json> [输出.docx]');
  process.exit(2);
}

const args = process.argv.slice(2);

if (args[0] === '--inspect') {
  const templatePath = args[1];
  if (!templatePath) usage();
  const names = listBookmarkNames(fs.readFileSync(path.resolve(here, '..', templatePath)));
  console.log(`共 ${names.length} 个书签：`);
  for (const name of names) console.log(`  ${name}`);
  process.exit(0);
}

const [templateArg, valuesArg, outArg] = args;
if (!templateArg || !valuesArg) usage();

const templatePath = path.resolve(here, '..', templateArg);
const valuesPath = path.resolve(here, '..', valuesArg);
const outPath = path.resolve(here, '..', outArg ?? templatePath.replace(/\.docx$/i, '-已回填.docx'));

const template = fs.readFileSync(templatePath);
let raw;
try {
  raw = JSON.parse(fs.readFileSync(valuesPath, 'utf8'));
} catch (error) {
  console.error(`取值文件不是合法 JSON：${error.message}`);
  process.exit(2);
}
if (typeof raw !== 'object' || raw === null || Array.isArray(raw)) {
  console.error('取值 JSON 必须是对象：{"bid_no": "...", ...}');
  process.exit(2);
}
const values = {};
for (const [key, value] of Object.entries(raw)) {
  if (typeof value !== 'string' || value.trim() === '') {
    console.warn(`忽略非字符串/空取值：${key}`);
    continue;
  }
  values[key] = value;
}

const total = listBookmarkNames(template).length;
const result = fillBookmarkTemplate(template, values);
fs.writeFileSync(outPath, result.buffer);

console.log(`模板书签：${total} 个；取值：${Object.keys(values).length} 个变量`);
console.log(`已回填：${result.filled.length} 个变量`);
for (const name of result.filled) console.log(`  ✓ ${name}`);
if (result.skipped.length > 0) {
  console.log(`无处可填（有取值但书签区域无匹配形态，如误包住表头标签）：${result.skipped.length} 个`);
  for (const name of result.skipped) console.log(`  ! ${name}`);
}
console.log(`输出：${outPath}（${result.buffer.byteLength} 字节）`);
