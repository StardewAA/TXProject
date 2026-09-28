import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { getSupplierSimpleList } from '#/api/erp/purchase/supplier';
import { getSimpleUserList } from '#/api/system/user';
import { getRangePickerDefaultProps } from '#/utils';

/** 供应商评价审批状态 */
const STATUS_LABELS: Record<number, string> = {
  0: '草稿',
  10: '审批中',
  20: '审批通过',
  30: '审批驳回',
  40: '已撤销',
};

/** 供应商评价等级下拉选项 */
const GRADE_OPTIONS = ['A', 'B', 'C', 'D'].map((grade) => ({
  label: grade,
  value: grade,
}));

/** 新增/修改的表单 */
export function useFormSchema(): VbenFormSchema[] {
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
      component: 'Input',
      fieldName: 'no',
      label: '评价单号',
      componentProps: {
        placeholder: '系统自动生成',
        disabled: true,
      },
    },
    {
      component: 'ApiSelect',
      fieldName: 'supplierId',
      label: '供应商',
      rules: 'required',
      componentProps: {
        placeholder: '请选择供应商',
        allowClear: true,
        showSearch: true,
        api: getSupplierSimpleList,
        labelField: 'name',
        valueField: 'id',
      },
    },
    {
      component: 'Input',
      fieldName: 'evalPeriod',
      label: '评价周期',
      componentProps: {
        placeholder: '如：2026-01、2026 年一季度',
      },
    },
    {
      component: 'DatePicker',
      fieldName: 'evalDate',
      label: '评价日期',
      componentProps: {
        placeholder: '请选择评价日期',
        showTime: true,
        format: 'YYYY-MM-DD HH:mm:ss',
        valueFormat: 'x',
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'qualityScore',
      label: '质量得分',
      componentProps: {
        class: '!w-full',
        placeholder: '请输入质量得分（0 ~ 100）',
        min: 0,
        max: 100,
        precision: 2,
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'deliveryScore',
      label: '交付得分',
      componentProps: {
        class: '!w-full',
        placeholder: '请输入交付得分（0 ~ 100）',
        min: 0,
        max: 100,
        precision: 2,
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'priceScore',
      label: '价格得分',
      componentProps: {
        class: '!w-full',
        placeholder: '请输入价格得分（0 ~ 100）',
        min: 0,
        max: 100,
        precision: 2,
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'serviceScore',
      label: '服务得分',
      componentProps: {
        class: '!w-full',
        placeholder: '请输入服务得分（0 ~ 100）',
        min: 0,
        max: 100,
        precision: 2,
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'totalScore',
      label: '综合得分',
      componentProps: {
        class: '!w-full',
        placeholder: '留空则按权重自动计算',
        min: 0,
        max: 100,
        precision: 2,
      },
    },
    {
      component: 'Select',
      fieldName: 'grade',
      label: '评价等级',
      componentProps: {
        placeholder: '留空则按综合得分自动评定',
        allowClear: true,
        options: GRADE_OPTIONS,
      },
    },
    {
      component: 'ApiSelect',
      fieldName: 'evaluatorId',
      label: '评价人',
      componentProps: {
        placeholder: '请选择评价人',
        allowClear: true,
        showSearch: true,
        api: getSimpleUserList,
        labelField: 'nickname',
        valueField: 'id',
      },
    },
    {
      component: 'Textarea',
      fieldName: 'comment',
      label: '评价说明',
      componentProps: {
        placeholder: '请输入评价说明',
        maxlength: 512,
        showCount: true,
        autoSize: { minRows: 2, maxRows: 4 },
      },
      formItemClass: 'col-span-2',
    },
    {
      component: 'Textarea',
      fieldName: 'remark',
      label: '备注',
      componentProps: {
        placeholder: '请输入备注',
        maxlength: 512,
        showCount: true,
        autoSize: { minRows: 2, maxRows: 4 },
      },
      formItemClass: 'col-span-2',
    },
  ];
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'no',
      label: '评价单号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入评价单号',
        allowClear: true,
      },
    },
    {
      fieldName: 'supplierId',
      label: '供应商',
      component: 'ApiSelect',
      componentProps: {
        placeholder: '请选择供应商',
        allowClear: true,
        showSearch: true,
        api: getSupplierSimpleList,
        labelField: 'name',
        valueField: 'id',
      },
    },
    {
      fieldName: 'evalPeriod',
      label: '评价周期',
      component: 'Input',
      componentProps: {
        placeholder: '请输入评价周期',
        allowClear: true,
      },
    },
    {
      fieldName: 'grade',
      label: '评价等级',
      component: 'Select',
      componentProps: {
        placeholder: '请选择评价等级',
        allowClear: true,
        options: GRADE_OPTIONS,
      },
    },
    {
      fieldName: 'evalDate',
      label: '评价日期',
      component: 'RangePicker',
      componentProps: {
        ...getRangePickerDefaultProps(),
        allowClear: true,
      },
    },
  ];
}

/** 列表的字段 */
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'no',
      title: '评价单号',
      width: 190,
      fixed: 'left',
    },
    {
      field: 'supplierName',
      title: '供应商',
      minWidth: 160,
    },
    {
      field: 'evalPeriod',
      title: '评价周期',
      width: 120,
    },
    {
      field: 'evalDate',
      title: '评价日期',
      width: 160,
      formatter: 'formatDateTime',
    },
    {
      field: 'qualityScore',
      title: '质量得分',
      width: 110,
      formatter: 'formatAmount2',
    },
    {
      field: 'deliveryScore',
      title: '交付得分',
      width: 110,
      formatter: 'formatAmount2',
    },
    {
      field: 'priceScore',
      title: '价格得分',
      width: 110,
      formatter: 'formatAmount2',
    },
    {
      field: 'serviceScore',
      title: '服务得分',
      width: 110,
      formatter: 'formatAmount2',
    },
    {
      field: 'totalScore',
      title: '综合得分',
      width: 110,
      formatter: 'formatAmount2',
    },
    {
      field: 'grade',
      title: '评价等级',
      width: 100,
    },
    {
      field: 'status',
      title: '审批状态',
      width: 110,
      formatter: ({ cellValue }) => STATUS_LABELS[cellValue as number] ?? '',
    },
    {
      field: 'creatorName',
      title: '创建人',
      width: 110,
    },
    {
      field: 'createTime',
      title: '创建时间',
      width: 180,
      formatter: 'formatDateTime',
    },
    {
      title: '操作',
      width: 160,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
