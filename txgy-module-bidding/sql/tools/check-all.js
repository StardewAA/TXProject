#!/usr/bin/env node
/**
 * 自检总入口：一次跑完本模块所有结构与一致性校验（CI / 发版前 / 提交前均可）。
 *
 * 用法：
 *   node sql/tools/check-all.js            # 汇总输出，失败项打印尾部日志
 *   node sql/tools/check-all.js --verbose  # 打印每项的完整输出
 * 退出码：0 = 全部通过；1 = 有失败项（可直接作为 CI 门禁）。
 *
 * 为什么要有总入口：单个脚本散落各处时，"该跑哪几个"靠口口相传，漏跑就变成线上事故
 * （真实事故：终态包 master-bidding-sync.sql 里菜单 6739 仍指向已删除页面，新装库点进去 404；
 *  前端字典名写成 apply_type 而非 qual_apply_type，页面静默显示英文码，均为人工比对才发现）。
 */
const { spawnSync } = require('node:child_process');
const path = require('node:path');

const VERBOSE = process.argv.includes('--verbose');
const moduleRoot = path.resolve(__dirname, '..', '..');

/** 每项：脚本 + 参数 + 一句话用途 */
const CHECKS = [
  { args: ['sql/tools/validate-sql-structure.js', 'sql/master-credential-sync.sql'], name: '结构自检 · 证照一键包', why: '语句/建表规模、括号与分号闭合' },
  { args: ['sql/tools/validate-sql-structure.js', 'sql/master-bidding-sync.sql'], name: '结构自检 · 招投标一键包', why: '同上（表 21 张）' },
  { args: ['sql/tools/verify-credential-sync.js'], name: '一键包纯净性', why: '不得残留 ALTER / DROP / TRUNCATE / DELETE（须折叠进基表）' },
  { args: ['sql/tools/check-do-tables.js'], name: 'DO ↔ 建表覆盖', why: '每个 DO 都必须能在任一一键包中找到建表语句' },
  { args: ['sql/tools/check-do-columns.js'], name: 'DO ↔ 列覆盖', why: 'DO 字段必须在合并包中有对应列' },
  { args: ['sql/tools/check-menu-pages.js'], name: '菜单 ↔ 页面一致性', why: 'type=2 菜单的 component 必须存在对应 .vue（防死菜单/404）' },
  { args: ['sql/tools/check-dict-coverage.js'], name: '字典覆盖率', why: '前端引用的字典类型必须已在 bidding_dict_data 注册（防英文码直出）' },
  { args: ['sql/tools/check-api-coverage.js'], name: '接口接线覆盖率', why: '正向：后端端点已接线；反向：前端请求路径都存在（防 404）' },
  { args: ['sql/tools/check-permission-coverage.js'], name: '权限码覆盖率', why: '前端判权码必须同时在 system_menu 注册（否则无法授权 → 按钮永远不可见）' },
];

const results = [];
for (const c of CHECKS) {
  const r = spawnSync(process.execPath, c.args, { cwd: moduleRoot, encoding: 'utf8' });
  const out = `${r.stdout ?? ''}${r.stderr ?? ''}`.trim();
  const ok = r.status === 0;
  results.push({ ...c, ok, out });
  console.log(`${ok ? '[OK]  ' : '[FAIL]'} ${c.name.padEnd(20)} ${c.why}`);
  if (!ok) {
    const lines = out.split('\n').slice(-12).join('\n');
    console.log(lines.replace(/^/gm, '        '));
  } else if (VERBOSE) {
    console.log(out.replace(/^/gm, '        '));
  }
}

const failed = results.filter((r) => !r.ok);
console.log(`\n合计 ${results.length} 项，通过 ${results.length - failed.length} 项${failed.length > 0 ? `，失败 ${failed.length} 项：${failed.map((f) => f.name).join('、')}` : ''}`);
process.exit(failed.length > 0 ? 1 : 0);
