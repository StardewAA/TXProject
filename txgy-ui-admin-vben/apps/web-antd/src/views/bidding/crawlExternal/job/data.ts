import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { fmtDt, JOB_STATUS_OPTIONS, TRIGGER_OPTIONS, jobSummaryText } from '../shared';

/** 顶部检索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    { fieldName: 'sourceCode', label: '源 code', component: 'Input', componentProps: { placeholder: '空=全源批次', allowClear: true } },
    { fieldName: 'triggerType', label: '触发类型', component: 'Select', componentProps: { options: TRIGGER_OPTIONS, allowClear: true, placeholder: '全部' } },
    { fieldName: 'status', label: '批次状态', component: 'Select', componentProps: { options: JOB_STATUS_OPTIONS, allowClear: true, placeholder: '全部' } },
  ];
}

/** 表格列（批次只读） */
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    { field: 'id', title: '批次 ID', width: 90 },
    {
      field: 'sourceCode',
      title: '源 code',
      width: 120,
      showOverflow: true,
      formatter: ({ cellValue }) => (cellValue ? String(cellValue) : '全源'),
    },
    { field: 'triggerType', title: '触发', width: 100, slots: { default: 'col_trigger' } },
    { field: 'status', title: '状态', width: 120, slots: { default: 'col_status' } },
    { field: 'scope', title: '范围说明', minWidth: 160, showOverflow: true },
    { field: 'summaryJson', title: '汇总', minWidth: 170, formatter: ({ cellValue }) => jobSummaryText(cellValue as string | undefined) },
    { field: 'finishedAt', title: '结束时间', width: 150, formatter: ({ cellValue }) => fmtDt(cellValue as number | string | null) },
    { field: 'createTime', title: '创建时间', width: 150, formatter: ({ cellValue }) => fmtDt(cellValue as number | string | null) },
  ];
}
