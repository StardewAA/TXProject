#!/usr/bin/env node
// 字段级校验：电子资质域 DO 的每个字段（含 09-17 的 ALTER 加列）在合并包里都能找到列名
const fs = require('fs'), path = require('path');
const pkg = fs.readFileSync('sql/master-credential-sync.sql', 'utf8');
const BASE = new Set(['id','tenant_id','creator','create_time','updater','update_time','deleted','version']);
const snake = (s) => s.replace(/([a-z0-9])([A-Z])/g, '$1_$2').replace(/([A-Z]+)([A-Z][a-z])/g, '$1_$2').toLowerCase();
function walk(dir, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, out); else if (e.name.endsWith('.java')) out.push(p);
  }
  return out;
}
const DO_DIRS = ['certificate','qualification','personnel','ruleconfig'];
const missing = []; let checkedTables = 0, checkedCols = 0;
for (const d of DO_DIRS) {
  const dir = 'txgy-module-bidding-server/src/main/java/cn/iocoder/txgy/module/bidding/dal/dataobject/' + d;
  if (!fs.existsSync(dir)) continue;
  for (const f of walk(dir)) {
    const s = fs.readFileSync(f, 'utf8');
    const tm = s.match(/@TableName\("([^"]+)"\)/);
    if (!tm) continue;
    if (!new RegExp('CREATE TABLE(?: IF NOT EXISTS)? \`?' + tm[1] + '\`?\\b', 'i').test(pkg)) continue;
    checkedTables++;
    const body = s.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/[^\n]*/g, '');
    for (const m of body.matchAll(/(?:private|protected)\s+(?:static\s+final\s+)?[\w<>,\[\]\s.?]+\s+(\w+)\s*;/g)) {
      const col = snake(m[1]);
      if (BASE.has(col)) continue;
      checkedCols++;
      if (!pkg.includes(col)) missing.push(tm[1] + '.' + col + '  ← ' + path.basename(f, '.java'));
    }
  }
}
console.log('已核对 DO 表=' + checkedTables + '，字段=' + checkedCols);
console.log('合并包中找不到的列：' + (missing.length ? '\n  ' + missing.join('\n  ') : '无 ✓'));
