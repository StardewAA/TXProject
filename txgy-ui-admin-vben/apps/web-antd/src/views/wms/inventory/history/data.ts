import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';

import { getSimpleUserList } from '#/api/system/user';
import { getWarehouseSimpleList } from '#/api/wms/md/warehouse';
import { getRangePickerDefaultProps } from '#/utils';

/** 搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'orderType',
      label: '单据类型',
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: getDictOptions(DICT_TYPE.WMS_ORDER_TYPE, 'number'),
        placeholder: '请选择单据类型',
      },
    },
    {
      fieldName: 'orderNo',
      label: '单据号',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入单据号',
      },
    },
    {
      fieldName: 'warehouseId',
      label: '仓库',
      component: 'ApiSelect',
      componentProps: {
        allowClear: true,
        api: getWarehouseSimpleList,
        labelField: 'name',
        placeholder: '请选择仓库',
        showSearch: true,
        valueField: 'id',
      },
    },
    {
      fieldName: 'itemCode',
      label: '商品编号',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入商品编号',
      },
    },
    {
      fieldName: 'itemName',
      label: '商品名称',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入商品名称',
      },
    },
    {
      fieldName: 'skuCode',
      label: '规格编号',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入规格编号',
      },
    },
    {
      fieldName: 'skuName',
      label: '规格名称',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入规格名称',
      },
    },
    {
      fieldName: 'creator',
      label: '操作人',
      component: 'ApiSelect',
      componentProps: {
        allowClear: true,
        api: getSimpleUserList,
        labelField: 'nickname',
        placeholder: '请选择操作人',
        showSearch: true,
        valueField: 'id',
      },
    },
    {
      fieldName: 'createTime',
      label: '操作时间',
      component: 'RangePicker',
      componentProps: {
        ...getRangePickerDefaultProps(),
        allowClear: true,
      },
    },
  ];
}

/** 库存流水列表字段 */
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'orderNo',
      title: '单据号',
      width: 180,
      fixed: 'left',
    },
    {
      field: 'orderType',
      title: '单据类型',
      width: 110,
      fixed: 'left',
      align: 'center',
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.WMS_ORDER_TYPE },
      },
    },
    {
      field: 'itemInfo',
      title: '商品信息',
      minWidth: 220,
      slots: { default: 'itemInfo' },
    },
    {
      field: 'skuInfo',
      title: '规格信息',
      minWidth: 220,
      slots: { default: 'skuInfo' },
    },
    {
      field: 'warehouseName',
      title: '仓库',
      minWidth: 160,
    },
    {
      field: 'beforeQuantity',
      title: '操作前',
      align: 'right',
      minWidth: 110,
      slots: { default: 'beforeQuantity' },
    },
    {
      field: 'afterQuantity',
      title: '操作后',
      align: 'right',
      minWidth: 110,
      slots: { default: 'afterQuantity' },
    },
    {
      field: 'quantityPrice',
      title: '数量/金额(元)',
      minWidth: 180,
      slots: { default: 'quantityPrice' },
    },
    {
      field: 'unit',
      title: '计量单位',
      width: 100,
      align: 'center',
    },
    {
      field: 'creatorName',
      title: '操作人',
      width: 120,
      align: 'center',
      formatter: ({ cellValue, row }) => cellValue || row.creator || '-',
    },
    {
      field: 'remark',
      title: '备注',
      minWidth: 180,
      showOverflow: true,
    },
    {
      field: 'actions',
      title: '操作',
      width: 100,
      fixed: 'right',
      align: 'center',
      slots: { default: 'actions' },
    },
  ];
}
