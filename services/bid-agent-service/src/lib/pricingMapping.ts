/**
 * COPY_PRICING 通道：报价数据 → 书签取值（改进版落地方案 · 改动 3 的 Node 侧）。
 *
 * 报价 Excel 由 Java 侧解析为「表名 → 行列表（列名 → 文本值）」的结构化数据（/contract/pricing），
 * 本模块按 templates/bookmark-dict.json 的 table/row/col 把行数据展开为书签名取值：
 *   - 通用计价表（bill/measure/other/service/analysis/material/project_cost/unit_cost）：
 *     行序映射（书签名序号 = 字典该表首行行号 + 数据行号 - 2），列后缀与列名取自字典首行条目；
 *   - daywork：按「组别」（人工/材料/机械）分流到 labor / daywork_material / machine 三组；
 *   - fee：按「费用项」名映射到 fee_<item>_<列后缀>（11 个固定费用项）；
 *   - cost_breakdown：按行序映射 cost_breakdown_item_1..15；
 *   - shared：直接作为书签基础名取值（bid_total / measure_fee / 工期 / 质量标准等）。
 * 并派生确定性合计（表4 合计、表7/9/10 合计、表8 小计总计、表5 合计等，金额求和保留 2 位小数）。
 * 超出模板书签行数的数据（未做行扩展或扩展不够）→ 截断并在返回值中报告，由导出层告警。
 */
import type { BookmarkDict } from './bookmarkFields.js';
import { toChineseAmount } from './valueRules.js';

export interface PricingData {
  shared: Record<string, string>;
  tables: Record<string, Array<Record<string, string>>>;
}

export interface PricingTruncation {
  table: string;
  expected: number;
  available: number;
}

export interface PricingMappingResult {
  values: Record<string, string>;
  truncated: PricingTruncation[];
}

/** 通用计价表（行序映射，书签名序号 = minRow - 1 + 数据行号） */
const GENERIC_TABLES = [
  'bill', 'measure', 'other', 'service', 'analysis', 'material', 'project_cost', 'unit_cost',
] as const;

/** daywork 组别 → 书签前缀 */
const DAYWORK_GROUPS: Record<string, string> = {
  人工: 'labor',
  材料: 'daywork_material',
  机械: 'machine',
};

/** 表10 规费/税金：费用项 → 书签 item 键（顺序即规费合计口径：前 8 项为规费，后 3 项为税金） */
const FEE_ITEMS: Array<[string, string]> = [
  ['养老保险', 'pension'],
  ['失业保险', 'unemployment'],
  ['医疗保险', 'medical'],
  ['工伤保险', 'work_injury'],
  ['残疾人就业保险', 'disability'],
  ['女工生育保险', 'maternity'],
  ['住房公积金', 'housing_fund'],
  ['危险作业意外伤害保险', 'hazard'],
  ['营业税', 'business_tax'],
  ['城市维护建设税', 'city_tax'],
  ['教育附加费', 'education'],
];

/** 表10 列名 → 书签列后缀 */
const FEE_COLUMNS: Record<string, string> = {
  计量单位: 'unit',
  工程数量: 'quantity',
  综合单价: 'unit_price',
  合价: 'total_price',
};

/** 去掉含义列标注（「第 N 行」前缀、组别第 N 项前缀、全部括注、空白），得到列名 */
function headerOf(label: string): string {
  return label
    .replace(/^第\s*\d+\s*行\s*/, '')
    .replace(/^(人工|材料|机械)第\s*\d+\s*项\s*/, '')
    .replace(/[（(][^（）()]*[）)]/g, '')
    .replace(/\s+/g, '');
}

function toFixed2(value: string): string {
  const numeric = Number(value.replace(/,/g, ''));
  return Number.isFinite(numeric) ? numeric.toFixed(2) : '';
}

/** 金额列求和（缺失/非数字单元格跳过；全部无效返回空串） */
function sumColumn(rows: Array<Record<string, string>>, header: string): string {
  let sum = 0;
  let any = false;
  for (const row of rows) {
    const raw = (row[header] ?? '').trim();
    if (!raw) continue; // 缺失列按空跳过，避免 Number('')===0 污染合计
    const numeric = Number(raw.replace(/,/g, ''));
    if (Number.isFinite(numeric)) {
      sum += numeric;
      any = true;
    }
  }
  return any ? sum.toFixed(2) : '';
}

/**
 * 展开报价数据为书签取值表。
 * @param dict 机器可读字典（templates/bookmark-dict.json）
 * @param pricing Java 侧返回的报价数据
 * @param availableNames 模板实际书签集合（用于行数超限截断判定；缺省不做截断判定）
 */
