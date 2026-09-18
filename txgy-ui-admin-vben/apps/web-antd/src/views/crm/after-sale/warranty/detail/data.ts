import type { DescriptionItemSchema } from '#/components/description';

import { h } from 'vue';

import { DICT_TYPE } from '@vben/constants';
import { formatDateTime } from '@vben/utils';

import { DictTag } from '#/components/dict-tag';

/** 质保档案详情 - 基本信息 */
export function useDetailSchema(): DescriptionItemSchema[] {
  return [
    {
      field: 'no',
      label: '质保编号',
    },
    {
      field: 'customerName',
      label: '客户名称',
    },
    {
      field: 'contractNo',
      label: '合同编号',
    },
    {
      field: 'contractName',
      label: '合同名称',
    },
    {
      field: 'projectName',
      label: '工程项目名称',
    },
    {
      field: 'projectAcceptDate',
      label: '完工验收日期',
    },
    {
      field: 'warrantyYears',
      label: '质保年限（年）',
    },
    {
      field: 'warrantyEndDate',
      label: '质保到期日期',
    },
    {
      field: 'remainDays',
      label: '剩余天数',
    },
    {
      field: 'status',
      label: '质保状态',
      render: (val) =>
        h(DictTag, {
          type: DICT_TYPE.CRM_AFTER_SALE_WARRANTY_STATUS,
          value: val,
        }),
    },
    {
      field: 'ownerUserName',
      label: '业务负责人',
    },
    {
      field: 'serviceUserName',
      label: '售后责任人',
    },
    {
      field: 'warrantyScope',
      label: '质保范围',
    },
    {
      field: 'remark',
      label: '备注',
    },
    {
      field: 'createTime',
      label: '创建时间',
      render: (val) => formatDateTime(val) as string,
    },
  ];
}
