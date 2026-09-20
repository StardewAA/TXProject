import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { HrmEmployeeBlacklistApi } from '#/api/hrm/employee/blacklist';

import {
  HrmBlacklistSourceOptions,
  HrmBlacklistStatusOptions,
} from '#/api/hrm/employee/blacklist';

/** 列表搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'name',
      label: '姓名',
      component: 'Input',
      componentProps: { placeholder: '请输入姓名', allowClear: true },
    },
    {
      fieldName: 'mobile',
      label: '手机号',
      component: 'Input',
      componentProps: { placeholder: '请输入手机号', allowClear: true },
    },
    {
      fieldName: 'idCardNo',
      label: '身份证号',
      component: 'Input',
      componentProps: { placeholder: '请输入身份证号', allowClear: true },
    },
    {
      fieldName: 'status',
      label: '状态',
      component: 'Select',
      componentProps: {
        options: [...HrmBlacklistStatusOptions],
        placeholder: '请选择状态',
        allowClear: true,
      },
    },
  ];
}

/** 列表字段 */
export function useGridColumns(): VxeTableGridOptions<HrmEmployeeBlacklistApi.Blacklist>['columns'] {
  return [
    { field: 'name', title: '姓名', width: 110 },
    { field: 'mobile', title: '手机号', width: 130 },
    { field: 'jobNumber', title: '工号', width: 120 },
    { field: 'idCardNo', title: '身份证号', width: 190 },
    { field: 'reason', title: '加入原因', minWidth: 240 },
    { field: 'sourceName', title: '来源', width: 120 },
    { field: 'joinTime', title: '加入时间', width: 160 },
    { field: 'releaseTime', title: '解除时间', width: 160 },
    {
      field: 'permanentFlag',
      title: '是否永久',
      width: 100,
      formatter: ({ cellValue }) => (cellValue ? '永久' : '可解除'),
    },
    {
      field: 'status',
      title: '状态',
      width: 100,
      slots: { default: 'status' },
    },
    {
      title: '操作',
      width: 190,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}

/** 新增/修改表单 */
export function useFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'id',
      component: 'Input',
      dependencies: { triggerFields: [''], show: () => false },
    },
    {
      fieldName: 'name',
      label: '姓名',
      component: 'Input',
      rules: 'required',
      componentProps: { placeholder: '例如：李四', maxlength: 64, allowClear: true },
    },
    {
      fieldName: 'mobile',
      label: '手机号',
      component: 'Input',
      rules: 'required',
      componentProps: {
        placeholder: '录用/再入职时按手机号命中拦截',
        maxlength: 32,
        allowClear: true,
      },
    },
    {
      fieldName: 'idCardNo',
      label: '身份证号',
      component: 'Input',
      componentProps: {
        placeholder: '选填，填写后按身份证号精确拦截',
        maxlength: 32,
        allowClear: true,
      },
    },
    {
      fieldName: 'reason',
      label: '加入原因',
      component: 'Textarea',
      rules: 'required',
      componentProps: {
        rows: 3,
        maxlength: 500,
        allowClear: true,
        placeholder: '例如：高空作业未系安全带，严重安全违规',
      },
      formItemClass: 'col-span-2',
    },
    {
      fieldName: 'source',
      label: '来源',
      component: 'Select',
      defaultValue: 1,
      componentProps: {
        options: [...HrmBlacklistSourceOptions],
        placeholder: '请选择来源',
      },
    },
    {
      fieldName: 'permanentFlag',
      label: '是否永久',
      component: 'Switch',
      defaultValue: false,
      componentProps: { checkedChildren: '永久', unCheckedChildren: '可解除' },
    },
    {
      fieldName: 'joinTime',
      label: '加入时间',
      component: 'DatePicker',
      componentProps: {
        showTime: true,
        format: 'YYYY-MM-DD HH:mm:ss',
        valueFormat: 'YYYY-MM-DD HH:mm:ss',
        class: 'w-full',
      },
    },
    {
      fieldName: 'remark',
      label: '备注',
      component: 'Textarea',
      componentProps: { rows: 2, maxlength: 500, allowClear: true },
      formItemClass: 'col-span-2',
    },
  ];
}
