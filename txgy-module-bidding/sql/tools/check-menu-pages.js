#!/usr/bin/env node
/**
 * 规范依据：《招投标模块开发规范》4.4（菜单/权限码与 sql/ 脚本同步）+ README「一键同步包 = 终态」口径。
 * 业务目标：防「死菜单」——菜单行 component 指向的前端页面文件不存在时，侧边栏点进去是空白页/404。
 *   典型来源：① 页面被删但菜单未退役；② 退役 UPDATE 只写在 history/ 归档脚本里，没进一键包（新库复现）。
 * 做法：解析所有 system_menu 的 INSERT 元组 + 退役式 UPDATE（SET type/component），按「后写覆盖先写」求终态，
 *   再逐个组件检查 views/<component>.vue 是否存在。
 *
 * 用法：
 *   node sql/tools/check-menu-pages.js              # 终态口径（sql/*.sql，即一键同步包；权威）
 *   node sql/tools/check-menu-pages.js --all        # 叠加 sql/history/*.sql（按文件名时序，提示归档脚本能否修正）
 *   MENU_VIEWS_ROOT=<dir> node sql/tools/check-menu-pages.js   # 覆盖前端 views 根目录
 * 退出码：0=无死菜单；1=存在死菜单（可直接用于 CI / 提交前检查）。
 */
const fs = require('fs');
const path = require('path');

const MODULE_ROOT = path.resolve(__dirname, '..', '..'); // txgy-module-bidding
const REPO_ROOT = path.resolve(MODULE_ROOT, '..');
const SQL_ROOT = path.join(MODULE_ROOT, 'sql');
const VIEWS_ROOT =
  process.env.MENU_VIEWS_ROOT || path.join(REPO_ROOT, 'txgy-ui-admin-vben', 'apps', 'web-antd', 'src', 'views');
const MODULE_PREFIX = 'bidding/'; // 本模块自有页面：缺失即失败；其它前缀只提示

const withAll = process.argv.includes('--all');
const unquote = (v) => {
  const s = v.trim().replace(/^_binary\s+/i, '');
  if (/^'[\s\S]*'$/.test(s)) return s.slice(1, -1).replace(/''/g, "'");
  return s;
};
const isNullish = (s) => /^null$/i.test(s.trim());

