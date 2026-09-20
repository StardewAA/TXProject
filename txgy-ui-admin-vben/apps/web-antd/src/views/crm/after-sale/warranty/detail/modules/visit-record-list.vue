<!-- 质保档案详情 - 回访记录（只读） -->
<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CrmAfterSaleVisitRecordApi } from '#/api/crm/after-sale/record';

import { DICT_TYPE } from '@vben/constants';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getAfterSaleVisitRecordPage } from '#/api/crm/after-sale/record';

const props = defineProps<{ warrantyId: number }>();

const [Grid] = useVbenVxeGrid({
  gridOptions: {
    columns: [
      {
        field: 'taskBizNo',
        title: '关联任务编号',
        width: 120,
      },
      {
        field: 'visitTime',
        title: '回访时间',
        formatter: 'formatDateTime',
        minWidth: 180,
      },
      {
        field: 'visitType',
        title: '回访方式',
        minWidth: 120,
        cellRender: {
          name: 'CellDict',
          props: { type: DICT_TYPE.CRM_AFTER_SALE_VISIT_TYPE },
        },
      },
      {
        field: 'content',
        title: '回访内容',
        minWidth: 260,
      },
      {
        field: 'satisfactionLevel',
        title: '满意度',
        minWidth: 120,
        cellRender: {
          name: 'CellDict',
          props: { type: DICT_TYPE.CRM_AFTER_SALE_SATISFACTION },
        },
      },
      {
        field: 'nextVisitDate',
        title: '建议下次回访日期',
        minWidth: 160,
      },
      {
        field: 'creatorNickname',
        title: '回访人',
        minWidth: 120,
      },
    ],
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }) => {
          return await getAfterSaleVisitRecordPage({
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
  } as VxeTableGridOptions<CrmAfterSaleVisitRecordApi.VisitRecord>,
});
</script>

<template>
  <Grid table-title="回访记录" />
</template>
