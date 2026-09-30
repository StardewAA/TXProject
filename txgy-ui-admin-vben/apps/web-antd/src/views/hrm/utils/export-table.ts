import { downloadFileFromBlobPart } from '@vben/utils';

import * as XLSX from 'xlsx';

/**
 * HRM 通用「按当前筛选条件导出」工具（QA 报告 Q-021 / I-013）
 *
 * <p>高频台账（合同签订、证件、社保、薪资档案、发放记录等）此前没有导出入口，
 * HR 只能翻页抄数据对账。后端这些台账都是分页接口，这里统一按当前筛选条件
 * 循环拉取全部分页数据，在浏览器端生成 xlsx（依赖仓库已有的 xlsx 包，无需新增后端接口）。</p>
 *
 * <p>注意：为避免误点导出把浏览器拖垮，默认最多导出 {@link DEFAULT_MAX_ROWS} 行，
 * 超出部分会在控制台提示，建议先缩小筛选条件。</p>
 */

export interface HrmExportColumn<T> {
  /** 表头（中文） */
  title: string;
  /** 取值函数；返回 null/undefined 时导出为空单元格 */
  value: (row: T) => number | string | undefined;
}

export interface HrmExportOptions<T> {
  /** 每列的取值方式 */
  columns: HrmExportColumn<T>[];
  /** 拉取一页数据（直接返回后端分页 data，即 { list, total }） */
  fetchPage: (
    pageNo: number,
    pageSize: number,
  ) => Promise<{ list: T[]; total: number }>;
  /** 导出文件名（不含扩展名） */
  fileName: string;
  /** 单次请求条数，默认 200 */
  pageSize?: number;
  /** 最多导出行数，默认 10000 */
  maxRows?: number;
}

const DEFAULT_PAGE_SIZE = 200;
const DEFAULT_MAX_ROWS = 10_000;

/**
 * 把已在内存中的行数组导出为 xlsx（用于环比面板这类"数据已一次性取回"的场景）
 */
export function exportRowsToExcel<T>(
  fileName: string,
  columns: HrmExportColumn<T>[],
  rows: T[],
) {
  writeExcel(fileName, columns, rows);
  return rows.length;
}

export async function exportHrmTable<T>(options: HrmExportOptions<T>) {
  const pageSize = options.pageSize ?? DEFAULT_PAGE_SIZE;
  const maxRows = options.maxRows ?? DEFAULT_MAX_ROWS;

  const rows: T[] = [];
  let pageNo = 1;
  // 循环拉取全部分页：pageNo 递增直到取满 total 或达到上限
  // 单页最少取 1 行，避免 pageSize 配置为 0 时死循环
  const safePageSize = Math.max(pageSize, 1);
  for (;;) {
    const page = await options.fetchPage(pageNo, safePageSize);
    const list = page?.list ?? [];
    rows.push(...list);
    const total = Number(page?.total ?? 0);
    if (
      list.length === 0 ||
      rows.length >= total ||
      list.length < safePageSize ||
      rows.length >= maxRows
    ) {
      break;
    }
    pageNo += 1;
  }

  const exportRows = rows.slice(0, maxRows);
  if (rows.length > maxRows) {
    // eslint-disable-next-line no-console
    console.warn(
      `[exportHrmTable] 数据量超过 ${maxRows} 行，仅导出前 ${maxRows} 行，请缩小筛选条件后重试`,
    );
  }

  writeExcel(options.fileName, options.columns, exportRows);
  return exportRows.length;
}

function writeExcel<T>(
  fileName: string,
  columns: HrmExportColumn<T>[],
  rows: T[],
) {
  const header = columns.map((column) => column.title);
  const body = rows.map((row) =>
    columns.map((column) => {
      const value = column.value(row);
      return value === null || value === undefined ? '' : value;
    }),
  );

  const sheet = XLSX.utils.aoa_to_sheet([header, ...body]);
  sheet['!cols'] = columns.map((column) => ({
    wch: Math.max(12, String(column.title).length * 2),
  }));
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, sheet, '数据');
  const buffer = XLSX.write(workbook, {
    bookType: 'xlsx',
    type: 'array',
  }) as ArrayBuffer;

  downloadFileFromBlobPart({
    fileName: `${fileName}.xlsx`,
    source: new Blob([buffer], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    }),
  });
}
