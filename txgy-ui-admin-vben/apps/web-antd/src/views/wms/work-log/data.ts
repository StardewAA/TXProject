import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { getSimpleUserList } from '#/api/system/user';
import { getRangePickerDefaultProps } from '#/utils';

export const ACTION_LABELS: Record<string, string> = {
  APPROVE: '审批通过',
  CREATE: '新增',
  DELETE: '删除',
  EXPORT: '导出',
  IMPORT: '导入',
  OTHER: '其他',
  QUERY: '查询',
  REJECT: '审批驳回',
  SUBMIT: '提交',
  UPDATE: '修改',
};

export const MODULE_LABELS: Record<string, string> = {
  bpm: '流程审批',
  crm: '客户管理',
  erp: '进销存',
  fms: '财务管理',
  infra: '系统基础',
  mes: '生产管理',
  system: '系统管理',
  wms: '仓储管理',
};

export const CHANGE_OPERATION_LABELS: Record<string, string> = {
  DELETE: '删除',
  INSERT: '新增',
  UPDATE: '修改',
};

export const CHANGE_OUTCOME_LABELS: Record<string, string> = {
  COMMITTED: '已提交',
  EXECUTED: '已执行',
  PENDING: '提交状态待确认',
  ROLLED_BACK: '已回滚',
};

/** 留痕筛选表单 */
export function useGridFormSchema(showMember: boolean): VbenFormSchema[] {
  const schema: VbenFormSchema[] = [
    {
      fieldName: 'beginTime',
      label: '操作时间',
      component: 'RangePicker',
      componentProps: {
        ...getRangePickerDefaultProps(),
        allowClear: true,
      },
    },
  ];

  if (showMember) {
    schema.push({
      fieldName: 'userId',
      label: '操作成员',
      component: 'ApiSelect',
      componentProps: {
        allowClear: true,
        api: getSimpleUserList,
        labelField: 'nickname',
        placeholder: '请选择成员',
        showSearch: true,
        valueField: 'id',
      },
    });
  }

  schema.push(
    {
      fieldName: 'keyword',
      label: '关键词',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '人员、业务编号、摘要或条件',
      },
    },
    {
      fieldName: 'action',
      label: '操作类型',
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: Object.entries(ACTION_LABELS).map(([value, label]) => ({
          label,
          value,
        })),
        placeholder: '请选择操作类型',
      },
    },
    {
      fieldName: 'resultStatus',
      label: '操作结果',
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: [
          { label: '成功', value: 'SUCCESS' },
          { label: '失败', value: 'FAILURE' },
        ],
        placeholder: '请选择操作结果',
      },
    },
  );

  return schema;
}

/** 留痕列表字段 */
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'beginTime',
      title: '操作时间',
      width: 172,
      fixed: 'left',
      formatter: 'formatDateTime',
    },
    {
      field: 'userName',
      title: '操作人',
      minWidth: 128,
      slots: { default: 'userName' },
    },
    {
      field: 'module',
      title: '仓储位置',
      minWidth: 115,
      slots: { default: 'module' },
    },
    {
      field: 'action',
      title: '操作类型',
      minWidth: 108,
      slots: { default: 'action' },
    },
    {
      field: 'summary',
      title: '工作内容',
      minWidth: 280,
      slots: { default: 'summary' },
    },
    {
      field: 'resultCode',
      title: '结果',
      align: 'center',
      width: 90,
      slots: { default: 'result' },
    },
    {
      field: 'duration',
      title: '耗时',
      align: 'right',
      width: 95,
      formatter: ({ cellValue }) =>
        cellValue === undefined || cellValue === null ? '-' : `${cellValue} ms`,
    },
    {
      title: '操作',
      width: 86,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
