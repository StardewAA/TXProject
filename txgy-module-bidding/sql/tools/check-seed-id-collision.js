#!/usr/bin/env node
/**
 * 种子主键号段自检（2026-09-20 新增，起因是一次**静默**事故）：
 *
 * 背景：本域若干净表的主键是**手工指派**的（非自增，见 check-package-invariants ⑥），
 * 号段按"域"划分。只要两个域用了同一段，第二个脚本的 INSERT IGNORE 会**不报错地整行跳过** ——
 * 命令返回成功、脚本看起来执行过了，但库里根本没有那几行。
 *
 * 真实事故：`2026-09-20-review-task-center.sql` 的 review_task_status 用了 ...0030xx，
 * 与既有 personnel_status 撞号 ⇒ 三行被静默跳过 ⇒ /bidding/meta/dicts 报
 * 1060108008「字典类型不支持：review_task_status」⇒ 审查页状态下拉与角标拿不到中文。
 * 排查代价高：脚本"执行成功"、库表"存在"、接口"有返回"，全都正常，只有内容缺了三行。
 *
 * 本脚本把这条不变式独立出来：**扫 sql/ 与 sql/history/ 的全部种子 INSERT，按 (表, 主键) 找撞号**。
 * 输出里会列出每个号段的归属，便于新增脚本挑空闲段。
 *
 * 用法：node sql/tools/check-seed-id-collision.js [--list]
 * 退出码：0 = 无撞号；1 = 有撞号（附冲突明细与占用报告）。
 */
const fs = require('node:fs');
const path = require('node:path');

const LIST = process.argv.includes('--list');
const sqlRoot = path.resolve(__dirname, '..');

/** 只检查"主键手工指派"的表：这些表的种子必须显式带 id，撞号即静默丢数据 */
const SEED_TABLES = ['bidding_dict_data', 'cert_alert_config', 'bidding_rule_config', 'system_menu'];

/**
 * 有意复用主键的文件（**必须逐条写明理由**）。
 * 判据不是"文件名叫什么"，而是"它的语句是 ON DUPLICATE KEY UPDATE 还是 INSERT IGNORE"：
 *   - ON DUPLICATE KEY UPDATE：撞号是**设计意图**（把旧行改写/迁号），重复执行安全，内容不会丢；
 *   - INSERT IGNORE：撞号是**静默丢弃**（后一条整行不落库、命令还不报错）—— 本脚本要抓的就是它。
 * 故这里只放"确实用 upsert 做迁移"的脚本；新增条目请先确认它真的是 upsert。
 */
const INTENTIONAL_REUSE_FILES = new Map([
  ['2026-09-18-cert-menu-renumber.sql',
    '菜单号段迁移脚本：第 1 步用 ON DUPLICATE KEY UPDATE 显式"恢复爬虫域原始行"，是设计意图，非静默丢弃（文件头依据段有完整说明）'],
  ['2026-09-20-perm-carrier-role-backfill.sql',
    '超管总览载体恢复：与 2026-09-28-process-track-overview.sql 同 id(60006222) 同内容，属**有意复用主键**的恢复性 upsert'
    + '（已用 ON DUPLICATE KEY UPDATE 表达；本文件唯一的种子 INSERT 即该行，其余为授权/归属 UPDATE）'],
]);

/**
 * 已作废的号段：这些 id 在**源脚本**里仍写着，但已被迁移脚本搬走、且**当前库里不存在**。
 * 为什么不做成"白名单跳过"而是单独一类：跳过会让门禁失明；这里保留可见性（--list 会打印），
 * 只在**该 id 在当前库中确实不存在**时才算已作废 —— 万一哪天它又冒出来，门禁立刻变红。
 *
 * 依据：2026-09-18 两域菜单号段冲突核查（range 60002xxx：爬虫域先占用，证照域后复用同号段，
 * 导致 60002105 被改名、爬虫域权限点丢失名称）。修法是"证照域整体换号段"，故 60002xxx 已是历史号段。
 * 实测：`SELECT ... FROM system_menu WHERE id BETWEEN 60002000 AND 60002199` 返回 0 行。
 */
