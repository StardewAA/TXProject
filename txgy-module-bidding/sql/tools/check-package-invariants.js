#!/usr/bin/env node
/**
 * 一键包**不变式自检**（2026-09-13 新增，起因是一次真实事故与三次并发覆盖）：
 *
 * 背景：master-credential-sync.sql 是**生成物**，而它曾经出过三类会让"整包执行直接中断"的问题，
 * 且修复被并发编辑覆盖过多次。本脚本把这几条不变式**独立出来**（不放在生成器里，
 * 避免有人改生成器时连带删掉），任何一条不满足立即 FAIL：
 *
 *   ① 建表语句里 `id` 必须是 AUTO_INCREMENT
 *      —— 应用侧不自生成主键（与平台一致），缺它则**一切写操作失败**：
 *         java.sql.SQLException: Field 'id' doesn't have a default value
 *   ② 列定义行必须以逗号分隔
 *      —— 折叠 ALTER 时丢过行尾逗号，导致 CREATE TABLE 语法错误（实测第 184 行中断）
 *   ③ 列定义里不得出现定位子句 AFTER/FIRST
 *      —— 只在 ALTER 里合法，折进 CREATE TABLE 会报 near 'AFTER ...'
 *   ④ 不得出现 MariaDB 专有语法 ADD COLUMN IF NOT EXISTS（MySQL 不支持）
 *   ⑤ ALTER 的目标表必须由本包创建（跨域来源脚本必须移出，否则干净库必然失败）
 *   ⑥ 种子 INSERT 必须显式带 id（本域主键非自增）
 *   ⑦ 继承 TenantBaseDO 的表必须建 `tenant_id` 列
 *      —— DO 侧继承租户基类后，MyBatis-Plus 租户插件会对该表**无条件**追加 `tenant_id = ?`；
 *         表里没这列则所有读写直接 500（实测：java.sql.SQLSyntaxErrorException:
 *         Unknown column 'tenant_id' in 'where clause'——审查任务中心首次联调即因此全接口失败）。
 *         本域这类表即便业务上"只有租户自己看"，也必须建列（隔离条件由插件强制）。
 */
const fs = require('fs');
const path = require('path');
const file = path.resolve(__dirname, '..', 'master-credential-sync.sql');
const text = fs.readFileSync(file, 'utf8');
const problems = [];

// ① id 必须 AUTO_INCREMENT
for (const m of text.matchAll(/CREATE TABLE(?:\s+IF NOT EXISTS)?\s+`?(\w+)`?\s*\(([\s\S]*?)\)\s*ENGINE/gi)) {
    const body = m[2];
    const idLine = body.split(/\r?\n/).find((l) => /^\s*`id`\s+/.test(l));
    if (!idLine) { problems.push(m[1] + ': 没有 id 列'); continue; }
    if (!/AUTO_INCREMENT/i.test(idLine)) problems.push(m[1] + ': id 列缺 AUTO_INCREMENT（写操作会全部失败）');
}

// ② 列定义行尾逗号（除最后一行）
for (const m of text.matchAll(/CREATE TABLE(?:\s+IF NOT EXISTS)?\s+`?(\w+)`?\s*\(([\s\S]*?)\)\s*ENGINE/gi)) {
    const lines = m[2].split(/\r?\n/).map((l) => l.trim()).filter((l) => l.startsWith('`'));
    for (let i = 0; i < lines.length - 1; i++) {
        if (!lines[i].endsWith(',')) problems.push(m[1] + ': 列定义缺行尾逗号 -> ' + lines[i].slice(0, 60));
    }
}

// ③ 不得有 AFTER / FIRST
for (const m of text.matchAll(/^\s*`\w+`[^\n]*\b(AFTER|FIRST)\b[^\n]*$/gim)) {
    problems.push('列定义含定位子句（CREATE 里非法）：' + m[0].trim().slice(0, 70));
}

// ④ 不得有 MariaDB 专有语法
const mdb = (text.match(/ADD COLUMN IF NOT EXISTS/gi) || []).length;
if (mdb) problems.push('存在 MariaDB 专有语法 ADD COLUMN IF NOT EXISTS × ' + mdb + '（MySQL 不支持）');

// ⑤ 跨域 ALTER
const created = new Set([...text.matchAll(/CREATE TABLE(?:\s+IF NOT EXISTS)?\s+`?(\w+)`?/gi)].map((m) => m[1].toLowerCase()));
for (const m of text.matchAll(/ALTER\s+TABLE\s+`?(\w+)`?/gi)) {
    if (!created.has(m[1].toLowerCase())) problems.push('ALTER 的目标表本包不创建（跨域）：' + m[1]);
}

// ⑥ 种子 INSERT 必须带 id
for (const m of text.matchAll(/INSERT INTO `?(cert_alert_config|bidding_rule_config|bidding_dict_data)`?\s*\(([^)]*)\)/gi)) {
    if (!/`id`/.test(m[2])) problems.push(m[1] + ' 的 INSERT 未显式带 id（该表主键非自增，会报 no default value）');
}

// ⑦ 继承 TenantBaseDO 的表必须建 tenant_id 列。
// 名单**显式维护**（不用启发式猜测）：每新增一个 extends TenantBaseDO 的 DO，就把表名登记到这里。
// 之所以宁可手工登记：启发式（比如"表名带 bidding_ 就要租户列"）会误伤非租户表，让守卫变成噪音，
// 而噪音守卫最终会被绕过 —— 那比没有守卫更糟。
const TENANT_SCOPED_TABLES = [
    'bidding_review_task',
];
for (const t of TENANT_SCOPED_TABLES) {
    const m = text.match(new RegExp('CREATE TABLE(?:\\s+IF NOT EXISTS)?\\s+`?' + t + '`?\\s*\\(([\\s\\S]*?)\\)\\s*ENGINE', 'i'));
    if (!m) { problems.push('租户表 ' + t + ' 在本包内没有 CREATE TABLE（登记名与建表名不一致？）'); continue; }
    if (!/^\s*`tenant_id`\s/m.test(m[1])) {
        problems.push(t + ': 继承 TenantBaseDO 但缺 tenant_id 列（租户插件会追加 tenant_id 条件 → 全接口 500）');
    }
}

if (problems.length) {
    console.error('[FAIL] 一键包不变式未满足（共 ' + problems.length + ' 条）：');
    for (const p of problems.slice(0, 20)) console.error('  - ' + p);
    console.error('修复提示：这些不变式由 sql/tools/build-credential-sync.js 的折叠/后处理逻辑保证；');
    console.error('         若生成器里的相关修复被覆盖，请恢复后重建（本脚本是防覆盖的独立守卫）。');
    process.exit(1);
}
console.log('[OK] 一键包不变式全部满足（id 自增 / 逗号 / 无定位子句 / 无 MariaDB 语法 / 无跨域 ALTER / 种子带 id）');
