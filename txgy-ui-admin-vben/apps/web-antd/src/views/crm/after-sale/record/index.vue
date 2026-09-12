<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CrmAfterSaleVisitRecordApi } from '#/api/crm/after-sale/record';

import { Page, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteAfterSaleVisitRecord,
  getAfterSaleVisitRecordPage,
} from '#/api/crm/after-sale/record';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 创建回访记录 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑回访记录 */
function handleEdit(row: CrmAfterSaleVisitRecordApi.VisitRecord) {
  formModalApi.setData(row).open();
}

/** 删除回访记录 */
async function handleDelete(row: CrmAfterSaleVisitRecordApi.VisitRecord) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.warrantyNo]),
    duration: 0,
  });
  try {
    await deleteAfterSaleVisitRecord(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.warrantyNo]));
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
        query: async ({ page }, formValues) => {
          return await getAfterSaleVisitRecordPage({
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
  } as VxeTableGridOptions<CrmAfterSaleVisitRecordApi.VisitRecord>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <Grid table-title="回访记录列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['回访记录']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['crm:after-sale-visit-record:create'],
              onClick: handleCreate,
            },
          ]"
        />
      </template>
      <template #status="{ row }">
        <span v-if="row.status === 1" style="color: #fa8c16">待填写</span>
        <span v-else>已填写</span>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: row.status === 1 ? '填写' : $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['crm:after-sale-visit-record:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['crm:after-sale-visit-record:delete'],
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
