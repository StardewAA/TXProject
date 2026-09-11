#!/usr/bin/env node
/**
 * 初始状态包拼装脚本（配合 build-init-from-db.js 使用）
 *
 * 作用：① 从既有 master-bidding-sync.sql 中原样抽取全部 CREATE TABLE 语句（不改写 DDL）；
 *   ② 用 sql/.tmp/ 下由 build-init-from-db.js 导出的菜单/授权/采集源/闸位种子，重排为
 *   「结构 + 最小启动数据」的初始状态包；③ 把业务数据类种子归档到 history/，不入包。
 *
 * 用法（模块根目录）：node sql/tools/build-init-from-db.js && node sql/tools/build-init.js
 */
const fs = require('fs');
const path = require('path');
// 统一按模块根目录解析相对路径（脚本可从任意工作目录调用）
process.chdir(path.join(__dirname, '..', '..'));
const M = 'sql/master-bidding-sync.sql';
const raw = fs.readFileSync(M, 'utf8');
const L = raw.split(/\r?\n/);

// 备份原包（防手误）
// 原包备份由 git 版本控制承担（本脚本会覆盖 master-bidding-sync.sql）

// 1) 抽取全部 CREATE TABLE 语句（到分号结尾），保持原顺序
const ddls = [];
const names = [];
for (let i = 0; i < L.length; i++) {
  if (/^\s*CREATE TABLE IF NOT EXISTS/i.test(L[i])) { … }
}

// 2) 归档被剔除的种子段落（原 §⑤~§⑧：主体授权 / AI 链路 / 演示公告 / AI 启用）
const removed = L.slice(548, 693).join('\n').replace(/\s+$/, '');
const archive = [
'-- ============================================================================',
'-- 【已归档】原 master-bidding-sync.sql 中被剔除的种子数据（2026-09-11 整理）',
'-- 剔除原因：用户口径「不要数据，只要结构和最基础的数据（爬虫能启动 + 菜单能进），',
'--   不需要任务和公告的数据」——下列内容属业务数据/凭据，不属于「初始状态」。',
'-- 保留方式：原样归档，供确需时人工取用；不在初始状态包内自动执行。',
'--   原 §⑤ 主体授权与档案（tenant 1 招标人发布门 / 延河热力）',
'--   原 §⑥ AI 链路配置（ai_chat_role 角色 20 / ai_model 66 / ai_api_key 28）',
'--   原 §⑦ 演示公告 5 条（ACTIVE）',
'--   原 §⑧ AI 链路启用状态（目标态 UPDATE）',
'-- 注意：AI 渠道密钥属凭据，不入初始包；新环境按《部署与启动方式》单独配置。',
'-- ============================================================================',
'',
removed,
''].join('\n');

// 3) 开发库导出的当前目标态种子
const menu = fs.readFileSync('sql/.tmp/menu.sql', 'utf8').trim();
const rolemenu = fs.readFileSync('sql/.tmp/rolemenu.sql', 'utf8').trim();
const source = fs.readFileSync('sql/.tmp/source.sql', 'utf8').trim();
const gate = fs.readFileSync('sql/.tmp/gate.sql', 'utf8').trim();

const out = [
'-- ============================================================================',
'-- bidding 模块「初始状态」SQL 包（表结构 + 最小启动数据；幂等，可重复执行）',
'-- 生成时间：2026-09-11（表结构取自原同步包；菜单/采集源按开发库当前目标态导出）',
'-- 用途：新环境/新库一键初始化 —— 建表 → 菜单可见 → 爬虫可启动，即可投入使用。',
'--',
'-- 包含：',
'--   ① 业务表结构 ' + ddls.length + ' 张（CREATE TABLE IF NOT EXISTS；字段语义见模块设计文档）',
'--   ② 菜单 6735~6776（招投标及其子菜单/按钮权限 37 条）+ 角色挂载 135 条',
'--   ③ 采集源配置 3 条（EBIDDING / QYZB / GX —— 爬虫启动所需的最小数据）',
'--   ④ 原文下载限速闸位 GLOBAL 单行（合规频控；缺行时程序亦会自愈补建）',
'--',
'-- 不包含（用户口径 2026-09-11「不要数据…不需要任务和公告的数据」）：',
'--   采集任务/批次、公告/附件、主体授权与档案、AI 渠道与密钥、演示公告等业务数据与凭据；',
'--   原包中相关种子已归档到 sql/history/2026-09-11-master-sync-removed-seeds.sql（不在本包内执行）。',
'--',
'-- 执行：mysql -uroot -p123456 --default-character-set=utf8mb4 ruoyi-vue-pro < master-bidding-sync.sql',
'-- 幂等：结构 IF NOT EXISTS、数据 INSERT IGNORE；包内无 DROP / TRUNCATE / DELETE，',
'--   重复执行不改变既有行，也不清空任何业务数据。',
'-- ============================================================================',
'SET NAMES utf8mb4;',
'/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;',
'',
'-- ========== ① 业务表结构（' + ddls.length + ' 张，全部 IF NOT EXISTS） ==========',
'',
ddls.join('\n\n'),
'',
'-- ========== ② 菜单（招投标 6735 及子菜单/按钮，37 条；INSERT IGNORE = 已存在则保持现状） ==========',
'',
menu,
'',
'-- ========== ③ 角色菜单授权（system_role_menu，135 条） ==========',
'',
rolemenu,
'',
'-- ========== ④ 采集源配置（3 条：爬虫启动的最小数据） ==========',
'',
source,
'',
'-- ========== ⑤ 原文下载限速闸位（GLOBAL 单行，合规频控 2 分钟/次） ==========',
'',
gate,
'',
'/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;',
'',
'-- ========== 校验（期望：结构 ' + ddls.length + ' / 菜单 37 / 授权 135 / 采集源 3 / 闸位 1） ==========',
"SELECT COUNT(*) AS tables_ok FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN (" +
  names.map(function (n) { return "'" + n + "'"; }).join(', ') + ");",
"SELECT COUNT(*) AS menus_ok FROM system_menu WHERE id BETWEEN 6735 AND 6799;",
"SELECT COUNT(*) AS role_menus_ok FROM system_role_menu WHERE menu_id BETWEEN 6735 AND 6799;",
"SELECT COUNT(*) AS sources_ok FROM bidding_fetch_source;",
"SELECT COUNT(*) AS gate_ok FROM bidding_crawl_download_gate;",
''
].join('\n');

fs.writeFileSync(M, out, 'utf8');
fs.writeFileSync('sql/history/2026-09-11-master-sync-removed-seeds.sql', archive, 'utf8');
console.log('新包：' + out.split('\n').length + ' 行 / ' + Buffer.byteLength(out) + ' 字节');
console.log('表结构 ' + ddls.length + ' 张: ' + names.join(', '));
console.log('归档种子：' + archive.split('\n').length + ' 行');

[…5ln elided; re-read needed ranges with sql/.tmp/build-init.js:13-19]