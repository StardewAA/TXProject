#!/usr/bin/env node
/**
 * 字典可读性自检（第 12 道门禁）：消费字典的页面，其用户是否**读得到**字典？
 *
 * 由来：GET /bidding/meta/dicts|configs 的 @PreAuthorize 只列了 5 个电子证照/资质/人员域权限
 * （见 docs/前端规范-全域接口说明/12-字典元数据读取权限契约-2026-09-14.md），
 * 而这两个接口是所有招投标页面共用的前置元数据。于是出现一类**静默降级**：
 * 页面用了字典，但能打开它的角色没有那 5 个权限之一 → 接口 403 → 页面回落英文 code。
 * 界面上看不出"坏"，只是中文变英文；没人报错，也就没人发现（用户硬约束要的正是"枚举动态可配置"）。
 *
 * 判定口径：
 *   · 只检查调用 useCertMeta() 的前端文件（= 真的在消费字典）；
 *   · 该文件的"可读性证据" = 文件内引用的 bidding 权限码 ∪ **该页面菜单的页权限**（从 sql 的 system_menu 解析，
 *     component → permission）。只看文件内的 can(...) 会误判：例如 certificate/lifecycle 页自身只引用
 *     lifecycle:apply/audit，但菜单页权限 bidding:certificate:lifecycle:query 本就在可读集合内；
 *   · 两者与 META_READABLE 无交集 → FAIL（除非在 KNOWN_GAP 登记并注明契约出处）；
 *   · 文件内没有任何 bidding 权限码、且不是页面（如子组件 modules/*.vue）→ 跟随父页面，跳过。
 *
 * 后端按契约放开读取面后，请同步更新 META_READABLE 并清空 KNOWN_GAP。
 * 用法：node sql/tools/check-dict-permission.js [--list]
 */
const fs = require('node:fs');
const path = require('node:path');

const LIST = process.argv.includes('--list');
const repoRoot = path.resolve(__dirname, '../../..');
const FE_ROOT = path.join(repoRoot, 'txgy-ui-admin-vben/apps/web-antd/src/views/bidding');
const SQL_DIRS = [path.join(repoRoot, 'txgy-module-bidding/sql')];

/** meta 接口当前可读的权限集合（镜像 MetaController 的 @PreAuthorize；变更以契约文件 12 的答复为准） */
const META_READABLE = new Set([
  'bidding:certificate:query',
  'bidding:qualification:query',
  'bidding:personnel:profile:query',
  'bidding:certificate:lifecycle:query',
  'bidding:qualification:permit:query',
  // 2026-09-20 追加：平台审查任务中心页要读 review_task_status / review_biz_type（状态角标 + 业务类型下拉），
  // 页面权限即 bidding:review:query。该权限点本就存在（菜单 60005600 已验证），故只加读取面、不新增权限点。
  'bidding:review:query',
]);

/**
 * 既知缺口（方案 A/B 落地前）：只许减少。
 * 依据：docs/前端规范-全域接口说明/12-字典元数据读取权限契约-2026-09-14.md
 */
