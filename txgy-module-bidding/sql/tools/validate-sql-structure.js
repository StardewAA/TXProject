#!/usr/bin/env node
/**
 * 规范依据：本模块要求「一键同步包必须幂等、且包内不得出现 DROP/TRUNCATE」——
 *   无数据库环境（Docker 未启动）时无法用 mysql 客户端校验，故提供纯文本结构自检。
 * 业务目标：在提交前发现 SQL 文件的低级破坏性错误（引号/括号不闭合、语句被截断、建表重名、
 *   以及**意外出现的 DROP/TRUNCATE**），把「执行到一半才发现」的风险前移到编辑期。
 * 用法：node sql/tools/validate-sql-structure.js <文件...>（缺省校验 sql/master-bidding-sync.sql）
 * 退出码：0=通过；1=发现问题。
 */
const fs = require('fs');
const path = require('path');

const files = process.argv.slice(2);
if (files.length === 0) {
  files.push(path.join(__dirname, '..', 'master-bidding-sync.sql'));
}

// 允许出现 DROP/TRUNCATE 的白名单（归零脚本本就是破坏性脚本，单独标注）
const DESTRUCTIVE_ALLOWED = /reset-crawl-to-zero/i;

let failed = false;

for (const file of files) {
  const raw = fs.readFileSync(file, 'utf8');
  const lines = raw.split(/\r?\n/);

  // 去注释：行注释 --、MySQL 版本注释 /*!...*/（版本注释是 mysqldump 噪声，不影响语句结构）
  const kept = [];
  lines.forEach((line) => {
    const t = line.trim();
    if (t.startsWith('--')) return;
    if (t.startsWith('/*!')) return;
    kept.push(line);
  });
  const sql = kept.join('\n');

  let depth = 0;
  let quote = null;
  let stmts = [];
  let cur = '';
  let negativeAt = -1;
  for (let i = 0; i < sql.length; i++) {
    const c = sql[i];
    if (quote) {
      if (c === '\\') { cur += c + (sql[i + 1] || ''); i++; continue; }
      if (c === quote) quote = null;
      cur += c;
      continue;
    }
    if (c === "'" || c === '"' || c === '`') { quote = c; cur += c; continue; }
    if (c === '(') depth++;
    if (c === ')') { depth--; if (depth < 0 && negativeAt < 0) negativeAt = i; }
    if (c === ';') { stmts.push(cur); cur = ''; continue; }
    cur += c;
  }

  const problems = [];
  if (depth !== 0) problems.push('括号不闭合：最终深度=' + depth);
  if (quote) problems.push('引号不闭合：' + quote);
  if (negativeAt >= 0) problems.push('多余的右括号，位置偏移 ' + negativeAt);
  if (cur.trim()) problems.push('末尾存在未以分号结束的语句片段：' + cur.trim().slice(0, 80));

  const creates = stmts
    .filter((s) => /CREATE TABLE/i.test(s))
    .map((s) => (s.match(/CREATE TABLE(?: IF NOT EXISTS)?\s+`?([A-Za-z0-9_]+)`?/i) || [])[1])
    .filter(Boolean);
  const dup = creates.filter((v, idx) => creates.indexOf(v) !== idx);
  if (dup.length) problems.push('重复建表：' + [...new Set(dup)].join(', '));
  const notIdempotent = stmts.filter((s) => /CREATE TABLE/i.test(s) && !/CREATE TABLE IF NOT EXISTS/i.test(s));
  if (notIdempotent.length) problems.push('存在非幂等建表（缺 IF NOT EXISTS）：' + notIdempotent.length + ' 处');

  if (!DESTRUCTIVE_ALLOWED.test(file)) {
    const destructive = stmts.filter((s) => /^\s*(DROP|TRUNCATE)\b/i.test(s.trim()));
    if (destructive.length) {
      problems.push('同步包内出现破坏性语句 DROP/TRUNCATE：' + destructive.length + ' 处（违反「同步包不得清数据」约定）');
    }
  }

  // TRUNCATE/DROP 属允许的语句起始（破坏性另有专门检查，仅归零脚本豁免）
  const unexpected = stmts.filter((s) => s.trim() && !/^(SET|CREATE|INSERT|UPDATE|SELECT|DELETE|LOCK|UNLOCK|ALTER|USE|COMMIT|START|REPLACE|TRUNCATE|DROP)/i.test(s.trim()));
  if (unexpected.length) problems.push('非常规语句起始：' + unexpected.slice(0, 3).map((s) => s.trim().slice(0, 40)).join(' | '));

  const rel = path.relative(process.cwd(), file);
  if (problems.length === 0) {
    console.log('[OK] ' + rel + ' 语句=' + stmts.length + ' 建表=' + creates.length + '（结构自检通过）');
  } else {
    failed = true;
    console.log('[FAIL] ' + rel);
    problems.forEach((p) => console.log('   - ' + p));
  }
}

process.exit(failed ? 1 : 0);
