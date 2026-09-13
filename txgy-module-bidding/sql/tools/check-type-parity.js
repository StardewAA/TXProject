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
const PAIRS = process.argv.includes('--pairs');
const UPDATE = process.argv.includes('--update-baseline');
const repoRoot = path.resolve(__dirname, '../../..');
const BE_ROOT = path.join(repoRoot, 'txgy-module-bidding/txgy-module-bidding-server/src/main/java');
/** 全仓 Java 根：解析 extends 继承需能查到框架层类（如 txgy-framework 的 PageParam） */
const REPO_JAVA_ROOT = repoRoot;
const FE_ROOT = path.join(repoRoot, 'txgy-ui-admin-vben/apps/web-antd/src');
const MIN_OVERLAP = 0.6;

/**
 * 既知不完整镜像（只许减少）。key = VO名 ← 前端 interface 名；值 = [冻结缺字段数, 理由]。
 * 缺口变多 → FAIL；缺口消失（补全了）→ 也必须删条目（陈旧基线会掩盖将来重现的缺口）。
 */
const KNOWN_INCOMPLETE = new Map([
  ['CrawlSourceRespVO ← SourceItem', [11, '缺的是 API 直取的 11 个展开列（apiUrl/apiMethod/apiHeadersJson/…/apiUrlPrefix）；页面走 apiConfigJson 整体 JSON 配置，未逐一展开——属②后端保留字段；若将来改为表单化配置需先补类型']],
]);

/** 从 '{' 起按花括号配平取出接口体（见上文关于嵌套对象字面量的说明） */
function sliceBraces(text, openIdx) {
  let depth = 0;
  for (let i = openIdx; i < text.length; i++) {
    if (text[i] === '{') depth++;
    else if (text[i] === '}') {
      depth--;
      if (depth === 0) return text.slice(openIdx + 1, i);
    }
  }
  return text.slice(openIdx + 1);
}

/**
 * 反向检查白名单：前端类型里有、后端 VO 没有，但**确属前端自有字段**（不是拼写错位/凭空字段）。
 * 目前为空——若有新增，必须写明"为什么它是前端自有的"。
 */
const IGNORED_EXTRA = new Set([]);

/**
 * 既知反向差异（只许减少）：key = VO名 ← 前端 interface 名；值 = [冻结多字段数, 理由]。
 * 出现未登记的反向差异 → FAIL（这类差异＝前端在读取一个后端根本不返回的字段，界面必然是空的）。
 */
