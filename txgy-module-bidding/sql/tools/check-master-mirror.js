#!/usr/bin/env node
/**
 * 一键包镜像完整性自检：sql/history 里"声称已并入"的脚本，其内容必须真的出现在一键包里。
 *
 * 背景（真实事故）：一键包 master-*.sql 是**生成物**（sql/tools/build-credential-sync.js 按写死的来源清单拼接，
 * 目前清单止于 2026-09-19-cert-dict-register.sql）。只要有人新增 history 脚本却没加进清单，
 * 下次重建就会把这段内容**静默抹掉**：新装库缺字典 → 前端下拉空白；缺权限点 → 按钮永远不可见。
 * 实测发生过两次（2026-09-20 字典、2026-09-21 规则配置权限点）。
 *
 * 比对口径（按业务键，不按自增 id —— 号段迁移会换 id，按 id 比会全是假阳性）：
 *   - bidding_dict_data      → (dict_type, dict_value)
 *   - system_menu            → permission 码（无码取 name）
 *   - system_role_menu       → 跳过（授权属部署态，不应要求进终态包）
 *   - 其它表                  → 首列 id
 *
 * 用法：node sql/tools/check-master-mirror.js [--list]
 * 退出码：0 = 全部已镜像；1 = 有脚本内容没进一键包（重建会丢）。
 */
const fs = require('node:fs');
const path = require('node:path');

const LIST = process.argv.includes('--list');
const sqlRoot = path.resolve(__dirname, '..');
const histDir = path.join(sqlRoot, 'history');

/** 有意不镜像的文件（必须写明理由） */
const SKIP_FILES = new Map([
  ['2026-09-11-master-sync-removed-seeds.sql', '文件名即语义：该脚本是"把某些种子从一键包里移除"，本就不该再镜像回包'],
  [
    '2026-09-03-notice-menu.sql',
    '已作废（页面 2026-09-16 退役）：其 60001000/60001100 页面行指向的 views/bidding/notice/index.vue 已随' +
      '提交 9938a243f「删除公告/公示管理与平台授权管理的废弃前端页面」删除；公告权限点 bidding:notice:* 由' +
      '2026-09-20-perm-carrier-and-tenant-fix.sql 以 type=3 按钮承载。整段镜像会把已退役的 type=2 死页面' +
      '带回新装库（check-menu-pages 报错），故有意不并入一键包。',
  ],
  [
    '2026-09-28-tender-doc-prequal-menu.sql',
    '已作废（2026-09-29 统一）：其 8 项内容已由他批次登记并入库 —— tender-doc 2 项见 ' +
      '2026-09-29-权限审计批次-菜单与授权统一.sql（登记为 60006302/60006303），prequal 6 项见 60006235~60006240。' +
      '脚本头已标注「不要执行」（重复登记会产生同码多承载），故有意不镜像回包。',
  ],
]);

function mastersText() {
  return fs
    .readdirSync(sqlRoot)
    .filter((f) => /^master-.*\.sql$/.test(f))
    .map((f) => fs.readFileSync(path.join(sqlRoot, f), 'utf8'))
    .join('\n');
}

/** 解析 VALUES 元组（尊重引号内的逗号与括号） */
function parseTuples(text) {
  const rows = [];
  let depth = 0;
  let cur = '';
  let fields = [];
  let inStr = false;
  let quote = '';
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (inStr) {
      cur += c;
      if (c === quote && text[i - 1] !== '\\') inStr = false;
      continue;
    }
    if (c === "'" || c === '\u0060') {
      inStr = true;
      quote = c;
      cur += c;
      continue;
    }
    if (c === '(') {
      depth++;
      if (depth === 1) {
        cur = '';
        fields = [];
        continue;
      }
    }
    if (c === ')') {
      depth--;
      if (depth === 0) {
        fields.push(cur);
        rows.push(fields);
        continue;
      }
    }
    if (c === ',' && depth === 1) {
      fields.push(cur);
      cur = '';
      continue;
    }
    if (depth >= 1) cur += c;
  }
  return rows;
}

const unq = (s) => (s ?? '').trim().replace(/^['\u0060]|['\u0060]$/g, '');

