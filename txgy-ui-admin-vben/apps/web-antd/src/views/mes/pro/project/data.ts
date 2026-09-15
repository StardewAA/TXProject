import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { MesProProjectApi } from '#/api/mes/pro/project';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';

import { getSimpleUserList } from '#/api/system/user';

/** 表单类型 */
export type FormType = 'create' | 'detail' | 'update';

/** 新增/修改的表单 */
export function useFormSchema(formType: FormType): VbenFormSchema[] {
  const headerReadonly = formType === 'detail';
  return [
    {
      fieldName: 'id',
      component: 'Input',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
    },
    {
      fieldName: 'code',
      label: '项目编码',
      component: 'Input',
      componentProps: {
        disabled: headerReadonly,
        placeholder: '请输入项目编码',
      },
      rules: 'required',
    },
    {
      fieldName: 'name',
      label: '项目名称',
      component: 'Input',
      componentProps: {
        disabled: headerReadonly,
        placeholder: '请输入项目名称',
      },
      rules: 'required',
    },
    {
      fieldName: 'sourceType',
      label: '项目来源',
      component: 'Select',
      componentProps: {
        disabled: headerReadonly,
        options: getDictOptions(DICT_TYPE.MES_PRO_PROJECT_SOURCE_TYPE, 'number'),
        placeholder: '请选择项目来源',
      },
    },
    {
      fieldName: 'orderSourceCode',
      label: '来源单据编号',
      component: 'Input',
      componentProps: {
        disabled: headerReadonly,
        placeholder: '请输入销售订单号/报价单号',
      },
    },
    {
      fieldName: 'status',
      label: '项目状态',
      component: 'Select',
      componentProps: {
        disabled: headerReadonly,
        options: getDictOptions(DICT_TYPE.MES_PRO_PROJECT_STATUS, 'number'),
        placeholder: '请选择项目状态',
      },
      rules: 'selectRequired',
    },
    {
      fieldName: 'priority',
      label: '项目优先级',
      component: 'Select',
      componentProps: {
        disabled: headerReadonly,
        options: getDictOptions(DICT_TYPE.MES_PRO_PROJECT_PRIORITY, 'number'),
        placeholder: '请选择项目优先级',
      },
    },
    {
      fieldName: 'ownerUserId',
      label: '负责人',
      component: 'ApiSelect',
      componentProps: {
        api: getSimpleUserList,
        labelField: 'nickname',
        valueField: 'id',
        disabled: headerReadonly,
        placeholder: '请选择负责人',
      },
    },
    {
      fieldName: 'dept',
      label: '责任部门',
      component: 'Input',
      componentProps: {
        disabled: true,
        placeholder: '选择负责人后自动带出',
      },
    },
    {
      fieldName: 'planStartTime',
      label: '计划开始时间',
      component: 'DatePicker',
      componentProps: {
        disabled: headerReadonly,
        showTime: true,
        // 后端 TimestampLocalDateTimeDeserializer 只接受毫秒时间戳
        valueFormat: 'x',
        format: 'YYYY-MM-DD HH:mm:ss',
        placeholder: '请选择计划开始时间',
      },
    },
    {
      fieldName: 'planEndTime',
      label: '计划结束时间',
      component: 'DatePicker',
      componentProps: {
        disabled: headerReadonly,
        showTime: true,
        valueFormat: 'x',
        format: 'YYYY-MM-DD HH:mm:ss',
        placeholder: '请选择计划结束时间',
      },
    },
    {
      fieldName: 'actualStartTime',
      label: '实际开始时间',
      component: 'DatePicker',
      componentProps: {
        disabled: headerReadonly,
        showTime: true,
        valueFormat: 'x',
        format: 'YYYY-MM-DD HH:mm:ss',
        placeholder: '请选择实际开始时间',
      },
    },
    {
      fieldName: 'actualEndTime',
      label: '实际结束时间',
      component: 'DatePicker',
      componentProps: {
        disabled: headerReadonly,
        showTime: true,
        valueFormat: 'x',
        format: 'YYYY-MM-DD HH:mm:ss',
        placeholder: '请选择实际结束时间',
      },
    },
    {
      fieldName: 'remark',
      label: '备注',
      component: 'Textarea',
      formItemClass: 'col-span-3',
      componentProps: {
        disabled: headerReadonly,
        placeholder: '请输入备注',
        rows: 3,
      },
    },
  ];
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'code',
      label: '项目编码',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入项目编码',
      },
    },
    {
      fieldName: 'name',
      label: '项目名称',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入项目名称',
      },
    },
    {
      fieldName: 'status',
      label: '项目状态',
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: getDictOptions(DICT_TYPE.MES_PRO_PROJECT_STATUS, 'number'),
        placeholder: '请选择项目状态',
      },
    },
    {
      fieldName: 'sourceType',
      label: '项目来源',
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: getDictOptions(DICT_TYPE.MES_PRO_PROJECT_SOURCE_TYPE, 'number'),
        placeholder: '请选择项目来源',
      },
    },
    {
      fieldName: 'priority',
      label: '项目优先级',
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: getDictOptions(DICT_TYPE.MES_PRO_PROJECT_PRIORITY, 'number'),
        placeholder: '请选择项目优先级',
      },
    },
    {
      fieldName: 'owner',
      label: '负责人',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入负责人',
      },
    },
    {
      fieldName: 'dept',
      label: '责任部门',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入责任部门',
      },
    },
  ];
}

