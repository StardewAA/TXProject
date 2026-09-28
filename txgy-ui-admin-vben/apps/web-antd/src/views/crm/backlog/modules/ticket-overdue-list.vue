<!-- 工单超时预警（待办来源：售后工单）—— 实时查询，不预生成待办记录 -->
<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CrmAfterSaleTicketApi } from '#/api/crm/after-sale/ticket';

import { DICT_TYPE } from '@vben/constants';

import { useRouter } from 'vue-router';

import { Button } from 'ant-design-vue';

import { TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  AFTER_SALE_TICKET_REMIND_TYPE_OVERDUE,
  getAfterSaleTicketPage,
} from '#/api/crm/after-sale/ticket';

const { push } = useRouter();

/** 打开问题工单详情（携带工单 ID，跳转售后模块的工单详情页） */
function handleTicketDetail(row: CrmAfterSaleTicketApi.Ticket) {
  push({ name: 'CrmAfterSaleTicketDetail', params: { id: row.id } });
}

/**
 * 超时起算时间：开始处理时间，尚未开始处理时以创建时间兜底
 *
 * 与后端判定口径（COALESCE(start_time, create_time)）保持一致。
 */
function getStartTime(row: CrmAfterSaleTicketApi.Ticket) {
  return row.startTime ?? row.createTime;
}

/**
 * 已超时天数：起算日期距今的日历天数
 *
 * 与后端判定（起算日期 + 超时预警天数 ≤ 今天）同一口径，命中的条目该值 ≥ 预警天数。
 */
function getOverdueDays(row: CrmAfterSaleTicketApi.Ticket) {
  const start = getStartTime(row);
  if (!start) {
    return undefined;
  }
  const time = new Date(start.replace(' ', 'T')).getTime();
  if (Number.isNaN(time)) {
    return undefined;
  }
  const startDay = new Date(time);
  startDay.setHours(0, 0, 0, 0);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return Math.round((today.getTime() - startDay.getTime()) / 86_400_000);
}

const columns: VxeTableGridOptions<CrmAfterSaleTicketApi.Ticket>['columns'] = [
  {
    field: 'todoContent',
    title: '待办事项',
    minWidth: 360,
    fixed: 'left',
    slots: { default: 'todoContent' },
  },
  {
    field: 'no',
    title: '工单编号',
    minWidth: 180,
    slots: { default: 'no' },
  },
  {
    field: 'title',
    title: '问题标题',
    minWidth: 200,
  },
  {
    field: 'customerName',
    title: '客户名称',
    minWidth: 160,
  },
  {
    field: 'warrantyNo',
    title: '质保编号',
    minWidth: 170,
  },
  {
    field: 'priority',
    title: '优先级',
    minWidth: 100,
    cellRender: {
      name: 'CellDict',
      props: { type: DICT_TYPE.CRM_AFTER_SALE_TICKET_PRIORITY },
    },
  },
  {
    field: 'status',
    title: '工单状态',
    minWidth: 110,
    cellRender: {
      name: 'CellDict',
      props: { type: DICT_TYPE.CRM_AFTER_SALE_TICKET_STATUS },
    },
  },
  {
    field: 'startTime',
    title: '开始处理时间',
    minWidth: 170,
    formatter: 'formatDateTime',
  },
  {
    field: 'overdueDays',
    title: '已超时天数',
    minWidth: 110,
    slots: { default: 'overdueDays' },
  },
  {
    field: 'assigneeNickname',
    title: '归属人（工单负责人）',
    minWidth: 160,
  },
  {
    field: 'createTime',
    title: '创建时间',
    formatter: 'formatDateTime',
    minWidth: 180,
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
          // 实时查询：仅「未闭环 + 本人及下属负责 + 创建日期距今 ≥ 超时预警天数」，
          // 超时预警天数由后端读取售后设置，改动后刷新即生效
          return await getAfterSaleTicketPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            remindType: AFTER_SALE_TICKET_REMIND_TYPE_OVERDUE,
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
  } as VxeTableGridOptions<CrmAfterSaleTicketApi.Ticket>,
});
</script>

<template>
  <Grid>
    <template #todoContent="{ row }">
      <span class="text-gray-700 dark:text-gray-200">
        【工单超时预警】客户{{ row.customerName ?? '-' }}的工单已持续
        {{ getOverdueDays(row) ?? '-' }} 天未闭环（自
        {{ getStartTime(row) ?? '-' }} 起算），请尽快跟进
      </span>
    </template>
    <template #no="{ row }">
      <Button type="link" @click="handleTicketDetail(row)">
        {{ row.no }}
      </Button>
    </template>
    <template #overdueDays="{ row }">
      <span class="font-semibold text-red-500">
        {{ getOverdueDays(row) ?? '-' }}
      </span>
    </template>
    <template #actions="{ row }">
      <TableAction
        :actions="[
          {
            label: '查看工单',
            type: 'link',
            auth: ['crm:after-sale-ticket:query'],
            onClick: handleTicketDetail.bind(null, row),
          },
        ]"
      />
    </template>
  </Grid>
</template>
