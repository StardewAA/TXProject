#!/usr/bin/env node
/**
 * 术语一致性**报告**（不设阈值、不进 check-all —— 术语属于"人对齐"的范畴）。
 *
 * 与门禁的分工：
 *   · 第 11 道（枚举棘轮）/ 第 15 道（内部术语直出）能自动判定的，交给门禁；
 *   · "同一个业务对象在界面上有多种叫法"无法靠正则判定——例如"项目 vs 招标项目"是两张表两个 VO
 *     （ProjectRespVO / TenderProjectRespVO），强行统一就是错。故本脚本只**报告**分布，
 *     由人对照 docs/前端规范-全域接口说明/17-前端术语表-2026-09-25.md 判断。
 *
 * 只统计**引号内的界面文案**，忽略注释与变量名，避免像"源配置需校准"这类注释造成误报。
 * 用法：
 *   node sql/tools/report-terminology.js              # 全部同义词族
 *   node sql/tools/report-terminology.js --family 信息源
 */
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '../../..');
const SRC = path.join(repoRoot, 'txgy-ui-admin-vben/apps/web-antd/src');
const DIRS = [path.join(SRC, 'views/bidding'), path.join(SRC, 'views/portal/bidding')];
const only = process.argv.includes('--family') ? process.argv[process.argv.indexOf('--family') + 1] : '';

/** 同义词族：key = 族名（用于 --family），值 = 该族已出现的叫法（按需增补，增补前先查 17 号术语表） */
const FAMILIES = {
  信息源: ['信息源', '源配置', '采集源', '爬虫源', '外部源'],
  采集批次: ['采集批次', '批次', '采集作业'],
  采集任务: ['采集任务', '任务条'],
  外部公告: ['外部公告', '外采公告', '第三方公告', '采集公告'],
  内部公告: ['内部公告', '自营公告', '自有公告', '本平台公告'],
  标段: ['标段', '分包', '包件'],
  资质证照: ['资质', '证书', '证照', '证件'],
  项目: ['招标项目', '采购项目', '项目'],
  申报: ['申报', '报批'],
  抓取动作: ['抓取', '爬取', '采集', '获取', '下载'],
  审核动作: ['审核', '审批', '复核', '审查'],
  预警: ['预警', '告警', '提醒'],
};

function walk(d, o = []) {
  let ents = [];
  try { ents = fs.readdirSync(d, { withFileTypes: true }); } catch { return o; }
  for (const e of ents) {
    const p = path.join(d, e.name);
    if (e.isDirectory()) walk(p, o);
    else if (/\.(vue|ts)$/.test(p)) o.push(p);
  }
  return o;
}

const files = DIRS.flatMap((d) => walk(d));
const fams = Object.keys(FAMILIES).filter((f) => !only || f === only);
if (fams.length === 0) {
  console.error('未找到该族：' + only + '（可选：' + Object.keys(FAMILIES).join(' / ') + '）');
  process.exit(1);
}

console.log('扫描 ' + files.length + ' 个文件（views/bidding + views/portal/bidding），只统计引号内界面文案\n');
for (const fam of fams) {
  const variants = FAMILIES[fam];
  const counts = new Map(variants.map((v) => [v, 0]));
  const mixed = [];
  for (const f of files) {
    const t = fs.readFileSync(f, 'utf8');
    const hits = new Map();
    const stripped = t
      .replace(/\/\*[\s\S]*?\*\//g, ' ')     // 块注释
      .replace(/<!--[\s\S]*?-->/g, ' ')    // HTML 注释（含文件头业务说明）
      .replace(/(^|\s)\/\/[^\n]*/g, '$1 '); // 行注释
    const literals = [...stripped.matchAll(/'([^'\n]{1,40})'|"([^"\n]{1,40})"|>([^<>\n]{1,60})</g)].map((m) => m[1] || m[2] || m[3] || '');
    const seen = [];
    // 每条文案只归给**最长匹配**的变体：否则"信息源配置"会同时被算成"信息源"和"源配置"（子串误报）
    for (const lit of literals) {
      let best = '';
      for (const v of variants) if (lit.includes(v) && v.length > best.length) best = v;
      if (best) hits.set(best, (hits.get(best) || 0) + 1);
    }
    for (const v of variants) {
      const n = hits.get(v) || 0;
      if (n > 0) { counts.set(v, counts.get(v) + n); seen.push(v + '×' + n); }
    }
    if (seen.length >= 2) mixed.push('     同文件混用 ' + path.relative(repoRoot, f).split(path.sep).join('/') + '  ' + seen.join(' '));
  }
  const used = [...counts.entries()].filter(([, n]) => n > 0).sort((a, b) => b[1] - a[1]);
  const total = used.reduce((s, [, n]) => s + n, 0);
  if (used.length <= 1) {
    console.log('【' + fam + '】' + (used[0] ? '单一叫法：' + used[0][0] + '×' + used[0][1] : '未出现') + '  ✅');
    continue;
  }
  console.log('【' + fam + '】共 ' + total + ' 处 / ' + used.length + ' 种叫法：' + used.map(([v, n]) => v + '×' + n).join(' | '));
  if (mixed.length) console.log(mixed.join('\n'));
  console.log('     判定依据见 docs/前端规范-全域接口说明/17-前端术语表-2026-09-25.md（注意：部分族是**两个不同对象**，禁止合并）');
}