/** 解析 INSERT INTO system_menu 的所有元组（按列名取字段，兼容列名顺序变化） */
function parseInserts(sql, file, state) {
  const re = /INSERT\s+(?:IGNORE\s+)?INTO\s+`?system_menu`?\s*(\(([^)]*)\))?\s*VALUES/gi;
  let m;
  while ((m = re.exec(sql))) {
    const cols = m[2]
      ? m[2].split(',').map((c) => c.trim().replace(/`/g, '').toLowerCase())
      : ['id', 'name', 'permission', 'type', 'sort', 'parent_id', 'path', 'icon', 'component', 'component_name'];
    let i = m.index + m[0].length;
    const tuples = [];
    for (;;) {
      while (i < sql.length && /[\s,]/.test(sql[i])) i++;
      if (i >= sql.length || sql[i] === ';') break;
      if (/^ON\s+DUPLICATE/i.test(sql.slice(i, i + 16))) break;
      if (sql[i] !== '(') break;
      const start = i;
      let depth = 0;
      let quote = null;
      for (; i < sql.length; i++) {
        const c = sql[i];
        if (quote) {
          if (c === '\\') { i++; continue; }
          if (c === quote) quote = null;
          continue;
        }
        if (c === "'" || c === '"') { quote = c; continue; }
        if (c === '(') depth++;
        else if (c === ')') { depth--; if (depth === 0) { i++; break; } }
      }
      tuples.push(sql.slice(start, i));
      while (i < sql.length && /\s/.test(sql[i])) i++;
      if (sql[i] === ',') { i++; continue; }
      break;
    }
    for (const t of tuples) {
      const parts = splitTuple(t);
      const row = {};
      cols.forEach((c, idx) => { if (idx < parts.length) row[c] = unquote(parts[idx]); });
      if (row.id === undefined) continue;
      state.set(row.id, {
        component: isNullish(row.component ?? '') ? '' : (row.component ?? ''),
        id: row.id,
        name: row.name ?? '',
        permission: row.permission ?? '',
        source: file,
        type: row.type ?? '',
      });
    }
  }
}

/** 拆分单个元组 "(a,'b',(c))" → 顶层字段数组 */
function splitTuple(t) {
  const inner = t.slice(1, -1);
  const parts = [];
  let cur = '';
  let quote = null;
  let depth = 0;
  for (let i = 0; i < inner.length; i++) {
    const c = inner[i];
    if (quote) {
      if (c === '\\') { cur += c + (inner[i + 1] || ''); i++; continue; }
      if (c === quote) quote = null;
      cur += c;
      continue;
    }
    if (c === "'" || c === '"') { quote = c; cur += c; continue; }
    if (c === '(') { depth++; cur += c; continue; }
    if (c === ')') { depth--; cur += c; continue; }
    if (c === ',' && depth === 0) { parts.push(cur.trim()); cur = ''; continue; }
    cur += c;
  }
  parts.push(cur.trim());
  return parts;
}

/** 退役式 UPDATE：SET type/component/... WHERE id = n | id IN (...) */
function parseUpdates(sql, file, state) {
  const re = /UPDATE\s+`?system_menu`?\s+SET\s+([\s\S]*?)\s+WHERE\s+id\s+(=|IN)\s*([^;]*);/gi;
  let m;
  while ((m = re.exec(sql))) {
    const assign = {};
    for (const seg of m[1].split(',')) {
      const mm = seg.match(/^\s*`?(\w+)`?\s*=\s*([\s\S]+)$/);
      if (mm) assign[mm[1].toLowerCase()] = mm[2].trim();
    }
    const ids = (m[3].match(/\d+/g) || []);
    for (const id of ids) {
      const prev = state.get(id);
      if (!prev) continue;
      const next = { ...prev, source: file };
      if (assign.type !== undefined) next.type = unquote(assign.type);
      if (assign.component !== undefined) next.component = unquote(assign.component);
      if (assign.name !== undefined) next.name = unquote(assign.name);
      if (assign.permission !== undefined) next.permission = unquote(assign.permission);
      state.set(id, next);
    }
  }
}

function listSql(dir) {
  if (!fs.existsSync(dir)) return [];
  return fs
    .readdirSync(dir)
    .filter((f) => f.endsWith('.sql'))
    .sort()
    .map((f) => path.join(dir, f));
}

const files = listSql(SQL_ROOT);
if (withAll) files.push(...listSql(path.join(SQL_ROOT, 'history')));

const state = new Map();
for (const f of files) {
  const sql = fs.readFileSync(f, 'utf8');
  const rel = path.relative(MODULE_ROOT, f).replace(/\\/g, '/');
  parseInserts(sql, rel, state);
  parseUpdates(sql, rel, state);
}

const pages = [...state.values()].filter((r) => r.type === '2' && r.component);
const missing = [];
for (const r of pages.sort((a, b) => Number(a.id) - Number(b.id))) {
  const comp = r.component.replace(/\.vue$/, '');
  if (!fs.existsSync(path.join(VIEWS_ROOT, comp + '.vue'))) missing.push({ ...r, comp });
}
const own = missing.filter((r) => r.comp.startsWith(MODULE_PREFIX));
const foreign = missing.filter((r) => !r.comp.startsWith(MODULE_PREFIX));

console.log('菜单↔页面一致性校验（' + (withAll ? '含 history 归档口径' : '终态口径 sql/*.sql') + '）');
console.log('  解析文件 ' + files.length + ' 个；菜单行 ' + state.size + ' 个；页面型（type=2 且有 component）' + pages.length + ' 个');
console.log('  views 根目录：' + path.relative(REPO_ROOT, VIEWS_ROOT).replace(/\\/g, '/'));

if (own.length === 0 && foreign.length === 0) {
  console.log('✅ 所有页面组件均存在，无死菜单。');
  process.exit(0);
}
if (own.length > 0) {
  console.log('\n❌ 死菜单 ' + own.length + " 个（本模块 bidding/*，必须修）：");
  for (const r of own) {
    console.log('   ' + r.id + '  ' + r.name + '  permission=' + (r.permission || '-'));
    console.log('        component=' + r.comp + '  → 缺失文件 views/' + r.comp + '.vue');
    console.log('        来源：' + r.source);
  }
}
if (foreign.length > 0) {
  console.log('\nℹ️ 非本模块前缀 ' + foreign.length + ' 个（可能是主系统/其它模块页面，仅提示）：');
  for (const r of foreign) console.log('   ' + r.id + '  ' + r.name + '  component=' + r.comp + '  来源 ' + r.source);
}
if (!withAll) {
  console.log('\n提示：加 --all 可查看 history/ 归档脚本是否已修正这些 id（若已修正，应把该语句并入一键包）。');
}
process.exit(own.length > 0 ? 1 : 0);
