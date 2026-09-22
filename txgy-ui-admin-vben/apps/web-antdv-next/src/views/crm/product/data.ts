import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'name',
      label: '产品名称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入产品名称',
        allowClear: true,
      },
    },
  ];
}

/** 列表的字段：数据全部来自 WMS 仓库库存，只读 */
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'id',
      title: '产品编号',
      visible: false,
    },
    {
      field: 'name',
      title: '产品名称',
      minWidth: 240,
      slots: { default: 'name' },
    },
    {
      field: 'unitName',
      title: '单位',
      minWidth: 100,
    },
    {
      field: 'no',
      title: '产品编码',
      minWidth: 140,
    },
    {
      field: 'stockQuantity',
      title: 'WMS 库存数量',
      minWidth: 130,
    },
    {
      field: 'price',
      title: '价格（元）',
      formatter: 'formatAmount2',
      minWidth: 120,
    },
    {
      field: 'status',
      title: '上架状态',
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.CRM_PRODUCT_STATUS },
      },
      minWidth: 120,
    },
    {
      field: 'wmsItemId',
      title: 'WMS 商品编号',
      minWidth: 130,
    },
    {
      field: 'wmsSkuId',
      title: 'WMS 规格编号',
      minWidth: 130,
    },
    {
      field: 'wmsSkuCode',
      title: 'WMS 规格编码',
      minWidth: 140,
    },
    {
      field: 'dataSource',
      title: '数据来源',
      minWidth: 110,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.CRM_PRODUCT_DATA_SOURCE },
      },
    },
    {
      field: 'description',
      title: '产品描述',
      minWidth: 200,
    },
  ];
}
