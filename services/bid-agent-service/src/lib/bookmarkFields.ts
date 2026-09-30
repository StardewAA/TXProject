/**
 * 书签模板字段映射：把导出阶段的「标准字段取值」（中文名，来自事实卡/招标解析，
 * 见 templateFields.resolveFieldValues）映射为书签基础变量名 → 取值。
 *
 * 映射的单一维护入口是 tools/gen-bookmark-dict.mjs：它解析 templates/投标标书_变量字典.md
 * 生成 templates/bookmark-dict.json（含每条书签的 stdNames 标准字段映射）。
 * 本文件运行期只读 JSON（标量部分），不再硬编码 1334 条映射；
 * 下方 BOOKMARK_STD_MAP 仅作为字典缺失/损坏时的兜底降级，以及旧版模板（quote_* 等）的兼容补充。
 *
 * 日期类书签（date_year/month/day、established_year/month/day）由完整日期拆分生成，
 * 取值为完整中文日期时同步生成 full_date（整段日期，回填引擎会吃掉「年月日」骨架）。
 */
import fs from 'node:fs';
import path from 'node:path';
import { SERVICE_ROOT } from '../config.js';

export interface BookmarkDictEntry {
  name: string;
  label: string;
  chapter: string;
  kind: 'scalar' | 'table-cell';
  source: string;
  table?: string;
  row?: number;
  col?: number | null;
  note?: string;
  /** 该书签对应的事实卡/招标解析标准字段名（中文名精确匹配）；仅 scalar 且走标准字段通道的条目有 */
  stdNames?: string[];
}

export interface BookmarkDict {
  version: string;
  template: string;
  bookmarks: BookmarkDictEntry[];
}

const BOOKMARK_STD_MAP: Record<string, string[]> = {
  // —— 封面 / 投标函 ——
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
  company_registered_address: ['注册地址', '地址'],
  company_registered_capital: ['注册资金', '注册资本'],
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

/** 去掉尾部 _数字 / _appendix 后缀：bidder_name_2 → bidder_name（与回填引擎 stripVariantSuffix 一致） */
function stripVariantSuffix(name: string): string {
  return name.replace(/_\d+$/, '').replace(/_appendix$/, '');
}

const DEFAULT_DICT_PATH = path.join(SERVICE_ROOT, 'templates', 'bookmark-dict.json');

let dictCache: BookmarkDict | null | undefined;

/**
 * 加载机器可读字典（templates/bookmark-dict.json，由 tools/gen-bookmark-dict.mjs 生成）。
 * 失败返回 undefined（调用方降级为内置硬编码映射）；结果缓存，进程内只读一次。
 */
export function loadBookmarkDict(dictPath?: string): BookmarkDict | undefined {
  if (dictCache !== undefined) return dictCache ?? undefined;
  const file = dictPath ?? process.env.BID_BOOKMARK_DICT ?? DEFAULT_DICT_PATH;
  try {
    const parsed = JSON.parse(fs.readFileSync(file, 'utf8')) as BookmarkDict;
    if (
      typeof parsed !== 'object'
      || parsed === null
      || !Array.isArray(parsed.bookmarks)
      || parsed.bookmarks.some((entry) => typeof entry.name !== 'string')
    ) {
      dictCache = null;
      return undefined;
    }
    dictCache = parsed;
    return parsed;
  } catch {
    dictCache = null;
    return undefined;
  }
}

/** 由字典生成标准字段映射表：scalar 条目按基础变量名归并（变体 bidder_name_2 归入 bidder_name；
 * 仅在去后缀后的基础名本身也是字典条目时才归并，行号命名 cost_breakdown_item_1 不受影响） */
function stdMapFromDict(dict: BookmarkDict): Record<string, string[]> {
  const map: Record<string, string[]> = {};
  const dictNames = new Set(dict.bookmarks.map((entry) => entry.name));
  for (const entry of dict.bookmarks) {
    if (entry.kind !== 'scalar' || !entry.stdNames || entry.stdNames.length === 0) continue;
    const stripped = stripVariantSuffix(entry.name);
    const base = stripped !== entry.name && dictNames.has(stripped) ? stripped : entry.name;
    if (!map[base]) map[base] = [...entry.stdNames];
  }
  return map;
}

interface DateParts {
  year: string;
  month: string;
  day: string;
  full: string;
}

/** 拆分完整日期（中文或 YYYY-MM-DD 形式）为年月日分量 */
function splitDateParts(value: string): DateParts | undefined {
  const cn = /(\d{4})\s*年\s*(\d{1,2})\s*月\s*(\d{1,2})\s*日?/.exec(value);
  if (cn) return { year: cn[1]!, month: cn[2]!, day: cn[3]!, full: `${cn[1]}年${cn[2]}月${cn[3]}日` };
  const en = /(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})/.exec(value);
  if (en) return { year: en[1]!, month: en[2]!, day: en[3]!, full: `${en[1]}年${en[2]}月${en[3]}日` };
  return undefined;
}

/**
 * 由标准字段取值生成书签取值表。
 * 基础变量名会由回填引擎自动回填同名 _N 变体（bidder_name_2、date_year_2 …），无需展开。
 */
export function buildBookmarkValues(stdValues: Map<string, string>): Record<string, string> {
  const out: Record<string, string> = {};
  const put = (key: string, value: string | undefined): void => {
    if (value && value.trim()) out[key] = value.trim();
  };

  // 字典可用时以字典为准（硬编码表作为旧模板 quote_* 等条目的兼容补充）
  const dict = loadBookmarkDict();
  const stdMap = dict ? { ...BOOKMARK_STD_MAP, ...stdMapFromDict(dict) } : BOOKMARK_STD_MAP;

  for (const [bookmark, stdNames] of Object.entries(stdMap)) {
    const hit = stdNames.map((name) => stdValues.get(name)).find((v) => v && v.trim());
    if (hit) put(bookmark, hit);
  }

  // 成立时间 → established_year/month/day（法定代表人身份证明）与基本情况表整段日期
  const established = stdValues.get('成立时间');
  const estParts = established ? splitDateParts(established) : undefined;
  if (estParts) {
    put('established_year', estParts.year);
    put('established_month', estParts.month);
    put('established_day', estParts.day);
    put('company_established_date', estParts.full);
  }

  // 签署日期 → date_year/month/day（封面/落款）与 full_date（整段日期，回填时吃掉年月日骨架）
  const signed = stdValues.get('签署日期');
  const signParts = signed ? splitDateParts(signed) : undefined;
  if (signParts) {
    put('date_year', signParts.year);
    put('date_month', signParts.month);
    put('date_day', signParts.day);
    put('full_date', signParts.full);
  }

  return out;
}
