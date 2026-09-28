/**
 * 变量字典 → 机器可读字典（改进版落地方案 · 改动 2）：
 *   解析 templates/投标标书_变量字典.md（按 ### 2.x 小节切分、提取 | `变量名` | 含义 | 表格行），
 *   生成 templates/bookmark-dict.json（书签 → 含义/章节/形态/数据来源/表格坐标/标准字段映射），
 *   并输出「字典 vs 模板实际书签」差集报告 templates/bookmark-dict-report.md。
 *
 * 用法：node tools/gen-bookmark-dict.mjs [字典.md] [模板.docx]
 * 缺省：templates/投标标书_变量字典.md + templates/投标标书_主模板.docx
 *
 * 该脚本是字典的唯一维护入口：标准字段映射（stdNames）在此集中维护，
 * 运行时 bookmarkFields.ts 只读生成的 JSON，不再硬编码 1334 条映射。
 */
import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';
import { listBookmarkNames } from '../dist/lib/bookmarkFill.js';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const dictPath = path.resolve(root, process.argv[2] ?? 'templates/投标标书_变量字典.md');
const templatePath = path.resolve(root, process.argv[3] ?? 'templates/投标标书_主模板.docx');
const outJson = path.join(root, 'templates', 'bookmark-dict.json');
const outReport = path.join(root, 'templates', 'bookmark-dict-report.md');

/** 去掉尾部 _数字 / _appendix 后缀：bidder_name_2 → bidder_name（与回填引擎 stripVariantSuffix 一致） */
function baseName(name) {
  return name.replace(/_\d+$/, '').replace(/_appendix$/, '');
}

/** 计价表前缀 → 数据来源 PRICING（报价数据，需改动 3 通道导入） */
const PRICING_PREFIXES = new Set([
  'analysis', 'material', 'measure', 'other', 'service', 'bill', 'project_cost',
  'unit_cost', 'cost_breakdown', 'fee', 'daywork_material', 'daywork', 'machine', 'labor', 'quote',
]);
/** 人员台账前缀 → STAFF（P3 通道） */
const STAFF_PREFIXES = new Set(['staff', 'person']);
/** 企业事实前缀 → FACT（企业资料通道） */
const FACT_PREFIXES = new Set([
  'company', 'legal', 'agent', 'bidder', 'established', 'business',
  'address', 'phone', 'fax', 'postal', 'company_type',
]);

/** 数据来源覆盖：默认按前缀归类，这里补充共享变量/派生变量的精确来源 */
const SOURCE_OVERRIDES = {
  bid_no: 'TENDER|FACT',
  section_no: 'TENDER',
  tenderer: 'TENDER',
  project_name: 'TENDER|FACT',
  bidder_name: 'FACT',
  bid_total: 'TENDER|PRICING',
  bid_total_upper: 'DERIVE',
  measure_fee: 'PRICING|FACT',
  measure_fee_rmb: 'DERIVE',
  safety_civil_fee: 'PRICING|FACT',
  safety_civil_fee_rmb: 'DERIVE',
  date_year: 'DERIVE|TENDER',
  date_month: 'DERIVE|TENDER',
  date_day: 'DERIVE|TENDER',
  full_date: 'DERIVE',
  established_year: 'DERIVE|FACT',
  established_month: 'DERIVE|FACT',
  established_day: 'DERIVE|FACT',
  company_established_date: 'DERIVE|FACT',
  legal_rep_or_agent: 'MANUAL',
  cost_compiler: 'MANUAL',
  supplementary_notes: 'MANUAL',
  authorization_days: 'MANUAL',
  appendix_clause_no: 'TENDER|GENERATE',
  appendix_extra: 'MANUAL',
  specialty: 'TENDER|PRICING',
  project_manager_name: 'FACT',
  quote_bid_total: 'PRICING',
  quote_duration_days: 'TENDER',
  quote_measure_fee: 'PRICING',
  quote_safety_fee: 'PRICING',
  quote_quality: 'TENDER',
};

