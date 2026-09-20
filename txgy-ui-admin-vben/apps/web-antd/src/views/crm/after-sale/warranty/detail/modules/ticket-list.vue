<!-- 质保档案详情 - 售后问题工单（只读） -->
<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CrmAfterSaleTicketApi } from '#/api/crm/after-sale/ticket';

import { DICT_TYPE } from '@vben/constants';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getAfterSaleTicketPage } from '#/api/crm/after-sale/ticket';

const props = defineProps<{ warrantyId: number }>();

const [Grid] = useVbenVxeGrid({
  gridOptions: {
    columns: [
      {
        field: 'no',
        title: '工单编号',
        minWidth: 170,
      },
      {
        field: 'title',
        title: '问题标题',
        minWidth: 200,
      },
      {
        field: 'priority',
        title: '优先级',
        minWidth: 110,
        cellRender: {
          name: 'CellDict',
          props: { type: DICT_TYPE.CRM_AFTER_SALE_TICKET_PRIORITY },
        },
      },
      {
        field: 'status',
        title: '工单状态',
        minWidth: 120,
        cellRender: {
          name: 'CellDict',
          props: { type: DICT_TYPE.CRM_AFTER_SALE_TICKET_STATUS },
        },
      },
      {
        field: 'assigneeNickname',
        title: '处理人',
        minWidth: 120,
      },
      {
        field: 'createTime',
        title: '创建时间',
        formatter: 'formatDateTime',
        minWidth: 180,
      },
    ],
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }) => {
          return await getAfterSaleTicketPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            warrantyId: props.warrantyId,
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
  <Grid table-title="售后工单" />
</template>
