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
        【工单超时预警】客户{{ row.customerName ?? '-' }}工单长时间未处理，请跟进
      </span>
    </template>
    <template #no="{ row }">
      <Button type="link" @click="handleTicketDetail(row)">
        {{ row.no }}
      </Button>
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
