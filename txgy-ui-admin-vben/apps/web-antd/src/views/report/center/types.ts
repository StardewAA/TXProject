import type { ReportCenterApi } from '#/api/report/center';

/**
 * 报表中心（生产执行报表）- 共享类型
 */

/** 每张报表独立的查询状态（切走再切回不丢失） */
export interface CardState {
  /** 是否正在查询 */
  loading: boolean;
  /** 中文表头 */
  headers: string[];
  /** 行数据（按表头顺序） */
  rows: any[][];
  /** 表格列定义 */
  columns: any[];
  /** 当前页码 */
  page: number;
  /** 每页条数，<=0 表示全部 */
  pageSize: number;
  /** 表格区是否收起 */
  collapsed: boolean;
  /** 开始日期 */
  startDate?: string;
  /** 结束日期 */
  endDate?: string;
}

/**
 * 「今日生产概况」统计卡片
 *
 * 数据全部来自真实接口（MES 首页统计 /mes/home-statistics/*），
 * 不再使用任何前端 mock。取不到数据的指标 value 为 null，由页面展示占位符。
 */
export interface OverviewStat {
  /** 唯一标识 */
  key: string;
  /** 指标名称，如「生产计划」 */
  label: string;
  /** 主数值（已格式化）；null 表示当前无真实数据来源 */
  value: null | string;
  /** 单位，如「件」「%」 */
  unit: string;
  /** 图标（iconify 名称），如 'lucide:clipboard-list' */
  icon: string;
  /** 图标主色 */
  iconColor: string;
  /** 图标底色 */
  iconBg: string;
  /** 环比文案，如「较昨日 ↑5%」；null 表示无法计算（如昨日为 0 或缺失） */
  trend: null | string;
  /** 环比是否为上升（决定趋势文字颜色） */
  trendUp: boolean;
  /** 数据口径说明（鼠标悬浮提示），用于说明指标真实来源 */
  hint: string;
  /** 该指标是否缺少真实数据来源（缺接口时置 true，展示「暂无数据」） */
  missing?: boolean;
}

/** 报表卡片项：直接持有真实报表清单中的一项 */
export interface ReportCardItem {
  report: ReportCenterApi.Report;
}
