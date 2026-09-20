<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CrmAfterSaleWarrantyApi } from '#/api/crm/after-sale/warranty';

import { useRouter } from 'vue-router';

import { Page, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteAfterSaleWarranty,
  getAfterSaleWarrantyPage,
} from '#/api/crm/after-sale/warranty';
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

/** 创建质保档案 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑质保档案 */
function handleEdit(row: CrmAfterSaleWarrantyApi.Warranty) {
  formModalApi.setData(row).open();
}

const { push } = useRouter();

/** 查看质保档案详情（跳转详情页，待办「质保即将到期」等来源同样跳此处） */
function handleDetail(row: CrmAfterSaleWarrantyApi.Warranty) {
  push({ name: 'CrmAfterSaleWarrantyDetail', params: { id: row.id } });
}

/** 删除质保档案 */
async function handleDelete(row: CrmAfterSaleWarrantyApi.Warranty) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.no ?? row.projectName]),
    duration: 0,
  });
  try {
    await deleteAfterSaleWarranty(row.id!);
    message.success(
      $t('ui.actionMessage.deleteSuccess', [row.no ?? row.projectName]),
    );
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
          return await getAfterSaleWarrantyPage({
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
  } as VxeTableGridOptions<CrmAfterSaleWarrantyApi.Warranty>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <Grid table-title="质保档案列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['质保档案']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['crm:after-sale-warranty:create'],
              onClick: handleCreate,
            },
          ]"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '详情',
              type: 'link',
              auth: ['crm:after-sale-warranty:query'],
              onClick: handleDetail.bind(null, row),
            },
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['crm:after-sale-warranty:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['crm:after-sale-warranty:delete'],
              popConfirm: {
                title: $t('ui.actionMessage.deleteConfirm', [
                  row.no ?? row.projectName,
                ]),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
