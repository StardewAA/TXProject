<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CrmAfterSaleTicketApi } from '#/api/crm/after-sale/ticket';

import { Page, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteAfterSaleTicket,
  getAfterSaleTicketPage,
  updateAfterSaleTicketStatus,
} from '#/api/crm/after-sale/ticket';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import AssignForm from './modules/assign.vue';
import Form from './modules/form.vue';

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

const [AssignModal, assignModalApi] = useVbenModal({
  connectedComponent: AssignForm,
  destroyOnClose: true,
});

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 创建问题工单 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑问题工单 */
function handleEdit(row: CrmAfterSaleTicketApi.Ticket) {
  formModalApi.setData(row).open();
}

/** 指派处理人 */
function handleAssign(row: CrmAfterSaleTicketApi.Ticket) {
  assignModalApi.setData(row).open();
}

/** 删除问题工单 */
async function handleDelete(row: CrmAfterSaleTicketApi.Ticket) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.no]),
    duration: 0,
  });
  try {
    await deleteAfterSaleTicket(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.no]));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 变更工单状态 */
async function handleStatus(row: CrmAfterSaleTicketApi.Ticket, status: number) {
  const hideLoading = message.loading({
    content: '正在更新工单状态...',
    duration: 0,
  });
  try {
    await updateAfterSaleTicketStatus(row.id!, status);
    message.success('工单状态已更新');
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
          return await getAfterSaleTicketPage({
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
  } as VxeTableGridOptions<CrmAfterSaleTicketApi.Ticket>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <AssignModal @success="handleRefresh" />
    <Grid table-title="售后问题工单列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['问题工单']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['crm:after-sale-ticket:create'],
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
              auth: ['crm:after-sale-ticket:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: '指派',
              type: 'link',
              auth: ['crm:after-sale-ticket:update'],
              onClick: handleAssign.bind(null, row),
            },
            {
              label: '完成',
              type: 'link',
              auth: ['crm:after-sale-ticket:update'],
              ifShow: row.status !== 3 && row.status !== 4,
              onClick: handleStatus.bind(null, row, 3),
            },
            {
              label: '关闭',
              type: 'link',
              auth: ['crm:after-sale-ticket:update'],
              ifShow: row.status !== 4,
              onClick: handleStatus.bind(null, row, 4),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['crm:after-sale-ticket:delete'],
              popConfirm: {
                title: $t('ui.actionMessage.deleteConfirm', [row.no]),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
