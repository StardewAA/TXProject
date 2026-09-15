const fs=require('fs');
const f='sql/history/2026-09-11-master-sync-removed-seeds.sql';
let t=fs.readFileSync(f,'utf8');
const before=(t.match(/'sk-[A-Za-z0-9_\-]{16,}'/g)||[]);
t=t.replace(/'sk-[A-Za-z0-9_\-]{16,}'/g, "'__REDACTED__请在平台「AI 密钥管理」重新填写或从环境变量注入");
// 尾部还留了半个引号，修一下
t=t.replace(/'__REDACTED__请在平台「AI 密钥管理」重新填写或从环境变量注入(?=[,;)])/g, "'__REDACTED__（真实密钥不入库：请在平台「AI 密钥管理」配置，或由环境变量注入）'");
if(!/脱敏/.test(t)){
  t=t.replace(/^--/, '-- ⚠ 本文件中的 AI 密钥已脱敏（真实值不入库；需要时可从平台「AI 密钥管理」重新配置）。\n--');
}
fs.writeFileSync(f,t,'utf8');
console.log('归档文件：脱敏 '+before.length+' 处');

// 另一份预存脚本里只留了掩码形式的 sk-4f6f...，一并收敛
const f2='sql/history/2026-09-10-ai-config-restore.sql';
let t2=fs.readFileSync(f2,'utf8');
const n2=(t2.match(/sk-[0-9a-f]{4}\.\.\./g)||[]).length;
t2=t2.replace(/sk-[0-9a-f]{4}\.\.\./g,'sk-****（已脱敏）');
fs.writeFileSync(f2,t2,'utf8');
console.log('预存脚本：掩码收敛 '+n2+' 处');
