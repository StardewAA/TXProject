<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { MesSetPollutionCheckApi } from '#/api/mes/safetyEnv/pollutionCheck';

import { Page, useVbenDrawer, useVbenModal } from '@vben/common-ui';

import { message, Tag } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deletePollutionCheck,
  getPollutionCheckPage,
} from '#/api/mes/safetyEnv/pollutionCheck';
import { $t } from '#/locales';

import { AI_RESULT_MAP, REVIEW_RESULT_MAP, STAGE_MAP, useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';
import HistoryDrawer from './modules/history-drawer.vue';
import Review from './modules/review.vue';

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

const [ReviewModal, reviewModalApi] = useVbenModal({
  connectedComponent: Review,
  destroyOnClose: true,
});

const [HistoryDrawerComp, historyDrawerApi] = useVbenDrawer({
  connectedComponent: HistoryDrawer,
  destroyOnClose: true,
});

/** 打开判定履历(全链追溯) */
function handleHistory(row: MesSetPollutionCheckApi.PollutionCheck) {
  historyDrawerApi.setData({ row }).open();
}

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 新建污染判定 */
function handleCreate() {
  formModalApi.setData({ formType: 'create' }).open();
}

/** 编辑污染判定(仅待复核) */
function handleEdit(row: MesSetPollutionCheckApi.PollutionCheck) {
  formModalApi.setData({ id: row.id, formType: 'update' }).open();
}

/** 人工复核(仅待复核) */
function handleReview(row: MesSetPollutionCheckApi.PollutionCheck) {
  reviewModalApi.setData({ row }).open();
}

/** 删除污染判定(仅待复核) */
async function handleDelete(row: MesSetPollutionCheckApi.PollutionCheck) {
  const label = row.recordNo ?? '';
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [label]),
    duration: 0,
  });
  try {
    await deletePollutionCheck(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [label]));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

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
          await getPollutionCheckPage({
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
  } as VxeTableGridOptions<MesSetPollutionCheckApi.PollutionCheck>,
});
</script>
<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <ReviewModal @success="handleRefresh" />
    <HistoryDrawerComp />
    <Grid table-title="污染判定列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '新建污染判定',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['mes:set-pollution-check:create'],
              onClick: handleCreate,
            },
          ]"
        />
      </template>
      <template #stage="{ row }">
        <span>{{ STAGE_MAP[row.stage] ?? row.stage }}</span>
      </template>
      <template #aiResult="{ row }">
        <Tag v-if="row.aiResult" :color="AI_RESULT_MAP[row.aiResult]?.color">
          {{ AI_RESULT_MAP[row.aiResult]?.text }}
        </Tag>
        <span v-else>-</span>
      </template>
      <template #reviewResult="{ row }">
        <Tag
          v-if="row.reviewResult"
          :color="REVIEW_RESULT_MAP[row.reviewResult]?.color"
        >
          {{ REVIEW_RESULT_MAP[row.reviewResult]?.text }}
        </Tag>
        <Tag v-else>待复核</Tag>
      </template>
      <template #marked="{ row }">
        <Tag v-if="row.marked" color="orange">已标记</Tag>
        <span v-else>-</span>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '履历',
              type: 'link',
              icon: ACTION_ICON.BOOK,
              auth: ['mes:set-pollution-check:query'],
              onClick: handleHistory.bind(null, row),
            },
            {
              label: '复核',
              type: 'link',
              icon: ACTION_ICON.AUDIT,
              auth: ['mes:set-pollution-check:review'],
              ifShow: () => !row.reviewResult,
              onClick: handleReview.bind(null, row),
            },
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['mes:set-pollution-check:update'],
              ifShow: () => !row.reviewResult,
              onClick: handleEdit.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['mes:set-pollution-check:delete'],
              ifShow: () => !row.reviewResult,
              popConfirm: {
                title: $t('ui.actionMessage.deleteConfirm', [row.recordNo]),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>