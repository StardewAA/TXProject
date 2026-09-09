import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';
export const costFormSchema = (): VbenFormSchema[] => [
  {
    fieldName: 'no',
    label: '造价编号',
    component: 'Input',
    componentProps: { allowClear: true },
  },
  {
    fieldName: 'name',
    label: '项目名称',
    component: 'Input',
    componentProps: { allowClear: true },
  },
  {
    fieldName: 'projectCode',
    label: '项目编码',
    component: 'Input',
    componentProps: { allowClear: true },
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
export const costColumns = (): VxeTableGridOptions['columns'] => [
  { field: 'no', title: '造价编号', width: 190, fixed: 'left' },
  { field: 'name', title: '项目名称', minWidth: 180 },
  { field: 'projectCode', title: '项目编码', width: 140 },
  { field: 'costVersion', title: '版本', width: 70 },
  { field: 'itemCount', title: '材料项', width: 80 },
  { field: 'mappedCount', title: '已映射', width: 80 },
  {
    field: 'totalPrice',
    title: '造价合计',
    width: 130,
    formatter: 'formatAmount2',
  },
  {
    field: 'receivedAt',
    title: '接收时间',
    width: 160,
    formatter: 'formatDate',
  },
  {
    field: 'status',
    title: '审批状态',
    width: 110,
    cellRender: {
      name: 'CellDict',
      props: { type: DICT_TYPE.ERP_AUDIT_STATUS },
    },
  },
  {
    field: 'convertedType',
    title: '转换结果',
    width: 100,
    formatter: ({ cellValue }: any) => {
      const labels: Record<number, string> = {
        1: '报价单',
        2: '销售订单',
        3: '采购申请',
        4: '仅归档',
      };
      return labels[Number(cellValue)] ?? '未转换';
    },
  },
  { title: '操作', width: 260, fixed: 'right', slots: { default: 'actions' } },
];
