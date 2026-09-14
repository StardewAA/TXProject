#!/usr/bin/env node
/**
 * 内部术语直出检查（第 15 道门禁）：中文界面里的**列名/标签不得是纯英文内部术语**。
 *
 * 由来（用户 2026-09-25："数据胡乱展示"）：采集控制台把 `scope`、`worker` 直接当列名，
 * 规则配置面板把 `traceId` 当列名，资质页把 6 个英文业务码（NEW/UPGRADE/RENEW/ADDITION/CHANGE/CANCEL）
 * 直出为筛选项文案 —— 业务人员在中文界面里看到的是开发者的字段名。
 * 与第 11 道"枚举硬编码棘轮"互补：那道门禁只认**具名常量**里的 code→中文映射，
 * 发现不了**内联在模板里**的英文标签（README 已记录该盲区），本门禁正是补这一块。
 *
 * 口径：扫描 views/bidding + views/portal/bidding 的
 *   title: '…' / label: '…' / <Descriptions.Item label="…"> / <th>…</th>
 * 中**不含任何中文**的值；命中即 FAIL，除非在 ALLOW 里（标准术语：URL/SHA-256/robots/JSON/…）。
 * 用法：node sql/tools/check-internal-terms.js [--list]
 * 退出码：0 通过；1 存在内部术语直出。
 */
const fs = require('node:fs');
const path = require('node:path');

const LIST = process.argv.includes('--list');
const repoRoot = path.resolve(__dirname, '../../..');
const SRC = path.join(repoRoot, 'txgy-ui-admin-vben/apps/web-antd/src');
const DIRS = [path.join(SRC, 'views/bidding'), path.join(SRC, 'views/portal/bidding')];
const SKIP_DIRS = new Set(['node_modules', 'dist']);

/** 允许的纯英文标签：国际/行业标准术语或协议名，业务人员本身就这么认 */
const ALLOW = new Set([
  'URL', 'JSON', 'HTML', 'PDF', 'API', 'SHA-256', 'SHA', 'MD5', 'robots', 'A/B/C', 'IP', 'ID', 'KB', 'MB', 'GB',
]);

const PATTERNS = [
  { re: /title:\s*'([^']{1,20})'/g, kind: 'title' },
  { re: /label:\s*'([^']{1,20})'/g, kind: 'label' },
  { re: /<Descriptions\.Item\s+label="([^"]{1,20})"/g, kind: 'descriptions' },
  { re: /<th[^>]*>\s*([A-Za-z][\w ./\-]{0,18})\s*<\/th>/g, kind: '<th>' },
];

function walk(dir, out = []) {
  let ents = [];
  try { ents = fs.readdirSync(dir, { withFileTypes: true }); } catch { return out; }
  for (const e of ents) {
    if (SKIP_DIRS.has(e.name)) continue;
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, out);
    else out.push(p);
  }
  return out;
}

const files = DIRS.flatMap((d) => walk(d)).filter((p) => /\.(vue|ts)$/.test(p));
const hits = [];
for (const f of files) {
  const lines = fs.readFileSync(f, 'utf8').split('\n');
  lines.forEach((line, i) => {
    for (const { re, kind } of PATTERNS) {
      re.lastIndex = 0;
      let m;
      while ((m = re.exec(line))) {
        const v = m[1].trim();
        if (/[\u4e00-\u9fa5]/.test(v)) continue;              // 含中文 → 正常
        if (!/^[A-Za-z][A-Za-z0-9 ./\-]*$/.test(v)) continue;  // 非纯 ASCII → 跳过
        if (ALLOW.has(v)) continue;
        hits.push({ file: path.relative(repoRoot, f).split(path.sep).join('/'), line: i + 1, kind, value: v });
      }
    }
  });
}

if (LIST) {
  console.log('扫描 ' + files.length + ' 个文件；纯英文标签命中 ' + hits.length + ' 处');
  for (const h of hits) console.log('   ' + h.file + ':' + h.line + '  [' + h.kind + '] ' + h.value);
}
if (hits.length === 0) {
  console.log('扫描 ' + files.length + ' 个文件；[OK] 未发现内部术语直出（业务列/标签均为中文或标准术语）');
  process.exit(0);
}
console.log('[FAIL] 以下列名/标签是纯英文内部术语（业务人员在中文界面看到开发字段名）：');
for (const h of hits.slice(0, 30)) console.log('   ' + h.file + ':' + h.line + '  [' + h.kind + '] ' + h.value);
if (hits.length > 30) console.log('   … 另有 ' + (hits.length - 30) + ' 处');
console.log('修复：改成业务中文名（如 worker→执行节点、scope→采集范围、traceId→追踪编号）；标准术语请加入 ALLOW 并说明理由。');
process.exit(1);
