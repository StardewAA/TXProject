#!/usr/bin/env node
/**
 * 接口接线覆盖率自检：后端招投标端点 vs 前端引用。
 *
 * 目的：后端新增端点后前端很可能"忘了接"——这类缺口不报错、只是功能缺失，
 * 靠人工比对 158 个端点与 30+ 个前端模块极易漏。本脚本把该比对固化成可复跑的门禁。
 *
 * 用法：
 *   node sql/tools/check-api-coverage.js            # 汇总（未接线项逐条列出并给出归属）
 *   node sql/tools/check-api-coverage.js --list     # 打印全部端点与接线状态（✅ 精确 / ≈ 后缀 / ⚪ 白名单）
 *   node sql/tools/check-api-coverage.js --json     # 机器可读输出
 * 退出码：0 = 除白名单外全部接线；1 = 存在未接线端点。
 *
 * 识别口径（两侧都归一化，避免误报）：
 *   - 后端：类级 @RequestMapping + 方法级 @(Get|Post|Put|Delete)Mapping 拼接；路径变量归一为 {x}；
 *     去 /admin-api 前缀，只统计 /bidding 开头的端点。
 *   - 前端：扫描 .ts/.vue 里的 '/bidding/...' 字面量；为识别
 *     `${FOLLOW_API}/item/${id}/timeline`（FOLLOW_API = `${baseUrl}${apiPrefix}/notice-follow`）这类写法：
 *       ① 解析引号字面量常量与模板字符串常量的**字面尾段**，做同名常量内插；
 *       ② 在替换前收集 `${CONST}` 之后直到查询串为止的路径段（内层 `${...}` 归一为 {x}）；
 *       ③ 判定时先精确匹配，再按"后缀 + ≥2 段"匹配（应对配置常量拼前缀的写法）。
 *     因此"未接线"= 前端完全没有引用（任何形态），而不是"字面量里没写全路径"。
 */
const fs = require('node:fs');
const path = require('node:path');

const LIST = process.argv.includes('--list');
const AS_JSON = process.argv.includes('--json');

/** 有意不接前端调用的端点（必须写明理由，并说明替代路径） */
const ALLOW = new Map([
  ['/bidding/notice-external/ingest', '入参为采集侧 ExternalNoticeIngestDTO，属爬虫/脚本程序化入库，后台不应提供人工表单'],
  ['/bidding/safety-permit/project/key-post-assignment', '后端新增写端点（项目关键岗位任职登记），属另一开发者在飞的功能；VO/语义未稳定，接线待其提交后跟进'],
]);

const repoRoot = path.resolve(__dirname, '..', '..', '..');
const javaRoot = path.join(repoRoot, 'txgy-module-bidding', 'txgy-module-bidding-server', 'src', 'main', 'java');
const feRoot = path.join(repoRoot, 'txgy-ui-admin-vben', 'apps', 'web-antd', 'src');

function walk(dir, filter, out = []) {
  let entries;
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch {
    return out;
  }
  for (const e of entries) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) {
      if (['.git', 'node_modules', 'dist', 'target'].includes(e.name)) continue;
      walk(p, filter, out);
    } else if (filter(p)) {
      out.push(p);
    }
  }
  return out;
}

/** 路径变量归一为 {x} 并去掉尾部多余斜杠（纯字符串操作，不做正则转义） */
function norm(p) {
  let out = '';
  let i = 0;
  while (i < p.length) {
    if (p[i] === '{') {
      const end = p.indexOf('}', i);
      if (end < 0) { out += p.slice(i); break; }
      out += '{x}';
      i = end + 1;
      continue;
    }
    out += p[i];
    i++;
  }
  while (out.length > 1 && out.endsWith('/')) out = out.slice(0, -1);
  return out;
}

