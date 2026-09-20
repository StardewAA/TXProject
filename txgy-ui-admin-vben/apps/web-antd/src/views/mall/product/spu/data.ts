import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { handleTree, treeToString } from '@vben/utils';

import { getCategoryList } from '#/api/mall/product/category';
import { getShopList } from '#/api/mall/product/shop';
import { getRangePickerDefaultProps } from '#/utils';

import { reviewStatusOptions, reviewStatusText } from './review-model';

/** 关联数据 */
let categoryList: any[] = [];
let shopList: any[] = [];
getCategoryList({}).then((data) => {
  categoryList = handleTree(data, 'id', 'parentId', 'children');
});
getShopList().then((data) => {
  shopList = data;
});

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'name',
      label: '商品名称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入商品名称',
        allowClear: true,
      },
    },
    {
      fieldName: 'categoryId',
      label: '商品分类',
      component: 'ApiTreeSelect',
      componentProps: {
        placeholder: '请选择商品分类',
        allowClear: true,
        options: categoryList,
        fieldNames: { label: 'name', value: 'id', children: 'children' },
      },
    },
    {
      fieldName: 'shopId',
      label: '所属店铺',
      component: 'ApiSelect',
      componentProps: {
        api: async () => shopList,
        labelField: 'name',
        valueField: 'id',
        allowClear: true,
        placeholder: '请选择所属店铺',
      },
    },
    {
      fieldName: 'reviewStatus',
      label: '审核状态',
      component: 'Select',
      componentProps: {
        options: reviewStatusOptions,
        allowClear: true,
        placeholder: '全部审核状态（含历史过渡）',
      },
    },
    {
      fieldName: 'createTime',
      label: '创建时间',
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
      field: 'id',
      title: '商品编号',
      fixed: 'left',
      minWidth: 100,
    },
    {
      field: 'name',
      title: '商品名称',
      fixed: 'left',
      minWidth: 200,
    },
    {
      field: 'picUrl',
      title: '商品图片',
      minWidth: 100,
      cellRender: {
        name: 'CellImage',
      },
    },
    {
      field: 'categoryId',
      title: '商品分类',
      minWidth: 150,
      formatter: ({ row }) => {
        return treeToString(categoryList, row.categoryId);
      },
    },
    {
      field: 'shopId',
      title: '所属店铺',
      minWidth: 150,
      formatter: ({ cellValue }) => {
        return shopList.find((item) => item.id === cellValue)?.name
          ?? (cellValue ? `店铺 #${cellValue}（信息不可用）` : '未关联店铺');
      },
    },
    {
      field: 'status',
      title: '销售状态',
      minWidth: 100,
      formatter: ({ cellValue }) => (cellValue === 1 ? '上架' : cellValue === 0 ? '下架' : cellValue === -1 ? '回收站' : '未知'),
    },
    {
      field: 'reviewStatus',
      title: '审核状态',
      minWidth: 170,
      formatter: ({ cellValue }) => reviewStatusText(cellValue),
    },
    {
      field: 'contentVersion',
      title: '内容版本',
      minWidth: 100,
      formatter: ({ cellValue }) => cellValue == null ? '历史版本' : `V${cellValue}`,
    },
    {
      field: 'price',
      title: '价格(元)',
      minWidth: 100,
      formatter: 'formatFenToYuanAmount',
    },
    {
      field: 'marketPrice',
      title: '市场价(元)',
      minWidth: 100,
      formatter: 'formatFenToYuanAmount',
    },
    {
      field: 'costPrice',
      title: '成本价(元)',
      minWidth: 100,
      formatter: 'formatFenToYuanAmount',
    },
    {
      field: 'salesCount',
      title: '销量',
      minWidth: 80,
    },
    {
      field: 'virtualSalesCount',
      title: '虚拟销量',
      minWidth: 100,
    },
    {
      field: 'stock',
      title: '库存',
      minWidth: 80,
    },
    {
      field: 'browseCount',
      title: '浏览量',
      minWidth: 100,
    },
    {
      field: 'sort',
      title: '排序',
      minWidth: 80,
    },
    {
      field: 'createTime',
      title: '创建时间',
      minWidth: 160,
      formatter: 'formatDateTime',
    },
    {
      title: '操作',
      width: 390,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
