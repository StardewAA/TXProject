import type { VbenFormApi, VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { h } from 'vue';

import { CommonStatusEnum, DICT_TYPE, generateWmsCode } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';

import { Button } from 'ant-design-vue';

import { z } from '#/adapter/form';

const warehouseTypes = [
  { label: '原材料仓', value: 'RAW_MATERIAL' },
  { label: '成品仓', value: 'FINISHED_GOODS' },
];

/** 新增/修改仓库的表单 */
export function useFormSchema(formApi?: VbenFormApi): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'id',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
    },
    {
      fieldName: 'name',
      label: '仓库名称',
      component: 'Input',
      componentProps: {
        maxLength: 50,
        placeholder: '请输入仓库名称',
      },
      rules: z.string().min(1, '仓库名称不能为空').max(50),
    },
    {
      fieldName: 'code',
      label: '仓库编号',
      component: 'Input',
      componentProps: {
        maxLength: 20,
        placeholder: '请输入仓库编号',
      },
      rules: z.string().min(1, '仓库编号不能为空').max(20),
      suffix: () => {
        return h(
          Button,
          {
            type: 'default',
            onClick: () => {
              formApi?.setFieldValue('code', generateWmsCode('W'));
            },
          },
          { default: () => '生成' },
        );
      },
    },
    {
      fieldName: 'warehouseType',
      label: '仓库类型',
      component: 'Select',
      componentProps: { options: warehouseTypes, placeholder: '请选择仓库类型' },
      rules: z.enum(['RAW_MATERIAL', 'FINISHED_GOODS']).default('RAW_MATERIAL'),
    },
    {
      fieldName: 'status',
      label: '仓库状态',
      component: 'Select',
      componentProps: {
        options: getDictOptions(DICT_TYPE.COMMON_STATUS, 'number'),
        placeholder: '请选择仓库状态',
      },
      rules: z.number().default(CommonStatusEnum.ENABLE),
    },
    {
      fieldName: 'organizationName',
      label: '所属组织/部门',
      component: 'Input',
      componentProps: { maxLength: 100, placeholder: '请输入所属组织/部门' },
    },
    {
      fieldName: 'city',
      label: '所在城市',
      component: 'Input',
      componentProps: { maxLength: 50, placeholder: '请输入所在城市' },
    },
    {
      fieldName: 'address',
      label: '仓库地址',
      component: 'Input',
      componentProps: { maxLength: 255, placeholder: '请输入仓库地址' },
    },
    {
      fieldName: 'principal',
      label: '负责人',
      component: 'Input',
      componentProps: { maxLength: 64, placeholder: '请输入负责人' },
    },
    {
      fieldName: 'principalPhone',
      label: '负责人联系电话',
      component: 'Input',
      componentProps: {
        maxLength: 11,
        placeholder: '请输入11位手机号码',
        inputmode: 'numeric',
      },
      rules: z
        .string()
        .refine((value) => !value || /^1\d{10}$/.test(value), '请输入11位有效手机号码'),
    },
    {
      fieldName: 'sort',
      label: '排序',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        min: 0,
      },
      rules: z.number().default(0),
    },
    {
      fieldName: 'remark',
      label: '备注',
      component: 'Textarea',
      componentProps: {
        maxLength: 255,
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
      fieldName: 'name',
      label: '仓库名称',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入仓库名称',
      },
    },
    {
      fieldName: 'code',
      label: '仓库编号',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入仓库编号',
      },
    },
    {
      fieldName: 'warehouseType',
      label: '仓库类型',
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: [
          { label: '原料仓', value: 'RAW_MATERIAL' },
          { label: '成品仓', value: 'FINISHED_GOODS' },
        ],
      },
    },
    {
      fieldName: 'status',
      label: '仓库状态',
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: getDictOptions(DICT_TYPE.COMMON_STATUS, 'number'),
      },
    },
    {
      fieldName: 'organizationName',
      label: '所属组织/部门',
      component: 'Input',
      componentProps: { allowClear: true },
    },
    {
      fieldName: 'city',
      label: '所在城市',
      component: 'Input',
      componentProps: { allowClear: true },
    },
    {
      fieldName: 'address',
      label: '仓库地址',
      component: 'Input',
      componentProps: { allowClear: true },
    },
    {
      fieldName: 'principal',
      label: '负责人',
      component: 'Input',
      componentProps: { allowClear: true, placeholder: '请输入负责人' },
    },
  ];
}

/** 列表的字段 */
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'name',
      title: '仓库名称',
      minWidth: 160,
    },
    {
      field: 'code',
      title: '仓库编号',
      minWidth: 140,
    },
    {
      field: 'warehouseType',
      title: '仓库类型',
    {
      field: 'warehouseType',
      title: '仓库类型',
      minWidth: 130,
      formatter: ({ cellValue }) => warehouseTypes.find(type => type.value === cellValue)?.label ?? '未分类',
    },
    {
      field: 'status',
      title: '仓库状态',
      minWidth: 110,
      cellRender: { name: 'CellDict', props: { type: DICT_TYPE.COMMON_STATUS } },
    },
    {
      field: 'organizationName',
      title: '所属组织/部门',
      minWidth: 150,
    },
    {
      field: 'city',
      title: '所在城市',
      minWidth: 110,
    },
    {
      field: 'address',
      title: '仓库地址',
      minWidth: 220,
      showOverflow: 'tooltip',
    },
    {
      field: 'principal',
      title: '负责人',
      minWidth: 110,
    },
    {
      field: 'remark',
      title: '备注',
      minWidth: 220,
    },
    {
      field: 'sort',
      title: '排序',
      width: 100,
      align: 'center',
    },
    {
      field: 'createTime',
      title: '创建时间',
      width: 180,
      formatter: 'formatDateTime',
    },
    {
      title: '操作',
      width: 240,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
