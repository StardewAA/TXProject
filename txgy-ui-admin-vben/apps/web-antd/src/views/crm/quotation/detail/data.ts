import type { DescriptionItemSchema } from '#/components/description';

import { h } from 'vue';

import { DICT_TYPE } from '@vben/constants';
import { erpPriceInputFormatter, formatDateTime } from '@vben/utils';

import { DictTag } from '#/components/dict-tag';

/** 详情头部的配置 */
export function useDetailSchema(): DescriptionItemSchema[] {
  return [
    {
      field: 'customerName',
      label: '客户名称',
    },
    {
      field: 'businessName',
      label: '商机名称',
    },
    {
      field: 'totalPrice',
      label: '报价金额（元）',
      render: (val) => erpPriceInputFormatter(val) as string,
    },
    {
      field: 'orderDate',
      label: '报价日期',
      render: (val) => formatDateTime(val) as string,
    },
    {
      field: 'ownerUserName',
      label: '负责人',
    },
    {
      field: 'auditStatus',
      label: '审批状态',
      render: (val) =>
        h(DictTag, {
          type: DICT_TYPE.CRM_AUDIT_STATUS,
          value: val,
        }),
    },
  ];
}

/** 详情基本信息的配置 */
export function useDetailBaseSchema(): DescriptionItemSchema[] {
  return [
    {
      field: 'no',
      label: '报价单号',
    },
    {
      field: 'quotationType',
      label: '报价类型',
      render: (val) =>
        h(DictTag, {
          type: DICT_TYPE.CRM_QUOTATION_TYPE,
          value: val,
        }),
    },
    {
      field: 'name',
      label: '报价名称',
    },
    {
      field: 'customerName',
      label: '客户名称',
    },
    {
      field: 'businessName',
      label: '商机名称',
    },
    {
      field: 'totalPrice',
      label: '报价金额（元）',
      render: (val) => erpPriceInputFormatter(val) as string,
    },
    {
      field: 'totalProductPrice',
      label: '产品总金额（元）',
      render: (val) => erpPriceInputFormatter(val) as string,
    },
    {
      field: 'discountPercent',
      label: '整单折扣（%）',
      render: (val) => erpPriceInputFormatter(val) as string,
    },
    {
      field: 'orderDate',
      label: '报价日期',
      render: (val) => formatDateTime(val) as string,
    },
    {
      field: 'validUntil',
      label: '有效期至',
      render: (val) => formatDateTime(val) as string,
    },
    {
      field: 'contractId',
      label: '关联合同编号',
    },
    {
      field: 'remark',
      label: '备注',
    },
    {
      field: 'auditStatus',
      label: '审批状态',
      render: (val) =>
        h(DictTag, {
          type: DICT_TYPE.CRM_AUDIT_STATUS,
          value: val,
        }),
    },
  ];
}
