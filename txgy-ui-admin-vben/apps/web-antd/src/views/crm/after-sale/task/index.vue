<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CrmAfterSaleVisitTaskApi } from '#/api/crm/after-sale/task';

import { Page, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteAfterSaleVisitTask,
  finishAfterSaleVisitTask,
  getAfterSaleVisitTaskPage,
} from '#/api/crm/after-sale/task';
import { $t } from '#/locales';

import RecordForm from '../record/modules/form.vue';
import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

// 复用回访记录表单：任务完成后自动生成的记录，可直接打开填写
const [RecordFormModal, recordFormModalApi] = useVbenModal({
  connectedComponent: RecordForm,
  destroyOnClose: true,
});

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 创建回访任务 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑回访任务 */
function handleEdit(row: CrmAfterSaleVisitTaskApi.VisitTask) {
  formModalApi.setData(row).open();
}

/** 删除回访任务 */
async function handleDelete(row: CrmAfterSaleVisitTaskApi.VisitTask) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.warrantyNo]),
    duration: 0,
  });
  try {
    await deleteAfterSaleVisitTask(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.warrantyNo]));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 完成回访任务（后端会自动生成一条「待填写」回访记录） */
async function handleFinish(row: CrmAfterSaleVisitTaskApi.VisitTask) {
  const hideLoading = message.loading({
    content: '正在完成回访任务...',
    duration: 0,
  });
  try {
    await finishAfterSaleVisitTask(row.id!);
    message.success('已完成回访任务，并自动生成待填写的回访记录');
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 填写回访记录（直接打开任务自动生成的记录，无需手动新建） */
function handleFillRecord(row: CrmAfterSaleVisitTaskApi.VisitTask) {
  if (!row.recordId) {
    message.warning('该任务尚未生成回访记录，请先完成回访任务');
    return;
  }
  recordFormModalApi.setData({ id: row.recordId }).open();
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
          return await getAfterSaleVisitTaskPage({
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
  } as VxeTableGridOptions<CrmAfterSaleVisitTaskApi.VisitTask>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <RecordFormModal @success="handleRefresh" />
    <Grid table-title="回访任务列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['回访任务']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['crm:after-sale-visit-task:create'],
              onClick: handleCreate,
            },
          ]"
        />
      </template>
      <template #recordStatus="{ row }">
        <span v-if="row.recordStatus === 1" style="color: #fa8c16">待填写</span>
        <span v-else-if="row.recordStatus === 2">已填写</span>
        <span v-else>—</span>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['crm:after-sale-visit-task:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: '完成回访',
              type: 'link',
              auth: ['crm:after-sale-visit-task:update'],
              ifShow: row.status !== 2,
              onClick: handleFinish.bind(null, row),
            },
            {
              label: '填写回访记录',
              type: 'link',
              auth: ['crm:after-sale-visit-record:update'],
              ifShow: row.recordId && row.recordStatus === 1,
              onClick: handleFillRecord.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['crm:after-sale-visit-task:delete'],
              popConfirm: {
                title: $t('ui.actionMessage.deleteConfirm', [row.warrantyNo]),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