/** 标准字段映射（书签基础名 → 事实卡/招标解析标准字段名，中文名精确匹配，见 templateFields.resolveFieldValues） */
const STD_OVERRIDES = {
  // —— 封面 / 投标函 / 报价表签署 ——
  bid_no: ['招标编号'],
  section_no: ['标段号'],
  bidder_name: ['投标人名称'],
  legal_rep_or_agent: ['委托代理人姓名', '法定代表人'],
  tenderer: ['招标人'],
  project_name: ['工程名称'],
  bid_total_upper: ['投标报价大写'],
  bid_total: ['投标报价小写'],
  measure_fee: ['措施项目费'],
  measure_fee_rmb: ['措施项目费'],
  safety_civil_fee: ['安全文明施工措施费'],
  safety_civil_fee_rmb: ['安全文明施工措施费'],
  quality_standard: ['质量标准'],
  duration_days: ['工期天数'],
  performance_bond_wan: ['履约担保金额'],
  bid_bond_amount: ['投标保证金金额'],
  bid_bond_type: ['投标担保类型'],
  project_manager_name: ['项目经理'],
  supplementary_notes: ['补充说明'],
  // —— 投标函附录 ——
  late_penalty_per_day: ['误期赔偿限额'],
  quality_penalty_max: ['质量违约金限额'],
  // —— 法定代表人身份证明 ——
  address: ['地址'],
  phone: ['电话'],
  fax: ['传真'],
  postal_code: ['邮政编码'],
  company_type: ['单位性质'],
  business_term: ['经营期限'],
  legal_rep_name: ['法定代表人'],
  legal_rep_gender: ['法定代表人性别'],
  legal_rep_age: ['法定代表人年龄'],
  legal_rep_position: ['法定代表人职务'],
  // —— 授权委托书 ——
  agent_name: ['委托代理人姓名'],
  authorization_days: ['委托期限'],
  legal_rep_id: ['法定代表人身份证号'],
  agent_id: ['委托代理人身份证号'],
  // —— 报价表签署 ——
  cost_compiler: ['造价编制人'],
  // —— 报价汇总引用（quote_*，仅旧版主文件使用；合并模板已统一为共享书签）——
  quote_bid_total: ['投标报价小写'],
  quote_duration_days: ['工期天数'],
  quote_measure_fee: ['措施项目费'],
  quote_safety_fee: ['安全文明施工措施费'],
  quote_quality: ['质量标准'],
  // —— 投标人基本情况表（company_*）——
  company_bidder_name: ['投标人名称'],
  company_registered_address: ['注册地址'],
  company_registered_capital: ['注册资金'],
  company_legal_rep_name: ['法定代表人'],
  company_phone: ['电话'],
  company_fax: ['传真'],
  company_postal_code: ['邮政编码'],
  company_contact_name: ['联系人'],
  company_website: ['网址'],
  company_organization_structure: ['组织结构'],
  company_legal_rep_title: ['法定代表人技术职称'],
  company_legal_rep_phone: ['法定代表人电话'],
  company_technical_leader_name: ['技术负责人姓名'],
  company_technical_leader_title: ['技术负责人技术职称'],
  company_technical_leader_phone: ['技术负责人电话'],
  company_qualification_level: ['企业资质等级'],
  company_project_manager_count: ['项目经理人数'],
  company_business_license_no: ['营业执照号'],
  company_senior_title_count: ['高级职称人员数'],
  company_middle_title_count: ['中级职称人员数'],
  company_junior_title_count: ['初级职称人员数'],
  company_bank_name: ['开户银行'],
  company_bank_account: ['账号'],
  company_skilled_worker_count: ['技工人数'],
  company_business_scope_remark: ['经营范围备注'],
};

/** 已知表前缀（按长度降序，取最长匹配为 table 标识） */
const TABLE_PREFIXES = [
  'daywork_material', 'project_cost', 'unit_cost', 'cost_breakdown', 'person_work',
  'bill', 'measure', 'other', 'service', 'fee', 'analysis', 'material', 'labor',
  'machine', 'daywork', 'staff', 'person', 'appendix',
];

/** 由变量名推导所属表标识（table 字段，供 P2 报价通道按表路由） */
function tableOf(name) {
  const base = baseName(name);
  const rowMatch = /^(.+?)_\d+_/.exec(base);
  if (rowMatch) return rowMatch[1];
  for (const prefix of TABLE_PREFIXES) {
    if (base.startsWith(prefix)) return prefix;
  }
  return undefined;
}
const SHARED_IN_TABLE1 = new Set([
  'bid_total', 'measure_fee', 'safety_civil_fee', 'duration_days', 'quality_standard',
  'project_name', 'bidder_name', 'legal_rep_or_agent',
]);

