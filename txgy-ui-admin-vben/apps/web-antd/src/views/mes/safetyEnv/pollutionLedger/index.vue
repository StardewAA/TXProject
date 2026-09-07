<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { MesPollutionLedgerApi } from '#/api/mes/safetyEnv/pollutionLedger';

import { Page, useVbenDrawer, useVbenModal } from '@vben/common-ui';

import { Tag } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { getPollutionLedgerPage } from '#/api/mes/safetyEnv/pollutionLedger';
import { STAGE_MAP } from '../pollutionCheck/data';

import { LEDGER_STATUS_MAP, useGridColumns, useGridFormSchema } from './data';
import HistoryDrawer from './modules/history-drawer.vue';
import StatusModal from './modules/status-modal.vue';

const [StatusModalComp, statusModalApi] = useVbenModal({
  connectedComponent: StatusModal,
  destroyOnClose: true,
});

const [HistoryDrawerComp, historyDrawerApi] = useVbenDrawer({
  connectedComponent: HistoryDrawer,
  destroyOnClose: true,
});

/** 打开台账流转历史(全链追溯) */
function handleHistory(row: MesPollutionLedgerApi.Ledger) {
  historyDrawerApi.setData({ row }).open();
}

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 处置流转(暂存/处置中 → 终态) */
function handleFlow(row: MesPollutionLedgerApi.Ledger) {
  statusModalApi.setData({ row }).open();
}

/** 闭环状态(处置终态/无污染自动解除)：不再提供处置流转入口 */
const CLOSED_STATUSES = ['REUSED', 'DISCHARGED', 'DISPOSED', 'CLEARED'];

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
  },
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) =>
          await getPollutionLedgerPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
          }),
      },
    },
    rowConfig: {
      keyField: 'id',
      isHover: true,
    },
    toolbarConfig: {
      refresh: true,
      search: true,
    },
  } as VxeTableGridOptions<MesPollutionLedgerApi.Ledger>,
});
</script>
<template>
  <Page auto-content-height>
    <StatusModalComp @success="handleRefresh" />
    <HistoryDrawerComp />
    <Grid table-title="污染/危废暂存台账">
      <template #stage="{ row }">
        <span>{{ STAGE_MAP[row.stage] ?? row.stage }}</span>
      </template>
      <template #status="{ row }">
        <Tag v-if="row.status" :color="LEDGER_STATUS_MAP[row.status]?.color">
          {{ LEDGER_STATUS_MAP[row.status]?.text ?? row.status }}
        </Tag>
        <span v-else>-</span>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '历史',
              type: 'link',
              icon: ACTION_ICON.BOOK,
              auth: ['mes:set-pollution-check:query'],
              onClick: handleHistory.bind(null, row),
            },
            {
              label: '处置流转',
              type: 'link',
              icon: ACTION_ICON.AUDIT,
              auth: ['mes:set-pollution-check:review'],
              ifShow: () => !CLOSED_STATUSES.includes(row.status ?? ''),
              onClick: handleFlow.bind(null, row),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
