import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

const sourceOptions = [
  { label: '全部来源', value: '' },
  { label: 'WMS 实物仓', value: 'WMS' },
  { label: '商城已映射', value: 'MALL' },
  { label: 'ERP 已匹配', value: 'ERP' },
];

export function useWarehouseFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'code',
      label: '仓库编号',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入仓库编号',
      },
    },
    {
      fieldName: 'name',
      label: '仓库名称',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入仓库名称',
      },
    },
    {
      fieldName: 'sourceSystem',
      label: '关联来源',
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: sourceOptions,
        placeholder: '请选择来源系统',
      },
    },
  ];
}

export function useWarehouseColumns(): VxeTableGridOptions['columns'] {
  return [
    { field: 'warehouseName', title: 'WMS 仓库', minWidth: 160 },
    { field: 'warehouseCode', title: '仓库编号', width: 140 },
    {
      field: 'ownerSystem',
      title: '实物归属',
      width: 110,
      slots: { default: 'ownerSystem' },
    },
    {
      field: 'sourceSystems',
      title: '可见来源',
      minWidth: 180,
      slots: { default: 'sourceSystems' },
    },
    { field: 'wmsSkuCount', title: 'WMS SKU', width: 100, align: 'right' },
    { field: 'mallSkuCount', title: '商城 SKU', width: 100, align: 'right' },
    { field: 'erpProductCount', title: 'ERP 产品', width: 100, align: 'right' },
    {
      field: 'physicalQuantity',
      title: '实物库存',
      width: 110,
      align: 'right',
    },
    {
      field: 'relatedErpWarehouseNames',
      title: 'ERP 对照仓',
      minWidth: 160,
      slots: { default: 'relatedErpWarehouseNames' },
    },
    {
      title: '操作',
      width: 120,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}

export function useProductFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'itemName',
      label: 'WMS 商品',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入商品名称',
      },
    },
    {
      fieldName: 'sourceSystem',
      label: '关联来源',
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: sourceOptions,
        placeholder: '请选择来源系统',
      },
    },
  ];
}

export function useProductColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'wmsItemName',
      title: 'WMS 商品',
      minWidth: 180,
      slots: { default: 'wmsItem' },
    },
    { field: 'wmsSkuName', title: 'WMS SKU', minWidth: 160 },
    { field: 'barCode', title: '条码', width: 150 },
    {
      field: 'physicalQuantity',
      title: '实物库存',
      width: 110,
      align: 'right',
    },
    {
      field: 'sourceSystems',
      title: '来源系统',
      minWidth: 160,
      slots: { default: 'sourceSystems' },
    },
    { field: 'mallSpuName', title: '商城商品', minWidth: 180 },
    { field: 'mallCachedStock', title: '商城缓存库存', width: 120, align: 'right' },
    { field: 'erpProductName', title: 'ERP 产品', minWidth: 180 },
  ];
}
