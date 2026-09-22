import type { ReportDatasetApi } from '#/api/report/dataset';

/**
 * 数据集编码生成规则
 *
 * 统一格式：RPT-[业务域]-[业务主题]-[序号]，例如 RPT-MES-FBK-001
 * - 业务域：直接复用「分类（域）」，如 MES / WMS / QMS / EMS
 * - 业务主题：由「业务分组」的中文名映射到固定编码（映射只用于生成编码，不改数据库分组名）
 * - 序号：解析同一「域 + 主题」下已有编码的最大序号 + 1，固定 3 位
 */

/** 分类（域）候选值 */
export const CATEGORY_OPTIONS = ['MES', 'WMS', 'QMS', 'EMS', 'ERP', 'CRM'];

/** 业务主题编码映射：业务分组中文名 → 主题编码 */
const BIZ_TOPIC_CODE_MAP: Record<string, string> = {
  生产: 'PRO',
  工单: 'WO',
  报工: 'FBK',
  品质: 'QC',
  质量: 'QC',
  不良: 'DEF',
  设备: 'EQP',
  物料: 'MAT',
  库存: 'INV',
  出入库: 'INOUT',
};

/** 未命中映射（如用户自建分组）时的兜底主题编码 */
const FALLBACK_TOPIC = 'GEN';

/** 业务分组（中文名）转主题编码；空分组归入 GEN */
function toTopicCode(group?: string): string {
  const key = (group ?? '').trim();
  return BIZ_TOPIC_CODE_MAP[key] ?? FALLBACK_TOPIC;
}

/**
 * 生成数据集编码：RPT-[业务域]-[业务主题]-[序号]
 *
 * 序号通过解析已有编码得到当前「域 + 主题」下的最大序号后 +1，
 * 不使用数组长度，保证「已删除/跳号」不影响正确递增。
 */
export function generateDatasetCode(
  category: string | undefined,
  bizGroups: string[] | undefined,
  existingCodes: string[],
): string {
  const domain = (category ?? '').trim().toUpperCase() || 'MES';
  const topic = toTopicCode((bizGroups ?? [])[0]);
  const prefix = `RPT-${domain}-${topic}-`;

  let max = 0;
  for (const code of existingCodes) {
    if (!code || !code.startsWith(prefix)) {
      continue;
    }
    const matched = /-(\d{3})$/.exec(code);
    if (!matched) {
      continue;
    }
    // parseInt 正确处理 010 -> 10，而不是按字符串长度误判
    const seq = Number.parseInt(matched[1]!, 10);
    if (!Number.isNaN(seq) && seq > max) {
      max = seq;
    }
  }
  return `${prefix}${String(max + 1).padStart(3, '0')}`;
}

/**
 * 从已有数据集列表中生成新的 RPT 编码
 */
export function buildDatasetCode(
  category: string | undefined,
  bizGroups: string[] | undefined,
  datasets: ReportDatasetApi.Dataset[],
): string {
  return generateDatasetCode(
    category,
    bizGroups,
    datasets.map((item) => item.code ?? ''),
  );
}
