#!/usr/bin/env node
/**
 * 硬编码枚举映射表自检（棘轮式：冻结既有欠债，只拦"新增或变多"）。
 *
 * 为什么需要它：用户硬约束是"状态码/枚举值必须支持动态配置，严禁硬编码"。此前的字典门禁只能发现
 * "引用了未注册的字典"，看不见"前端干脆自己写死一张 code→中文 的映射表"——这类硬编码在页面上看不出异常，
 * 却让后台改了字典文案也不生效、多租户/多语言差异无从落地。实测 bidding 域有 20+ 处、10+ 个文件。
 *
 * 检测两类形态（均要求"SCREAMING_SNAKE code + 中文文案"成对出现，避免误报）：
 *   MAP     const X = { CODE: '中文' } / { CODE: { label|text|title|name: '中文' } }
 *   OPTIONS const X = [{ label: '中文', value: 'CODE' }, ...]
 *
 * ⚠️ 已知盲区（2026-09-25 实测发现）：**只认"具名常量"**。写在模板/内联处的枚举映射看不出来，例如
 *    subjectAuth/index.vue 里 <Select.Option value="BIDDER">投标人</Select.Option> 与
 *    :options="[{ label: '投标人 BIDDER', value: 'BIDDER' }, …]" —— 这两处硬编码是人工迁移时才发现并收口的。
 *    要覆盖内联形态需要 AST 级识别（按属性名+中文文案判定），误报率与成本都更高，故暂不纳入，
 *    改用约定：**枚举类文案一律收口到具名常量或字典**（收口后本门禁即可见）。
 *
 * 门禁口径（棘轮）：
 *   · 文件不在 KNOWN_HARDCODED 里且命中 → FAIL（新页面必须走字典）；
 *   · 文件在册但命中数**超过**登记数      → FAIL（不许变多）；
 *   · 命中数少于登记数                    → OK，并提示用 --update-baseline 收紧基线。
 *
 * 用法：node sql/tools/check-hardcoded-enums.js [--list] [--update-baseline]
 * 退出码：0 通过；1 违反棘轮。
 */
const fs = require('node:fs');
const path = require('node:path');

const LIST = process.argv.includes('--list');
const UPDATE = process.argv.includes('--update-baseline');
const repoRoot = path.resolve(__dirname, '../../..');
const SRC = path.join(repoRoot, 'txgy-ui-admin-vben/apps/web-antd/src');
/* 扫描根：管理端 bidding 域 + 门户前台 bidding 域。2026-09-25 补门户——此前只扫 views/bidding，
 * 导致门户里同一批枚举（公告状态、内容形态、关注类型、跟踪阶段…）的中文映射**完全逃过门禁**，
 * 而后台同一状态走字典 ⇒ 前后台两套口径却无人拦。 */
const DIRS = [
  path.join(SRC, 'views/bidding'),
  path.join(SRC, 'api/bidding'),
  path.join(SRC, 'views/portal/bidding'),
];
const SKIP_DIRS = new Set(['node_modules', 'dist']);

/**
 * 登记在册的硬编码（棘轮基线）：文件 → [允许处数, 理由]。
 * 只许减少、不许增加；迁移到字典后把处数改小（--update-baseline 可生成新基线）。
 * 说明：这些是"既有欠债"，逐条登记是为了让新页面无处可藏，而不是认可它们的写法。
 */
