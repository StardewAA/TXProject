import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import {
  LOGISTICS_STATUS_OPTIONS,
  SOURCE_ORDER_TYPE_OPTIONS,
  getSourceOrderTypeText,
} from '#/api/erp/logistics';

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'logisticsTaskNo',
      label: '物流任务号',
      component: 'Input',
      componentProps: { allowClear: true, placeholder: '请输入物流任务号' },
    },
    {
      fieldName: 'waybillNo',
      label: '运单号',
      component: 'Input',
      componentProps: { allowClear: true, placeholder: '请输入运单号' },
    },
    {
      fieldName: 'sourceOrderType',
      label: '来源类型',
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: SOURCE_ORDER_TYPE_OPTIONS,
        placeholder: '请选择来源类型',
      },
    },
    {
      fieldName: 'sourceOrderNo',
      label: '来源单号',
      component: 'Input',
      componentProps: { allowClear: true, placeholder: '请输入来源单号' },
    },
    {
      fieldName: 'logisticsStatus',
      label: '物流状态',
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: LOGISTICS_STATUS_OPTIONS,
        placeholder: '请选择物流状态',
      },
    },
    {
      fieldName: 'hasException',
      label: '存在异常',
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: [
          { label: '是', value: true },
          { label: '否', value: false },
        ],
        placeholder: '请选择',
      },
    },
  ];
}

export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    { field: 'logisticsTaskNo', title: '物流任务号', minWidth: 180, fixed: 'left' },
    { field: 'waybillNo', title: '运单号', minWidth: 160 },
    {
      field: 'sourceOrderType',
      title: '来源类型',
      width: 120,
      formatter: ({ cellValue }) => getSourceOrderTypeText(cellValue),
    },
    { field: 'sourceOrderNo', title: '来源单号', minWidth: 160 },
    { field: 'carrierName', title: '承运商', minWidth: 140 },
    { field: 'logisticsStatus', title: '物流状态', width: 110, slots: { default: 'status' } },
    { field: 'latestNodeName', title: '最新节点', minWidth: 120 },
    { field: 'latestLocation', title: '当前位置', minWidth: 180, showOverflow: 'tooltip' },
    { field: 'estimatedArrivalTime', title: '预计到达', width: 160, formatter: 'formatDate' },
    { field: 'latestNodeTime', title: '节点时间', width: 160, formatter: 'formatDate' },
    { field: 'hasException', title: '异常', width: 75, slots: { default: 'exception' } },
    { title: '操作', width: 90, fixed: 'right', slots: { default: 'actions' } },
  ];
}
