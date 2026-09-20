import type { DescriptionItemSchema } from '#/components/description';

import { h } from 'vue';

import { DICT_TYPE } from '@vben/constants';
import { formatDateTime } from '@vben/utils';

import { DictTag } from '#/components/dict-tag';

/** 回访任务详情 - 基本信息 */
export function useDetailSchema(): DescriptionItemSchema[] {
  return [
    {
      field: 'bizNo',
      label: '业务编号',
    },
    {
      field: 'warrantyNo',
      label: '质保编号',
    },
    {
      field: 'projectName',
      label: '工程项目名称',
    },
    {
      field: 'contractName',
      label: '合同名称',
    },
    {
      field: 'customerName',
      label: '客户名称',
    },
    {
      field: 'taskDate',
      label: '计划回访日期',
    },
    {
      field: 'windowEndDate',
      label: '窗口期截止日期',
    },
    {
      field: 'status',
      label: '任务状态',
      render: (val) =>
        h(DictTag, {
          type: DICT_TYPE.CRM_AFTER_SALE_VISIT_TASK_STATUS,
          value: val,
        }),
    },
    {
      field: 'assigneeNickname',
      label: '处理人',
    },
    {
      field: 'finishTime',
      label: '完成时间',
      render: (val) => formatDateTime(val) as string,
    },
    {
      field: 'remark',
      label: '回访提示',
    },
    {
      field: 'createTime',
      label: '创建时间',
      render: (val) => formatDateTime(val) as string,
    },
  ];
}

/** 回访任务详情 - 关联回访记录 */
export function useRecordSchema(): DescriptionItemSchema[] {
  return [
    {
      field: 'status',
      label: '记录状态',
      // 1=待填写 2=已填写（该字段无字典，按文案展示）
      render: (val) => (val === 2 ? '已填写' : '待填写'),
    },
    {
      field: 'visitTime',
      label: '回访时间',
      render: (val) => formatDateTime(val) as string,
    },
    {
      field: 'visitType',
      label: '回访方式',
      render: (val) =>
        h(DictTag, {
          type: DICT_TYPE.CRM_AFTER_SALE_VISIT_TYPE,
          value: val,
        }),
    },
    {
      field: 'satisfactionLevel',
      label: '满意度',
      render: (val) =>
        h(DictTag, {
          type: DICT_TYPE.CRM_AFTER_SALE_SATISFACTION,
          value: val,
        }),
    },
    {
      field: 'nextVisitDate',
      label: '建议下次回访日期',
    },
    {
      field: 'content',
      label: '回访内容',
    },
    {
      field: 'satisfactionRemark',
      label: '满意度备注',
    },
  ];
}