/** 列表的字段 */
export function useGridColumns(): VxeTableGridOptions<MesProProjectApi.Project>['columns'] {
  return [
    {
      field: 'code',
      title: '项目编码',
      fixed: 'left',
      width: 180,
      slots: { default: 'code' },
    },
    {
      field: 'name',
      title: '项目名称',
      minWidth: 160,
    },
    {
      field: 'sourceType',
      title: '项目来源',
      width: 110,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.MES_PRO_PROJECT_SOURCE_TYPE },
      },
    },
    {
      field: 'orderSourceCode',
      title: '来源单据编号',
      width: 150,
    },
    {
      field: 'status',
      title: '项目状态',
      width: 110,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.MES_PRO_PROJECT_STATUS },
      },
    },
    {
      field: 'progress',
      title: '生产进度',
      width: 120,
      formatter: ({ cellValue }) =>
        cellValue === null || cellValue === undefined ? '—' : `${cellValue}%`,
    },
    {
      field: 'priority',
      title: '优先级',
      width: 100,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.MES_PRO_PROJECT_PRIORITY },
      },
    },
    {
      field: 'owner',
      title: '负责人',
      width: 120,
    },
    {
      field: 'dept',
      title: '责任部门',
      width: 140,
    },
    {
      field: 'durationDays',
      title: '工期(天)',
      width: 100,
    },
    {
      field: 'workOrderCount',
      title: '工单数(已完成/总)',
      width: 130,
      formatter: ({ row, cellValue }) => {
        return `${row.finishedWorkOrderCount ?? 0}/${cellValue ?? 0}`;
      },
    },
    {
      field: 'createTime',
      title: '创建时间',
      width: 180,
      formatter: 'formatDateTime',
    },
    {
      field: 'remark',
      title: '备注',
      minWidth: 120,
    },
    {
      title: '操作',
      width: 320,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}

/** 项目选择弹窗的搜索表单 */
export function useProjectSelectGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'code',
      label: '项目编码',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入项目编码',
      },
    },
    {
      fieldName: 'name',
      label: '项目名称',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入项目名称',
      },
    },
    {
      fieldName: 'status',
      label: '项目状态',
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: getDictOptions(DICT_TYPE.MES_PRO_PROJECT_STATUS, 'number'),
        placeholder: '请选择项目状态',
      },
    },
  ];
}

/** 项目选择弹窗的字段 */
export function useProjectSelectGridColumns(
  multiple = false,
): VxeTableGridOptions<MesProProjectApi.Project>['columns'] {
  return [
    {
      type: multiple ? 'checkbox' : 'radio',
      width: 50,
    },
    {
      field: 'code',
      title: '项目编码',
      width: 180,
    },
    {
      field: 'name',
      title: '项目名称',
      minWidth: 180,
    },
    {
      field: 'orderSourceCode',
      title: '来源单据编号',
      width: 150,
    },
    {
      field: 'status',
      title: '项目状态',
      width: 110,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.MES_PRO_PROJECT_STATUS },
      },
    },
    {
      field: 'workOrderCount',
      title: '工单数(已完成/总)',
      width: 130,
      formatter: ({ row, cellValue }) =>
        `${row.finishedWorkOrderCount ?? 0}/${cellValue ?? 0}`,
    },
  ];
}

/** 导入表单 */
export function useImportFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'file',
      label: 'Excel 文件',
      component: 'Input',
      rules: 'required',
    },
    {
      fieldName: 'updateSupport',
      label: '是否覆盖更新',
      component: 'Switch',
      componentProps: { defaultChecked: false },
      defaultValue: false,
    },
  ];
}
