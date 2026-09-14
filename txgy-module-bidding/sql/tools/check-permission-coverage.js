#!/usr/bin/env node
/**
 * 权限码覆盖率自检：前端判权码 vs 后端 @PreAuthorize vs 菜单 SQL 注册。
 *
 * 为什么需要：前端用 can('bidding:xxx:yyy') / hasAccessByCodes([...]) 决定按钮是否渲染，
 * 而这个码必须同时满足两个条件才真的可用：
 *   ① 后端接口的 @PreAuthorize 认它（否则点下去 403）；
 *   ② system_menu 里注册了它（否则后台「角色授权」界面里根本没有这一项 → 任何角色都拿不到 → 按钮永远不显示）。
 * 两种缺口都不报错：页面只是安静地少一个按钮、或点下去 403，靠人工比对很难发现。
 *
 * 用法：
 *   node sql/tools/check-permission-coverage.js          # 汇总 + 逐条列出缺口
 *   node sql/tools/check-permission-coverage.js --list   # 打印全部权限码与三处出现情况
 * 退出码：0 = 无缺口（白名单除外）；1 = 存在缺口。
 *
 * 口径说明：
 *   - 前端：views/bidding 与 api/bidding 下的 can(...)、hasAccessByCodes([...])、v-access:code="[...]"。
 *   - 后端：bidding server 里 @PreAuthorize 的 hasPermission / hasAnyPermissions 字面量。
 *   - 菜单：sql 目录下**含 system_menu 的文件**里出现的 'bidding:...' 字面量（含 INSERT 与 history 里的 UPDATE/DELETE）。
 */
const fs = require('node:fs');
const path = require('node:path');

const LIST = process.argv.includes('--list');

/** 有意例外（必须写明理由） */
const ALLOW = new Map([
  // 例：['bidding:xxx:yyy', '理由'],
]);

const repoRoot = path.resolve(__dirname, '..', '..', '..');
const feRoot = path.join(repoRoot, 'txgy-ui-admin-vben', 'apps', 'web-antd', 'src');
const feBidding = [path.join(feRoot, 'views', 'bidding'), path.join(feRoot, 'api', 'bidding')];
const javaRoot = path.join(repoRoot, 'txgy-module-bidding', 'txgy-module-bidding-server', 'src', 'main', 'java');
const sqlRoot = path.join(repoRoot, 'txgy-module-bidding', 'sql');

const CODE = /bidding:[A-Za-z0-9_-]+:[A-Za-z0-9_-]+/g;
/** can('a:b:c') / hasAccessByCodes(['a:b:c']) / v-access:code="['a:b:c']" 里的所有权限码 */
const FE_HINT = /(?:\bcan\s*\(|hasAccessByCodes\s*\(|v-access:code\s*=\s*")/;

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

// ---- 前端判权码（记录来源文件） ----
const feCodes = new Map(); // code -> Set<file>
for (const root of feBidding) {
  for (const f of walk(root, (p) => /\.(ts|vue)$/.test(p))) {
    const text = fs.readFileSync(f, 'utf8');
    // 按行切，只在含判权函数/指令的行上取码，避免把注释里的示例码也算进来
    text.split('\n').forEach((line, i) => {
      if (!FE_HINT.test(line)) return;
      for (const m of line.match(CODE) || []) {
        if (line.trimStart().startsWith('//') || line.trimStart().startsWith('*')) return;
        if (!feCodes.has(m)) feCodes.set(m, new Set());
        feCodes.get(m).add(path.relative(repoRoot, f) + ':' + (i + 1));
      }
    });
  }
}

// ---- 后端 @PreAuthorize 码 ----
const beCodes = new Map();
for (const f of walk(javaRoot, (p) => p.endsWith('.java'))) {
  const text = fs.readFileSync(f, 'utf8');
  for (const mm of text.matchAll(/has(?:Any)?Permissions?\(([^)]*)\)/g)) {
    for (const m of mm[1].match(CODE) || []) {
      if (!beCodes.has(m)) beCodes.set(m, new Set());
      beCodes.get(m).add(path.basename(f));
    }
  }
}

// ---- 菜单 SQL 注册码（只看含 system_menu 的文件） ----
const menuCodes = new Set();
for (const f of walk(sqlRoot, (p) => p.endsWith('.sql'))) {
  const text = fs.readFileSync(f, 'utf8');
  if (!text.includes('system_menu')) continue;
  for (const m of text.match(CODE) || []) menuCodes.add(m);
}

const codes = [...new Set([...feCodes.keys(), ...beCodes.keys()])].sort();
const notRegistered = codes.filter((c) => !menuCodes.has(c));
const feNotBackend = [...feCodes.keys()].filter((c) => !beCodes.has(c)).sort();
const beNotFe = [...beCodes.keys()].filter((c) => !feCodes.has(c)).sort();

if (LIST) {
  console.log('权限码（前端判权 / 后端校验 / 菜单注册）：');
  for (const c of codes) {
    const fe = feCodes.has(c) ? 'FE' : '--';
    const be = beCodes.has(c) ? 'BE' : '--';
    const mn = menuCodes.has(c) ? 'MENU' : '----';
    console.log('  ' + [fe, be, mn].join(' ') + '  ' + c);
  }
  console.log('');
}

console.log('前端判权码 ' + feCodes.size + ' 个；后端校验码 ' + beCodes.size + ' 个；菜单 SQL 注册码 ' + menuCodes.size + ' 个');

let failed = false;

const unexpectedReg = notRegistered.filter((c) => !ALLOW.has(c));
if (unexpectedReg.length > 0) {
  console.error('\n[FAIL] ' + unexpectedReg.length + ' 个权限码未在 system_menu 注册（后台无法授权 → 按钮永远不可见 / 接口永远 403）：');
  for (const c of unexpectedReg) console.error('  ' + c + '   ← ' + [...(feCodes.get(c) ?? beCodes.get(c) ?? ['?'])].slice(0, 3).join(', '));
  failed = true;
}

const unexpectedFe = feNotBackend.filter((c) => !ALLOW.has(c) && !menuCodes.has(c));
if (unexpectedFe.length > 0) {
  console.error('\n[FAIL] ' + unexpectedFe.length + ' 个权限码前端在判权、后端没有对应校验（拼错或接口已删）：');
  for (const c of unexpectedFe) console.error('  ' + c + '   ← ' + [...feCodes.get(c)].slice(0, 3).join(', '));
  failed = true;
}

if (failed) {
  console.error('\n修复：① 码写错 → 改成后端/菜单里的真实码；② 确实缺注册 → 在 sql/history 补 system_menu 按钮行（含 system_role_menu 授权），或加入 ALLOW 并写明理由。');
  process.exit(1);
}
console.log('\n[OK] 权限码三处（前端判权 / 后端校验 / 菜单注册）无缺口');
