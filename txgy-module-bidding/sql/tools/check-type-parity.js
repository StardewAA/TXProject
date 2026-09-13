#!/usr/bin/env node
/**
 * 出参/入参类型对等自检（第 13 道门禁）：前端 interface 是不是后端 VO 的**完整镜像**？
 *
 * 由来：同一类缺陷已咬过三次——
 *   · KeyPostAssignmentResp 只声明一半字段 → 挂靠嫌疑告警拿不到数据；
 *   · ParseAttachment 漏 urlIncomplete、ParseEntry 漏 date/category → 解析诊断面板做不出来；
 *   · （审计脚本 audit-respvo-fields.js 只能按"字段名是否在全仓出现"比对，
 *      date/category 这种**通用词**会被别处同名用法掩盖而漏报 —— 本门禁按"类型对类型"比对，没有这个盲区。）
 *
 * 判定口径（先量化后定阈值，见 README 的实测数据）：
 *   · 只认**注释里点名了 VO 的前端 interface**（如"解析测试回执（CrawlParseTestRespVO）"这类 JSDoc），
 *     不猜、不按名字硬凑；
 *   · 候选 = 被点名的 VO 本身 ∪ 它的嵌套类（前端类型常对应嵌套行，如 ParseEntry ↔ …RespVO.Entry）；
 *     取字段重合度最高者，且重合度必须 ≥ 0.6 才认（实测：<0.6 的基本都是误链，
 *     e.g. QualificationPermitPageReqVO ← Permit 重合 0.10）；
 *   · 重合度 = "前端字段中能在该 VO 找到同名的比例"。1.00 表示前端类型是后端 VO 的**严格子集**（可信）；
 *   · 缺失字段 = VO 有、前端类型没有 → 超基线即 FAIL。
 *
 * 为什么用基线而不是一次全清：缺口分两类 ——
 *   ① 真缺口（页面要做的事缺数据，如内部公告标记、人员岗位名）；
 *   ② 后端保留/未采用字段（如 CrawlSourceRespVO 的 api* 展开列，页面走 apiConfigJson 整体配置）。
 *   二者只能靠人判断，故按 VO←前端 逐条冻结并写明理由；**基线只许减少**，新增即 FAIL。
 * 用法：node sql/tools/check-type-parity.js [--list|--update-baseline]
 */
const fs = require('node:fs');
const path = require('node:path');

const LIST = process.argv.includes('--list');
const UPDATE = process.argv.includes('--update-baseline');
const repoRoot = path.resolve(__dirname, '../../..');
const BE_ROOT = path.join(repoRoot, 'txgy-module-bidding/txgy-module-bidding-server/src/main/java');
const FE_ROOT = path.join(repoRoot, 'txgy-ui-admin-vben/apps/web-antd/src');
const MIN_OVERLAP = 0.6;

/**
 * 既知不完整镜像（只许减少）。key = VO名 ← 前端 interface 名；值 = [冻结缺字段数, 理由]。
 * 缺口变多 → FAIL；缺口消失（补全了）→ 也必须删条目（陈旧基线会掩盖将来重现的缺口）。
 */
const KNOWN_INCOMPLETE = new Map([
  ['CrawlSourceRespVO ← SourceItem', [11, '缺的是 API 直取的 11 个展开列（apiUrl/apiMethod/apiHeadersJson/…/apiUrlPrefix）；页面走 apiConfigJson 整体 JSON 配置，未逐一展开——属②后端保留字段；若将来改为表单化配置需先补类型']],
  ['NoticeExternalManageRespVO ← ManageItem', [10, '缺审核留痕（auditSource/auditStageName/auditByName/auditBy/auditAt/auditRemark）与 AI 复核（aiReviewStatus/aiReviewReason/aiReviewHitWords/aiReviewAt）共 10 个；属①真缺口——审核抽屉应展示留痕与 AI 命中词，待该页负责人确认口径后补类型并接线']],
  ['PersonnelProfileSaveReqVO ← SaveReq', [4, '缺 positionName/archiveNo/bindedAuthUserId/version；属①真缺口——岗位名与档案号是档案必填口径的一部分，version 是更新时的乐观锁字段（不传则并发更新可能被后端拒绝）']],
  ['NoticeRespVO ← InternalNoticeItem', [1, '缺 internalFlag（是否内部公告）；属①真缺口——门户列表需要据此标注/过滤内部公告']],
]);

function walk(dir, filter, out = []) {
  let ents = [];
  try { ents = fs.readdirSync(dir, { withFileTypes: true }); } catch { return out; }
  for (const e of ents) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, filter, out);
    else if (filter(p)) out.push(p);
  }
  return out;
}

/* ---- 后端 VO（含嵌套类）→ 字段集 ---- */
const backend = new Map();
for (const f of walk(BE_ROOT, (p) => /VO\.java$/.test(p))) {
  const text = fs.readFileSync(f, 'utf8');
  const classes = [...text.matchAll(/(?:public\s+)?(?:static\s+)?class\s+(\w+)/g)].map((m) => m[1]);
  const outer = classes[0];
  if (!outer) continue;
  text.split(/class\s+\w+/).slice(1).forEach((body, i) => {
    const name = classes[i];
    if (!name) return;
    const fields = [...body.matchAll(/private\s+(?:static\s+final\s+)?[\w<>,\[\]\.\s]+?\s+(\w+)\s*;/g)]
      .map((m) => m[1])
      .filter((n) => n !== 'serialVersionUID');
    backend.set(i === 0 ? outer : outer + '.' + name, new Set(fields));
  });
}

