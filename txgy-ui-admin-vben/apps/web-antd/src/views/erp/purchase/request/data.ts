import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';
export const requestGridFormSchema = (): VbenFormSchema[] => [
  {
    fieldName: 'no',
    label: '申请编号',
    component: 'Input',
    componentProps: { allowClear: true, placeholder: '请输入申请编号' },
  },
  {
    fieldName: 'purpose',
    label: '采购用途',
    component: 'Input',
    componentProps: { allowClear: true, placeholder: '请输入采购用途' },
  },
  {
    fieldName: 'status',
    label: '审批状态',
    component: 'Select',
    componentProps: {
      allowClear: true,
      options: getDictOptions(DICT_TYPE.ERP_AUDIT_STATUS, 'number'),
    },
  },
];
export const requestGridColumns = (): VxeTableGridOptions['columns'] => [
  { field: 'no', title: '申请编号', width: 190, fixed: 'left' },
  {
    field: 'productNames',
    title: '申请物料',
    minWidth: 180,
    showOverflow: 'tooltip',
  },
  { field: 'purpose', title: '采购用途', minWidth: 160 },
  {
    field: 'requiredDate',
    title: '需求日期',
    width: 160,
    formatter: 'formatDate',
  },
  {
    field: 'totalCount',
    title: '申请数量',
    width: 110,
    formatter: 'formatAmount3',
  },
  {
    field: 'budgetAmount',
    title: '预算金额',
    width: 120,
    formatter: 'formatAmount2',
  },
  { field: 'creatorName', title: '申请人', width: 110 },
  {
    field: 'status',
    title: '审批状态',
    width: 110,
    cellRender: {
      name: 'CellDict',
      props: { type: DICT_TYPE.ERP_AUDIT_STATUS },
    },
  },
  { title: '操作', width: 260, fixed: 'right', slots: { default: 'actions' } },
];
