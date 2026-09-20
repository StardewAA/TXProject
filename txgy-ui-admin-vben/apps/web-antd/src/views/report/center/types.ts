/**
 * 报表中心 - 共享类型
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
