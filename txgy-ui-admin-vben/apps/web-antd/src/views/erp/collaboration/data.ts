import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { getRangePickerDefaultProps } from '#/utils';

import {
  BUSINESS_STATUS_OPTIONS,
  COLLABORATION_TYPE_OPTIONS,
} from '#/api/erp/collaboration';

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'collaborationNo',
      label: '协同单号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入协同单号',
        allowClear: true,
      },
    },
    {
      fieldName: 'sourceOrderNo',
      label: '来源单号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入来源单号',
        allowClear: true,
      },
    },
    {
      fieldName: 'businessChainNo',
      label: '业务链号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入业务链号',
        allowClear: true,
      },
    },
    {
      fieldName: 'collaborationType',
      label: '协同单类型',
      component: 'Select',
      componentProps: {
        options: COLLABORATION_TYPE_OPTIONS,
        placeholder: '请选择类型',
        allowClear: true,
      },
    },
    {
      fieldName: 'businessStatus',
      label: '业务状态',
      component: 'Select',
      componentProps: {
        options: BUSINESS_STATUS_OPTIONS,
        placeholder: '请选择状态',
        allowClear: true,
      },
    },
    {
      fieldName: 'createTime',
      label: '接收时间',
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
      field: 'collaborationNo',
      title: '协同单号',
      width: 200,
      fixed: 'left',
    },
    {
      field: 'collaborationType',
      title: '类型',
      minWidth: 130,
      formatter: ({ cellValue }) =>
        COLLABORATION_TYPE_OPTIONS.find((item) => item.value === cellValue)
          ?.label ?? cellValue,
    },
    {
      field: 'sourceOrderNo',
      title: '来源单号',
      minWidth: 150,
      showOverflow: 'tooltip',
    },
    {
      field: 'sourceProductionOrderNo',
      title: '来源生产工单',
      minWidth: 150,
      showOverflow: 'tooltip',
    },
    {
      field: 'sourceEnterpriseCode',
      title: '发起方',
      minWidth: 130,
      showOverflow: 'tooltip',
    },
    {
      field: 'businessStatus',
      title: '业务状态',
      width: 110,
      slots: { default: 'businessStatus' },
    },
    {
      field: 'requiredAt',
      title: '期望交期',
      width: 150,
      formatter: 'formatDate',
    },
    {
      field: 'purchaseOrderNo',
      title: '关联采购订单',
      minWidth: 170,
      showOverflow: 'tooltip',
    },
    {
      field: 'confirmUserName',
      title: '确认人',
      minWidth: 100,
    },
    {
      field: 'eventVersion',
      title: '事件版本',
      width: 90,
    },
    {
      field: 'createTime',
      title: '接收时间',
      width: 160,
      formatter: 'formatDate',
    },
    {
      title: '操作',
      width: 240,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
