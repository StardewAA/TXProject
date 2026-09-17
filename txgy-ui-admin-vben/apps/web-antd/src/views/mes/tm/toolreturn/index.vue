<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { MesTmToolReturnApi } from '#/api/mes/tm/tool/return';

import { Page, useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  confirmToolReturn,
  deleteToolReturn,
  exportToolReturn,
  getToolReturnPage,
} from '#/api/mes/tm/tool/return';
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

function handleDetail(row: MesTmToolReturnApi.ToolReturn) {
  formModalApi.setData({ id: row.id, formType: 'detail' }).open();
}

function handleEdit(row: MesTmToolReturnApi.ToolReturn) {
  formModalApi.setData({ id: row.id, formType: 'update' }).open();
}

async function handleDelete(row: MesTmToolReturnApi.ToolReturn) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.name]),
    duration: 0,
  });
  try {
    await deleteToolReturn(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.name]));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

async function handleConfirm(row: MesTmToolReturnApi.ToolReturn) {
  await confirmToolReturn(row.id!);
  message.success('归还确认成功');
  handleRefresh();
}

async function handleExport() {
  const data = await exportToolReturn(await gridApi.formApi.getValues());
  downloadFileFromBlobPart({ fileName: '工具归还单.xls', source: data });
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
          return await getToolReturnPage({
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
  } as VxeTableGridOptions<MesTmToolReturnApi.ToolReturn>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <Grid table-title="工具归还单列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['归还单']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['mes:tm-tool-return:create'],
              onClick: handleCreate,
            },
            {
              label: $t('ui.actionTitle.export'),
              type: 'primary',
              icon: ACTION_ICON.DOWNLOAD,
              auth: ['mes:tm-tool-return:export'],
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
              auth: ['mes:tm-tool-return:update'],
              show: row.status === 0,
              onClick: handleEdit.bind(null, row),
            },
            {
              label: '确认',
              type: 'link',
              auth: ['mes:tm-tool-return:update'],
              show: row.status === 0,
              popConfirm: {
                title: '确认归还后将恢复工具可用数量，是否继续？',
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
              auth: ['mes:tm-tool-return:delete'],
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
