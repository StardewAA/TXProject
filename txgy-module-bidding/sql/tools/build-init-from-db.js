#!/usr/bin/env node
/**
 * 初始状态包生成/维护脚本
 *
 * 规范依据：用户口径 2026-09-11「sql 只留下表结构 + 最基础的数据（爬虫能启动、菜单能进），
 *   不要任务和公告的数据」——菜单与采集源的"当前目标态"以开发库为准，本脚本负责把库里的
 *   目标态重新导出并拼装成 sql/master-bidding-sync.sql（表结构仍取自包内既有 DDL，不做改写）。
 *
 * 用法（在模块根目录）：
 *   node sql/tools/build-init-from-db.js
 * 前置：
 *   · docker 容器可访问（脚本用 mysqldump 导出 4 类种子）；
 *     容器名/账号/密码/库名可用环境变量覆盖（默认值见下方 const）：
 *       MYSQL_CONTAINER（默认 deploy-mysql-1，本地 compose 容器名；dev 环境可覆盖回 txgy-mysql）/
 *       MYSQL_USER（默认 root）/ MYSQL_ROOT_PASSWORD（默认 123456）/ MYSQL_DATABASE（默认 ruoyi-vue-pro）
 *   · 目标库名 ruoyi-vue-pro，菜单按**招投标 6735 子树闭包**导出（2026-09-24 起；此前为扁平 id 区间 6735~6799，
 *     本地库号段与 dev 错位——区间口径会混入 MES 6750/6753~6763、漏掉 6800 智能体/6821 招标全流程/60006xxx 按钮，
 *     故改为递归子树）。只导出 deleted=b'0' 的存活行（软删行不进包）。
 * 说明：
 *   · 菜单/角色授权/采集源/闸位 均以 INSERT IGNORE 落包 → 重复执行不改变既有行；
 *   · 包内绝不产生 DROP/TRUNCATE/DELETE（sql/tools/validate-sql-structure.js 可校验）；
 *   · 业务数据（任务/公告/主体授权/AI 密钥/演示数据）按口径不入包，历史种子见
 *     sql/history/2026-09-11-master-sync-removed-seeds.sql。
 */
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..', '..');
const TMP = path.join(ROOT, 'sql', '.tmp');
const DB = process.env.MYSQL_DATABASE || 'ruoyi-vue-pro';
const CONTAINER = process.env.MYSQL_CONTAINER || 'deploy-mysql-1';
const USER = process.env.MYSQL_USER || 'root';
const PASSWORD = process.env.MYSQL_ROOT_PASSWORD || '123456';
const MENU_ROOT = 6735;

/** 招投标子树闭包（仅存活行）。递归不限软删分支：软删父级下的存活子行（如 6747 挂已软删的 6740）仍会导出。 */
function liveSubtreeIds() {
  const sql = 'WITH RECURSIVE t AS (' +
    `SELECT id, deleted FROM system_menu WHERE id = ${MENU_ROOT} ` +
    'UNION ALL ' +
    'SELECT m.id, m.deleted FROM system_menu m JOIN t ON m.parent_id = t.id' +
    ') SELECT id FROM t WHERE deleted = b\'0\' ORDER BY id';
  const out = execFileSync('docker', ['exec', CONTAINER, 'mysql', '-u' + USER, '-p' + PASSWORD,
    '-N', '-B', '--default-character-set=utf8mb4', DB, '-e', sql], { encoding: 'utf8', maxBuffer: 8 * 1024 * 1024 });
  const ids = out.trim().split(/\r?\n/).filter((s) => /^\d+$/.test(s.trim())).map((s) => s.trim());
  if (ids.length === 0) throw new Error('招投标 6735 子树导出为空，已中止（避免生成空菜单包）');
  return ids;
}
const MENU_IDS = liveSubtreeIds();
const IN_LIST = MENU_IDS.join(', ');

function dump(table, where, out) {
  const args = ['exec', CONTAINER, 'mysqldump', '-u' + USER, '-p' + PASSWORD,
    '--no-create-info', '--insert-ignore', '--complete-insert', '--skip-comments', '--compact',
    '--default-character-set=utf8mb4'];
  if (where) args.push('--where=' + where);
  args.push(DB, table);
  const text = execFileSync('docker', args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
  fs.writeFileSync(out, text.replace(/^.*Warning.*$/gm, '').trim() + '\n', 'utf8');
  return text;
}

fs.mkdirSync(TMP, { recursive: true });
dump('system_menu', `id IN (${IN_LIST}) AND deleted = b'0'`, path.join(TMP, 'menu.sql'));
dump('system_role_menu', `menu_id IN (${IN_LIST}) AND deleted = b'0'`, path.join(TMP, 'rolemenu.sql'));
dump('bidding_fetch_source', null, path.join(TMP, 'source.sql'));
dump('bidding_crawl_download_gate', null, path.join(TMP, 'gate.sql'));

// 导出菜单/授权实际条数：供拼装脚本写入"校验期望值"。
// 为什么：期望值写死会随库漂移——旧包写死「菜单 37 / 授权 135」，而开发库早已多于该数（授权实际 >135），
// 导致包尾校验的"期望"与实际对不上、易被误判为失败。改为从库实时计数。
function scalar(sql) {
  const out = execFileSync('docker', ['exec', CONTAINER, 'mysql', '-u' + USER, '-p' + PASSWORD,
    '-N', '-B', '--default-character-set=utf8mb4', DB, '-e', sql], { encoding: 'utf8', maxBuffer: 8 * 1024 * 1024 });
  return out.trim();
}
const counts = {
  menu: MENU_IDS.length,
  roleMenu: Number(scalar(`SELECT COUNT(*) FROM system_role_menu WHERE menu_id IN (${IN_LIST}) AND deleted = b'0'`)),
};
fs.writeFileSync(path.join(TMP, 'counts.json'), JSON.stringify(counts), 'utf8');
fs.writeFileSync(path.join(TMP, 'menu-ids.json'), JSON.stringify(MENU_IDS), 'utf8');

console.log('种子已导出到 sql/.tmp/（菜单 ' + counts.menu + ' / 授权 ' + counts.roleMenu + '），接着执行：node sql/.tmp/build-init.js');
