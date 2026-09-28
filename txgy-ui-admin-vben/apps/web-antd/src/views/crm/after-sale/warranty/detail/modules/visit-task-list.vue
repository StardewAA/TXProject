<!-- 质保档案详情 - 回访任务（只读） -->
<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CrmAfterSaleVisitTaskApi } from '#/api/crm/after-sale/task';

import { DICT_TYPE } from '@vben/constants';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getAfterSaleVisitTaskSimpleList } from '#/api/crm/after-sale/task';

const props = defineProps<{ warrantyId: number }>();

const [Grid] = useVbenVxeGrid({
  gridOptions: {
    columns: [
      {
        field: 'bizNo',
        title: '业务编号',
        width: 100,
      },
      {
        field: 'taskDate',
        title: '计划回访日期',
        minWidth: 140,
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
        title: '处理人',
        minWidth: 120,
      },
      {
        field: 'finishTime',
        title: '完成时间',
        formatter: 'formatDateTime',
        minWidth: 180,
      },
      {
        field: 'remark',
        title: '回访提示',
        minWidth: 200,
      },
    ],
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async () => {
          const list = await getAfterSaleVisitTaskSimpleList(
            props.warrantyId,
          );
          return { list: list ?? [], total: (list ?? []).length };
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
  <Grid table-title="回访任务" />
</template>
