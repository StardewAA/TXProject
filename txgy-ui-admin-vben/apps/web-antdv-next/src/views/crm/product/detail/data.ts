import type { DescriptionItemSchema } from '#/components/description';

import { h } from 'vue';

import { DICT_TYPE } from '@vben/constants';
import { erpPriceInputFormatter } from '@vben/utils';

import { DictTag } from '#/components/dict-tag';

/** 详情页的字段（数据实时取自 WMS 仓库库存） */
export function useDetailSchema(): DescriptionItemSchema[] {
  return [
    {
      field: 'stockQuantity',
      label: 'WMS 库存数量',
    },
    {
      field: 'unitName',
      label: '单位',
    },
    {
      field: 'price',
      label: '产品价格（元）',
      render: (val) => erpPriceInputFormatter(val),
    },
    {
      field: 'no',
      label: '产品编码',
    },
  ];
}

/** 详情页的基础字段（数据实时取自 WMS 仓库库存） */
export function useDetailBaseSchema(): DescriptionItemSchema[] {
  return [
    {
      field: 'name',
      label: '产品名称',
    },
    {
      field: 'no',
      label: '产品编码',
    },
    {
      field: 'price',
      label: '价格（元）',
      render: (val) => erpPriceInputFormatter(val),
    },
    {
      field: 'stockQuantity',
      label: 'WMS 库存数量',
    },
    {
      field: 'description',
      label: '产品描述',
    },
    {
      field: 'status',
      label: '是否上下架',
      render: (val) =>
        h(DictTag, { type: DICT_TYPE.CRM_PRODUCT_STATUS, value: val }),
    },
    {
      field: 'unitName',
      label: '单位',
    },
    {
      field: 'wmsItemId',
      label: 'WMS 商品编号',
    },
    {
      field: 'wmsSkuId',
      label: 'WMS 规格编号',
    },
    {
      field: 'wmsSkuCode',
      label: 'WMS 规格编码',
    },
  ];
}