const RETIRED_MENU_IDS = new Set([
  '60002000', '60002100', '60002101', '60002102', '60002103', '60002104', '60002105', '60002106',
  // 600023xx：员工资质旧号段，2026-09-18-cert-menu-renumber.sql 第 4 步已物理删除（迁至 60005xxx）；
  //   2026-09-18-subject-company.sql 的「所属企业」菜单曾复用同段，故 60002300~303 属已作废号段内的撞号。
  '60002300', '60002301', '60002302', '60002303',
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

/** 解析 VALUES 顶层元组（尊重引号内的逗号/括号） */
function parseTuples(text) {
  const rows = [];
  let depth = 0, cur = '', fields = [], inStr = false, quote = '';
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (inStr) { cur += c; if (c === quote && text[i - 1] !== '\\') inStr = false; continue; }
    if (c === "'" || c === '`') { inStr = true; quote = c; cur += c; continue; }
    if (c === '(') { depth++; if (depth === 1) { cur = ''; fields = []; continue; } }
    if (c === ')') { depth--; if (depth === 0) { fields.push(cur); rows.push(fields); continue; } }
    if (c === ',' && depth === 1) { fields.push(cur); cur = ''; continue; }
    if (depth >= 1) cur += c;
  }
  return rows;
}
const unq = (s) => (s ?? '').trim().replace(/^['`]|['`]$/g, '');

const files = [
  ...walk(sqlRoot, (p) => /^master-.*\.sql$/.test(p) && path.dirname(p) === sqlRoot),
  ...walk(path.join(sqlRoot, 'history'), (p) => /\.sql$/.test(p)),
];

/** (表, id) → 首次出现处；重复即冲突 */
const seen = new Map();
const collisions = [];
/** 已作废号段里的撞号（历史遗留，见 RETIRED_MENU_IDS）：只报告，不判红 */
const retiredHits = [];
/** 号段归属：用于 --list 报告（按 id 前 12 位聚合） */
const bands = new Map();

for (const f of files) {
  const text = fs.readFileSync(f, 'utf8');
  const base = path.basename(f);
  // 有意迁移：跳过（理由见 INTENTIONAL_REUSE_FILES）。仍要求它真的是 upsert，否则说明理由不成立。
  if (INTENTIONAL_REUSE_FILES.has(base)) {
    if (!/ON\s+DUPLICATE\s+KEY\s+UPDATE/i.test(text)) {
      collisions.push({ key: 'ALLOWLIST', id: '-', table: '(白名单自检)', first: base,
        firstLabel: '被登记为"有意复用主键"，但文件里没有 ON DUPLICATE KEY UPDATE', dup: base,
        dupLabel: '若是 INSERT IGNORE，它其实是静默丢弃，必须从白名单移除并按真实撞号处理' });
    }
    continue;
  }
  for (const table of SEED_TABLES) {
    const re = new RegExp('INSERT\\s+(?:IGNORE\\s+)?INTO\\s+`?' + table + '`?\\s*\\(([^)]*)\\)\\s*VALUES([\\s\\S]*?);', 'gi');
    let m;
    while ((m = re.exec(text)) !== null) {
      const cols = m[1].split(',').map(unq);
      const idIdx = cols.indexOf('id');
      if (idIdx < 0) continue;
      for (const row of parseTuples(m[2])) {
        if (row.length <= idIdx) continue;
        const id = unq(row[idIdx]);
        if (!/^\d+$/.test(id)) continue;
        const key = table + '#' + id;
        const rel = path.relative(sqlRoot, f).split(path.sep).join('/');
        const label = table === 'bidding_dict_data'
          ? unq(row[cols.indexOf('dict_type')]) + '/' + unq(row[cols.indexOf('dict_value')])
          : (table === 'system_menu' ? unq(row[cols.indexOf('name')]) : '');
        const band = id.slice(0, 12);
        if (!bands.has(table + ':' + band)) bands.set(table + ':' + band, { table, band, n: 0, sample: label, file: rel });
        bands.get(table + ':' + band).n++;
        if (seen.has(key)) {
          // 落在"已作废号段"里的撞号：不判红，但要能被看见（--list 打印），且一旦该 id 复活即可被发现
          if (table === 'system_menu' && RETIRED_MENU_IDS.has(id)) {
            retiredHits.push({ id, first: seen.get(key).file, dup: rel, label });
          } else {
            collisions.push({ key, id, table, first: seen.get(key).file, firstLabel: seen.get(key).label, dup: rel, dupLabel: label });
          }
        } else {
          seen.set(key, { file: rel, label });
        }
      }
    }
  }
}

if (LIST) {
  console.log('\n--- 号段占用报告（新增种子请挑空闲段）---');
  for (const b of [...bands.values()].sort((a, c) => (a.table + a.band).localeCompare(c.table + c.band))) {
    console.log('  ' + b.table.padEnd(20) + b.band + 'xxx  占用 ' + String(b.n).padStart(3) + ' 行   例: ' + b.sample + '   (' + b.file + ')');
  }
}

if (retiredHits.length > 0 && LIST) {
  console.log('\n--- 已作废号段内的撞号（历史遗留，不判红）---');
  for (const h of retiredHits) console.log('  system_menu id=' + h.id + '  ' + h.first + ' ↔ ' + h.dup);
}
console.log('扫描种子文件 ' + files.length + ' 个；登记主键 ' + seen.size + ' 个；撞号 ' + collisions.length
  + ' 个；已作废号段内 ' + retiredHits.length + ' 个（不判红）');
if (collisions.length > 0) {
  console.error('[FAIL] 种子主键撞号 —— INSERT IGNORE 会**静默跳过**后出现的那条（库表看起来正常，内容却缺）：');
  for (const c of collisions.slice(0, 25)) {
    console.error('  ' + c.table + ' id=' + c.id);
    console.error('      先: ' + c.first + '  (' + c.firstLabel + ')');
    console.error('      后: ' + c.dup + '  (' + c.dupLabel + ')');
  }
  console.error('修复：给后出现的种子换一个**空闲号段**（用 --list 看占用报告），然后重建一键包。');
  process.exit(1);
}
console.log('[OK] 种子主键无撞号');
