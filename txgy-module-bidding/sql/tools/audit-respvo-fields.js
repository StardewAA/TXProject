#!/usr/bin/env node
/**
 * 审计（只报告，不作门禁）：后端 RespVO 返回、而**整个前端**从未引用的字段。
 *
 * 为什么要审：后端算了、页面没接的字段 = 用户看不到的数据。这类缺口编译期完全静默
 * （前端类型若也没声明该字段，连 vue-tsc 都不会报），只有把"后端出参"与"前端引用"对起来才看得见。
 * 本轮实测的两个真实例子：
 *   ① KeyPostAssignmentRespVO 的 windowChangeCount / threshold / exceeded / previousPersonId
 *      —— 该 VO 的 Javadoc 明写"前端需要在保存成功的同一屏立刻提示挂靠嫌疑"，而前端类型里根本没这几个字段；
 *   ② NoticeExternalDetailRespVO.contentHtml / bidSectionNames —— 属**门户**消费，管理端用 contentHtmlSafe/
 *      bodyBlocksJson；这正说明扫描范围必须覆盖整个前端 monorepo，否则会误报（只扫 web-antd 时报 55 个，
 *      扫全部 apps 后降到 28 个）。
 *
 * 口径：
 *   - 后端：txgy-module-bidding-server/.../bidding/**\/*RespVO.java 的 private 字段；
 *     跳过 @JsonIgnore / @Schema(hidden=true) 与通用审计列（id/creator/createTime/...）。
 *   - 前端：txgy-ui-admin-vben/apps/**\/*.{vue,ts,tsx}（含门户应用），按 \b字段名\b 全量文本匹配。
 *   - 命中即"疑似未被消费"；其中已确认属有意为之的登记在 KNOWN_INTENTIONAL 里，不再重复报警。
 *
 * 用法：node sql/tools/audit-respvo-fields.js [--all]
 *   --all  连"已确认有意为之"的一并列出
 * 退出码：默认恒为 0（审计工具）；加 --strict 时有未归类命中则退出 1（供将来按需接入 CI）。
 */
const fs = require('node:fs');
const path = require('node:path');

const SHOW_ALL = process.argv.includes('--all');
const STRICT = process.argv.includes('--strict');
const repoRoot = path.resolve(__dirname, '../../..');
const BE_ROOT = path.join(repoRoot, 'txgy-module-bidding/txgy-module-bidding-server/src/main/java/cn/iocoder/txgy/module/bidding');
const FE_ROOT = path.join(repoRoot, 'txgy-ui-admin-vben/apps');
const SKIP_DIRS = new Set(['node_modules', 'dist', '.git', 'coverage', 'unpackage']);

/** 通用审计列：前端不必展示，不算缺口 */
const COMMON = new Set(['id', 'creator', 'createTime', 'updater', 'updateTime', 'deleted', 'tenantId', 'pageNo', 'pageSize', 'total', 'sort', 'status', 'version']);

/** 已确认"有意为之"的出参（写明理由，避免下次重复排查） */
const KNOWN_INTENTIONAL = new Map([
  ['CrawlSourceRespVO', 'api* 扁平字段：前端统一用 apiConfigJson（FE 类型注释已写明 method/url/headers/... 打包在 JSON 里），后端扁平形式属冗余出参，前端不消费是正确的；如需清理应在后端做'],
  ['NoticeBodyStructureRespVO', 'blockCount/tableCount/aiRecommended 是结构化解析的诊断量：管理端直接渲染 bodyBlocksJson 的块，不展示计数'],
  ['NoticeExternalAiExtractRespVO', 'metaFieldCount/fullTextLength 为 AI 抽取诊断量（排查抽取质量用），非页面展示字段'],
  ['DictItemRespVO', 'dictTypeKey 是后端字典项的复合键（拼给前端做缓存键/去重），非展示字段'],
]);

function walk(dir, out) {
  let ents;
  try { ents = fs.readdirSync(dir, { withFileTypes: true }); } catch { return out; }
  for (const e of ents) {
    if (SKIP_DIRS.has(e.name)) continue;
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, out);
    else out.push(p);
  }
  return out;
}

const beFiles = walk(BE_ROOT, []).filter((f) => /RespVO\.java$/.test(f));
const feFiles = walk(FE_ROOT, []).filter((f) => /\.(vue|ts|tsx)$/.test(f));
if (beFiles.length === 0 || feFiles.length === 0) {
  console.error('[FAIL] 未找到后端 RespVO 或前端源文件，检查仓库结构是否变化');
  process.exit(1);
}
const feText = feFiles.map((f) => fs.readFileSync(f, 'utf8')).join('\n');

const hits = new Map();
let fieldTotal = 0;
for (const f of beFiles) {
  const src2 = fs.readFileSync(f, 'utf8');
  const vo = path.basename(f, '.java');
  for (const m of src2.matchAll(/private\s+(?:static\s+final\s+)?([\w<>,.\[\]\s]+?)\s+(\w+)\s*;/g)) {
    const field = m[2];
    if (COMMON.has(field)) continue;
    const lineStart = src2.lastIndexOf('\n', m.index) + 1;
    const ctx = src2.slice(Math.max(0, lineStart - 200), lineStart).split('\n').slice(-3).join('\n');
    if (/@JsonIgnore|hidden\s*=\s*true/.test(ctx)) continue;
    fieldTotal += 1;
    if (new RegExp('\\b' + field + '\\b').test(feText)) continue;
    if (!hits.has(vo)) hits.set(vo, []);
    hits.get(vo).push(field);
  }
}

const intentional = [...hits.keys()].filter((vo) => KNOWN_INTENTIONAL.has(vo));
const pending = [...hits.keys()].filter((vo) => !KNOWN_INTENTIONAL.has(vo));
const cnt = (vos) => vos.reduce((n, vo) => n + hits.get(vo).length, 0);

console.log('后端 RespVO ' + beFiles.length + ' 个 / 字段 ' + fieldTotal + ' 个；前端源文件 ' + feFiles.length + ' 个（含门户）');
console.log('未被前端引用的字段：' + cnt([...hits.keys()]) + ' 个，涉及 ' + hits.size + ' 个 RespVO');
console.log('  · 已确认有意为之：' + cnt(intentional) + ' 个 / ' + intentional.length + ' 个 VO');
console.log('  · 待确认（可能是真缺口）：' + cnt(pending) + ' 个 / ' + pending.length + ' 个 VO');

if (pending.length > 0) {
  console.log('\n【待确认】后端返回但前端（含门户）未消费 —— 请判断是"页面该展示但漏了"还是"仅内部/诊断用"：');
  for (const vo of pending) console.log('  ' + vo + '：' + hits.get(vo).join(', '));
}
if (SHOW_ALL && intentional.length > 0) {
  console.log('\n【已确认有意为之】（理由见脚本内 KNOWN_INTENTIONAL）');
  for (const vo of intentional) console.log('  ' + vo + '：' + hits.get(vo).join(', ') + '\n      → ' + KNOWN_INTENTIONAL.get(vo));
}
console.log('\n提示：确认属"有意为之"的，请补进脚本内 KNOWN_INTENTIONAL（写明理由）后重跑，保持输出可读。');
if (STRICT && pending.length > 0) process.exit(1);
