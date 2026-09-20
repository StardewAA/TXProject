import type { DescriptionItemSchema } from '#/components/description';

import { h } from 'vue';

import { DICT_TYPE } from '@vben/constants';
import { formatDateTime } from '@vben/utils';

import { DictTag } from '#/components/dict-tag';

/** 问题工单详情 - 基本信息 */
export function useDetailSchema(): DescriptionItemSchema[] {
  return [
    {
      field: 'no',
      label: '工单编号',
    },
    {
      field: 'title',
      label: '问题标题',
    },
    {
      field: 'customerName',
      label: '客户名称',
    },
    {
      field: 'warrantyNo',
      label: '质保编号',
    },
    {
      field: 'contractNo',
      label: '合同编号',
    },
    {
      field: 'source',
      label: '工单来源',
      render: (val) =>
        h(DictTag, {
          type: DICT_TYPE.CRM_AFTER_SALE_TICKET_SOURCE,
          value: val,
        }),
    },
    {
      field: 'priority',
      label: '优先级',
      render: (val) =>
        h(DictTag, {
          type: DICT_TYPE.CRM_AFTER_SALE_TICKET_PRIORITY,
          value: val,
        }),
    },
    {
      field: 'status',
      label: '工单状态',
      render: (val) =>
        h(DictTag, {
          type: DICT_TYPE.CRM_AFTER_SALE_TICKET_STATUS,
          value: val,
        }),
    },
    {
      field: 'problemType',
      label: '问题类型',
    },
    {
      field: 'contactName',
      label: '现场联系人',
    },
    {
      field: 'contactMobile',
      label: '联系电话',
    },
    {
      field: 'assigneeNickname',
      label: '处理人',
    },
    {
      field: 'creatorNickname',
      label: '创建人',
    },
    {
      field: 'isChargeable',
      label: '是否收费',
      render: (val) => (val ? '是' : '否'),
    },
    {
      field: 'createTime',
      label: '创建时间',
      render: (val) => formatDateTime(val) as string,
    },
    {
      field: 'startTime',
      label: '开始处理时间',
      render: (val) => formatDateTime(val) as string,
    },
    {
      field: 'finishTime',
      label: '处理完成时间',
      render: (val) => formatDateTime(val) as string,
    },
    {
      field: 'closeTime',
      label: '关闭时间',
      render: (val) => formatDateTime(val) as string,
    },
    {
      field: 'description',
      label: '问题描述',
    },
    {
      field: 'handleResult',
      label: '处理结果',
    },
    {
      field: 'remark',
      label: '备注',
    },
  ];
}
