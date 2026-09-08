import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { fmtDt, TASK_STATUS_OPTIONS } from '../shared';

/** 顶部检索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    { fieldName: 'jobId', label: '批次 ID', component: 'Input', componentProps: { placeholder: '精确匹配', allowClear: true } },
    { fieldName: 'sourceCode', label: '源 code', component: 'Input', componentProps: { placeholder: '精确匹配', allowClear: true } },
    { fieldName: 'status', label: '任务状态', component: 'Select', componentProps: { options: TASK_STATUS_OPTIONS, allowClear: true, placeholder: '全部' } },
    { fieldName: 'url', label: 'URL 检索', component: 'Input', componentProps: { placeholder: '定位死信/重试对象', allowClear: true } },
  ];
}

/** 表格列 */
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    { field: 'id', title: '任务 ID', width: 90 },
    { field: 'jobId', title: '批次', width: 80 },
    { field: 'sourceCode', title: '源 code', width: 110, showOverflow: true },
    { field: 'url', title: '待抓 URL', minWidth: 260, showOverflow: true },
    { field: 'status', title: '状态', width: 130, slots: { default: 'col_status' } },
    { field: 'attempt', title: '尝试', width: 70 },
    { field: 'nextRetryTime', title: '下次重试', width: 150, formatter: ({ cellValue }) => fmtDt(cellValue as number | string | null) },
    { field: 'startedAt', title: '开始时间', width: 150, formatter: ({ cellValue }) => fmtDt(cellValue as number | string | null) },
    { field: 'finishedAt', title: '结束时间', width: 150, formatter: ({ cellValue }) => fmtDt(cellValue as number | string | null) },
    { field: 'message', title: '结果/错误说明', minWidth: 200, showOverflow: true },
    { field: 'createTime', title: '创建时间', width: 150, formatter: ({ cellValue }) => fmtDt(cellValue as number | string | null) },
    { field: 'operation', title: '操作', width: 130, fixed: 'right', slots: { default: 'operation' } },
  ];
}
