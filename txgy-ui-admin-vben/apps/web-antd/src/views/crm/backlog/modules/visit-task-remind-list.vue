<!-- 任务提醒（待办来源：回访任务）—— 实时查询，不预生成待办记录；含临期与已逾期 -->
<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CrmAfterSaleVisitTaskApi } from '#/api/crm/after-sale/task';

import { DICT_TYPE } from '@vben/constants';

import { useRouter } from 'vue-router';

import { Button } from 'ant-design-vue';

import { TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  AFTER_SALE_VISIT_TASK_REMIND_TYPE_APPROACHING,
  AFTER_SALE_VISIT_TASK_STATUS_OVERDUE,
  getAfterSaleVisitTaskPage,
} from '#/api/crm/after-sale/task';

const { push } = useRouter();

/** 打开回访任务详情（携带任务 ID，跳转售后模块的回访任务详情页） */
function handleTaskDetail(row: CrmAfterSaleVisitTaskApi.VisitTask) {
  push({ name: 'CrmAfterSaleVisitTaskDetail', params: { id: row.id } });
}

/** 待办文案：临期与已逾期分别提示（已逾期需要更强的催促语气） */
function getTodoText(row: CrmAfterSaleVisitTaskApi.VisitTask) {
  const customer = row.customerName ?? '-';
  return row.status === AFTER_SALE_VISIT_TASK_STATUS_OVERDUE
    ? `【任务提醒】客户 ${customer}，回访任务已逾期，请尽快完成回访`
    : `【任务提醒】客户 ${customer}，请在窗口期完成回访`;
}

const columns: VxeTableGridOptions<CrmAfterSaleVisitTaskApi.VisitTask>['columns'] =
  [
    {
      field: 'todoContent',
      title: '待办事项',
      minWidth: 340,
      fixed: 'left',
      slots: { default: 'todoContent' },
    },
    {
      field: 'bizNo',
      title: '业务编号',
      width: 110,
      slots: { default: 'bizNo' },
    },
    {
      field: 'warrantyNo',
      title: '质保编号',
      minWidth: 170,
    },
    {
      field: 'projectName',
      title: '工程项目名称',
      minWidth: 200,
    },
    {
      field: 'customerName',
      title: '客户名称',
      minWidth: 160,
    },
    {
      field: 'taskDate',
      title: '计划回访日期',
      minWidth: 140,
      slots: { default: 'taskDate' },
    },
    {
      field: 'windowEndDate',
      title: '窗口期截止日期',
      minWidth: 150,
    },
    {
      field: 'status',
      title: '任务状态',
      minWidth: 110,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.CRM_AFTER_SALE_VISIT_TASK_STATUS },
      },
    },
    {
      field: 'assigneeNickname',
      title: '归属人（任务负责人）',
      minWidth: 160,
    },
    {
      title: '操作',
      width: 120,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];

const [Grid] = useVbenVxeGrid({
  gridOptions: {
    columns,
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }) => {
          // 实时查询：任务状态 ∈ {待处理, 已逾期} + 本人及下属负责；
          // 其中待处理还需「计划回访日在临期提醒天数内」，已逾期无条件纳入，
          // 临期提醒天数由后端读取售后设置，改动后刷新即生效
          return await getAfterSaleVisitTaskPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            remindType: AFTER_SALE_VISIT_TASK_REMIND_TYPE_APPROACHING,
          });
        },
      },
    },
    rowConfig: {
      keyField: 'id',
      isHover: true,
    },
    toolbarConfig: {
      refresh: true,
      search: false,
    },
  } as VxeTableGridOptions<CrmAfterSaleVisitTaskApi.VisitTask>,
});
</script>

<template>
  <Grid>
    <template #todoContent="{ row }">
      <span
        :class="
          row.status === AFTER_SALE_VISIT_TASK_STATUS_OVERDUE
            ? 'text-red-500'
            : 'text-gray-700 dark:text-gray-200'
        "
      >
        {{ getTodoText(row) }}
      </span>
    </template>
    <template #bizNo="{ row }">
      <Button type="link" @click="handleTaskDetail(row)">
        {{ row.bizNo ?? row.id }}
      </Button>
    </template>
    <template #taskDate="{ row }">
      <span class="font-medium">{{ row.taskDate ?? '-' }}</span>
    </template>
    <template #actions="{ row }">
      <TableAction
        :actions="[
          {
            label: '查看任务',
            type: 'link',
            auth: ['crm:after-sale-visit-task:query'],
            onClick: handleTaskDetail.bind(null, row),
          },
        ]"
      />
    </template>
  </Grid>
</template>
