import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { fmtDt, JOB_STATUS_OPTIONS, jobSummaryText, TRIGGER_OPTIONS } from '../shared';

/** 顶部检索表单 —— 信息源=下拉（原为**手输 code 的输入框**：业务人员背编码既易错，也与"信息源"这个业务对象脱节；
 *  选项由 index.vue 加载源列表后经 formApi.updateSchema 注入，故此处接受参数、默认空数组不阻塞渲染）。 */
export function useGridFormSchema(
  sourceOptions: Array<{ label: string; value: string }> = [],
): VbenFormSchema[] {
  return [
    { fieldName: 'sourceCode', label: '信息源', component: 'Select', componentProps: { options: sourceOptions, allowClear: true, showSearch: true, placeholder: '全部（含全源批次）' } },
    { fieldName: 'triggerType', label: '触发类型', component: 'Select', componentProps: { options: TRIGGER_OPTIONS, allowClear: true, placeholder: '全部' } },
    { fieldName: 'status', label: '批次状态', component: 'Select', componentProps: { options: JOB_STATUS_OPTIONS, allowClear: true, placeholder: '全部' } },
  ];
}

/** 表格列（批次只读）—— `getSourceLabel` 在**渲染期**调用，故源列表晚到也能正确显示名称 */
export function useGridColumns(
  getSourceLabel: (code?: null | string) => string = (c) => (c ? String(c) : '全源'),
): VxeTableGridOptions['columns'] {
  return [
    { field: 'id', title: '批次 ID', width: 90 },
    {
      field: 'sourceCode',
      title: '信息源',
      width: 190,
      showOverflow: true,
      // 后端 CrawlJobRespVO 只有 sourceCode（无 sourceName）→ 前端用已加载的源列表做名称映射，
      // 未知/未加载时回落显示 code；已在契约 16 请求后端补 sourceName。
      formatter: ({ cellValue }) => getSourceLabel(cellValue as null | string),
    },
    { field: 'triggerType', title: '触发', width: 100, slots: { default: 'col_trigger' } },
    { field: 'status', title: '状态', width: 120, slots: { default: 'col_status' } },
    { field: 'scope', title: '范围说明', minWidth: 160, showOverflow: true },
    { field: 'summaryJson', title: '汇总', minWidth: 170, formatter: ({ cellValue }) => jobSummaryText(cellValue as string | undefined) },
    { field: 'finishedAt', title: '结束时间', width: 150, formatter: ({ cellValue }) => fmtDt(cellValue as null | number | string) },
    { field: 'createTime', title: '创建时间', width: 150, formatter: ({ cellValue }) => fmtDt(cellValue as null | number | string) },
    { field: 'operation', title: '操作', width: 90, fixed: 'right', slots: { default: 'operation' } },
  ];
}
