<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { ErpPurchasePlanApi } from '#/api/erp/purchase/plan';

import { Page, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  approvePurchasePlan,
  deletePurchasePlan,
  getPurchasePlanPage,
  rejectPurchasePlan,
  submitPurchasePlan,
} from '#/api/erp/purchase/plan';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

defineOptions({ name: 'ErpPurchasePlan' });

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 创建采购计划 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑采购计划 */
function handleEdit(row: ErpPurchasePlanApi.PurchasePlan) {
  formModalApi.setData(row).open();
}

/** 提交审批 */
async function handleSubmit(row: ErpPurchasePlanApi.PurchasePlan) {
  await submitPurchasePlan(row.id!);
  message.success('采购计划已提交审批');
  handleRefresh();
}

/** 审批通过 */
async function handleApprove(row: ErpPurchasePlanApi.PurchasePlan) {
  await approvePurchasePlan(row.id!);
  message.success('采购计划已审批通过');
  handleRefresh();
}

/** 审批驳回 */
async function handleReject(row: ErpPurchasePlanApi.PurchasePlan) {
  await rejectPurchasePlan(row.id!);
  message.success('采购计划已驳回');
  handleRefresh();
}

/** 删除采购计划 */
async function handleDelete(row: ErpPurchasePlanApi.PurchasePlan) {
  await deletePurchasePlan([row.id!]);
  message.success('删除成功');
  handleRefresh();
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
          return await getPurchasePlanPage({
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
  } as VxeTableGridOptions<ErpPurchasePlanApi.PurchasePlan>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <Grid table-title="采购计划列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['采购计划']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['erp:purchase-plan:create'],
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
              auth: ['erp:purchase-plan:update'],
              ifShow: () => [0, 30, 40].includes(row.status ?? 0),
              onClick: handleEdit.bind(null, row),
            },
            {
              label: '提交审批',
              type: 'link',
              icon: ACTION_ICON.AUDIT,
              auth: ['erp:purchase-plan:submit'],
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
              auth: ['erp:purchase-plan:approve'],
              ifShow: () => row.status === 10,
              popConfirm: {
                title: `确认审批通过${row.no}？`,
                confirm: handleApprove.bind(null, row),
              },
            },
            {
              label: '审批驳回',
              type: 'link',
              danger: true,
              icon: ACTION_ICON.AUDIT,
              auth: ['erp:purchase-plan:approve'],
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
              auth: ['erp:purchase-plan:delete'],
              ifShow: () => [0, 30, 40].includes(row.status ?? 0),
              popConfirm: {
                title: `确认删除${row.no}？`,
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