const KNOWN_EXTRA = new Map([
  ['NoticeExternalPageReqVO ← NoticePageParams', [1, 'resultRaws：门户"合并视图按组逐原始值抓全"的前端内部参数，不发后端（api.ts 的 toQueryPlain 白名单构造请求）；后端无此字段属正常']],
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

/* ---- 后端类 → 字段集（含 extends 继承） ----
 * 为什么要解析继承：VO 常继承 PageParam，pageNo/pageSize 不在本类体内声明。
 * 不解析继承会把这两个字段误判成"前端多出来的字段"（假阳性），故这里：
 *   ① 扫**全仓** Java（不只 bidding 模块）建立 类名 → 字段集 索引，才能查到框架层的 PageParam；
 *   ② 字段集 = 本类字段 ∪ 父类字段（递归，带环保护）。 */
const classFields = new Map(); // FQN → { fields, parent, pkg, isBidding }
const bySimple = new Map();     // 简单类名 → [FQN, ...]（**同名类在不同模块很常见**，必须按 FQN 区分：
                                //  实测 QualificationRespVO / NoticeRespVO 在别的模块也有同名类，
                                //  早期按简单类名索引会被覆盖，导致前端类型与"别的模块的同名 VO"比对 → 误判重合度）
for (const f of walk(REPO_JAVA_ROOT, (p) => /\.java$/.test(p))) {
  const text = fs.readFileSync(f, 'utf8');
  const pkg = (text.match(/^\s*package\s+([\w.]+)\s*;/m) || [])[1] || '';
  const isBidding = /txgy-module-bidding/.test(f);
  const classes = [...text.matchAll(/(?:public\s+)?(?:static\s+)?class\s+(\w+)(?:\s+extends\s+(\w+))?/g)];
  const outer = classes[0] && classes[0][1];
  if (!outer) continue;
  text.split(/class\s+\w+/).slice(1).forEach((body, i) => {
    const m = classes[i];
    if (!m) return;
    // 注意要容忍**带初始化的字段**（如 PageParam 的 private Integer pageNo = PAGE_NO;）——
    // 早期版本要求"名字后紧跟分号"，导致整个 PageParam 的字段都读不到，
    // 进而把 pageNo/pageSize 误判成"前端多出来的字段"（假阳性）。
    // 只取**实例字段**：排除 static（常量如 PageParam.PAGE_NO 不是 JSON 字段）与 serialVersionUID
    const fields = [...body.matchAll(/private\s+(static\s+final\s+)?[\w<>,\[\]\.\s]+?\s+(\w+)\s*(?:=[^;]*)?;/g)]
      .filter((x) => !x[1])
      .map((x) => x[2])
      .filter((n) => n !== 'serialVersionUID');
    const simple = i === 0 ? m[1] : outer + '.' + m[1];
    const fqn = pkg + '.' + simple;
    classFields.set(fqn, { fields, parent: m[2] || null, pkg, isBidding });
    if (!bySimple.has(simple)) bySimple.set(simple, []);
    bySimple.get(simple).push(fqn);
  });
}
/** 按简单名解析父类：**优先同模块（招投标）、再同包、再唯一候选**——避免同名类串模块 */
function resolveParent(simple, child) {
  const cands = bySimple.get(simple) || [];
  if (!cands.length) return null;
  const sameModule = cands.find((c) => classFields.get(c).isBidding === child.isBidding);
  if (sameModule) return sameModule;
  const samePkg = cands.find((c) => classFields.get(c).pkg === child.pkg);
  if (samePkg) return samePkg;
  return cands.length === 1 ? cands[0] : null;
}
/** 递归合并父类字段（带环保护） */
function withInherited(fqn, seen = new Set()) {
  const info = classFields.get(fqn);
  if (!info || seen.has(fqn)) return new Set();
  seen.add(fqn);
  const out = new Set(info.fields);
  const parentFqn = info.parent ? resolveParent(info.parent, info) : null;
  if (parentFqn) for (const x of withInherited(parentFqn, seen)) out.add(x);
  return out;
}
const backend = new Map();
for (const f of walk(BE_ROOT, (p) => /VO\.java$/.test(p))) {
  const text = fs.readFileSync(f, 'utf8');
  const classes = [...text.matchAll(/(?:public\s+)?(?:static\s+)?class\s+(\w+)/g)].map((m) => m[1]);
  const outer = classes[0];
  if (!outer) continue;
  text.split(/class\s+\w+/).slice(1).forEach((body, i) => {
    const name = classes[i];
    if (!name) return;
    const key = i === 0 ? outer : outer + '.' + name;
    // 用招投标模块下的 FQN 取字段（同名类别模块也有，按 FQN 才不会串）
    const fqn = (text.match(/^\s*package\s+([\w.]+)\s*;/m) || [])[1] + '.' + key;
    backend.set(key, withInherited(fqn));
  });
}

/* ---- 前端 interface（注释点名 VO 的才算）→ 与候选 VO 求最佳重合 ---- */
const pairs = [];
const skipped = [];
for (const f of walk(FE_ROOT, (p) => /\.(ts|vue)$/.test(p))) {
  const text = fs.readFileSync(f, 'utf8');
  // 逐个 interface 定位，再用**花括号配平**取接口体：
  // 不能用"第一个行首 }"截断——接口里出现嵌套对象字面量（字段?: { … }）时会提前结束，
  // 导致后面的字段被误报为"缺失"（假阳性）。花括号配平对字符串/注释里的括号不敏感，
  // 属可接受的近似（前端接口定义里极少出现这类内容）。
  const re = /(\/\*\*[\s\S]{0,300}?\*\/\s*)?export\s+interface\s+(\w+)\s*(\{)/g;
  for (const m of text.matchAll(re)) {
    const vo = (m[1] || '').match(/([A-Z]\w*VO)(?:\.(\w+))?/);
    if (!vo) continue;
    const body = sliceBraces(text, m.index + m[0].length - 1);
    const outer = vo[1];
    const cands = [outer, ...[...backend.keys()].filter((k) => k.startsWith(outer + '.'))].filter((k) => backend.has(k));
    if (!cands.length) { skipped.push({ fe: m[2], vo: outer, why: '后端无此 VO（可能已删除/改名）' }); continue; }
    const feFields = [...body.matchAll(/^\s*(\w+)\s*\??\s*:/gm)].map((x) => x[1]);
    if (!feFields.length) continue;
    let best = null;
    for (const c of cands) {
      const bf = backend.get(c);
      const ov = feFields.filter((x) => bf.has(x)).length / feFields.length;
      if (!best || ov > best.ov) best = { key: c, ov, bf };
    }
    if (best.ov < MIN_OVERLAP) { skipped.push({ fe: m[2], vo: best.key, why: '重合度 ' + best.ov.toFixed(2) + ' < ' + MIN_OVERLAP + '（疑似误链，已剔除）' }); continue; }
    const missing = [...best.bf].filter((x) => !feFields.includes(x));
    // 反向：前端有、VO 没有（扣除 IGNORED_EXTRA 里的白名单）
    const extra = feFields.filter((x) => !best.bf.has(x) && !IGNORED_EXTRA.has(x));
    pairs.push({
      key: best.key + ' ← ' + m[2],
      file: path.relative(repoRoot, f).split(path.sep).join('/'),
      ov: best.ov,
      missing,
      extra,
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

// 反向：前端多出来的字段
const withExtra = pairs.filter((p) => p.extra.length > 0);
const extraViolations = [];
const extraShrunk = [];
for (const p of withExtra) {
  const base = KNOWN_EXTRA.get(p.key);
  if (!base) extraViolations.push({ ...p, why: '新出现的反向差异（基线未登记）' });
  else if (p.extra.length > base[0]) extraViolations.push({ ...p, why: '反向差异扩大：' + base[0] + ' → ' + p.extra.length });
  else if (p.extra.length < base[0]) extraShrunk.push({ ...p, was: base[0] });
}
const extraStale = [...KNOWN_EXTRA.entries()].filter(([k]) => !withExtra.some((p) => p.key === k));

console.log('\n后端 VO（含嵌套）' + backend.size + ' 个；前端按注释可链接的 interface ' + pairs.length + ' 个（剔除疑似误链 ' + skipped.filter((s) => s.why.includes('重合度')).length + ' 个）');
console.log('完整镜像 ' + (pairs.length - incomplete.length) + ' 个；不完整 ' + incomplete.length + ' 个（基线登记 ' + KNOWN_INCOMPLETE.size + ' 个）');
console.log('反向（前端多出字段）差异 ' + withExtra.length + ' 个（基线登记 ' + KNOWN_EXTRA.size + ' 个）');
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

if (PAIRS) {
  console.log('\n--- 全部配对（VO ← 前端 interface，重合度与字段规模）---');
  for (const p of [...pairs].sort((a, b) => a.key.localeCompare(b.key))) {
    console.log('  ' + p.key.padEnd(58) + ' 重合' + p.ov.toFixed(2) + '  VO字段' + String(p.total).padStart(3) +
      '  缺' + String(p.missing.length).padStart(3) + '  多' + String(p.extra.length).padStart(2) + '  ' + p.file.replace(/^txgy-ui-admin-vben\/apps\/web-antd\/src\//, ''));
  }
}

if (LIST && withExtra.length) {
  console.log('\n--- 反向差异清单（前端有、后端 VO 没有 → 该字段在界面上恒为空）---');
  for (const p of withExtra.sort((a, b) => b.extra.length - a.extra.length)) {
    const base = KNOWN_EXTRA.get(p.key);
    console.log('  ' + (base ? '⚪ 基线' : '❌ 未登记') + ' 多' + String(p.extra.length).padStart(3) + '/' + String(p.extra.length + p.total - p.missing.length).padStart(3) + '  ' + p.key);
    console.log('        ' + p.file);
    console.log('        多：' + p.extra.join(', '));
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
if (extraViolations.length) {
  failed = true;
  console.error('\n[FAIL] 前端类型声明了后端 VO 没有的字段（界面读它必然是空的/拼写错位）：');
  for (const v of extraViolations) {
    console.error('  ✗ ' + v.key + '  多 ' + v.extra.length + '（' + v.why + '）');
    console.error('      多：' + v.extra.join(', '));
    console.error('      ' + v.file);
  }
  console.error('处理：① 删掉前端凭空/拼错的字段；② 若确属前端自有（不发后端），写进 IGNORED_EXTRA 或 KNOWN_EXTRA 并说明理由。');
}
if (extraStale.length) {
  failed = true;
  console.error('\n[FAIL] KNOWN_EXTRA 存在陈旧条目：' + extraStale.map(([k]) => k).join('、'));
  console.error('反向差异已消失（字段已删或后端已补），请删除条目。');
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
if (extraShrunk.length) {
  console.log('\n提示：以下反向差异已缩小，请把基线数字改小（只许减）：');
  for (const e of extraShrunk) console.log('  ' + e.key + '：' + e.was + ' → ' + e.extra.length);
}
console.log('[OK] 前端类型与后端 VO 双向对等（' + pairs.length + ' 个配对：完整 ' + (pairs.length - incomplete.length) + '，既知不完整 ' + KNOWN_INCOMPLETE.size + '，既知反向差异 ' + KNOWN_EXTRA.size + '，均注明理由）');
