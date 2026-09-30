import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { getSupplierSimpleList } from '#/api/erp/purchase/supplier';
import { getSimpleUserList } from '#/api/system/user';
import { getRangePickerDefaultProps } from '#/utils';

/** 采购合同审批状态 */
const STATUS_LABELS: Record<number, string> = {
  0: '草稿',
  10: '审批中',
  20: '审批通过',
  30: '审批驳回',
  40: '已撤销',
};

/** 采购合同审批状态下拉选项 */
const STATUS_OPTIONS = Object.entries(STATUS_LABELS).map(([value, label]) => ({
  label,
  value: Number(value),
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
      label: '合同单号',
      componentProps: {
        placeholder: '系统自动生成',
        disabled: true,
      },
    },
    {
      component: 'Input',
      fieldName: 'contractName',
      label: '合同名称',
      rules: 'required',
      componentProps: {
        placeholder: '请输入合同名称',
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
      fieldName: 'purchaseOrderNo',
      label: '采购订单号',
      componentProps: {
        placeholder: '请输入关联的采购订单号',
        allowClear: true,
      },
    },
    {
      component: 'DatePicker',
      fieldName: 'signDate',
      label: '签订日期',
      componentProps: {
        placeholder: '请选择签订日期',
        showTime: true,
        format: 'YYYY-MM-DD HH:mm:ss',
        valueFormat: 'x',
      },
    },
    {
      component: 'DatePicker',
      fieldName: 'effectiveDate',
      label: '生效日期',
      componentProps: {
        placeholder: '请选择生效日期',
        showTime: true,
        format: 'YYYY-MM-DD HH:mm:ss',
        valueFormat: 'x',
      },
    },
    {
      component: 'DatePicker',
      fieldName: 'expireDate',
      label: '到期日期',
      componentProps: {
        placeholder: '请选择到期日期',
        showTime: true,
        format: 'YYYY-MM-DD HH:mm:ss',
        valueFormat: 'x',
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'totalAmount',
      label: '合同金额',
      componentProps: {
        class: '!w-full',
        placeholder: '请输入合同金额',
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
      component: 'Input',
      fieldName: 'paymentTerms',
      label: '付款条件',
      componentProps: {
        placeholder: '请输入付款条件',
      },
    },
    {
      component: 'Input',
      fieldName: 'deliveryPlace',
      label: '交货地点',
      componentProps: {
        placeholder: '请输入交货地点',
      },
    },
    {
      component: 'ApiSelect',
      fieldName: 'signerId',
      label: '签订人',
      componentProps: {
        placeholder: '请选择签订人',
        allowClear: true,
        showSearch: true,
        api: getSimpleUserList,
        labelField: 'nickname',
        valueField: 'id',
      },
    },
    {
      component: 'FileUpload',
      fieldName: 'fileUrl',
      label: '合同附件',
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
      label: '合同单号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入合同单号',
        allowClear: true,
      },
    },
    {
      fieldName: 'contractName',
      label: '合同名称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入合同名称',
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
      fieldName: 'status',
      label: '审批状态',
      component: 'Select',
      componentProps: {
        placeholder: '请选择审批状态',
        allowClear: true,
        options: STATUS_OPTIONS,
      },
    },
    {
      fieldName: 'signDate',
      label: '签订日期',
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
      title: '合同单号',
      width: 190,
      fixed: 'left',
    },
    {
      field: 'contractName',
      title: '合同名称',
      minWidth: 180,
      showOverflow: 'tooltip',
    },
    {
      field: 'supplierName',
      title: '供应商',
      minWidth: 150,
    },
    {
      field: 'signDate',
      title: '签订日期',
      width: 160,
      formatter: 'formatDateTime',
    },
    {
      field: 'expireDate',
      title: '到期日期',
      width: 160,
      formatter: 'formatDateTime',
    },
    {
      field: 'totalAmount',
      title: '合同金额',
      width: 130,
      formatter: 'formatAmount2',
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
      width: 320,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
