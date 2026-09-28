<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { ErpArrivalInspectionApi } from '#/api/erp/purchase/inspection';

import { Page, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  approveArrivalInspection,
  deleteArrivalInspection,
  getArrivalInspectionPage,
  rejectArrivalInspection,
  submitArrivalInspection,
} from '#/api/erp/purchase/inspection';
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

/** 创建到货检验 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑到货检验 */
function handleEdit(row: ErpArrivalInspectionApi.ArrivalInspection) {
  formModalApi.setData({ id: row.id }).open();
}

/** 提交审批 */
async function handleSubmit(row: ErpArrivalInspectionApi.ArrivalInspection) {
  await submitArrivalInspection(row.id as number);
  message.success('提交审批成功');
  handleRefresh();
}

/** 审批通过 */
async function handleApprove(row: ErpArrivalInspectionApi.ArrivalInspection) {
  await approveArrivalInspection(row.id as number);
  message.success('审批通过成功');
  handleRefresh();
}

/** 审批驳回 */
async function handleReject(row: ErpArrivalInspectionApi.ArrivalInspection) {
  await rejectArrivalInspection(row.id as number);
  message.success('审批驳回成功');
  handleRefresh();
}

/** 删除到货检验 */
async function handleDelete(row: ErpArrivalInspectionApi.ArrivalInspection) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.no]),
    duration: 0,
  });
  try {
    await deleteArrivalInspection([row.id as number]);
    message.success($t('ui.actionMessage.deleteSuccess', [row.no]));
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
          return await getArrivalInspectionPage({
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
  } as VxeTableGridOptions<ErpArrivalInspectionApi.ArrivalInspection>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <Grid table-title="到货检验列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['到货检验']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['erp:purchase-inspection:create'],
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
              auth: ['erp:purchase-inspection:update'],
              ifShow: () => [0, 30, 40].includes(row.status ?? 0),
              onClick: handleEdit.bind(null, row),
            },
            {
              label: '提交审批',
              type: 'link',
              icon: ACTION_ICON.AUDIT,
              auth: ['erp:purchase-inspection:submit'],
              ifShow: () => [0, 30, 40].includes(row.status ?? 0),
              popConfirm: {
                title: `确认提交${row.no}？`,
                confirm: handleSubmit.bind(null, row),
              },
            },
            {
              label: '审批通过',
              type: 'link',
              icon: ACTION_ICON.AUDIT,
              auth: ['erp:purchase-inspection:approve'],
              ifShow: () => row.status === 10,
              popConfirm: {
                title: `确认审批通过${row.no}？`,
                confirm: handleApprove.bind(null, row),
              },
            },
            {
              label: '驳回',
              type: 'link',
              danger: true,
              icon: ACTION_ICON.AUDIT,
              auth: ['erp:purchase-inspection:approve'],
              ifShow: () => row.status === 10,
              popConfirm: {
                title: `确认驳回${row.no}？`,
                confirm: handleReject.bind(null, row),
              },
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['erp:purchase-inspection:delete'],
              ifShow: () => [0, 30, 40].includes(row.status ?? 0),
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