/* ---- 前端 interface（注释点名 VO 的才算）→ 与候选 VO 求最佳重合 ---- */
const pairs = [];
const skipped = [];
for (const f of walk(FE_ROOT, (p) => /\.(ts|vue)$/.test(p))) {
  const text = fs.readFileSync(f, 'utf8');
  const re = /(\/\*\*[\s\S]{0,300}?\*\/\s*)?export\s+interface\s+(\w+)\s*\{([\s\S]*?)\n\s*\}/g;
  for (const m of text.matchAll(re)) {
    const vo = (m[1] || '').match(/([A-Z]\w*VO)(?:\.(\w+))?/);
    if (!vo) continue;
    const outer = vo[1];
    const cands = [outer, ...[...backend.keys()].filter((k) => k.startsWith(outer + '.'))].filter((k) => backend.has(k));
    if (!cands.length) { skipped.push({ fe: m[2], vo: outer, why: '后端无此 VO（可能已删除/改名）' }); continue; }
    const feFields = [...m[3].matchAll(/^\s*(\w+)\s*\??\s*:/gm)].map((x) => x[1]);
    if (!feFields.length) continue;
    let best = null;
    for (const c of cands) {
      const bf = backend.get(c);
      const ov = feFields.filter((x) => bf.has(x)).length / feFields.length;
      if (!best || ov > best.ov) best = { key: c, ov, bf };
    }
    if (best.ov < MIN_OVERLAP) { skipped.push({ fe: m[2], vo: best.key, why: '重合度 ' + best.ov.toFixed(2) + ' < ' + MIN_OVERLAP + '（疑似误链，已剔除）' }); continue; }
    const missing = [...best.bf].filter((x) => !feFields.includes(x));
    pairs.push({
      key: best.key + ' ← ' + m[2],
      file: path.relative(repoRoot, f).split(path.sep).join('/'),
      ov: best.ov,
      missing,
      total: best.bf.size,
    });
  }
}

const incomplete = pairs.filter((p) => p.missing.length > 0);
const violations = [];
const shrunk = [];
for (const p of incomplete) {
  const base = KNOWN_INCOMPLETE.get(p.key);
  if (!base) violations.push({ ...p, why: '新出现的缺口（基线未登记）' });
  else if (p.missing.length > base[0]) violations.push({ ...p, why: '缺口扩大：' + base[0] + ' → ' + p.missing.length });
  else if (p.missing.length < base[0]) shrunk.push({ ...p, was: base[0] });
}
const stale = [...KNOWN_INCOMPLETE.entries()].filter(([k]) => !incomplete.some((p) => p.key === k));

console.log('\n后端 VO（含嵌套）' + backend.size + ' 个；前端按注释可链接的 interface ' + pairs.length + ' 个（剔除疑似误链 ' + skipped.filter((s) => s.why.includes('重合度')).length + ' 个）');
console.log('完整镜像 ' + (pairs.length - incomplete.length) + ' 个；不完整 ' + incomplete.length + ' 个（基线登记 ' + KNOWN_INCOMPLETE.size + ' 个）');
if (skipped.length && LIST) {
  console.log('\n--- 未纳入比对的配对 ---');
  for (const s of skipped) console.log('  ' + s.vo.padEnd(34) + ' ← ' + s.fe.padEnd(24) + s.why);
}
if (LIST) {
  console.log('\n--- 不完整镜像清单 ---');
  for (const p of incomplete.sort((a, b) => b.missing.length - a.missing.length)) {
    const base = KNOWN_INCOMPLETE.get(p.key);
    console.log('  ' + (base ? '⚪ 基线' : '❌ 未登记') + ' 缺' + String(p.missing.length).padStart(3) + '/' + String(p.total).padStart(3) + '  重合' + p.ov.toFixed(2) + '  ' + p.key);
    console.log('        ' + p.file);
    console.log('        缺：' + p.missing.join(', '));
    if (base) console.log('        理由：' + base[1]);
  }
}

if (UPDATE) {
  const lines = incomplete.map((p) => '  [' + JSON.stringify(p.key) + ', [' + p.missing.length + ", '" + (KNOWN_INCOMPLETE.get(p.key)?.[1] || '待补理由') + "']],").join('\n');
  console.log('\n--update-baseline 建议内容（请人工复核理由后再写入）：\n' + lines);
  process.exit(0);
}

let failed = false;
if (violations.length) {
  failed = true;
  console.error('\n[FAIL] 前端类型与后端 VO 不对等（页面缺字段 → 该字段在界面上永远拿不到）：');
  for (const v of violations) {
    console.error('  ✗ ' + v.key + '  缺 ' + v.missing.length + '/' + v.total + '（' + v.why + '）');
    console.error('      缺：' + v.missing.join(', '));
    console.error('      ' + v.file);
  }
  console.error('处理：① 补齐前端类型并接线（真缺口）；② 若确属后端保留字段，登记进本脚本 KNOWN_INCOMPLETE 并写明理由。');
}
if (stale.length) {
  failed = true;
  console.error('\n[FAIL] KNOWN_INCOMPLETE 存在陈旧条目：' + stale.map(([k]) => k).join('、'));
  console.error('缺口已消失（类型已补齐/该类型已删），请删除条目——陈旧基线会掩盖将来重现的缺口。');
}
if (failed) process.exit(1);

if (shrunk.length) {
  console.log('\n提示：以下条目缺口已缩小，请把基线数字改小（只许减）：');
  for (const s of shrunk) console.log('  ' + s.key + '：' + s.was + ' → ' + s.missing.length);
}
console.log('[OK] 前端类型与后端 VO 对等（' + pairs.length + ' 个配对：完整 ' + (pairs.length - incomplete.length) + '，既知不完整 ' + KNOWN_INCOMPLETE.size + ' 且均注明理由）');
