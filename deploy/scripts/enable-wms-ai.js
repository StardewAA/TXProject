#!/usr/bin/env node
/**
 * WMS AI 客服 —— 一键启用脚本（供人工或 AI Agent 直接执行）
 *
 * 作用：自动判断数据库是否需要初始化，并在需要时执行 0080 脚本；最后打印结果与后续人工步骤。
 * 设计原则：
 *   - 幂等：重复执行无副作用（0080 脚本本身幂等）
 *   - 无副作用判断：只读查询，不会误改数据
 *   - 明确输出：每一步都打印做了什么、结果如何，便于 AI 据输出决定下一步
 *
 * 用法：
 *   node deploy/scripts/enable-wms-ai.js
 *   node deploy/scripts/enable-wms-ai.js --container txgy-mysql --user root --password root --database ruoyi-vue-pro
 *
 * 退出码：
 *   0 数据库已就绪（无论本次是否执行过初始化）
 *   1 执行失败（输出里含原因）
 */
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

// ---------- 参数 ----------
function arg(name, fallback) {
  const i = process.argv.indexOf(`--${name}`);
  return i >= 0 && process.argv[i + 1] ? process.argv[i + 1] : fallback;
}
const CONTAINER_ARG = arg('container', '');
const DB_USER = arg('user', 'root');
const DB_PASS = arg('password', 'root');

/**
 * 数据库名：优先命令行，其次 deploy/.env 里的 MYSQL_DATABASE，最后默认 ruoyi-vue-pro。
 * 各 compose 都用 ${MYSQL_DATABASE}，直接读 .env 可避免库名被改后脚本失效。
 */
function resolveDbName() {
  const fromArg = arg('database', '');
  if (fromArg) return fromArg;
  for (const envFile of ['.env', '.env.example']) {
    const p = path.join(__dirname, '..', envFile);
    if (!fs.existsSync(p)) continue;
    const m = fs.readFileSync(p, 'utf8').match(/^\s*MYSQL_DATABASE\s*=\s*(\S+)\s*$/m);
    if (m) return m[1];
  }
  return 'ruoyi-vue-pro';
}
const DB_NAME = resolveDbName();
const SQL_FILE = arg(
  'sql',
  path.join(__dirname, '..', 'mysql', 'init', '0080-wms-ai-service.sql'),
);
const WAIT_SECONDS = Number(arg('wait', '120'));

let CONTAINER = CONTAINER_ARG;

function log(msg) {
  console.log(msg);
}
function fail(msg) {
  console.error(`✗ ${msg}`);
  process.exit(1);
}

function sleep(ms) {
  // 同步等待：脚本会被 start.bat 顺序调用，简单起见不引入异步
  Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms);
}

/** 列出运行中的容器名 */
function runningContainers() {
  try {
    return execFileSync('docker', ['ps', '--format', '{{.Names}}'], { encoding: 'utf8' })
      .split(/\r?\n/).map((s) => s.trim()).filter(Boolean);
  } catch {
    return [];
  }
}

/**
 * 自动探测 MySQL 容器名。
 *
 * 同一份代码在不同 compose 文件下容器名不同：
 *   docker-compose.local.yml      → txgy-mysql
 *   docker-compose.yml（deploy 下）→ deploy-mysql-1
 * 因此默认按「名字里含 mysql」自动匹配，避免换 compose 就找不到容器。
 */
function resolveContainer() {
  const names = runningContainers();
  if (names.length === 0) return '';
  if (CONTAINER_ARG) return names.includes(CONTAINER_ARG) ? CONTAINER_ARG : '';
  // 优先精确匹配常见名，其次模糊匹配
  for (const exact of ['txgy-mysql', 'deploy-mysql-1']) {
    if (names.includes(exact)) return exact;
  }
  return names.find((n) => /mysql/i.test(n)) || '';
}

/** 等待 MySQL 可连接（容器起来后还要几秒才能接受连接） */
function waitForMysql() {
  const deadline = Date.now() + WAIT_SECONDS * 1000;
  let last = '';
  while (Date.now() < deadline) {
    CONTAINER = resolveContainer();
    if (!CONTAINER) {
      last = '未找到运行中的 MySQL 容器';
    } else {
      try {
        query('SELECT 1;');
        return true;
      } catch (e) {
        last = String(e.stderr || e.message || '').split('\n')[0].slice(0, 120);
      }
    }
    sleep(3000);
  }
  fail(`等待 MySQL 就绪超时（${WAIT_SECONDS}s）。最后错误：${last || '未知'}`);
}

/** 在 MySQL 容器里执行一条查询，返回去掉表头的原始文本 */
function query(sql) {
  const out = execFileSync(
    'docker',
    [
      'exec', '-i', CONTAINER,
      'mysql', `-u${DB_USER}`, `-p${DB_PASS}`, DB_NAME,
      '--default-character-set=utf8mb4', '-N', '-B', '-e', sql,
    ],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] },
  );
  return out.trim();
}

