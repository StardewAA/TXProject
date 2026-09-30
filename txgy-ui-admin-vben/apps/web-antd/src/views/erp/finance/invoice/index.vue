<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { ErpInvoiceApi } from '#/api/erp/finance/invoice';

import { Page, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  certifyInvoice,
  deleteInvoice,
  getInvoicePage,
  voidInvoice,
} from '#/api/erp/finance/invoice';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

defineOptions({ name: 'ErpInvoice' });

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 创建发票 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑发票 */
function handleEdit(row: ErpInvoiceApi.Invoice) {
  formModalApi.setData(row).open();
}

/** 认证发票 */
async function handleCertify(row: ErpInvoiceApi.Invoice) {
  const hideLoading = message.loading({
    content: `正在认证${row.invoiceNo}`,
    duration: 0,
  });
  try {
    await certifyInvoice(row.id as number);
    message.success('认证成功');
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 作废发票 */
async function handleVoid(row: ErpInvoiceApi.Invoice) {
  const hideLoading = message.loading({
    content: `正在作废${row.invoiceNo}`,
    duration: 0,
  });
  try {
    await voidInvoice(row.id as number);
    message.success('已作废');
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 删除发票 */
async function handleDelete(row: ErpInvoiceApi.Invoice) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.invoiceNo]),
    duration: 0,
  });
  try {
    await deleteInvoice([row.id as number]);
    message.success($t('ui.actionMessage.deleteSuccess', [row.invoiceNo]));
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
          return await getInvoicePage({
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
  } as VxeTableGridOptions<ErpInvoiceApi.Invoice>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <Grid table-title="发票列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['发票']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['erp:invoice:create'],
              onClick: handleCreate,
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
              auth: ['erp:invoice:update'],
              ifShow: () => [0, 1].includes(row.status ?? 0),
              onClick: handleEdit.bind(null, row),
            },
            {
              label: '认证',
              type: 'link',
              icon: ACTION_ICON.AUDIT,
              auth: ['erp:invoice:update'],
              ifShow: () => row.status === 0,
              popConfirm: {
                title: `确认认证发票${row.invoiceNo}？`,
                confirm: handleCertify.bind(null, row),
              },
            },
            {
              label: '作废',
              type: 'link',
              danger: true,
              icon: ACTION_ICON.AUDIT,
              auth: ['erp:invoice:update'],
              ifShow: () => row.status !== 3,
              popConfirm: {
                title: `确认作废发票${row.invoiceNo}？`,
                confirm: handleVoid.bind(null, row),
              },
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['erp:invoice:delete'],
              ifShow: () => [0, 1].includes(row.status ?? 0),
              popConfirm: {
                title: $t('ui.actionMessage.deleteConfirm', [row.invoiceNo]),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
