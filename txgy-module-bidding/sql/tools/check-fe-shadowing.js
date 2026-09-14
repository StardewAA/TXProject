#!/usr/bin/env node
/**
 * 前端"同名遮蔽 / 自委托"检查（第 16 道门禁）。
 *
 * 由来（2026-09-25 时间/大小格式化统一时，我**连续踩了三次同一个坑**）：
 *   把各页自带的 `fmtBytes`/`fmtT` 改成委托共享实现时，用"整文件批量改名"把**局部包装函数体内的委托调用也改了名**，
 *   于是出现：
 *     · `const fmtBytes = (n) => fmtBytes(n)`（箭头形式，自递归）；
 *     · `function sizeText(n) { return sizeText(n, '') }`（函数形式，自递归）；
 *     · 局部名与 import 进来的共享实现**同名**（遮蔽），肉眼很难发现。
 *   更糟的是：**eslint 不报、vue-tsc 也不报**（递归与遮蔽都是类型正确的），只有真跑起来才会栈溢出。
 *   当时靠"回读文件原文 + 打印定义"才发现 —— 这类"只有运行时才炸"的写法必须由门禁兜住。
 *
 * 判定口径（**零基线**：实测全仓 0 处，故不允许新增）：
 *   R1 自委托：函数/箭头函数的**唯一语句**就是调用自身（`return f(...)` / `=> f(...)`）；
 *   R2 同名遮蔽：本文件声明的函数/常量名与 import 进来的名字相同（招投标域内实测 0 处；
 *      mall 域另有 2 处 `message` 遮蔽，不属本模块扫描范围，故本门禁只扫招投标域，避免越界改别人代码）。
 * 扫描范围：views/bidding、views/portal/bidding、api/bidding、utils/format.ts、utils/datetime.ts。
 * 用法：node sql/tools/check-fe-shadowing.js [--list]
 * 退出码：0 通过；1 存在违规。
 */
const fs = require('node:fs');
const path = require('node:path');

const LIST = process.argv.includes('--list');
const repoRoot = path.resolve(__dirname, '../../..');
const SRC = path.join(repoRoot, 'txgy-ui-admin-vben/apps/web-antd/src');
const ROOTS = [
  path.join(SRC, 'views/bidding'),
  path.join(SRC, 'views/portal/bidding'),
  path.join(SRC, 'api/bidding'),
  path.join(SRC, 'utils/format.ts'),
  path.join(SRC, 'utils/datetime.ts'),
];
const SKIP_DIRS = new Set(['node_modules', 'dist']);

function walk(p, out = []) {
  let st;
  try { st = fs.statSync(p); } catch { return out; }
  if (st.isFile()) { if (/\.(vue|ts)$/.test(p)) out.push(p); return out; }
  for (const e of fs.readdirSync(p, { withFileTypes: true })) {
    if (SKIP_DIRS.has(e.name)) continue;
    walk(path.join(p, e.name), out);
  }
  return out;
}

const files = ROOTS.flatMap((r) => walk(r));
const hits = [];
for (const f of files) {
  const src = fs.readFileSync(f, 'utf8');
  const rel = path.relative(repoRoot, f).split(path.sep).join('/');
  const lines = src.split('\n');

  // R1 自委托：**按花括号配对取真实函数体**，并要求"整个函数体就是调用自己"
  //   （早期版本用"声明后 N 行"的窗口判断，会把下一个函数里的调用误判成自委托——已按实测修正）
  const bodies = [];
  lines.forEach((l, i) => {
    const fn = l.match(/^\s*(?:export\s+)?(?:async\s+)?function\s+(\w+)\s*\(/);
    const cn = l.match(/^\s*(?:export\s+)?const\s+(\w+)\s*=[^=]*?=>/);
    if (fn) bodies.push({ name: fn[1], line: i + 1, kind: 'function' });
    else if (cn) bodies.push({ name: cn[1], line: i + 1, kind: 'arrow' });
  });
  for (const b of bodies) {
    const rest = lines.slice(b.line - 1).join('\n');
    let body = '';
    if (b.kind === 'arrow' && !/=>\s*\{/.test(rest.split('\n').slice(0, 2).join('\n'))) {
      // 表达式箭头：从 => 取到首个分号（允许表达式换行），而不是只看第一行
      const flat = rest.split('\n').slice(0, 4).join(' ');
      const after = flat.slice(flat.indexOf('=>') + 2);
      body = (after.includes(';') ? after.slice(0, after.indexOf(';')) : after).trim();
    } else {
      // 块体：从首个 { 起做花括号配对（字符串内的花括号不参与，够用且不引入解析器依赖）
      const start = rest.indexOf('{');
      if (start < 0) continue;
      let depth = 0;
      let end = -1;
      for (let k = start; k < rest.length; k++) {
        const ch = rest[k];
        if (ch === '{') depth++;
        else if (ch === '}') { depth--; if (depth === 0) { end = k; break; } }
      }
      if (end < 0) continue;
      body = rest.slice(start + 1, end).trim();
    }
    const selfCall = new RegExp('^(?:return\\s+)?' + b.name + '\\s*\\(');
    if (selfCall.test(body)) hits.push({ kind: 'R1 自委托', file: rel, line: b.line, name: b.name });
  }

  // R2 同名遮蔽：import { X } 且本文件又声明 X
  const imported = new Set();
  for (const m of src.matchAll(/import\s+(?:type\s+)?\{([^}]+)\}\s+from\s+'[^']+'/g)) {
    for (const raw of m[1].split(',')) {
      const n = raw.trim().split(/\s+as\s+/).pop().trim();
      if (n) imported.add(n);
    }
  }
  for (const { name, line } of bodies) {
    if (imported.has(name)) hits.push({ kind: 'R2 同名遮蔽', file: rel, line, name });
  }
}

if (LIST) {
  console.log('扫描 ' + files.length + ' 个文件；违规 ' + hits.length + ' 处');
  for (const h of hits) console.log('   ' + h.file + ':' + h.line + '  [' + h.kind + '] ' + h.name);
}
if (hits.length === 0) {
  console.log('扫描 ' + files.length + ' 个文件；[OK] 无同名遮蔽 / 无自委托（递归与遮蔽类型正确，eslint/tsc 都发现不了，故设门禁）');
  process.exit(0);
}
console.log('[FAIL] 发现"类型正确但运行时出错"的写法：');
for (const h of hits.slice(0, 30)) console.log('   ' + h.file + ':' + h.line + '  [' + h.kind + '] ' + h.name);
console.log('修复：自委托改为调用共享实现（如 fmtBytes）；遮蔽请给局部符号换名（如 sizeText）。');
process.exit(1);
