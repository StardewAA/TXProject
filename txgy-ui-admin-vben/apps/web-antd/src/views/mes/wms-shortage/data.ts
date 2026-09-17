import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import type { WmsShortageApi } from '#/api/mes/wms-shortage';

/** 缺料状态选项（后端无字典，前端静态） */
export const SHORTAGE_STATUS_OPTIONS = [
  { label: '缺料中', value: 'OPEN', color: 'error' },
  { label: '已补足', value: 'RESOLVED', color: 'success' },
  { label: '已取消', value: 'CANCELLED', color: 'default' },
];

/** 列表的字段 */
export function useGridColumns(): VxeTableGridOptions<WmsShortageApi.Message>['columns'] {
  return [
    { field: 'unread', title: '', width: 40, slots: { default: 'unread' } },
    { field: 'skuName', title: '成品物料', minWidth: 150 },
    { field: 'skuCode', title: '物料编码', minWidth: 130 },
    { field: 'sourceOrderNo', title: '来源订单号', minWidth: 160 },
    { field: 'warehouseName', title: '仓库', minWidth: 110 },
    { field: 'requestedQuantity', title: '需求数量', width: 100, align: 'right' },
    { field: 'currentQuantity', title: '现存数量', width: 100, align: 'right' },
    { field: 'shortageQuantity', title: '短缺数量', width: 100, align: 'right' },
    { field: 'unit', title: '单位', width: 70 },
    { field: 'shortageStatus', title: '状态', width: 90, slots: { default: 'status' } },
    { field: 'occurredAt', title: '发生时间', minWidth: 160 },
    { title: '操作', width: 100, fixed: 'right', slots: { default: 'actions' } },
  ];
}
