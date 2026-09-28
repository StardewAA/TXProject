const fs = require('fs');
const base = 'D:/Code/IDEA/txgy/txgycloud/txgy-module-bidding/sql/';
const outPath = base + 'history/2026-10-02-bid-agent-tables-backfill.sql';
let out = [];
out.push('-- ============================================================');
out.push('-- 2026-10-02 标书智能体域·纯建表回填（本库漏执行补跑）');
out.push('-- 来源：master-bidding-sync.sql + history/2026-09-16/09-27 建表段 + 09-18~09-28 安全增量');
out.push('-- 背景：菜单冲突已由 2026-10-02-bid-agent-menu-local-ids.sql 解决并执行（7000~7023 在库）；');
out.push('--       建表段因 09-16 脚本开头 ALTER 不存在的 bidding_bid_agent 报错中止从未跑到。');
out.push('-- 本文件 **不含任何 system_menu 语句**（菜单段全部跳过：09-18-model-menu / 09-22 两个 restore/embed）。');
out.push('-- 幂等：CREATE TABLE IF NOT EXISTS / INSERT IGNORE / information_schema 条件 DDL / 幂等 DELETE+UPDATE。');
out.push('-- ============================================================');
out.push('SET NAMES utf8mb4;');
out.push('');
out.push('-- ---------- A. 建表 15 张（master 4 + 09-16 六 + 09-27 五） ----------');
const tableRe = /CREATE TABLE IF NOT EXISTS \`bidding_bid_[a-z0-9_]*\` \([\s\S]*?\) ENGINE=InnoDB[^;]*;/g;
for (const f of ['master-bidding-sync.sql', 'history/2026-09-16-bid-agent-agent-platform.sql', 'history/2026-09-27-bid-agent-runtime.sql']) {
  const s = fs.readFileSync(base + f, 'utf8');
  const ms = s.match(tableRe) || [];
  for (const m of ms) out.push('-- 表: ' + m.match(/bidding_bid_[a-z0-9_]+/)[0] + '（' + f + '）', m);
}
out.push('');
out.push('-- ---------- B. 09-16 条件 ALTER（tool_whitelist/max_tokens/skill_auto_attach/outline_json） ----------');
const alterRe = /SET @c := \(SELECT COUNT\(\*\) FROM information_schema\.columns[\s\S]*?DEALLOCATE PREPARE stmt;/g;
const s16 = fs.readFileSync(base + 'history/2026-09-16-bid-agent-agent-platform.sql', 'utf8');
const alters = s16.match(alterRe) || [];
out.push('-- 提取条件 ALTER ' + alters.length + ' 块');
for (const a of alters) out.push(a);
out.push('');
out.push('-- ---------- C. 09-16 种子（8 条预置技能 + 云端模型占位） ----------');
const seedStart = s16.indexOf('INSERT IGNORE INTO \`bidding_bid_agent_skill\`');
if (seedStart >= 0) {
  const seed = s16.slice(seedStart).trimEnd();
  // 种子段直接到文件尾（含 model INSERT）
  out.push('-- 种子段（skill 8 条 + model 占位）');
  out.push(seed);
} else out.push('-- WARN: 未找到 skill 种子段');
out.push('');
out.push('-- ---------- D. 安全增量（条件/幂等，零菜单） ----------');
const incs = [
  'history/2026-09-18-bid-agent-knowledge-refine.sql',
  'history/2026-09-20-bid-agent-task-running-unique.sql',
  'history/2026-09-20-bid-agent-unique-key-deleted.sql',
  'history/2026-09-20-bid-agent-chunk-material-id.sql',
  'history/2026-09-20-bid-agent-task-doc-category.sql',
  'history/2026-09-21-bid-agent-task-ghost-cancel.sql',
  'history/2026-09-28-bid-agent-bm25.sql',
  'history/2026-09-28-bid-agent-all-editable.sql',
];
for (const f of incs) {
  const s = fs.readFileSync(base + f, 'utf8');
  out.push('-- >>> 增量: ' + f);
  out.push(s.trim());
  out.push('');
}
const final = out.join('\n');
fs.writeFileSync(outPath, final, 'utf8');
console.log('写入: ' + outPath);
console.log('总行数: ' + final.split('\n').length);
console.log('CREATE TABLE 数: ' + (final.match(/CREATE TABLE IF NOT EXISTS/g) || []).length);
console.log('system_menu 引用: ' + (final.match(/system_menu/g) || []).length);
console.log('INSERT 种子 数: ' + (final.match(/INSERT IGNORE INTO/g) || []).length);