function classifySource(name) {
  const base = baseName(name);
  if (SOURCE_OVERRIDES[base]) return SOURCE_OVERRIDES[base];
  if (/^appendix_extra_\d/.test(base)) return 'MANUAL';
  const longestMatch = (list) => [...list].sort((a, b) => b.length - a.length).find((p) => base.startsWith(p));
  if (longestMatch(PRICING_PREFIXES)) return 'PRICING';
  if (longestMatch(STAFF_PREFIXES)) return 'STAFF';
  if (longestMatch(FACT_PREFIXES)) return 'FACT';
  return 'TENDER';
}

/** 去掉含义列里的括注（落款/签字处/第 N 行/表格坐标等），保留主干 */
function stripAnnotation(label) {
  return label
    .replace(/[（(][^（）()]*[）)]\s*$/, '')
    .replace(/[（(][^（）()]*[）)]/g, '')
    .replace(/\s+/g, '')
    .trim();
}

/** 从含义列提取表格坐标：表格第 N 行第 M 列 / （第 N 行） / （第 N 条） */
function parseCoords(label) {
  const full = /表格第\s*(\d+)\s*行(?:第\s*(\d+)\s*列)?/.exec(label);
  if (full) return { row: Number(full[1]), col: full[2] ? Number(full[2]) : null };
  const rowOnly = /（第\s*(\d+)\s*行）/.exec(label);
  if (rowOnly) return { row: Number(rowOnly[1]), col: null };
  const clauseRow = /（第\s*(\d+)\s*条）/.exec(label);
  if (clauseRow) return { row: Number(clauseRow[1]), col: null };
  return null;
}

function noteFor(name, chapter) {
  const base = baseName(name);
  if (SHARED_IN_TABLE1.has(base)) {
    return '与一、五共用同名书签，按基础名统一回填一次即可';
  }
  if (['service_2_name', 'service_2_no', 'service_3_name', 'service_3_no'].includes(base)) {
    return '原件预填固定文案（发包人发包专业工程管理服务费 / 发包人供应材料、设备保管费），回填时写回等值文案或按实际覆盖';
  }
  if (/^cost_breakdown_item_(1[0-5]|[6-9])$/.test(base)) return '备用行';
  if (base.startsWith('daywork_material')) {
    return '表8 材料组已由 material_* 改名 daywork_material_*（2026-09-22），避免与表12 主要材料价格表冲突';
  }
  return undefined;
}

/** 解析合并字典：返回书签条目数组（按文档出现顺序，同名去重保留首次出现） */
function parseDict(md) {
  const entries = [];
  const seen = new Set();
  let duplicateCount = 0;
  const lines = md.split(/\r?\n/);
  let chapter = '';
  let inSections = false;
  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i];
    if (/^##\s+[^#]/.test(line)) {
      // 顶层大节（如「## 3. 共享变量说明」）不再属于 2.x 章节变量清单
      inSections = false;
      chapter = '';
      continue;
    }
    const sec = /^###\s+2\.\d+\s+(.+)$/.exec(line.trim());
    if (sec) {
      inSections = true;
      chapter = sec[1]
        .replace(/[\s—–]*\d+\s*个书签\s*$/, '')
        .replace(/[\s—–]*无变量\s*$/, '')
        .replace(/[（(][^（）()]*[）)]\s*$/, '')
        .trim();
      continue;
    }
    if (!inSections) continue;
    const row = /^\|\s*`([a-z][a-z0-9_]*)`\s*\|\s*(.+?)\s*\|$/.exec(line);
    if (!row) continue;
    const name = row[1];
    if (seen.has(name)) {
      duplicateCount += 1;
      continue;
    }
    seen.add(name);
    const label = row[2].trim();
    const coords = parseCoords(label);
    const numberedRowName = /^[a-z]+_\d+_[a-z_]+$/.test(name);
    const kind = coords || numberedRowName ? 'table-cell' : 'scalar';
    entries.push({
      name,
      label,
      chapter,
      kind,
      source: classifySource(name),
      table: kind === 'table-cell' ? tableOf(name) : undefined,
      row: coords ? coords.row : undefined,
      col: coords ? coords.col : undefined,
      note: noteFor(name, chapter),
    });
  }
  return { entries, duplicateCount };
}