// ---- 后端端点 ----
const backend = new Map(); // 归一化路径 -> Controller#Method
for (const f of walk(javaRoot, (p) => p.endsWith('.java') && p.includes('controller'))) {
  const code = fs.readFileSync(f, 'utf8');
  const m = code.match(/@RequestMapping\(\s*(?:value\s*=\s*)?"([^"]+)"/);
  if (!m) continue;
  const base = m[1].replace(/^\/admin-api/, '');
  const cls = path.basename(f, '.java');
  for (const mm of code.matchAll(/@(Get|Post|Put|Delete)Mapping\(\s*(?:value\s*=\s*)?"([^"]*)"/g)) {
    let p = (base + mm[2]).replace(/\/\//g, '/');
    if (!p.startsWith('/')) p = '/' + p;
    if (!p.startsWith('/bidding/')) continue;
    backend.set(norm(p), cls + '#' + mm[1]);
  }
}

// ---- 前端引用 ----
const DOLLAR = 36, LBRACE = 123, RBRACE = 125, UNDERSCORE = 95, DASH = 45, SLASH = 47;
const chD = String.fromCharCode(DOLLAR), chLB = String.fromCharCode(LBRACE), chRB = String.fromCharCode(RBRACE);
const PLACEHOLDER = chLB + 'x' + chRB;
const isPathChar = (c) => (c >= 48 && c <= 57) || (c >= 65 && c <= 90) || (c >= 97 && c <= 122) || c === UNDERSCORE || c === DASH || c === SLASH || c === LBRACE || c === RBRACE;

const fePaths = new Set();  // 含 /bidding 前缀的完整路径
const feSuffix = new Set(); // 由模板常量拼出的后缀路径（如 /notice-follow/item/{x}/timeline）

for (const f of walk(feRoot, (p) => /\.(ts|vue)$/.test(p))) {
  const original = fs.readFileSync(f, 'utf8');
  let text = original;

  // ① 常量：引号字面量 + 模板字符串的字面尾段
  const tailConsts = new Map();
  for (const mm of original.matchAll(/(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*['"]([^'"]*\/[^'"]*)['"]/g)) {
    tailConsts.set(mm[1], mm[2]);
  }
  for (const mm of original.matchAll(/(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*\u0060([^\u0060]*)\u0060/g)) {
    const body = mm[2];
    const lastOpen = body.lastIndexOf(chD + chLB);
    const tail = lastOpen < 0 ? body : body.slice(body.indexOf(chRB, lastOpen) + 1);
    if (tail.startsWith('/') && tail.length > 1) tailConsts.set(mm[1], tail);
  }

  // ② 替换前收集 ${CONST} 之后的路径段（替换后 token 就消失了）
  for (const name of tailConsts.keys()) {
    const token = chLB + name + chRB;
    const tail = tailConsts.get(name);
    let k = 0;
    while ((k = original.indexOf(token, k)) !== -1) {
      let j = k + token.length;
      let seg = '';
      while (j < original.length) {
        const c = original.charCodeAt(j);
        if (isPathChar(c)) { seg += original[j]; j++; continue; }
        if (c === DOLLAR && original.charCodeAt(j + 1) === LBRACE) {
          const end = original.indexOf(chRB, j);
          if (end < 0) break;
          seg += PLACEHOLDER; // 内层变量（如 encodeURIComponent(String(itemId))）统一归一
          j = end + 1;
          continue;
        }
        break;
      }
      if (seg.startsWith('/')) feSuffix.add(norm(tail + seg));
      k = j;
    }
  }

  // ③ 常量内插（最多 3 轮处理链式常量）
  for (let pass = 0; pass < 3; pass++) {
    let changed = false;
    for (const [name, value] of tailConsts) {
      const token = chLB + name + chRB;
      if (text.includes(token)) { text = text.split(token).join(value); changed = true; }
    }
    if (!changed) break;
  }

  // ④ 其余 ${...} 归一为 {x}，再抽取 /bidding 路径
  let out = '';
  let i = 0;
  while (i < text.length) {
    if (text.charCodeAt(i) === DOLLAR && text.charCodeAt(i + 1) === LBRACE) {
      const end = text.indexOf(chRB, i);
      if (end < 0) { out += text.slice(i); break; }
      out += PLACEHOLDER;
      i = end + 1;
      continue;
    }
    out += text[i];
    i++;
  }
  i = 0;
  while ((i = out.indexOf('/bidding/', i)) !== -1) {
    let j = i + 9;
    while (j < out.length && isPathChar(out.charCodeAt(j))) j++;
    fePaths.add(norm(out.slice(i, j)));
    i = j;
  }
}

/**
 * 接线判定：精确命中 → exact；"前端路径是端点路径的后缀且 ≥2 段" → suffix。
 * 后者用于应对 `const API = \u0060\${baseUrl}\${apiPrefix}/notice-follow\u0060` 这类配置常量拼前缀的写法
 * （门户站即如此：字面量里根本没有 '/bidding'，只做精确比对会误报成未接线）。
 */
function isWired(p) {
  if (fePaths.has(p)) return 'exact';
  for (const f of [...fePaths, ...feSuffix]) {
    if (f.split('/').filter(Boolean).length >= 2 && p.endsWith(f)) return 'suffix';
  }
  return '';
}

const wiredBy = new Map([...backend.keys()].map((p) => [p, isWired(p)]));
const unwired = [...backend.keys()].filter((p) => !wiredBy.get(p)).sort();
const unexpected = unwired.filter((p) => !ALLOW.has(p));
const suffixCount = [...wiredBy.values()].filter((v) => v === 'suffix').length;

if (AS_JSON) {
  console.log(JSON.stringify({ endpoints: backend.size, fePaths: fePaths.size, feSuffix: feSuffix.size, suffixMatched: suffixCount, unwired, allowed: unwired.filter((p) => ALLOW.has(p)) }, null, 2));
} else {
  console.log('后端 /bidding 端点 ' + backend.size + ' 个；前端引用 ' + fePaths.size + ' 个完整路径 + ' + feSuffix.size + ' 个后缀路径；未接线 ' + unwired.length + ' 个（白名单 ' + ALLOW.size + '，其中 ' + suffixCount + ' 个按后缀匹配认定已接线）');
  if (LIST) {
    console.log('\n--- 全量端点 ---');
    for (const p of [...backend.keys()].sort()) {
      const w = wiredBy.get(p);
      const mark = w === 'exact' ? '✅' : w === 'suffix' ? '≈' : ALLOW.has(p) ? '⚪' : '❌';
      console.log('  ' + mark + ' ' + p.padEnd(58) + ' ' + backend.get(p));
    }
  }
  if (unwired.length > 0) {
    console.log('\n--- 未接线端点 ---');
    for (const p of unwired) {
      console.log('  ' + (ALLOW.has(p) ? '⚪ 白名单' : '❌ 待接线') + '  ' + p);
      console.log('      ' + (ALLOW.get(p) || backend.get(p)));
    }
  }
}

if (unexpected.length > 0) {
  console.error('\n[FAIL] ' + unexpected.length + ' 个端点前端未接线：' + unexpected.join(', '));
  console.error('修复二选一：① 在前端补调用（api/ 模块 + 页面动作）；② 确属不需前端调用，则加入本脚本 ALLOW 白名单并写明理由。');
  process.exit(1);
}
console.log('\n[OK] 除白名单 ' + ALLOW.size + ' 项外，后端端点均已在前端接线');
