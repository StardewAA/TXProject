import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { MesTmToolScrapApi } from '#/api/mes/tm/tool/scrap';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';
import { formatDateTime } from '@vben/utils';

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'code',
      label: '报废单编码',
      component: 'Input',
      componentProps: { allowClear: true, placeholder: '请输入报废单编码' },
    },
    {
      fieldName: 'name',
      label: '报废单名称',
      component: 'Input',
      componentProps: { allowClear: true, placeholder: '请输入报废单名称' },
    },
    {
      fieldName: 'status',
      label: '状态',
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: getDictOptions(DICT_TYPE.MES_TM_TOOL_SCRAP_STATUS, 'number'),
        placeholder: '请选择状态',
      },
    },
  ];
}

/** 列表的字段 */
export function useGridColumns(): VxeTableGridOptions<MesTmToolScrapApi.ToolScrap>['columns'] {
  return [
    { field: 'code', title: '报废单编码', minWidth: 160 },
    { field: 'name', title: '报废单名称', minWidth: 180 },
    {
      field: 'scrapDate',
      title: '报废日期',
      width: 180,
      formatter: ({ cellValue }) => (cellValue ? formatDateTime(cellValue) : '-'),
    },
    {
      field: 'status',
      title: '状态',
      width: 120,
      cellRender: { name: 'CellDict', props: { type: DICT_TYPE.MES_TM_TOOL_SCRAP_STATUS } },
    },
    { field: 'scrapReason', title: '报废原因', minWidth: 200 },
    { field: 'remark', title: '备注', minWidth: 160 },
    {
      field: 'createTime',
      title: '创建时间',
      width: 180,
      formatter: 'formatDateTime',
    },
    { title: '操作', width: 200, fixed: 'right', slots: { default: 'actions' } },
  ];
}

/** 行列表的字段 */
export function useLineGridColumns(): VxeTableGridOptions<MesTmToolScrapApi.ToolScrapLine>['columns'] {
  return [
    { field: 'toolCode', title: '工具编码', minWidth: 140 },
    { field: 'toolName', title: '工具名称', minWidth: 160 },
    { field: 'quantity', title: '报废数量', width: 100 },
    { field: 'scrapReason', title: '报废原因', minWidth: 200 },
    { field: 'remark', title: '备注', minWidth: 160 },
  ];
}