const KNOWN_GAP = new Map([
  ['project/index.vue', '页权限 bidding:project:query 不在 meta 可读集合内（契约文件 12 §二 影响面第 1 行）；后端放开后删除本条'],
  ['noticeExternal/index.vue', '页权限 bidding:notice-external:update 不在集合内；2026-09-25 起状态/审核/内容形态改为"字典优先、映射表兜底"，后端放开前靠兜底不退化（契约 12 §二 第 4 行）'],
  ['subjectAuth/index.vue', '页权限 bidding:subject-auth:query 不在集合内；2026-09-25 起主体类型文案与选项改为"字典优先、SUBJECT_TYPE_LABEL 兜底"（契约 12 §二 第 5 行）'],
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

/* ---- 1) 解析 system_menu：component → 页权限（type=2 的页面行） ---- */
const pagePerm = new Map();
/** component 归一化：菜单里写 'bidding/x/index'，页面文件路径推出来的是 'bidding/x' —— 两端都去掉尾部 /index 再比对 */
const normComp = (c) => c.replace(/\/index$/, '');
const sqlFiles = SQL_DIRS.flatMap((d) => {
  const masters = walk(d, (p) => /master-.*\.sql$/.test(p));
  const hist = walk(path.join(d, 'history'), (p) => /\.sql$/.test(p));
  return [...masters, ...hist];
});
for (const f of sqlFiles) {
  const text = fs.readFileSync(f, 'utf8');
  // 逐行按 component 定位：从 component 字符串往回找行首左括号，避免被 NOW() 等函数括号打断
  // （列顺序：id, name, permission, type, sort, parent_id, path, icon, component, ... → 权限列在 component 之前）
  for (const m of text.matchAll(/'(bidding\/[a-z0-9/_-]+)'/g)) {
    const idx = m.index;
    const start = text.lastIndexOf('(', idx);
    const row = text.slice(start, idx);
    const perm = row.match(/'(bidding:[a-z0-9:_-]+)'/);
    if (perm) pagePerm.set(normComp(m[1]), perm[1]);
  }
}

/* ---- 2) 扫描消费字典的前端文件 ---- */
const files = walk(FE_ROOT, (p) => /\.(vue|ts)$/.test(p));
const rows = [];
for (const f of files) {
  const text = fs.readFileSync(f, 'utf8');
  if (!/useCertMeta\s*\(/.test(text)) continue;
  const rel = path.relative(FE_ROOT, f).split(path.sep).join('/');
  const inFile = [...new Set([...text.matchAll(/'(bidding:[a-z0-9:_-]+)'/g)].map((x) => x[1]))];
  // 页面 → 菜单 component：views/bidding/a/b/index.vue → bidding/a/b
  const comp = normComp('bidding/' + rel.replace(/\.vue$/, ''));
  const menuPerm = pagePerm.get(comp) || null;
  const evidence = [...new Set([...inFile, ...(menuPerm ? [menuPerm] : [])])];
  const readable = evidence.filter((p) => META_READABLE.has(p));
  const isPage = /\/index\.vue$/.test(rel);
  const gap = readable.length === 0 && (evidence.length > 0 || isPage);
  rows.push({ rel, inFile, menuPerm, readable, gap, skipped: evidence.length === 0 && !isPage });
}

const violations = rows.filter((r) => r.gap && !KNOWN_GAP.has(r.rel));
const staleGap = [...KNOWN_GAP.keys()].filter((k) => !rows.some((r) => r.rel === k && r.gap));

if (LIST) {
  console.log('\n--- 全量清单（消费字典的文件）---');
  for (const r of rows.sort((a, b) => a.rel.localeCompare(b.rel))) {
    const mark = r.skipped ? '—  跟随父页面' : r.gap ? (KNOWN_GAP.has(r.rel) ? '⚪ 既知缺口' : '❌ 无字典可读性') : '✅ 可读';
    console.log('  ' + mark.padEnd(14) + r.rel);
    console.log('        文件内权限：' + (r.inFile.join(', ') || '（无）'));
    console.log('        菜单页权限：' + (r.menuPerm || '（未登记）') + '   命中可读集合：' + (r.readable.join(', ') || '（无）'));
    if (KNOWN_GAP.has(r.rel)) console.log('        既知缺口理由：' + KNOWN_GAP.get(r.rel));
  }
}

console.log('\nmeta 可读权限集合（' + META_READABLE.size + ' 个）：' + [...META_READABLE].join(', '));
console.log('消费字典的文件 ' + rows.length + ' 个；其中既知缺口 ' + rows.filter((r) => r.gap && KNOWN_GAP.has(r.rel)).length + ' 个');

if (violations.length > 0) {
  console.error('\n[FAIL] 以下文件消费字典，但其用户没有任何一个 meta 可读权限（接口会 403 → 页面静默回落英文 code）：');
  for (const v of violations) {
    console.error('  ✗ ' + v.rel);
    console.error('      文件内权限：' + (v.inFile.join(', ') || '（无）'));
    console.error('      菜单页权限：' + (v.menuPerm || '（未登记）'));
  }
  console.error('修复三选一：① 该页改用不依赖字典的展示；');
  console.error('            ② 按契约文件 12 请后端放开 /bidding/meta/* 读取面（前端只提契约，不自行改权限）；');
  console.error('            ③ 若属过渡期既知缺口，写入 KNOWN_GAP 并注明契约出处。');
  process.exit(1);
}

if (staleGap.length > 0) {
  console.error('\n[FAIL] KNOWN_GAP 存在陈旧条目：' + staleGap.join(', '));
  console.error('缺口已消失（后端放开或页面已改），请删除该条 —— 陈旧白名单会掩盖将来重新出现的缺口。');
  process.exit(1);
}

console.log('[OK] 消费字典的页面均具备 meta 可读性（既知缺口 ' + KNOWN_GAP.size + ' 条，均注明契约出处）');
