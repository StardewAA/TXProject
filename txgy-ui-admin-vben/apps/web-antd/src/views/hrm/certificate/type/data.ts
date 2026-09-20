import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { HrmCertificateTypeApi } from '#/api/hrm/certificate/type';

import { z } from '@vben/common-ui';
import { CommonStatusEnum, DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';

import { HrmCertificateDefaultAdvanceDays } from '../constants';

/** 列表搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'name',
      label: '类型名称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入证件类型名称',
        allowClear: true,
      },
    },
    {
      fieldName: 'code',
      label: '类型编码',
      component: 'Input',
      componentProps: {
        placeholder: '请输入证件类型编码',
        allowClear: true,
      },
    },
    {
      fieldName: 'specialOperation',
      label: '特种作业',
      component: 'Select',
      componentProps: {
        options: [
          { label: '是', value: true },
          { label: '否', value: false },
        ],
        placeholder: '请选择是否特种作业',
        allowClear: true,
      },
    },
    {
      fieldName: 'status',
      label: '状态',
      component: 'Select',
      componentProps: {
        options: getDictOptions(DICT_TYPE.COMMON_STATUS, 'number'),
        placeholder: '请选择状态',
        allowClear: true,
      },
    },
  ];
}

/** 列表字段 */
export function useGridColumns(
  onStatusChange?: (
    newStatus: number,
    row: HrmCertificateTypeApi.CertificateType,
  ) => PromiseLike<boolean | undefined>,
): VxeTableGridOptions<HrmCertificateTypeApi.CertificateType>['columns'] {
  return [
    { field: 'name', title: '证件类型名称', minWidth: 180 },
    { field: 'code', title: '类型编码', minWidth: 150 },
    {
      field: 'specialOperation',
      title: '特种作业',
      width: 110,
      align: 'center',
      slots: { default: 'specialOperation' },
    },
    {
      field: 'needReview',
      title: '需要复审',
      width: 110,
      align: 'center',
      slots: { default: 'needReview' },
    },
    {
      field: 'reviewCycleMonths',
      title: '复审周期(月)',
      width: 130,
      align: 'center',
      formatter: ({ cellValue }) => cellValue ?? '-',
    },
    {
      field: 'advanceDays',
      title: '提前预警天数',
      width: 130,
      align: 'center',
      formatter: ({ cellValue }) => cellValue ?? HrmCertificateDefaultAdvanceDays,
    },
    {
      field: 'requireAttachment',
      title: '必传附件',
      width: 110,
      align: 'center',
      slots: { default: 'requireAttachment' },
    },
    {
      field: 'status',
      title: '状态',
      width: 100,
      align: 'center',
      cellRender: {
        attrs: { beforeChange: onStatusChange },
        name: 'CellSwitch',
        props: {
          checkedValue: CommonStatusEnum.ENABLE,
          unCheckedValue: CommonStatusEnum.DISABLE,
        },
      },
    },
    { field: 'sort', title: '排序', width: 90 },
    { field: 'remark', title: '备注', minWidth: 180 },
    {
      field: 'createTime',
      title: '创建时间',
      width: 180,
      formatter: 'formatDateTime',
    },
    {
      title: '操作',
      width: 140,
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
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
    },
    {
      fieldName: 'name',
      label: '类型名称',
      component: 'Input',
      rules: 'required',
      componentProps: {
        placeholder: '请输入证件类型名称',
        maxlength: 128,
        allowClear: true,
      },
    },
    {
      fieldName: 'code',
      label: '类型编码',
      component: 'Input',
      componentProps: {
        placeholder: '请输入证件类型编码，便于对接外部系统',
        maxlength: 64,
        allowClear: true,
      },
    },
    {
      fieldName: 'specialOperation',
      label: '是否特种作业',
      component: 'Switch',
      defaultValue: false,
      componentProps: {
        checkedChildren: '是',
        unCheckedChildren: '否',
      },
    },
    {
      fieldName: 'needReview',
      label: '是否需要复审',
      component: 'Switch',
      defaultValue: false,
      componentProps: {
        checkedChildren: '是',
        unCheckedChildren: '否',
      },
    },
    {
      // 特种作业必须配置复审周期，该约束由后端统一校验；表单侧在勾选特种作业时自动打开复审开关
      fieldName: 'reviewCycleMonths',
      label: '复审周期(月)',
      component: 'InputNumber',
      dependencies: {
        triggerFields: ['needReview'],
        disabled: (values) => !values.needReview,
      },
      componentProps: {
        placeholder: '请输入复审周期，单位月',
        min: 1,
        max: 600,
        class: 'w-full',
      },
    },
    {
      fieldName: 'advanceDays',
      label: '提前预警天数',
      component: 'InputNumber',
      defaultValue: HrmCertificateDefaultAdvanceDays,
      rules: z
        .number()
        .min(0, { message: '提前预警天数不能小于 0' })
        .max(365, { message: '提前预警天数不能大于 365' })
        .default(HrmCertificateDefaultAdvanceDays),
      componentProps: {
        placeholder: '默认 30 天，特种作业类可适当调大',
        min: 0,
        max: 365,
        class: 'w-full',
      },
    },
    {
      fieldName: 'requireAttachment',
      label: '必传附件',
      component: 'Switch',
      defaultValue: false,
      componentProps: {
        checkedChildren: '是',
        unCheckedChildren: '否',
      },
    },
    {
      fieldName: 'status',
      label: '状态',
      component: 'RadioGroup',
      defaultValue: CommonStatusEnum.ENABLE,
      componentProps: {
        options: getDictOptions(DICT_TYPE.COMMON_STATUS, 'number'),
        buttonStyle: 'solid',
        optionType: 'button',
      },
    },
    {
      fieldName: 'sort',
      label: '显示顺序',
      component: 'InputNumber',
      defaultValue: 0,
      rules: z.number().min(0, { message: '显示顺序不能小于 0' }).default(0),
      componentProps: {
        placeholder: '请输入显示顺序',
        min: 0,
        class: 'w-full',
      },
    },
    {
      fieldName: 'remark',
      label: '备注',
      component: 'Textarea',
      componentProps: {
        placeholder: '请输入备注，例如发证机关与复审要求',
        maxlength: 500,
        rows: 3,
        allowClear: true,
      },
    },
  ];
}