/** 用「拷贝文件 + 容器内重定向」执行 SQL 文件，避免 PowerShell 重定向的编码问题 */
function runSqlFile(file) {
  const remote = '/tmp/wms-ai-bootstrap.sql';
  execFileSync('docker', ['cp', file, `${CONTAINER}:${remote}`], { stdio: 'inherit' });
  execFileSync(
    'docker',
    ['exec', CONTAINER, 'sh', '-c',
     `mysql -u${DB_USER} -p${DB_PASS} ${DB_NAME} --default-character-set=utf8mb4 < ${remote}`],
    { stdio: 'inherit' },
  );
}

// ---------- 主流程 ----------
log('=== WMS AI 客服 启用检查 ===');

// 0. 等 MySQL 就绪（容器名自动探测：txgy-mysql / deploy-mysql-1 / 名字含 mysql 的容器）
log(`查找 MySQL 容器（最多等待 ${WAIT_SECONDS}s）...`);
waitForMysql();
log(`✓ MySQL 容器已就绪：${CONTAINER}`);

// 1. 表是否存在
let hasTable = false;
try {
  const n = query(
    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wms_ai_config';",
  );
  hasTable = n === '1';
} catch (e) {
  fail(`无法连接数据库：${e.message.split('\n')[0]}`);
}
log(`  wms_ai_config 表：${hasTable ? '已存在' : '不存在'}`);
if (!hasTable) {
  log('  → 这是「已有库但未装过 AI 功能」的情况，需要执行初始化脚本');
}

// 2. 菜单是否存在
let menuId = '';
if (hasTable) {
  try {
    menuId = query(
      "SELECT IFNULL(CAST(id AS CHAR),'') FROM system_menu WHERE deleted=b'0' AND path='ai-service' LIMIT 1;",
    );
  } catch { /* 表结构异常时忽略，走初始化 */ }
}
log(`  AI 客服管理菜单：${menuId ? `已存在（id=${menuId}）` : '不存在'}`);

// 3. 需要则执行
if (!hasTable || !menuId) {
  if (!fs.existsSync(SQL_FILE)) fail(`找不到初始化脚本：${SQL_FILE}`);
  log(`\n=== 执行初始化脚本 ===\n  ${SQL_FILE}`);
  try {
    runSqlFile(SQL_FILE);
  } catch (e) {
    fail(`执行初始化脚本失败：${e.message}`);
  }
  // 复核
  menuId = query(
    "SELECT IFNULL(CAST(id AS CHAR),'') FROM system_menu WHERE deleted=b'0' AND path='ai-service' LIMIT 1;",
  );
  if (!menuId) fail('脚本执行后仍未找到 AI 客服管理菜单，请检查 SQL 输出中的报错');
  log(`\n✓ 初始化完成，AI 客服管理菜单 id=${menuId}`);
} else {
  log('\n✓ 数据库已就绪，无需初始化（脚本幂等，如不放心可重复执行）');
}

// 4. 统计当前 AI 配置状态，便于判断是否已配 Key
const cfgCount = query('SELECT COUNT(*) FROM wms_ai_config WHERE deleted=b\'0\';');
const keyCount = query('SELECT COUNT(*) FROM ai_api_key WHERE deleted=b\'0\';');
const modelCount = query('SELECT COUNT(*) FROM ai_model WHERE deleted=b\'0\';');

log('\n=== 当前 AI 配置状态 ===');
log(`  wms_ai_config 记录数：${cfgCount}   （0 = 还没在界面选过模型）`);
log(`  ai_api_key  记录数：${keyCount}   （0 = 还没配过 API Key）`);
log(`  ai_model    记录数：${modelCount}   （0 = 还没建过模型）`);

log('\n=== 下一步（必须人工在界面完成）===');
if (keyCount === '0') {
  log('  1) 「AI 客服管理 → API 密钥」新增一条，粘贴你的厂商 API Key');
  log('  2) 「AI 客服管理 → 模型配置」新建模型（填模型标识如 deepseek-chat，绑定上面的密钥）');
  log('  3) 「AI 客服管理 → AI 助手配置」选中该模型 → 点「测试连通性」→ 保存');
} else if (modelCount === '0') {
  log('  1) 「AI 客服管理 → 模型配置」新建模型并绑定已有密钥');
  log('  2) 「AI 客服管理 → AI 助手配置」选中该模型 → 点「测试连通性」→ 保存');
} else if (cfgCount === '0') {
  log('  1) 「AI 客服管理 → AI 助手配置」选中模型 → 点「测试连通性」→ 保存');
} else {
  log('  配置已存在。若 AI 助手仍不可用，请进「AI 助手配置」点「测试连通性」看具体报错。');
}
log('\n完成后打开「智慧仓储系统 → 仓储首页」即可使用 AI 助手。');
log('详细说明见 docs/wms-ai-deployment.md');
