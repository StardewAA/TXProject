import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { CommonStatusEnum, DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';

import { z } from '#/adapter/form';

import { PORTAL_TENANT_SLUG_PATTERN } from './model';

export function useFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'id',
      dependencies: { show: () => false, triggerFields: [''] },
    },
    {
      fieldName: 'tenantId',
      label: '经营租户编号',
      component: 'InputNumber',
      componentProps: { class: '!w-full', disabled: true },
    },
    {
      fieldName: 'tenantName',
      label: '系统租户名称',
      component: 'Input',
      componentProps: { disabled: true },
      help: '保存时会以 system_tenant 的当前名称为准。',
    },
    {
      fieldName: 'tenantSlug',
      label: '租户商城路由',
      component: 'Input',
      componentProps: {
        maxlength: 64,
        placeholder: '例如 kedun-mall',
      },
      help: '仅小写字母、数字和中划线；保存后将作为稳定公开路由。',
      rules: z
        .string()
        .trim()
        .min(1, '租户商城路由不能为空')
        .max(64, '租户商城路由不能超过 64 个字符')
        .regex(PORTAL_TENANT_SLUG_PATTERN, '路由格式不正确'),
    },
    {
      fieldName: 'tenantLogo',
      label: '门户 Logo',
      component: 'ImageUpload',
      componentProps: { maxNumber: 1 },
    },
    {
      fieldName: 'mainCategories',
      label: '主营分类',
      component: 'Textarea',
      componentProps: {
        maxlength: 512,
        placeholder: '例如：防腐涂料、保温材料、管件',
        rows: 3,
        showCount: true,
      },
    },
    {
      fieldName: 'openStatus',
      label: '门户状态',
      component: 'RadioGroup',
      componentProps: {
        buttonStyle: 'solid',
        optionType: 'button',
        options: getDictOptions(DICT_TYPE.COMMON_STATUS, 'number'),
      },
      rules: z.number().default(CommonStatusEnum.DISABLE),
    },
    {
      fieldName: 'sort',
      label: '展示排序',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        max: 999_999,
        min: 0,
        placeholder: '数值越小越靠前',
      },
      rules: z.number().int().min(0).max(999_999).default(0),
    },
  ];
}

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'tenantId',
      label: '租户编号',
      component: 'InputNumber',
      componentProps: { allowClear: true, min: 1, placeholder: '请输入租户编号' },
    },
    {
      fieldName: 'tenantName',
      label: '租户名称',
      component: 'Input',
      componentProps: { allowClear: true, placeholder: '请输入租户名称' },
    },
    {
      fieldName: 'tenantSlug',
      label: '商城路由',
      component: 'Input',
      componentProps: { allowClear: true, placeholder: '请输入路由标识' },
    },
    {
      fieldName: 'openStatus',
      label: '门户状态',
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: getDictOptions(DICT_TYPE.COMMON_STATUS, 'number'),
        placeholder: '请选择状态',
      },
    },
  ];
}

export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    { field: 'tenantId', title: '租户编号', width: 110 },
    { field: 'tenantName', title: '租户名称', minWidth: 220 },
    { field: 'tenantSlug', title: '租户商城路由', minWidth: 180 },
    {
      field: 'tenantLogo',
      title: 'Logo',
      width: 90,
      cellRender: { name: 'CellImage' },
    },
    { field: 'mainCategories', title: '主营分类', minWidth: 240 },
    {
      field: 'openStatus',
      title: '门户状态',
      width: 100,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.COMMON_STATUS },
      },
    },
    { field: 'sort', title: '展示排序', width: 100 },
    {
      field: 'updateTime',
      title: '更新时间',
      width: 180,
      formatter: 'formatDateTime',
    },
    {
      title: '操作',
      width: 120,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
