/**
 * P2 行数超限策略选型实测 · 方案 1「程序化预扩展模板」：
 *   克隆 bill 表末行（bill_12_* 书签所在 w:tr）→ 重命名书签 bill_12_* → bill_13_* → 插回原行之后，
 *   然后用扩展后的模板回填 bill_13_* 取值，验证：
 *   - 扩展行回填全部命中（0 跳过）；
 *   - 产物 XML 合法、书签配对完好、表格几何 = 扩展模板（版式不破坏）。
 *
 * 用法：node tools/probe-expand-rows.mjs
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { unzip, zipStore } from '../dist/lib/zip.js';
import { fillBookmarkTemplate, listBookmarkNames } from '../dist/lib/bookmarkFill.js';
import { runAudits } from './audit-filled-docx.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const template = fs.readFileSync(path.join(root, 'templates', '投标标书_主模板.docx'));

const entries = unzip(template);
const docEntry = entries.find((entry) => entry.name === 'word/document.xml');
const xml = docEntry.data.toString('utf8');

// 定位 bill_12_no 书签所在表格行
const anchor = xml.indexOf('w:name="bill_12_no"');
if (anchor < 0) {
  console.error('模板中未找到 bill_12_no 书签');
  process.exit(2);
}
// 行边界：按标签名精确匹配 <w:tr …>（不能 lastIndexOf('<w:tr'——会误命中 <w:trPr / <w:trHeight）
const trOpenRe = /<w:tr(?:\s[^>]*)?>/g;
let trOpen = -1;
let m;
while ((m = trOpenRe.exec(xml)) !== null && m.index < anchor) trOpen = m.index;
const trClose = xml.indexOf('</w:tr>', anchor);
if (trOpen < 0 || trClose < 0) {
  console.error('bill_12_no 不在 <w:tr> 内');
  process.exit(2);
}
const rowXml = xml.slice(trOpen, trClose + '</w:tr>'.length);

// 克隆行：书签改名 bill_12_ → bill_13_（克隆行内书签必须完整闭合于行内）
const bookmarksInRow = [...rowXml.matchAll(/w:name="(bill_12_[a-z_]+)"/g)].map((m) => m[1]);
const startCount = (rowXml.match(/<w:bookmarkStart/g) ?? []).length;
const endCount = (rowXml.match(/<w:bookmarkEnd/g) ?? []).length;
console.log(`末行内书签：${bookmarksInRow.length} 个（start ${startCount} / end ${endCount}）`);

const newRow = rowXml.replaceAll('bill_12_', 'bill_13_');
const expandedXml = xml.slice(0, trClose + '</w:tr>'.length) + newRow + xml.slice(trClose + '</w:tr>'.length);

// 生成「50 行版」扩展模板（循环克隆到 bill_50_*）
let manyRowsXml = xml;
const baseRow = xml.slice(trOpen, trClose + '</w:tr>'.length);
let insertionPoint = trClose + '</w:tr>'.length;
const clonedRows = [];
for (let n = 13; n <= 50; n += 1) {
  const clone = baseRow.replaceAll('bill_12_', `bill_${n}_`);
  manyRowsXml = manyRowsXml.slice(0, insertionPoint) + clone + manyRowsXml.slice(insertionPoint);
  insertionPoint += clone.length;
  clonedRows.push(n);
}

const expandedBuffer = zipStore(entries.map((entry) =>
  entry.name === 'word/document.xml' ? { name: entry.name, data: Buffer.from(expandedXml, 'utf8') } : entry,
));
const manyBuffer = zipStore(entries.map((entry) =>
  entry.name === 'word/document.xml' ? { name: entry.name, data: Buffer.from(manyRowsXml, 'utf8') } : entry,
));

// 回填扩展行取值
const values = {
  bill_13_no: '12', bill_13_code: '010101001012', bill_13_name: '扩展行测试项目',
  bill_13_unit: '项', bill_13_quantity: '1', bill_13_unit_price: '100.00', bill_13_total_price: '100.00',
};
const filled = fillBookmarkTemplate(expandedBuffer, values);
console.log(`单行扩展回填：命中 ${filled.filled.length} / 跳过 ${filled.skipped.length}`);

// 50 行版：全部 13..50 行各给一个值，要求全部命中
const manyValues = {};
for (let n = 13; n <= 50; n += 1) {
  manyValues[`bill_${n}_no`] = String(n - 11);
  manyValues[`bill_${n}_code`] = `010101001${String(n).padStart(3, '0')}`;
  manyValues[`bill_${n}_name`] = `扩展行项目${n - 11}`;
  manyValues[`bill_${n}_unit`] = '项';
  manyValues[`bill_${n}_quantity`] = '1';
  manyValues[`bill_${n}_unit_price`] = '100.00';
  manyValues[`bill_${n}_total_price`] = '100.00';
}
const manyFilled = fillBookmarkTemplate(manyBuffer, manyValues);
console.log(`50 行版回填：命中 ${manyFilled.filled.length} / 跳过 ${manyFilled.skipped.length}`);

const outPath = path.join(root, 'templates', '投标标书_行扩展探针-50行版.docx');
fs.writeFileSync(outPath, manyFilled.buffer);

// 审计：与「50 行版扩展模板」自身比对（几何一致、书签配对、XML 合法、回填到位）。
// 本探针只回填扩展行，其余表未回填 → 5a 占位符残留属预期（部分回填），不计入结论。
const audits = runAudits(manyBuffer, manyFilled.buffer, manyValues);
const coreOk = audits.results.xmlBalanced.ok
  && audits.results.bookmarkPairs.ok
  && audits.results.tableGeometry.ok
  && audits.results.fillInPlace.ok
  && audits.results.fontSizes.ok;
console.log('（5a 占位符残留为部分回填的预期现象，不计入本探针结论）');
console.log(`50 行版模板书签数：${listBookmarkNames(manyBuffer).length}`);
console.log(`探针产物：templates/投标标书_行扩展探针-50行版.docx（${manyFilled.buffer.byteLength} 字节）`);
process.exit(coreOk && manyFilled.skipped.length === 0 ? 0 : 1);
