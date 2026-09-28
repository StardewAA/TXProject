/**
 * 行数超限策略 · 程序化预扩展模板（P2 选型实测通过的方案 1）：
 *   对通用计价表克隆末数据行并重命名书签（bill_12_* → bill_13_* …），生成「N 行版」模板。
 *   运行时报价数据超出模板行数 → 截断并告警（导出层已实现）；需要更多行时用本脚本重新生成模板并切换 .env。
 *
 * 用法：
 *   node tools/expand-template-rows.mjs [N] [模板.docx] [输出.docx]
 *   N 缺省 50（每个表扩展到 N 数据行；当前行数已 ≥N 的表不扩展）
 *   不带 N 传 0 只打印各表当前容量（干跑）
 *
 * 可扩展表（锚点 = 末数据行首列书签）：
 *   bill(11行)/measure(20)/other(20)/service(18)/analysis(20)/material(23)/project_cost(14)/unit_cost(15)
 * 表1（共享书签）、表4（固定+备用行）、表8（分组固定项）、表10（固定费用项）不在行扩展范围。
 */
import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';
import { unzip, zipStore } from '../dist/lib/zip.js';
import { fillBookmarkTemplate, listBookmarkNames } from '../dist/lib/bookmarkFill.js';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const target = Number(process.argv[2] ?? '50');
const templatePath = path.resolve(root, process.argv[3] ?? 'templates/投标标书_主模板.docx');
const outPath = path.resolve(
  root,
  process.argv[4] ?? `templates/投标标书_主模板-${target}行.docx`,
);

/** 可扩展表：锚点书签（末数据行首列）+ 表名 → 书签前缀 */
const EXPANDABLE = [
  { anchor: 'bill_12_no', prefix: 'bill' },
  { anchor: 'measure_21_no', prefix: 'measure' },
  { anchor: 'other_21_no', prefix: 'other' },
  { anchor: 'service_19_no', prefix: 'service' },
  { anchor: 'analysis_22_no', prefix: 'analysis' },
  { anchor: 'material_23_no', prefix: 'material' },
  { anchor: 'project_cost_14_no', prefix: 'project_cost' },
  { anchor: 'unit_cost_15_no', prefix: 'unit_cost' },
];

/** 定位书签所在 <w:tr>（按标签名精确匹配，不能 lastIndexOf('<w:tr'——会误命中 <w:trPr/<w:trHeight） */
function locateRow(xml, bookmarkName) {
  const anchor = xml.indexOf(`w:name="${bookmarkName}"`);
  if (anchor < 0) return null;
  const re = /<w:tr(?:\s[^>]*)?>/g;
  let open = -1;
  let m;
  while ((m = re.exec(xml)) !== null && m.index < anchor) open = m.index;
  const close = xml.indexOf('</w:tr>', anchor);
  if (open < 0 || close < 0) return null;
  return { open, close, rowXml: xml.slice(open, close + '</w:tr>'.length) };
}

const template = fs.readFileSync(templatePath);
const entries = unzip(template);
const docEntry = entries.find((entry) => entry.name === 'word/document.xml');
const baseXml = docEntry.data.toString('utf8');

console.log(`模板：${path.basename(templatePath)}（${listBookmarkNames(template).length} 个书签）`);
console.log(`目标数据行数：${target}${target <= 0 ? '（干跑，只报容量）' : ''}`);

let xml = baseXml;
let expandedAny = false;
for (const spec of EXPANDABLE) {
  const last = Number(/(\d+)_no$/.exec(spec.anchor)[1]);
  const located = locateRow(xml, spec.anchor);
  if (!located) {
    console.log(`- ${spec.prefix}：未找到锚点 ${spec.anchor}，跳过`);
    continue;
  }
  const renameFrom = `${spec.prefix}_${last}_`;
  if (target <= 0) {
    console.log(`- ${spec.prefix}：当前容量 ${last} 行`);
    continue;
  }
  if (target <= last) {
    console.log(`- ${spec.prefix}：当前 ${last} 行 ≥ 目标 ${target} 行，不扩展`);
    continue;
  }
  const starts = (located.rowXml.match(/<w:bookmarkStart/g) ?? []).length;
  const ends = (located.rowXml.match(/<w:bookmarkEnd/g) ?? []).length;
  if (starts !== ends || starts === 0) {
    console.log(`- ${spec.prefix}：锚点行书签不成对（start ${starts} / end ${ends}），跳过（行结构异常需人工检查）`);
    continue;
  }
  // 逐个克隆插回锚点行之后（升序插入，行序保持 last+1, last+2, …）
  let insertAt = located.close + '</w:tr>'.length;
  for (let n = last + 1; n <= target; n += 1) {
    const clone = located.rowXml.replaceAll(renameFrom, `${spec.prefix}_${n}_`);
    xml = xml.slice(0, insertAt) + clone + xml.slice(insertAt);
    insertAt += clone.length;
  }
  expandedAny = true;
  console.log(`- ${spec.prefix}：${last} 行 → ${target} 行（新增 ${target - last} 行 × ${starts} 书签）`);
}
if (target <= 0 || !expandedAny) {
  process.exit(0);
}

const outBuffer = zipStore(entries.map((entry) =>
  entry.name === 'word/document.xml' ? { name: entry.name, data: Buffer.from(xml, 'utf8') } : entry,
));
fs.writeFileSync(outPath, outBuffer);

// 自检：新增行回填 + 几何/配对/XML 审计（与新模板自身比对）
const probeValues = {};
for (const spec of EXPANDABLE) {
  const last = Number(/(\d+)_no$/.exec(spec.anchor)[1]);
  if (target <= last) continue;
  for (let n = last + 1; n <= target; n += 1) {
    probeValues[`${spec.prefix}_${n}_no`] = String(n - last + 1);
    probeValues[`${spec.prefix}_${n}_name`] = `扩展行${n - last + 1}`;
    probeValues[`${spec.prefix}_${n}_unit_price`] = '100.00';
    probeValues[`${spec.prefix}_${n}_total_price`] = '100.00';
  }
}
const filled = fillBookmarkTemplate(outBuffer, probeValues);
const selfFilled = fs.readFileSync(outPath);
const { runAudits } = await import('./audit-filled-docx.mjs');
const audits = runAudits(selfFilled, filled.buffer, probeValues, () => {});
const coreOk = audits.results.xmlBalanced.ok && audits.results.bookmarkPairs.ok
  && audits.results.tableGeometry.ok && audits.results.fillInPlace.ok && audits.results.fontSizes.ok;
console.log(`自检：新增行回填 ${filled.filled.length} 命中 / ${filled.skipped.length} 跳过；核心审计 ${coreOk ? '通过' : '失败'}`);
console.log(`输出：${path.relative(root, outPath)}（${listBookmarkNames(outBuffer).length} 个书签）`);
console.log('提示：在 .env 中把 BID_TEMPLATE_DOCX 指向新模板；启动期字典一致性校验会对新增行书签告警（预期现象，字典未含扩展行）');
process.exit(coreOk && filled.skipped.length === 0 ? 0 : 1);
