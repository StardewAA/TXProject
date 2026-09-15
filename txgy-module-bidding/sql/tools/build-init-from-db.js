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
 *   · docker 容器 txgy-mysql 可访问（脚本用 mysqldump 导出 4 类种子）；
 *   · 目标库名 ruoyi-vue-pro，菜单范围 6735~6799（招投标子树）。
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
const DB = 'ruoyi-vue-pro';
const MENU_FROM = 6735;
const MENU_TO = 6799;

function dump(table, where, out) {
  const args = ['exec', 'txgy-mysql', 'mysqldump', '-uroot', '-p123456',
    '--no-create-info', '--insert-ignore', '--complete-insert', '--skip-comments', '--compact',
    '--default-character-set=utf8mb4'];
  if (where) args.push('--where=' + where);
  args.push(DB, table);
  const text = execFileSync('docker', args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
  fs.writeFileSync(out, text.replace(/^.*Warning.*$/gm, '').trim() + '\n', 'utf8');
  return text;
}

fs.mkdirSync(TMP, { recursive: true });
dump('system_menu', `id BETWEEN ${MENU_FROM} AND ${MENU_TO}`, path.join(TMP, 'menu.sql'));
dump('system_role_menu', `menu_id BETWEEN ${MENU_FROM} AND ${MENU_TO}`, path.join(TMP, 'rolemenu.sql'));
dump('bidding_fetch_source', null, path.join(TMP, 'source.sql'));
dump('bidding_crawl_download_gate', null, path.join(TMP, 'gate.sql'));
console.log('种子已导出到 sql/.tmp/，接着执行：node sql/.tmp/build-init.js');
