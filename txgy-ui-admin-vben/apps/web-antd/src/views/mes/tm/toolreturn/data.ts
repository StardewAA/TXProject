import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { MesTmToolReturnApi } from '#/api/mes/tm/tool/return';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';
import { formatDateTime } from '@vben/utils';

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'code',
      label: '归还单编码',
      component: 'Input',
      componentProps: { allowClear: true, placeholder: '请输入归还单编码' },
    },
    {
      fieldName: 'name',
      label: '归还单名称',
      component: 'Input',
      componentProps: { allowClear: true, placeholder: '请输入归还单名称' },
    },
    {
      fieldName: 'status',
      label: '状态',
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: getDictOptions(DICT_TYPE.MES_TM_TOOL_RETURN_STATUS, 'number'),
        placeholder: '请选择状态',
      },
    },
  ];
}

/** 列表的字段 */
export function useGridColumns(): VxeTableGridOptions<MesTmToolReturnApi.ToolReturn>['columns'] {
  return [
    { field: 'code', title: '归还单编码', minWidth: 160 },
    { field: 'name', title: '归还单名称', minWidth: 180 },
    { field: 'workstationName', title: '工位', minWidth: 140 },
    {
      field: 'returnDate',
      title: '归还日期',
      width: 180,
      formatter: ({ cellValue }) => (cellValue ? formatDateTime(cellValue) : '-'),
    },
    {
      field: 'status',
      title: '状态',
      width: 120,
      cellRender: { name: 'CellDict', props: { type: DICT_TYPE.MES_TM_TOOL_RETURN_STATUS } },
    },
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
export function useLineGridColumns(): VxeTableGridOptions<MesTmToolReturnApi.ToolReturnLine>['columns'] {
  return [
    { field: 'toolCode', title: '工具编码', minWidth: 140 },
    { field: 'toolName', title: '工具名称', minWidth: 160 },
    { field: 'quantity', title: '归还数量', width: 100 },
    { field: 'remark', title: '备注', minWidth: 160 },
  ];
}
