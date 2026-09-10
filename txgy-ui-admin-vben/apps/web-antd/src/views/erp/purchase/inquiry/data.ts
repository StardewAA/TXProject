import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import { DICT_TYPE } from '@vben/constants';
export const businessOptions = [
  { label: '草稿', value: 0 },
  { label: '待发布', value: 10 },
  { label: '报价中', value: 20 },
  { label: '比价中', value: 40 },
  { label: '定标通过', value: 50 },
  { label: '已生成订单', value: 60 },
];
export const inquiryFormSchema = (): VbenFormSchema[] => [
  {
    fieldName: 'no',
    label: '询价编号',
    component: 'Input',
    componentProps: { allowClear: true },
  },
  {
    fieldName: 'title',
    label: '询价标题',
    component: 'Input',
    componentProps: { allowClear: true },
  },
  {
    fieldName: 'businessStatus',
    label: '业务状态',
    component: 'Select',
    componentProps: { allowClear: true, options: businessOptions },
  },
];
export const inquiryColumns = (): VxeTableGridOptions['columns'] => [
  { field: 'no', title: '询价编号', width: 190, fixed: 'left' },
  { field: 'title', title: '询价标题', minWidth: 180 },
  { field: 'purchaseRequestNo', title: '来源申请', width: 190 },
  {
    field: 'productNames',
    title: '询价物料',
    minWidth: 160,
    showOverflow: 'tooltip',
  },
  { field: 'deadline', title: '报价截止', width: 160, formatter: 'formatDate' },
  { field: 'quoteCount', title: '报价数', width: 80 },
  {
    field: 'businessStatus',
    title: '业务状态',
    width: 110,
    formatter: ({ cellValue }: any) =>
      businessOptions.find((x) => x.value === cellValue)?.label ?? '-',
  },
  {
    field: 'status',
    title: '询价审批',
    width: 110,
    cellRender: {
      name: 'CellDict',
      props: { type: DICT_TYPE.ERP_AUDIT_STATUS },
    },
  },
  {
    field: 'awardStatus',
    title: '定标审批',
    width: 110,
    cellRender: {
      name: 'CellDict',
      props: { type: DICT_TYPE.ERP_AUDIT_STATUS },
    },
  },
  { title: '操作', width: 340, fixed: 'right', slots: { default: 'actions' } },
];
