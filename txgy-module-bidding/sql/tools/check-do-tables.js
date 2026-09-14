#!/usr/bin/env node
// DO(@TableName) ↔ SQL 包 一致性校验：代码要用的表，初始化包里必须建得出来
const fs = require('fs'), path = require('path');
function walk(dir, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, out); else if (e.name.endsWith('.java')) out.push(p);
  }
  return out;
}
const root = 'txgy-module-bidding-server/src/main/java';
const tables = new Map();
for (const f of walk(root)) {
  const s = fs.readFileSync(f, 'utf8');
  const m = s.match(/@TableName\("([^"]+)"\)/);
  if (m) tables.set(m[1], path.basename(f, '.java'));
}
const read = (p) => (fs.existsSync(p) ? fs.readFileSync(p, 'utf8') : '');
const cred = read('sql/master-credential-sync.sql');
const master = read('sql/master-bidding-sync.sql');
const inPkg = (pkg, t) => new RegExp('CREATE TABLE(?: IF NOT EXISTS)? \\s*`?' + t + '`?\\b', 'i').test(pkg);
const missing = [], viaMaster = [];
for (const [t, doName] of [...tables].sort()) {
  if (inPkg(cred, t)) continue;
  if (inPkg(master, t)) { viaMaster.push(t + '(' + doName + ')'); continue; }
  missing.push(t + ' ← ' + doName);
}
console.log('DO 表总数=' + tables.size);
console.log('由 master-credential-sync.sql 建：' + [...tables.keys()].filter(t => inPkg(cred, t)).length);
console.log('由 master-bidding-sync.sql 建：' + viaMaster.length + (viaMaster.length ? ' → ' + viaMaster.join(', ') : ''));
console.log('两个包都没有的：' + (missing.length ? '\n  ' + missing.join('\n  ') : '无 ✓'));