const KNOWN_HARDCODED = new Map([
  ['txgy-ui-admin-vben/apps/web-antd/src/views/bidding/noticeExternal/data.ts', [7, '状态/审核/内容形态 3 类已于 2026-09-25 改为"字典优先、本表兜底"（本表仍保留：本页用户未必有 /bidding/meta/* 权限，纯字典化会退化成英文码）；行业/抽取/AI/正文结构化 4 类无后端枚举可对齐，仍以本表为主；筛选下拉仍由本表构建（form schema 在 setup 期构建，字典异步加载时会拿到空下拉）']],
  ['txgy-ui-admin-vben/apps/web-antd/src/api/bidding/qualification/index.ts', [6, '资质域选项表（序列/预警来源/人员角色/业绩角色等）——取值来自后端枚举，但该域字典尚未注册；**已逐条对照 QualificationSerialEnum/StaffRole/PerformanceRole/AssetType/ReviewType 确认取值一致**，注册请求见 docs/前端规范-全域接口说明/13-枚举字典注册请求…（A 类：可照抄枚举）']],
  ['txgy-ui-admin-vben/apps/web-antd/src/views/bidding/crawlExternal/shared.ts', [3, '采集任务/解析状态映射；采集域字典未注册。已核对：任务状态=CrawlTaskStatusEnum(5) 一致；**批次状态与触发方式后端无枚举**（B 类：请后端补 CrawlJobStatusEnum/CrawlTriggerTypeEnum）→ 见契约 13']],
  ['txgy-ui-admin-vben/apps/web-antd/src/api/bidding/ruleConfig/index.ts', [3, '规则配置的值类型/状态/命中来源选项；规则配置域字典未注册。已核对：值类型=RuleConfigValueTypeEnum(5) 一致、状态=RuleConfigStatusEnum(4) 而前端**有意只列 3 个可写入目标**（ROLLED_BACK 只能由回滚接口产生，注释在案）、命中来源无枚举（仅 VO 注释）→ 见契约 13（A 类 + B 类）']],
  ['txgy-ui-admin-vben/apps/web-antd/src/views/bidding/certificate/modules/material-check.vue', [2, 'RESULT_FALLBACK / STATUS_FALLBACK —— 命名即口径：字典为主路径、此表仅在字典未下发时兜底，非硬编码主展示']],
  ['txgy-ui-admin-vben/apps/web-antd/src/views/bidding/qualification/index.vue', [2, '同上（兜底表），资质页字典未下发时回落']],
  ['txgy-ui-admin-vben/apps/web-antd/src/views/bidding/processTrack/index.vue', [2, '流转状态/事件文案；投标流程跟踪页由他人开发中（菜单 60004xxx 尚在让位期）']],
  ['txgy-ui-admin-vben/apps/web-antd/src/views/bidding/crawlExternal/console/index.vue', [1, '采集控制台手动作业状态文案；采集域字典未注册。已核对：取值来自 CrawlParseTestRespVO.status 注释（SUCCESS/PARTIAL/FAILED），无枚举（B 类）→ 见契约 13']],
  ['txgy-ui-admin-vben/apps/web-antd/src/views/bidding/notice/NoticeHtmlView.vue', [1, '公告状态文案（管理端与门户共用渲染件）；notice 状态字典未注册。已核对与 NoticeStatusEnum 一致 → 见契约 13（A 类）']],
  ['txgy-ui-admin-vben/apps/web-antd/src/views/bidding/notice/payloadLabel.ts', [1, '公告 payload/正文块类型文案（56 项），属渲染件内部标签、**非后端枚举值域**（渲染件需自带可读名）→ 不需注册字典']],
  ['txgy-ui-admin-vben/apps/web-antd/src/views/bidding/safety-risk/index.vue', [1, 'KEY_POST_OPTIONS：key_post 字典已注册，本页已改为字典优先；此数组仅作字典未下发环境的兜底（与备案状态下拉同一口径）']],
  ['txgy-ui-admin-vben/apps/web-antd/src/views/bidding/subjectAuth/index.vue', [1, 'SUBJECT_TYPE_LABEL 已于 2026-09-25 降级为兜底（展示与选项均字典优先）；另有 reasonLabel 授权原因文案待该域字典']],
  ['txgy-ui-admin-vben/apps/web-antd/src/api/bidding/filing/index.ts', [1, 'FILING_STATUS_OPTIONS：filing_status 字典本轮已注册，项目页已改为字典优先，此数组仅作未升级环境兜底；可删除']],
  ['txgy-ui-admin-vben/apps/web-antd/src/api/bidding/notice/index.ts', [1, 'NOTICE_STATUS_LABEL 映射，notice 状态字典未注册']],
  ['txgy-ui-admin-vben/apps/web-antd/src/api/bidding/subject/index.ts', [1, 'SUBJECT_TYPE_LABEL：subject_type 字典本轮已注册，可迁移']],

  /* ===== 门户前台（views/portal/bidding）——2026-09-25 纳入扫描后实测登记的欠债 =====
   * 纳入原因：与后台**同一批枚举**（公告状态/内容形态/公告类型/关注类型/跟踪阶段），后台走字典、门户写死中文，
   * 字典改名后前后台展示会不一致；此前只扫 views/bidding，这些映射无处可拦。 */
  ['txgy-ui-admin-vben/apps/web-antd/src/views/portal/bidding/project/notices.vue', [1, '公告状态文案（DRAFT/PUBLISHED/UNPUBLISHED/ARCHIVED）：与后台 NoticeHtmlView 同一缺口——notice_status 字典未注册 → 见契约 13（A 类可照抄 NoticeStatusEnum）；注册后前台一并迁移']],
  ['txgy-ui-admin-vben/apps/web-antd/src/views/portal/bidding/index.vue', [2, 'CONTENT_TYPES（内容形态，与后台同值域，待 notice 域字典注册后迁移）+ INDUSTRIES（行业，后端无枚举可对齐，口径同 noticeExternal/data.ts 的 INDUSTRY_MAP）']],
  ['txgy-ui-admin-vben/apps/web-antd/src/views/portal/bidding/internalHall.vue', [1, 'TYPE_FILTERS（4 类公告类型）：**可迁移**——后端已下发类型元数据 GET /bidding/notice/meta/types（NoticeTypeMetaRespVO），应改为按元数据构建筛选，勿再写死']],
  ['txgy-ui-admin-vben/apps/web-antd/src/views/portal/bidding/format.ts', [4, '关注/订阅域展示文案：关注对象类型(FOLLOW_TYPE)、项目跟踪阶段(STAGE)、动态事件类型、阶段名兜底——该域字典未注册（取值来自 NoticeFollowController 的 targetType/stage 注释）→ 契约 13 追加 B 类；**且门户含免登页面（公告大厅/详情），即便注册字典也需后端提供匿名可读的公开字典面**，否则前台拿不到']],
  ['txgy-ui-admin-vben/apps/web-antd/src/views/portal/bidding/subscription.vue', [1, '关注类型筛选项：同上（follow_type 字典缺失）']],
  ['txgy-ui-admin-vben/apps/web-antd/src/views/portal/bidding/mock.ts', [1, 'INTERNAL_TYPE_LABEL：**演示数据**专用（仅 VITE_BIDDING_USE_MOCK=1 启用），不参与线上口径 → 永久豁免']],
]);