export function buildPricingBookmarkValues(
  dict: BookmarkDict,
  pricing: PricingData,
  availableNames?: ReadonlySet<string>,
): PricingMappingResult {
  const values: Record<string, string> = {};
  const truncated: PricingTruncation[] = [];
  const put = (name: string, value: string | undefined): void => {
    if (value && value.trim()) values[name] = value.trim();
  };

  // 共享变量直接回填（报价为权威：覆盖标准字段通道的同名取值）
  for (const [key, value] of Object.entries(pricing.shared ?? {})) {
    put(key, value);
  }

  // 投标报价大写：pricing 为权威报价来源，大写无独立来源字段，按央行大写规则从 bid_total 换算
  const totalLower = values['bid_total'];
  if (totalLower) {
    const upper = toChineseAmount(totalLower);
    if (upper) put('bid_total_upper', upper);
  }

  const entriesOf = (table: string) =>
    dict.bookmarks.filter((entry) => entry.kind === 'table-cell' && entry.table === table);

  // —— 通用计价表：字典首行条目给出列后缀与列名，数据行按行序展开 ——
  for (const table of GENERIC_TABLES) {
    const rows = pricing.tables?.[table];
    const entries = entriesOf(table);
    if (!rows || rows.length === 0 || entries.length === 0) continue;
    const minRow = Math.min(...entries.map((entry) => entry.row ?? Number.MAX_SAFE_INTEGER));
    if (!Number.isFinite(minRow)) continue;
    const cols: Array<{ suffix: string; header: string }> = [];
    for (const entry of entries) {
      if (entry.row !== minRow) continue;
      const nameIndex = entry.row - 1;
      const suffix = entry.name.replace(new RegExp(`^${table}_${nameIndex}_`), '');
      cols.push({ suffix, header: headerOf(entry.label) });
    }
    // 行序：数据行 k（1 起）→ 书签名序号 minRow + k - 2（bill_2=第3行、material_1=第2行、analysis_3=第4行 均成立）
    let mappedCount = 0;
    for (let k = 0; k < rows.length; k += 1) {
      const nameIndex = minRow + k - 1;
      for (const col of cols) {
        const name = `${table}_${nameIndex}_${col.suffix}`;
        if (availableNames && !availableNames.has(name)) {
          if (truncated.every((item) => item.table !== table)) {
            truncated.push({ table, expected: rows.length, available: k });
          }
          break;
        }
        put(name, rows[k]![col.header]);
      }
      if (truncated.every((item) => item.table !== table)) mappedCount = k + 1;
    }
    // 截断时合计只按已映射行计算（表内数字与合计必须自洽，避免数字打架触发废标红线）
    const effectiveRows = rows.slice(0, mappedCount);
    if (table === 'bill') {
      put('bill_total_price', sumColumn(effectiveRows, '合价'));
      put('bill_total_unit_price', sumColumn(effectiveRows, '综合单价'));
    }
    if (table === 'measure') put('measure_total_price', sumColumn(effectiveRows, '合价'));
    if (table === 'other') put('other_total_price', sumColumn(effectiveRows, '合价'));
    if (table === 'service') put('service_total', sumColumn(effectiveRows, '合价'));
    if (table === 'unit_cost') put('unit_cost_total', sumColumn(effectiveRows, '造价'));
  }

  // —— 表8 计日工：按组别分流 ——
  {
    const rows = pricing.tables?.['daywork'] ?? [];
    const byGroup: Record<string, Array<Record<string, string>>> = {};
    for (const row of rows) {
      const group = DAYWORK_GROUPS[row['组别'] ?? ''];
      if (!group) continue;
      (byGroup[group] ??= []).push(row);
    }
    for (const [group, groupRows] of Object.entries(byGroup)) {
      const entries = entriesOf(group);
      // 组内序号在书签名里（labor_0_name → 序号 0），列后缀与列名取自组内第 0 项条目
      const cols: Array<{ suffix: string; header: string }> = [];
      for (const entry of entries) {
        const m = /_(\d+)_([a-z_]+)$/.exec(entry.name);
        if (!m || Number(m[1]) !== 0) continue;
        cols.push({ suffix: m[2]!, header: headerOf(entry.label) });
      }
      groupRows.forEach((row, index) => {
        for (const col of cols) {
          put(`${group}_${index}_${col.suffix}`, row[col.header]);
        }
      });
      put(`${group}_subtotal`, sumColumn(groupRows, '合价'));
    }
    // 总计 = 三组合价合计（未填写组别行跳过；三组都在才写，避免半截数据）
    const all = rows.filter((row) => DAYWORK_GROUPS[row['组别'] ?? '']);
    put('daywork_total', sumColumn(all, '合价'));
  }

  // —— 表10 规费/税金：费用项 → fee_<item>_<列后缀> ——
  {
    const rows = pricing.tables?.['fee'] ?? [];
    const byItem = new Map<string, Record<string, string>>();
    for (const row of rows) {
      byItem.set(row['费用项'] ?? '', row);
    }
    for (const [label, key] of FEE_ITEMS) {
      const row = byItem.get(label);
      if (!row) continue;
      for (const [header, suffix] of Object.entries(FEE_COLUMNS)) {
        put(`fee_${key}_${suffix}`, row[header]);
      }
    }
    const regulation = FEE_ITEMS.slice(0, 8)
      .map(([label]) => byItem.get(label))
      .filter((row): row is Record<string, string> => Boolean(row));
    put('fee_regulation_total', sumColumn(regulation, '合价'));
    const tax = FEE_ITEMS.slice(8)
      .map(([label]) => byItem.get(label))
      .filter((row): row is Record<string, string> => Boolean(row));
    put('fee_tax_total', sumColumn(tax, '合价'));
  }

  // —— 表4 单位工程造价汇总：行序 → cost_breakdown_item_1..15，合计为金额求和 ——
  {
    const rows = pricing.tables?.['cost_breakdown'] ?? [];
    rows.slice(0, 15).forEach((row, index) => {
      put(`cost_breakdown_item_${index + 1}`, row['金额'] ?? row['金额(元)'] ?? row['金额（元）']);
    });
    put('cost_breakdown_total', sumColumn(rows, '金额') || sumColumn(rows, '金额(元)') || sumColumn(rows, '金额（元）'));
  }

  return { values, truncated };
}