function main() {
  const md = fs.readFileSync(dictPath, 'utf8');
  const template = fs.readFileSync(templatePath);
  const { entries, duplicateCount } = parseDict(md);

  // stdNames：基础名覆盖表 + 变体继承（bidder_name_2 等按基础名取映射）
  const withStdNames = entries.map((entry) => {
    const std = STD_OVERRIDES[baseName(entry.name)];
    return std ? { ...entry, stdNames: std } : entry;
  });

  const json = {
    version: '2026-09-22',
    template: path.basename(templatePath),
    bookmarks: withStdNames,
  };
  fs.writeFileSync(outJson, `${JSON.stringify(json, null, 2)}\n`);

  // —— 差集报告 ——
  const templateNames = listBookmarkNames(template);
  const templateSet = new Set(templateNames);
  const dictSet = new Set(entries.map((e) => e.name));
  const templateOnly = templateNames.filter((n) => !dictSet.has(n));
  const dictOnly = entries.filter((e) => !templateSet.has(e.name));
  // 基础变量归并：仅在去后缀后的基础名本身也是字典条目时归并（bidder_name_2 → bidder_name），
  // 行号命名（cost_breakdown_item_1、appendix_clause_no_1）保持独立基础名
  const collapseBase = (name) => {
    const stripped = baseName(name);
    return stripped !== name && dictSet.has(stripped) ? stripped : name;
  };
  const baseSet = new Set(entries.map((e) => collapseBase(e.name)));
  const scalars = entries.filter((e) => e.kind === 'scalar');
  const noStd = scalars.filter((e, idx) => {
    if (STD_OVERRIDES[baseName(e.name)]) return false;
    // 按基础名去重（date_year / date_year_2 等同名变体只列一次）
    const base = baseName(e.name);
    return !scalars.slice(0, idx).some((prev) => baseName(prev.name) === base);
  });
  const bySource = {};
  for (const entry of entries) {
    const key = entry.source.split('|')[0];
    bySource[key] = (bySource[key] ?? 0) + 1;
  }
  const byChapter = {};
  for (const entry of entries) {
    byChapter[entry.chapter] = (byChapter[entry.chapter] ?? 0) + 1;
  }

  const lines = [
    '# bookmark-dict.json 生成报告',
    '',
    `- 生成时间：${new Date().toISOString()}`,
    `- 字典：${path.basename(dictPath)}（${entries.length} 个唯一变量名，另有 ${duplicateCount} 处共享变量重复出现）`,
    `- 模板：${path.basename(templatePath)}（${templateNames.length} 个唯一书签）`,
    `- 基础变量（去 _N/_appendix 变体）：${baseSet.size} 个`,
    `- 形态：scalar ${scalars.length} 个 / table-cell ${entries.length - scalars.length} 个`,
    `- 数据来源：${Object.entries(bySource).map(([k, v]) => `${k} ${v}`).join('，')}`,
    '',
    '## 章节分布',
    '',
    '| 章节 | 变量数 |',
    '| --- | ---: |',
    ...Object.entries(byChapter).map(([ch, count]) => `| ${ch} | ${count} |`),
    '',
    `## 差集（字典 vs 模板书签）`,
    '',
    `- 模板有、字典没有（${templateOnly.length} 个）：${templateOnly.length ? templateOnly.join('、') : '无'}`,
    `- 字典有、模板没有（${dictOnly.length} 个）：${dictOnly.length ? dictOnly.map((e) => e.name).join('、') : '无'}`,
    '',
    `## 无标准字段映射的标量书签（${noStd.length} 个）`,
    '',
    '这些书签没有与事实卡/招标解析标准字段建立映射：MANUAL 手工项、GENERATE 生成项、STAFF 人员台账（P3 通道）、PRICING 报价数据（P2 通道），或 date/established 拆分与大写金额等 DERIVE 派生变量（由 buildBookmarkValues 特殊处理，无需映射）：',
    '',
    '| 书签 | 含义 | 数据来源 |',
    '| --- | --- | --- |',
    ...noStd.map((e) => `| \`${e.name}\` | ${e.label} | ${e.source} |`),
    '',
  ];
  fs.writeFileSync(outReport, lines.join('\n'));

  console.log(`字典条目：${entries.length}；基础变量：${baseSet.size}；scalar：${scalars.length}；table-cell：${entries.length - scalars.length}`);
  console.log(`模板书签：${templateNames.length} 个；字典差集 → 模板独有 ${templateOnly.length} 个 / 字典独有 ${dictOnly.length} 个`);
  console.log(`无 stdNames 标量：${noStd.length} 个`);
  console.log(`已生成：${path.relative(root, outJson)}、${path.relative(root, outReport)}`);
  if (templateOnly.length > 0) {
    console.log(`注意：模板中存在字典未覆盖的书签：${templateOnly.join('、')}`);
    process.exitCode = 2;
  }
}

main();
