#!/usr/bin/env node
/**
 * 字典覆盖率自检：前端引用的字典类型 vs bidding_dict_data 已注册的字典类型。
 *
 * 为什么需要它：字典是枚举的唯一动态来源（用户硬约束：状态码/枚举值必须支持动态配置、严禁硬编码），
 * 而"字典名写错"与"漏注册"都不会报错——页面只是静默退化成显示英文 code，线上很难发现。
 * 真实事故：资质页曾把已注册的 qual_apply_type / qual_apply_status 写成 apply_type / apply_status，
 * 页面因此长期只显示 DRAFT/APPROVED 这类原始码，直至人工逐条比对才发现。
 *
 * 用法：
 *   node sql/tools/check-dict-coverage.js                 # 默认：sql/*.sql + sql/history/*.sql（整仓口径）
 *   node sql/tools/check-dict-coverage.js --masters-only  # 只校验一键包（发版前用：确保新装库不缺字典）
 *   node sql/tools/check-dict-coverage.js --list     # 额外打印全部已注册类型与引用次数
 * 退出码：0 = 无未注册引用；1 = 存在未注册引用（修复方式：注册字典，或修前端引用名，或登记到 ALLOW 白名单）。
 */
const fs = require('node:fs');
const path = require('node:path');

/** 默认口径 = 终态包 + history（"整仓是否注册过"）；--masters-only 用于发版前只校验一键包 */
const MASTERS_ONLY = process.argv.includes('--masters-only');
const LIST_ALL = process.argv.includes('--list');

/** 有意不登记字典的类型：登记反而制造第二套编码（必须在 reason 里写清理由）
 *  ⚠️ 白名单只对"**当前真实引用**"生效：条目一旦不再被引用就是死配置，门禁会直接 FAIL 要求删除
 *     —— 陈旧白名单会变成静默盲区（将来前端真的引用该类型时会被放行）。
 *  历史：qual_grade 曾在此列，后经核实**前端并无任何调用**（全仓仅两行注释提及），
 *        且后端 /bidding/qualification/standard/options 明确"取自现行标准库，不固化字典"，
 *        故条目已删除；该值域由标准库数据决定，页面原样展示，本就不需要字典。 */
const ALLOW = new Map([
  ['ai_review_status', 'AI 合规审查结论（PASS/REJECT/FAILED），前端已按"字典优先、未下发显示后端原始 code"接线，' +
    '并在悬浮卡里注明原始 code 来源；注册请求见 docs/前端规范-全域接口说明/13-枚举字典注册请求-资质规则采集公告域-2026-09-14.md（B 类）。' +
    '字典一注册，本列自动变中文，届时删除本条目（陈旧条目会 FAIL）'],
]);

const repoRoot = path.resolve(__dirname, '..', '..', '..');
const sqlDir = path.join(repoRoot, 'txgy-module-bidding', 'sql');
const feDirs = [
  path.join(repoRoot, 'txgy-ui-admin-vben', 'apps', 'web-antd', 'src', 'views', 'bidding'),
  path.join(repoRoot, 'txgy-ui-admin-vben', 'apps', 'web-antd', 'src', 'api', 'bidding'),
];

function walk(dir, filter, out = []) {
  let entries;
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch {
    return out;
  }
  for (const e of entries) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) {
      if (['.git', 'node_modules', 'dist', 'target'].includes(e.name)) continue;
      walk(p, filter, out);
    } else if (filter(p)) {
      out.push(p);
    }
  }
  return out;
}

// ---- 1) 已注册字典类型（bidding_dict_data 种子行形态：,'<type>','<value>','<label>' ） ----
const SEED_RE = new RegExp(",'([a-z][a-z0-9_]{2,30})','([^']*)','([^']*)'", 'g');
const registered = new Map();
const sqlFiles = walk(sqlDir, (p) => p.endsWith('.sql') && (!MASTERS_ONLY || path.dirname(p) === sqlDir));
for (const f of sqlFiles) {
  const text = fs.readFileSync(f, 'utf8');
  // 只在插入 bidding_dict_data 的语句内取种子行，避免 system_menu / 其它表的 VALUES 被误当字典
  for (const stmt of text.split(';')) {
    if (!/INSERT\s+(?:IGNORE\s+)?INTO\s+`?bidding_dict_data`?/i.test(stmt)) continue;
    for (const m of stmt.matchAll(SEED_RE)) {
      const type = m[1];
      if (!registered.has(type)) registered.set(type, { count: 0, files: new Set() });
      const o = registered.get(type);
      o.count += 1;
      o.files.add(path.relative(repoRoot, f).split('\\').join('/'));
    }
  }
}

