<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { MesTmToolIssueApi } from '#/api/mes/tm/tool/issue';

import { Page, useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  confirmToolIssue,
  deleteToolIssue,
  exportToolIssue,
  getToolIssuePage,
} from '#/api/mes/tm/tool/issue';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

function handleRefresh() {
  gridApi.query();
}

function handleCreate() {
  formModalApi.setData({ formType: 'create' }).open();
}

function handleDetail(row: MesTmToolIssueApi.ToolIssue) {
  formModalApi.setData({ id: row.id, formType: 'detail' }).open();
}

function handleEdit(row: MesTmToolIssueApi.ToolIssue) {
  formModalApi.setData({ id: row.id, formType: 'update' }).open();
}

async function handleDelete(row: MesTmToolIssueApi.ToolIssue) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.name]),
    duration: 0,
  });
  try {
    await deleteToolIssue(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.name]));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

async function handleConfirm(row: MesTmToolIssueApi.ToolIssue) {
  await confirmToolIssue(row.id!);
  message.success('领用确认成功');
  handleRefresh();
}

async function handleExport() {
  const data = await exportToolIssue(await gridApi.formApi.getValues());
  downloadFileFromBlobPart({ fileName: '工具领用单.xls', source: data });
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
        query: async ({ page }, formValues) => {
          return await getToolIssuePage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
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
      search: true,
    },
  } as VxeTableGridOptions<MesTmToolIssueApi.ToolIssue>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <Grid table-title="工具领用单列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['领用单']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['mes:tm-tool-issue:create'],
              onClick: handleCreate,
            },
            {
              label: $t('ui.actionTitle.export'),
              type: 'primary',
              icon: ACTION_ICON.DOWNLOAD,
              auth: ['mes:tm-tool-issue:export'],
              onClick: handleExport,
            },
          ]"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['mes:tm-tool-issue:update'],
              show: row.status === 0,
              onClick: handleEdit.bind(null, row),
            },
            {
              label: '确认',
              type: 'link',
              auth: ['mes:tm-tool-issue:update'],
              show: row.status === 0,
              popConfirm: {
                title: '确认领用后将扣减工具可用数量，是否继续？',
                confirm: handleConfirm.bind(null, row),
              },
            },
            {
              label: $t('common.detail'),
              type: 'link',
              onClick: handleDetail.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['mes:tm-tool-issue:delete'],
              show: row.status === 0,
              popConfirm: {
                title: $t('ui.actionMessage.deleteConfirm', [row.name]),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
