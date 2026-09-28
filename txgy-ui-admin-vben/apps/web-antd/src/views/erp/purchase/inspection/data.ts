import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { z } from '#/adapter/form';
import { getSupplierSimpleList } from '#/api/erp/purchase/supplier';
import { getSimpleUserList } from '#/api/system/user';
import { getRangePickerDefaultProps } from '#/utils';

/** 审批状态选项 */
export const auditStatusOptions = [
  { label: '草稿', value: 0 },
  { label: '审批中', value: 10 },
  { label: '审批通过', value: 20 },
  { label: '审批驳回', value: 30 },
  { label: '已撤销', value: 40 },
];

/** 检验方式选项 */
export const inspectTypeOptions = [
  { label: '全检', value: 1 },
  { label: '抽检', value: 2 },
];

/** 检验结论选项（表头） */
export const inspectResultOptions = [
  { label: '合格', value: 1 },
  { label: '让步接收', value: 2 },
  { label: '退货', value: 3 },
];

/** 检验结论选项（明细） */
export const itemResultOptions = [
  { label: '合格', value: 1 },
  { label: '不合格', value: 2 },
];

/** 审批状态名 */
function formatAuditStatus(status?: number) {
  return auditStatusOptions.find((item) => item.value === status)?.label ?? '-';
}

/** 检验方式名 */
function formatInspectType(inspectType?: number) {
  return inspectTypeOptions.find((item) => item.value === inspectType)?.label ?? '-';
}

/** 检验结论名 */
function formatInspectResult(result?: number) {
  return inspectResultOptions.find((item) => item.value === result)?.label ?? '-';
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
      label: '检验单号',
      componentProps: {
        placeholder: '系统自动生成',
        disabled: true,
      },
    },
    {
      component: 'Input',
      fieldName: 'purchaseOrderNo',
      label: '采购订单号',
      componentProps: {
        placeholder: '请输入采购订单号',
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
      formItemClass: 'col-span-1',
    },
    {
      fieldName: 'inspectTime',
      label: '检验时间',
      component: 'DatePicker',
      componentProps: {
        placeholder: '请选择检验时间',
        showTime: true,
        format: 'YYYY-MM-DD HH:mm:ss',
        valueFormat: 'x',
      },
      rules: 'required',
    },
    {
      fieldName: 'inspectorId',
      label: '检验人',
      component: 'ApiSelect',
      componentProps: {
        placeholder: '请选择检验人',
        allowClear: true,
        showSearch: true,
        api: getSimpleUserList,
        labelField: 'nickname',
        valueField: 'id',
      },
    },
    {
      fieldName: 'inspectType',
      label: '检验方式',
      component: 'Select',
      componentProps: {
        placeholder: '请选择检验方式',
        allowClear: true,
        options: inspectTypeOptions,
      },
      rules: z.number().default(1),
    },
    {
      fieldName: 'result',
      label: '检验结论',
      component: 'Select',
      componentProps: {
        placeholder: '请选择检验结论',
        allowClear: true,
        options: inspectResultOptions,
      },
    },
    {
      component: 'Input',
      fieldName: 'origin',
      label: '来源',
      componentProps: {
        placeholder: '请输入来源',
        allowClear: true,
      },
    },
    {
      fieldName: 'conclusion',
      label: '结论说明',
      component: 'Textarea',
      componentProps: {
        placeholder: '请输入检验结论说明',
        autoSize: { minRows: 2, maxRows: 4 },
      },
      formItemClass: 'col-span-2',
    },
    {
      fieldName: 'remark',
      label: '备注',
      component: 'Textarea',
      componentProps: {
        placeholder: '请输入备注',
        autoSize: { minRows: 2, maxRows: 4 },
      },
    },
    {
      fieldName: 'fileUrl',
      label: '附件',
      component: 'FileUpload',
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
      formItemClass: 'col-span-3',
    },
  ];
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'no',
      label: '检验单号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入检验单号',
        allowClear: true,
      },
    },
    {
      fieldName: 'purchaseOrderNo',
      label: '采购订单号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入采购订单号',
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
      fieldName: 'result',
      label: '检验结论',
      component: 'Select',
      componentProps: {
        placeholder: '请选择检验结论',
        allowClear: true,
        options: inspectResultOptions,
      },
    },
    {
      fieldName: 'status',
      label: '审批状态',
      component: 'Select',
      componentProps: {
        placeholder: '请选择审批状态',
        allowClear: true,
        options: auditStatusOptions,
      },
    },
    {
      fieldName: 'inspectTime',
      label: '检验时间',
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
      title: '检验单号',
      width: 190,
      fixed: 'left',
    },
    {
      field: 'purchaseOrderNo',
      title: '采购订单号',
      width: 180,
    },
    {
      field: 'productNames',
      title: '检验物料',
      minWidth: 180,
      showOverflow: 'tooltip',
    },
    {
      field: 'supplierId',
      title: '供应商编号',
      width: 110,
    },
    {
      field: 'inspectTime',
      title: '检验时间',
      width: 170,
      formatter: 'formatDateTime',
    },
    {
      field: 'inspectType',
      title: '检验方式',
      width: 100,
      formatter: ({ cellValue }: any) => formatInspectType(cellValue),
    },
    {
      field: 'result',
      title: '检验结论',
      width: 110,
      formatter: ({ cellValue }: any) => formatInspectResult(cellValue),
    },
    {
      field: 'creatorName',
      title: '创建人',
      width: 110,
    },
    {
      field: 'status',
      title: '审批状态',
      width: 110,
      formatter: ({ cellValue }: any) => formatAuditStatus(cellValue),
    },
    {
      field: 'createTime',
      title: '创建时间',
      width: 180,
      formatter: 'formatDateTime',
    },
    {
      title: '操作',
      width: 300,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