// ---- 2) 前端引用的字典类型（dictLabel/dictTag/dictOptions/dictColor('type') ） ----
const USE_RE = new RegExp("(?:dict[A-Za-z]*|(?:text|tag|label|color|name|status)Of)\\(\\s*'([a-z][a-z0-9_]{2,30})'", 'g');
const used = new Map();
for (const dir of feDirs) {
  for (const f of walk(dir, (p) => /\.(ts|vue)$/.test(p))) {
    const text = fs.readFileSync(f, 'utf8');
    for (const m of text.matchAll(USE_RE)) {
      const type = m[1];
      if (!used.has(type)) used.set(type, { count: 0, files: new Set() });
      const o = used.get(type);
      o.count += 1;
      o.files.add(path.relative(repoRoot, f).split('\\').join('/'));
    }
  }
}

const missing = [...used.keys()].filter((t) => !registered.has(t)).sort();
const unexpected = missing.filter((t) => !ALLOW.has(t));
/** 命中的白名单（当前确实被引用且有意不登记） */
const allowHit = missing.filter((t) => ALLOW.has(t));
/** 陈旧白名单：登记了但当前没有任何引用 —— 死配置，必须删掉 */
const allowStale = [...ALLOW.keys()].filter((t) => !missing.includes(t));

console.log(`扫描范围：${MASTERS_ONLY ? 'sql/*.sql（仅一键包）' : 'sql/*.sql + sql/history/*.sql（整仓）'}；前端 bidding 域 views/api`);
console.log(`已注册字典 ${registered.size} 类；前端引用 ${used.size} 类`);

if (LIST_ALL) {
  const all = [...new Set([...registered.keys(), ...used.keys()])].sort();
  console.log('\n--- 全量清单（注册行数 / 前端引用次数）---');
  for (const t of all) {
    const r = registered.get(t);
    const u = used.get(t);
    console.log(`  ${t.padEnd(26)} 注册 ${String(r ? r.count : 0).padStart(3)} 行  引用 ${String(u ? u.count : 0).padStart(3)} 处${r ? '' : '   ← 未注册'}`);
  }
}

if (missing.length > 0) {
  console.log('\n--- 前端引用但未注册（页面会退化成显示英文 code）---');
  for (const t of missing) {
    const u = used.get(t);
    console.log(`\n  ✗ ${t}（引用 ${u.count} 处）`);
    for (const f of [...u.files].slice(0, 4)) console.log(`      ${f}`);
    if (ALLOW.has(t)) console.log(`      [白名单] ${ALLOW.get(t)}`);
  }
}

if (unexpected.length > 0) {
  console.error(`\n[FAIL] ${unexpected.length} 类字典未注册：${unexpected.join(', ')}`);
  console.error('修复三选一：① 在 sql/history 新增注册脚本并镜像进 master-credential-sync.sql；');
  console.error('            ② 修正前端引用名（以 bidding_dict_data 的 dict_type 为准）；');
  console.error('            ③ 确属有意不登记，则加入本脚本 ALLOW 白名单并写明理由。');
  process.exit(1);
}

if (allowStale.length > 0) {
  console.error(`\n[FAIL] ALLOW 白名单存在陈旧条目：${allowStale.join(', ')}`);
  console.error('白名单只对"当前真实引用"生效；陈旧条目是静默盲区——将来前端真引用了该类型，门禁会直接放行。');
  console.error('处理：删除该条目（字典已注册 / 前端已不再引用 / 从未真正引用过，三种情况都只需删一行）。');
  process.exit(1);
}

const allowNote = allowHit.length > 0
  ? `白名单命中 ${allowHit.length} 类：${allowHit.map((t) => `${t}（${ALLOW.get(t)}）`).join('；')}`
  : '白名单 0 条（全部字典均已注册，无需例外）';
console.log(`\n[OK] 前端引用的字典类型均已注册（${allowNote}）`);
