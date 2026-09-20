<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { ErpLogisticsApi } from '#/api/erp/logistics';

import { Page, useVbenModal } from '@vben/common-ui';
import { Tag } from 'ant-design-vue';

import { TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  getLogisticsPage,
  getLogisticsStatusColor,
  getLogisticsStatusText,
} from '#/api/erp/logistics';

import { useGridColumns, useGridFormSchema } from './data';
import Detail from './modules/detail.vue';
import EventLog from './modules/event-log.vue';

defineOptions({ name: 'ErpLogistics' });

const [DetailModal, detailModalApi] = useVbenModal({
  connectedComponent: Detail,
  destroyOnClose: true,
});

const [EventLogModal, eventLogModalApi] = useVbenModal({
  connectedComponent: EventLog,
  destroyOnClose: true,
});

function handleDetail(row: ErpLogisticsApi.Task) {
  detailModalApi.setData({ id: row.id }).open();
}

function handleEventLog() {
  eventLogModalApi.open();
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: { schema: useGridFormSchema() },
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => getLogisticsPage({
          pageNo: page.currentPage,
          pageSize: page.pageSize,
          ...formValues,
        }),
      },
    },
    rowConfig: { isHover: true, keyField: 'id' },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<ErpLogisticsApi.Task>,
});
</script>

<template>
  <Page auto-content-height>
    <DetailModal @success="gridApi.query()" />
    <EventLogModal />
    <Grid table-title="物流任务">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '事件补偿',
              type: 'primary',
              icon: 'ep:refresh-right',
              auth: ['erp:logistics:compensate'],
              onClick: handleEventLog,
            },
          ]"
        />
      </template>
      <template #status="{ row }">
        <Tag :color="getLogisticsStatusColor(row.logisticsStatus)">
          {{ getLogisticsStatusText(row.logisticsStatus) }}
        </Tag>
      </template>
      <template #exception="{ row }">
        <Tag :color="row.hasException ? 'error' : 'default'">
          {{ row.hasException ? '有' : '无' }}
        </Tag>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '详情',
              type: 'link',
              icon: 'ep:view',
              auth: ['erp:logistics:track'],
              onClick: handleDetail.bind(null, row),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
