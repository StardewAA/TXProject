#!/usr/bin/env node
// 电子资质一键包终态自检：无 ALTER / 无破坏性语句 / 折叠列已就位
const fs = require('fs');
const p = 'sql/master-credential-sync.sql';
const s = fs.readFileSync(p, 'utf8');
const noComment = s.split('\n').filter(l => !l.trim().startsWith('--')).join('\n');
const bt = String.fromCharCode(96);
// 与生成脚本统一用字节数（此前用字符数，中文占 3 字节导致两个工具报出的体积对不上）
console.log('文件: ' + p + ' (' + (Buffer.byteLength(s, 'utf8') / 1024).toFixed(1) + ' KB)');
console.log('ALTER TABLE 残留: ' + (noComment.match(/ALTER\s+TABLE/gi) || []).length + ' （应为 0，已折叠进基表）');
// 为什么先剥字符串字面量：菜单段里的权限码 'bidding:personnel:cert:delete' 含 delete 字样，
// 直接在去注释文本上匹配会把权限名误判成 DELETE 语句（假阳性），导致这道门禁失去可信度。
const noCommentNoLiteral = noComment.replace(/'[^']*'/g, "''");
console.log('DROP/TRUNCATE/DELETE 残留: ' + ((noCommentNoLiteral.match(/\b(DROP|TRUNCATE|DELETE)\b/gi) || []).length) + ' （应为 0；已排除字符串字面量中的权限码）');
for (const t of ['qualification_safety_permit', 'bidding_personnel_profile', 'cert_alert_records']) {
  const anchor = 'CREATE TABLE IF NOT EXISTS ' + bt + t + bt;
  const i = s.indexOf(anchor);
  if (i < 0) { console.log('  ' + t + ': 未找到建表语句 ✗'); continue; }
  const seg = s.slice(i, s.indexOf('ENGINE', i));
  const cols = (seg.match(new RegExp('^\\s+' + bt, 'gm')) || []).length;
  console.log('  ' + t + ': 列 ' + cols + ' 个；风险列 ' + (/risk_flag/.test(seg) ? '有' : '无') +
    '；人员风险列 ' + (/job_change_count12m/.test(seg) ? '有' : '无') +
    '；cert_id 可空 ' + (new RegExp(bt + 'cert_id' + bt + ' bigint DEFAULT NULL').test(seg) ? '是' : '否'));
}