function walk(dir, out) {
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

const CN = '[\\u4e00-\\u9fa5]';
const RE_MAP = /(?:export\s+)?const\s+(\w+)\s*(?::[^=]+)?=\s*\{([\s\S]{0,2000}?)\n\s*\}/g;
const RE_OPTIONS = /(?:export\s+)?const\s+(\w+)\s*(?::[^=]+)?=\s*\[([\s\S]{0,3000}?)\n\s*\];/g;
const RE_PAIR = new RegExp("['\"]?([A-Z][A-Z0-9_]{2,})['\"]?\\s*:\\s*(?:\\{[^}]*?(?:label|text|title|name)\\s*:\\s*)?['\"][^'\"]*" + CN + "[^'\"]*['\"]", 'g');
const RE_CODE = /value\s*:\s*['"]([A-Z][A-Z0-9_]{2,})['"]/;
const RE_LABEL = new RegExp("label\\s*:\\s*['\"][^'\"]*" + CN);

function scanFile(file) {
  const src = fs.readFileSync(file, 'utf8');
  const hits = [];
  for (const m of src.matchAll(RE_MAP)) {
    const pairs = [...m[2].matchAll(RE_PAIR)];
    if (pairs.length >= 2) hits.push({ kind: 'MAP', name: m[1], n: pairs.length });
  }
  for (const m of src.matchAll(RE_OPTIONS)) {
    let n = 0;
    for (const chunk of m[2].split('},')) {
      if (RE_CODE.test(chunk) && RE_LABEL.test(chunk)) n += 1;
    }
    if (n >= 2) hits.push({ kind: 'OPTIONS', name: m[1], n });
  }
  return hits;
}

const files = DIRS.flatMap((d) => walk(d, [])).filter((f) => /\.(vue|ts)$/.test(f));
const found = new Map();
for (const f of files) {
  const hits = scanFile(f);
  if (hits.length > 0) found.set(path.relative(repoRoot, f).split(path.sep).join('/'), hits);
}

if (UPDATE) {
  console.log('const KNOWN_HARDCODED = new Map([');
  [...found.entries()]
    .sort((a, b) => b[1].length - a[1].length)
    .forEach(([f, hs]) => console.log("  ['" + f + "', [" + hs.length + ", '待迁移字典']],"));
  console.log(']);');
  process.exit(0);
}

let bad = 0;
let total = 0;
for (const [f, hs] of [...found.entries()].sort()) {
  total += hs.length;
  const known = KNOWN_HARDCODED.get(f);
  const allowed = known ? known[0] : 0;
  const over = hs.length > allowed;
  if (over) bad += 1;
  const mark = over ? (known ? '❌ 变多' : '❌ 新增') : '✅ 在册';
  console.log('  [' + mark + '] ' + f + '  ' + hs.length + ' 处（登记 ' + allowed + '）');
  if (LIST || over) {
    hs.forEach((h) => console.log('        ' + h.kind.padEnd(8) + h.name + '（' + h.n + ' 项）'));
    if (known) console.log('        理由：' + known[1]);
  }
}

console.log('\n扫描 ' + files.length + ' 个源文件；硬编码枚举映射 ' + total + ' 处 / ' + found.size + ' 个文件；违规 ' + bad + ' 个文件');
if (bad > 0) {
  console.error('[FAIL] 不允许新增硬编码枚举映射（硬约束：状态码/枚举值必须支持动态配置）。');
  console.error('修复二选一：① 改用字典（sql/history 注册 dict_type + 页面 dictLabel/dictTag/dictOptions）；');
  console.error('           ② 确属无字典语义（如外部标准码），在 KNOWN_HARDCODED 登记文件与理由。');
  process.exit(1);
}
console.log('[OK] 硬编码枚举映射未超过基线（只减不增）');
