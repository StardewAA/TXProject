import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { getSupplierSimpleList } from '#/api/erp/purchase/supplier';
import { getCustomerSimpleList } from '#/api/erp/sale/customer';
import { getRangePickerDefaultProps } from '#/utils';

import { z } from '#/adapter/form';

/** 发票类型 */
const INVOICE_TYPE_LABELS: Record<number, string> = {
  1: '增值税专用发票',
  2: '增值税普通发票',
  3: '电子发票',
};

/** 业务类型 */
const BIZ_TYPE_LABELS: Record<number, string> = {
  1: '采购进项',
  2: '销售销项',
};

/** 发票状态 */
const STATUS_LABELS: Record<number, string> = {
  0: '待认证',
  1: '已认证',
  2: '已入账',
  3: '已作废',
};

/** 枚举转下拉选项 */
function toOptions(labels: Record<number, string>) {
  return Object.entries(labels).map(([value, label]) => ({
    label,
    value: Number(value),
  }));
}

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
      label: '发票单号',
      componentProps: {
        placeholder: '系统自动生成',
        disabled: true,
      },
    },
    {
      component: 'Select',
      fieldName: 'invoiceType',
      label: '发票类型',
      componentProps: {
        placeholder: '请选择发票类型',
        options: toOptions(INVOICE_TYPE_LABELS),
      },
      rules: z.number().default(1),
    },
    {
      component: 'Input',
      fieldName: 'invoiceNo',
      label: '发票号码',
      rules: 'required',
      componentProps: {
        placeholder: '请输入发票号码',
      },
    },
    {
      component: 'Input',
      fieldName: 'invoiceCode',
      label: '发票代码',
      componentProps: {
        placeholder: '请输入发票代码',
      },
    },
    {
      component: 'Input',
      fieldName: 'title',
      label: '发票抬头',
      componentProps: {
        placeholder: '请输入发票抬头',
      },
    },
    {
      component: 'Select',
      fieldName: 'bizType',
      label: '业务类型',
      componentProps: {
        placeholder: '请选择业务类型',
        options: toOptions(BIZ_TYPE_LABELS),
      },
      rules: z.number().default(1),
    },
    {
      component: 'ApiSelect',
      fieldName: 'supplierId',
      label: '供应商',
      componentProps: {
        placeholder: '请选择供应商',
        allowClear: true,
        showSearch: true,
        api: getSupplierSimpleList,
        labelField: 'name',
        valueField: 'id',
      },
      dependencies: {
        triggerFields: ['bizType'],
        show: (values) => values.bizType !== 2,
      },
    },
    {
      component: 'ApiSelect',
      fieldName: 'customerId',
      label: '客户',
      componentProps: {
        placeholder: '请选择客户',
        allowClear: true,
        showSearch: true,
        api: getCustomerSimpleList,
        labelField: 'name',
        valueField: 'id',
      },
      dependencies: {
        triggerFields: ['bizType'],
        show: (values) => values.bizType === 2,
      },
    },
    {
      component: 'Input',
      fieldName: 'orderNo',
      label: '关联单号',
      componentProps: {
        placeholder: '请输入关联的采购/销售单号',
        allowClear: true,
      },
    },
    {
      component: 'DatePicker',
      fieldName: 'invoiceDate',
      label: '开票日期',
      componentProps: {
        placeholder: '请选择开票日期',
        showTime: true,
        format: 'YYYY-MM-DD HH:mm:ss',
        valueFormat: 'x',
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'amount',
      label: '价税合计',
      componentProps: {
        class: '!w-full',
        placeholder: '请输入价税合计',
        min: 0,
        precision: 2,
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'taxAmount',
      label: '税额',
      componentProps: {
        class: '!w-full',
        placeholder: '请输入税额',
        min: 0,
        precision: 2,
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'taxRate',
      label: '税率(%)',
      componentProps: {
        class: '!w-full',
        placeholder: '请输入税率',
        min: 0,
        precision: 2,
      },
    },
    {
      component: 'FileUpload',
      fieldName: 'fileUrl',
      label: '发票附件',
      componentProps: {
        maxNumber: 1,
        maxSize: 10,
        accept: [
          'pdf',
          'doc',
          'docx',
          'xls',
          'xlsx',
          'txt',
          'jpg',
          'jpeg',
          'png',
        ],
        showDescription: true,
      },
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
      label: '发票单号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入发票单号',
        allowClear: true,
      },
    },
    {
      fieldName: 'invoiceNo',
      label: '发票号码',
      component: 'Input',
      componentProps: {
        placeholder: '请输入发票号码',
        allowClear: true,
      },
    },
    {
      fieldName: 'title',
      label: '发票抬头',
      component: 'Input',
      componentProps: {
        placeholder: '请输入发票抬头',
        allowClear: true,
      },
    },
    {
      fieldName: 'bizType',
      label: '业务类型',
      component: 'Select',
      componentProps: {
        placeholder: '请选择业务类型',
        allowClear: true,
        options: toOptions(BIZ_TYPE_LABELS),
      },
    },
    {
      fieldName: 'invoiceType',
      label: '发票类型',
      component: 'Select',
      componentProps: {
        placeholder: '请选择发票类型',
        allowClear: true,
        options: toOptions(INVOICE_TYPE_LABELS),
      },
    },
    {
      fieldName: 'status',
      label: '发票状态',
      component: 'Select',
      componentProps: {
        placeholder: '请选择发票状态',
        allowClear: true,
        options: toOptions(STATUS_LABELS),
      },
    },
    {
      fieldName: 'invoiceDate',
      label: '开票日期',
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
      title: '发票单号',
      width: 190,
      fixed: 'left',
    },
    {
      field: 'invoiceNo',
      title: '发票号码',
      width: 150,
    },
    {
      field: 'invoiceType',
      title: '发票类型',
      width: 130,
      formatter: ({ cellValue }) =>
        INVOICE_TYPE_LABELS[cellValue as number] ?? '',
    },
    {
      field: 'title',
      title: '发票抬头',
      minWidth: 160,
      showOverflow: 'tooltip',
    },
    {
      field: 'bizType',
      title: '业务类型',
      width: 110,
      formatter: ({ cellValue }) => BIZ_TYPE_LABELS[cellValue as number] ?? '',
    },
    {
      field: 'supplierName',
      title: '往来单位',
      minWidth: 150,
      formatter: ({ row }) =>
        (row.bizType === 2 ? row.customerName : row.supplierName) ?? '',
    },
    {
      field: 'invoiceDate',
      title: '开票日期',
      width: 160,
      formatter: 'formatDateTime',
    },
    {
      field: 'amount',
      title: '价税合计',
      width: 130,
      formatter: 'formatAmount2',
    },
    {
      field: 'taxAmount',
      title: '税额',
      width: 120,
      formatter: 'formatAmount2',
    },
    {
      field: 'status',
      title: '发票状态',
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
      width: 280,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
