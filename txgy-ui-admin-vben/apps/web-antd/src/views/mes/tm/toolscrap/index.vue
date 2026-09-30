<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { MesTmToolScrapApi } from '#/api/mes/tm/tool/scrap';

import { Page, useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  confirmToolScrap,
  deleteToolScrap,
  exportToolScrap,
  getToolScrapPage,
} from '#/api/mes/tm/tool/scrap';
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

function handleDetail(row: MesTmToolScrapApi.ToolScrap) {
  formModalApi.setData({ id: row.id, formType: 'detail' }).open();
}

function handleEdit(row: MesTmToolScrapApi.ToolScrap) {
  formModalApi.setData({ id: row.id, formType: 'update' }).open();
}

async function handleDelete(row: MesTmToolScrapApi.ToolScrap) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.name]),
    duration: 0,
  });
  try {
    await deleteToolScrap(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.name]));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

async function handleConfirm(row: MesTmToolScrapApi.ToolScrap) {
  await confirmToolScrap(row.id!);
  message.success('报废确认成功，工具数量已扣减');
  handleRefresh();
}

async function handleExport() {
  const data = await exportToolScrap(await gridApi.formApi.getValues());
  downloadFileFromBlobPart({ fileName: '工具报废单.xls', source: data });
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
          return await getToolScrapPage({
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
  } as VxeTableGridOptions<MesTmToolScrapApi.ToolScrap>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <Grid table-title="工具报废单列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['报废单']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['mes:tm-tool-scrap:create'],
              onClick: handleCreate,
            },
            {
              label: $t('ui.actionTitle.export'),
              type: 'primary',
              icon: ACTION_ICON.DOWNLOAD,
              auth: ['mes:tm-tool-scrap:export'],
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
              auth: ['mes:tm-tool-scrap:update'],
              show: row.status === 0,
              onClick: handleEdit.bind(null, row),
            },
            {
              label: '确认报废',
              type: 'link',
              danger: true,
              auth: ['mes:tm-tool-scrap:update'],
              show: row.status === 0,
              popConfirm: {
                title: '确认报废后工具数量将扣减且不可恢复，是否继续？',
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
              auth: ['mes:tm-tool-scrap:delete'],
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