const masters = mastersText();
const files = fs.readdirSync(histDir).filter((f) => f.endsWith('.sql')).sort();
const problems = [];
let checked = 0;

for (const f of files) {
  const text = fs.readFileSync(path.join(histDir, f), 'utf8');
  if (SKIP_FILES.has(f)) {
    if (LIST) console.log('  [SKIP] ' + f + '  —— ' + SKIP_FILES.get(f));
    continue;
  }
  // 文件自带"新库不需要"标记（迁移类脚本）→ 不要求镜像
  const head = text.split('\n').slice(0, 40).join('\n');
  if (head.includes('新库不需要')) {
    if (LIST) console.log('  [SKIP] ' + f + '  —— 文件头标注「新库不需要」');
    continue;
  }
  const missing = [];
  let keyCount = 0;
  for (const m of text.matchAll(/INSERT\s+(?:IGNORE\s+)?INTO\s+\u0060(\w+)\u0060\s*\(([^)]*)\)\s*VALUES([\s\S]*?);/g)) {
    const table = m[1];
    const cols = m[2].split(',').map((c) => unq(c));
    if (table === 'system_role_menu') continue;
    let keyIdx = [0];
    if (table === 'bidding_dict_data') {
      keyIdx = [cols.indexOf('dict_type'), cols.indexOf('dict_value')];
    } else if (table === 'system_menu') {
      keyIdx = [cols.indexOf('permission') >= 0 ? cols.indexOf('permission') : cols.indexOf('name')];
    }
    if (keyIdx.some((i) => i < 0)) continue;
    for (const row of parseTuples(m[3])) {
      if (row.length < cols.length) continue;
      const vals = keyIdx.map((i) => unq(row[i]));
      if (vals.some((v) => v === '' || v === 'NULL' || v === 'undefined')) continue;
      keyCount++;
      // 字典看「type,value」相邻；菜单看权限码/名称是否出现在包中。
      //
      // ⚠️ 必须在**去掉空白**的文本上比对（2026-09-20 修正）：
      // 原实现直接搜 "'type','value'" 这种**逗号后无空格**的紧凑串，而生成器拼接时会把 VALUES 规范成
      // "'type', 'value'"（逗号后带空格）—— 于是明明已镜像的内容被判成"缺失"，
      // 一键包检查常年**假红**（本次实测：review_task_status 明明在包里，却被报"缺 6 项"）。
      // 假红比不检查更糟：它会训练人忽略这条门禁，真缺失时也就没人看了。
      // 按业务键比对本来就不该关心格式化差异，故两侧一并压掉空白。
      const squash = (s) => s.replace(/\s+/g, '');
      const present = table === 'bidding_dict_data'
        ? squash(masters).includes(squash("'" + vals.join("','") + "'"))
        : masters.includes(vals[0]);
      if (!present) missing.push(table + ' → ' + vals.join(' / '));
    }
  }
  checked += keyCount;
  const flag = missing.length === 0 ? 'OK' : 'MISS';
  console.log('  [' + flag + '] ' + f + '  （键 ' + keyCount + '）' + (missing.length > 0 && LIST ? '\n         缺: ' + missing.slice(0, 6).join('; ') : ''));
  if (missing.length > 0) problems.push({ f, missing });
}

console.log('\n扫描 history 脚本 ' + files.length + ' 个，比对业务键 ' + checked + ' 个；未镜像 ' + problems.length + ' 个文件');
if (problems.length > 0) {
  console.error('[FAIL] 以下脚本内容不在任何一键包里（重建一键包会丢失，新装库直接缺功能）：');
  for (const p of problems) console.error('  ' + p.f + '  缺 ' + p.missing.length + ' 项，例如: ' + p.missing.slice(0, 4).join('; '));
  console.error('修复二选一：① 把脚本加入 sql/tools/build-credential-sync.js 的来源清单（SOURCES）后重建；');
  console.error('           ② 手工镜像进对应 master 并在脚本头注明（会被下次重建覆盖，故推荐 ①）。');
  process.exit(1);
}
console.log('[OK] history 脚本内容均已进入一键包');
