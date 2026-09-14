#!/usr/bin/env node
/**
 * 单位语义对等自检（第 14 道门禁）：后端 \`@Schema\` 声明了**单位**的数值字段，前端类型声明必须带同一单位。
 *
 * 由来（用户 2026-09-25 指出"接口的含义根本没对齐"）：
 *   · 类型对等门禁（第 13 道）只验**字段存在性**——字段都在，但"这个数是什么单位、管什么事"没人管；
 *   · 实测：后端 VO 里带单位语义的字段 15 个，前端只有 5 个注释了单位；最典型就是抓取间隔
 *     \`fetchIntervalSeconds\`——后端 Schema 只写"抓取间隔（秒，频控；默认 3600）"，DO 注释里才写清
 *     "同时约束列表轮询与原文下载"，前端界面因此长期答不出"这个间隔到底管什么"。
 *
 * 判定口径：
 *   ① 只认**数值类型**的字段（Integer/Long/int/long/BigDecimal/Double/Float/Short）——
 *      布尔字段描述里出现"天"（如"是否存在覆盖今天的劳动合同"）属误报，故排除；
 *   ② 单位取自后端 \`@Schema(description)\`，仅识别可判定的单位词：秒/毫秒/分钟/小时/天/字节/KB/MB；
 *   ③ 前端同名声明必须**带单位注释**（紧邻上文 JSDoc 或同行尾注，3 行内）；
 *   ④ 任何一条前端声明缺单位 → FAIL（前端类型是页面的直接数据来源，缺单位就会被当成无量纲数字用）。
 *
 * 用法：node sql/tools/check-unit-semantics.js [--list]
 * 退出码：0 通过；1 存在缺单位的前端声明。
 */
const fs = require('node:fs');
const path = require('node:path');

const LIST = process.argv.includes('--list');
const repoRoot = path.resolve(__dirname, '../../..');
const BE_VO = path.join(repoRoot, 'txgy-module-bidding/txgy-module-bidding-server/src/main/java/cn/iocoder/txgy/module/bidding/controller/admin');
const FE_API = path.join(repoRoot, 'txgy-ui-admin-vben/apps/web-antd/src/api/bidding');
const FE_EXTRA = [path.join(repoRoot, 'txgy-ui-admin-vben/apps/web-antd/src/views/portal/bidding/types.ts')];
const SKIP_DIRS = new Set(['node_modules', 'dist', 'target']);

/** 可判定的单位词（按长词优先匹配，避免"毫秒"被"秒"抢占） */
const UNITS = ['毫秒', '分钟', '小时', '字节', 'KB', 'MB', '秒', '天'];
/** 数值类型白名单：只对这些类型校验单位（排除布尔/字符串/集合） */
const NUMERIC = /^(?:Integer|Long|int|long|BigDecimal|Double|Float|double|float|Short)$/;

/**
 * 既知豁免：backendField → 理由。留空是常态——空缺说明前后端单位语义已完全对齐。
 * 新增豁免必须写明理由，且只用于"同名不同义"这类无法靠注释解决的场景。
 */
const KNOWN_EXEMPT = new Map([]);

function walk(dir, filter, out = []) {
  let ents = [];
  try { ents = fs.readdirSync(dir, { withFileTypes: true }); } catch { return out; }
  for (const e of ents) {
    if (SKIP_DIRS.has(e.name)) continue;
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, filter, out);
    else if (filter(p)) out.push(p);
  }
  return out;
}

/** 后端：收集 @Schema(带单位) + 紧随其后的数值字段 */
function collectBackend() {
  const found = [];
  for (const f of walk(BE_VO, (p) => /VO\.java$/.test(p))) {
    const lines = fs.readFileSync(f, 'utf8').split('\n');
    lines.forEach((l, i) => {
      const m = l.match(/@Schema\(\s*description\s*=\s*"([^"]*)"/);
      if (!m) return;
      const desc = m[1];
      const unit = UNITS.find((u) => desc.includes(u));
      if (!unit) return;
      for (let j = i + 1; j < Math.min(i + 7, lines.length); j++) {
        const fm = lines[j].match(/private\s+([\w<>,\[\]\.]+)\s+(\w+)\s*;/);
        if (!fm) continue;
        if (!NUMERIC.test(fm[1])) return; // 非数值：不校验
        found.push({ vo: path.basename(f, '.java'), field: fm[2], unit, desc: desc.slice(0, 60) });
        return;
      }
    });
  }
  return found;
}

/** 前端：收集字段声明 + 其上方/同行注释 */
function collectFrontend() {
  const decls = [];
  const files = walk(FE_API, (p) => /\.ts$/.test(p)).concat(FE_EXTRA.filter((p) => fs.existsSync(p)));
  for (const f of files) {
    const lines = fs.readFileSync(f, 'utf8').split('\n');
    lines.forEach((l, i) => {
      const dm = l.match(/^\s*(\w+)(\?)?:\s*([^;]+);/);
      if (!dm) return;
      const above = [lines[i - 1] || '', lines[i - 2] || '', lines[i - 3] || ''].join(' ');
      decls.push({
        field: dm[1],
        unitSeen: UNITS.find((u) => above.includes(u) || l.includes(u)) || '',
        file: path.relative(repoRoot, f).split(path.sep).join('/'),
        line: i + 1,
        comment: above.trim().slice(-80),
      });
    });
  }
  return decls;
}

const backend = collectBackend();
const frontend = collectFrontend();
const byField = new Map();
for (const d of frontend) {
  if (!byField.has(d.field)) byField.set(d.field, []);
  byField.get(d.field).push(d);
}

const violations = [];
const checked = [];
for (const b of backend) {
  if (KNOWN_EXEMPT.has(b.field)) continue;
  const decls = byField.get(b.field) || [];
  if (decls.length === 0) continue; // 前端未声明：类型对等门禁负责
  const missing = decls.filter((d) => d.unitSeen !== b.unit);
  checked.push({ ...b, decls: decls.length, missing: missing.length });
  for (const d of missing) violations.push({ ...d, expect: b.unit, vo: b.vo, desc: b.desc });
}

if (LIST) {
  console.log('后端带单位语义的数值字段 ↔ 前端声明（单位对齐情况）：');
  for (const c of checked) {
    console.log('  [' + (c.missing === 0 ? 'OK  ' : '缺 ' + c.missing) + '] ' + c.field.padEnd(24) + ' 单位=' + c.unit.padEnd(4) + ' 前端声明 ' + c.decls + ' 处  ← ' + c.vo);
  }
}

console.log('后端带单位的数值字段：' + backend.length + ' 个；前端有同名声明并纳入校验：' + checked.length + ' 个');
if (violations.length === 0) {
  console.log('[OK] 前端声明均带单位注释（单位语义与后端 @Schema 对齐）');
  process.exit(0);
}
console.log('[FAIL] 以下前端声明缺单位注释（后端已声明单位，前端会被当无量纲数字用）：');
for (const v of violations.slice(0, 30)) {
  console.log('   ' + v.file + ':' + v.line + '  ' + v.field + '  应为「' + v.expect + '」  后端 ' + v.vo + '：' + v.desc);
}
if (violations.length > 30) console.log('   … 另有 ' + (violations.length - 30) + ' 处');
console.log('修复：在该字段声明上方补一行 JSDoc 写明单位与语义（参考 api/bidding/crawlExternal 的 fetchIntervalSeconds）。');
process.exit(1);
