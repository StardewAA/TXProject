#!/usr/bin/env node
/**
 * 初始状态包拼装脚本（配合 build-init-from-db.js 使用）
 *
 * 作用：
 *   ① 从既有 sql/master-bidding-sync.sql 中原样抽取全部 CREATE TABLE 语句（不改写 DDL，避免结构漂移）；
 *   ② 用 sql/.tmp/ 下由 build-init-from-db.js 从开发库导出的种子（菜单/角色授权/采集源/闸位）重排初始状态包；
 *   ③ 段序固定为：表结构 → 采集源 → 闸位 → 菜单 → 授权。
 *      为什么这个顺序：mysql 客户端遇到第一个错误就中止；菜单属 system 服务库，目标库没有 system_menu 时
 *      菜单段必然报错——把爬虫必需项（采集源/闸位）排在菜单之前，菜单失败也不会拖垮爬虫。
 *
 * 用法（在模块根目录执行）：
 *   node sql/tools/build-init-from-db.js && node sql/tools/build-init.js
 *
 * 说明：被剔除的业务数据种子（主体授权 / AI 链路 / 演示公告）是一次性归档产物，
 *   已落在 sql/history/2026-09-11-master-sync-removed-seeds.sql，本脚本不再改动它。
 */
const fs = require('fs');
const path = require('path');
// 统一按模块根目录解析相对路径（脚本可从任意工作目录调用）
process.chdir(path.join(__dirname, '..', '..'));

const M = 'sql/master-bidding-sync.sql';
const raw = fs.readFileSync(M, 'utf8');
const L = raw.split(/\r?\n/);

// 1) 抽取全部 CREATE TABLE 语句（到分号结尾），保持原顺序
const ddls = [];
const names = [];
for (let i = 0; i < L.length; i++) {
  if (/^\s*CREATE TABLE IF NOT EXISTS/i.test(L[i])) {
    let j = i;
    while (j < L.length && !/;\s*$/.test(L[j])) j++;
    ddls.push(L.slice(i, j + 1).join('\n'));
    names.push((L[i].match(/CREATE TABLE IF NOT EXISTS\s+`?([A-Za-z0-9_]+)`?/i) || [])[1]);
    i = j;
  }
}
if (ddls.length === 0) { throw new Error('未从 ' + M + ' 抽到任何 CREATE TABLE，已中止（避免生成空结构包）'); }

// 2) 开发库导出的当前目标态种子
const menu = fs.readFileSync('sql/.tmp/menu.sql', 'utf8').trim();
const rolemenu = fs.readFileSync('sql/.tmp/rolemenu.sql', 'utf8').trim();
const source = fs.readFileSync('sql/.tmp/source.sql', 'utf8').trim();
const gate = fs.readFileSync('sql/.tmp/gate.sql', 'utf8').trim();

const out = [
'-- ============================================================================',
'-- bidding 模块「初始状态」SQL 包（表结构 + 最小启动数据；幂等，可重复执行）',
'-- 生成时间：见 git 提交（表结构取自包内既有 DDL；菜单/采集源按开发库当前目标态导出）',
'-- 用途：新环境/新库一键初始化 —— 建表 → 菜单可见 → 爬虫可启动，即可投入使用。',
'--',
'-- 包含（**顺序有讲究**：爬虫必需项排在菜单之前）：',
'--   ① 业务表结构 ' + ddls.length + ' 张（CREATE TABLE IF NOT EXISTS；字段语义见模块设计文档）',
'--   ② 采集源配置（EBIDDING / QYZB / GX —— 爬虫启动所需的最小数据）',
'--   ③ 原文下载限速闸位 GLOBAL 单行（合规频控 2 分钟/次；缺行时程序亦会自愈补建）',
'--   ④ 菜单 6735~6776（招投标及其子菜单/按钮权限，属 system 服务库）',
'--   ⑤ 角色菜单授权（system_role_menu，同上）',
'--',
'-- 为什么这个顺序（2026-09-11 事故复盘）：mysql 客户端**遇到第一个错误就中止**执行。旧版把限速闸/审计表',
'--   排在菜单段之后，目标库若没有 system_menu（菜单属 system 服务库），菜单段报错即中止 → 闸位表没建 →',
'--   爬虫每个任务都在闸位处抛错、退避后进死信、下一轮再造新任务 = 「无限重试」。现在：全部表结构集中在 ①，',
'--   爬虫必需的两段（②③）排在菜单（④⑤）之前——即使菜单段因库不同而失败，爬虫仍然可用。',
'--   库表缺失时应用启动会打 ERROR 并**自动停掉爬虫调度**（BiddingSchemaHealthCheck），不再空转重试。',
'--',
'-- 不包含（用户口径 2026-09-11「不要数据…不需要任务和公告的数据」）：',
'--   采集任务/批次、公告/附件、主体授权与档案、AI 渠道与密钥、演示公告等业务数据与凭据；',
'--   原包中相关种子已归档到 sql/history/2026-09-11-master-sync-removed-seeds.sql（不在本包内执行）。',
'--',
'-- 执行：mysql -uroot -p123456 --default-character-set=utf8mb4 ruoyi-vue-pro < master-bidding-sync.sql',
'--   若目标库没有 system_menu（菜单在 system 服务库）：本包会在 ④ 处报错中止，但前 ①②③ 已生效（爬虫可用），',
'--   随后把 ④⑤ 在 system 库单独执行即可；也可用 mysql --force 继续执行到结束。',
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
'-- ========== ② 采集源配置（爬虫启动的最小数据；ENABLED/栏目模板见 JSON 配置列） ==========',
'',
source,
'',
'-- ========== ③ 原文下载限速闸位（GLOBAL 单行，合规频控 2 分钟/次） ==========',
'',
gate,
'',
'-- ========== ④ 菜单（招投标 6735 及子菜单/按钮；INSERT IGNORE = 已存在则保持现状） ==========',
'-- 注意：system_menu 属 system 服务库；若本库没有该表，本段会报错中止（前面 ①②③ 已生效）。',
'',
menu,
'',
'-- ========== ⑤ 角色菜单授权（system_role_menu） ==========',
'',
rolemenu,
'',
'/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;',
'',
'-- ========== 校验（期望：结构 ' + ddls.length + ' / 采集源 3 / 闸位 1 / 菜单 37 / 授权 135） ==========',
"SELECT COUNT(*) AS tables_ok FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN (" +
  names.map(function (n) { return "'" + n + "'"; }).join(', ') + ');',
"SELECT COUNT(*) AS sources_ok FROM bidding_fetch_source;",
"SELECT COUNT(*) AS gate_ok FROM bidding_crawl_download_gate;",
"SELECT COUNT(*) AS menus_ok FROM system_menu WHERE id BETWEEN 6735 AND 6799;",
"SELECT COUNT(*) AS role_menus_ok FROM system_role_menu WHERE menu_id BETWEEN 6735 AND 6799;",
''
].join('\n');

fs.writeFileSync(M, out, 'utf8');
console.log('新包：' + out.split('\n').length + ' 行 / ' + Buffer.byteLength(out) + ' 字节');
console.log('表结构 ' + ddls.length + ' 张: ' + names.join(', '));
