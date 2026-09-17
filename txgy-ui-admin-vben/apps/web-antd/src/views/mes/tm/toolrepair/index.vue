<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { MesTmToolRepairApi } from '#/api/mes/tm/tool/repair';

import { Page, useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  confirmToolRepair,
  deleteToolRepair,
  exportToolRepair,
  finishToolRepair,
  getToolRepairPage,
} from '#/api/mes/tm/tool/repair';
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

function handleDetail(row: MesTmToolRepairApi.ToolRepair) {
  formModalApi.setData({ id: row.id, formType: 'detail' }).open();
}

function handleEdit(row: MesTmToolRepairApi.ToolRepair) {
  formModalApi.setData({ id: row.id, formType: 'update' }).open();
}

async function handleDelete(row: MesTmToolRepairApi.ToolRepair) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.name]),
    duration: 0,
  });
  try {
    await deleteToolRepair(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.name]));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

async function handleConfirm(row: MesTmToolRepairApi.ToolRepair) {
  await confirmToolRepair(row.id!);
  message.success('维修确认成功，工具状态已变更为维修中');
  handleRefresh();
}

async function handleFinish(row: MesTmToolRepairApi.ToolRepair) {
  await finishToolRepair(row.id!);
  message.success('维修完成，工具状态已恢复为在库');
  handleRefresh();
}

async function handleExport() {
  const data = await exportToolRepair(await gridApi.formApi.getValues());
  downloadFileFromBlobPart({ fileName: '工具维修单.xls', source: data });
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
          return await getToolRepairPage({
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
  } as VxeTableGridOptions<MesTmToolRepairApi.ToolRepair>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <Grid table-title="工具维修单列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['维修单']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['mes:tm-tool-repair:create'],
              onClick: handleCreate,
            },
            {
              label: $t('ui.actionTitle.export'),
              type: 'primary',
              icon: ACTION_ICON.DOWNLOAD,
              auth: ['mes:tm-tool-repair:export'],
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
              auth: ['mes:tm-tool-repair:update'],
              show: row.status === 0,
              onClick: handleEdit.bind(null, row),
            },
            {
              label: '确认维修',
              type: 'link',
              auth: ['mes:tm-tool-repair:update'],
              show: row.status === 0,
              popConfirm: {
                title: '确认维修后工具状态将变更为维修中，是否继续？',
                confirm: handleConfirm.bind(null, row),
              },
            },
            {
              label: '完成维修',
              type: 'link',
              auth: ['mes:tm-tool-repair:update'],
              show: row.status === 1,
              popConfirm: {
                title: '确认维修完成？工具状态将恢复为在库',
                confirm: handleFinish.bind(null, row),
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
              auth: ['mes:tm-tool-repair:delete'],
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
