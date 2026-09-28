<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { ErpPurchaseContractApi } from '#/api/erp/purchase/contract';

import { Page, useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  approvePurchaseContract,
  deletePurchaseContract,
  exportPurchaseContractPdf,
  getPurchaseContractPage,
  rejectPurchaseContract,
  submitPurchaseContract,
} from '#/api/erp/purchase/contract';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';
import TemplateManager from './modules/template-manager.vue';

defineOptions({ name: 'ErpPurchaseContract' });

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

const [TemplateModal, templateModalApi] = useVbenModal({
  connectedComponent: TemplateManager,
  destroyOnClose: true,
});

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 导出动态排版的采购合同 PDF；未审批合同由后端标记为草稿。 */
async function handleExportPdf(row: ErpPurchaseContractApi.PurchaseContract) {
  const data = await exportPurchaseContractPdf(row.id as number);
  downloadFileFromBlobPart({
    fileName: `采购合同-${row.no}.pdf`,
    source: data,
  });
}

/** 创建采购合同 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑采购合同 */
function handleEdit(row: ErpPurchaseContractApi.PurchaseContract) {
  formModalApi.setData(row).open();
}

/** 提交审批 */
async function handleSubmit(row: ErpPurchaseContractApi.PurchaseContract) {
  const hideLoading = message.loading({
    content: `正在提交${row.no}`,
    duration: 0,
  });
  try {
    await submitPurchaseContract(row.id as number);
    message.success('提交成功，等待审批');
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 审批通过 */
async function handleApprove(row: ErpPurchaseContractApi.PurchaseContract) {
  await approvePurchaseContract(row.id as number);
  message.success('审批通过');
  handleRefresh();
}

/** 驳回 */
async function handleReject(row: ErpPurchaseContractApi.PurchaseContract) {
  await rejectPurchaseContract(row.id as number);
  message.success('已驳回');
  handleRefresh();
}

/** 删除采购合同 */
async function handleDelete(row: ErpPurchaseContractApi.PurchaseContract) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.no]),
    duration: 0,
  });
  try {
    await deletePurchaseContract([row.id as number]);
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
          return await getPurchaseContractPage({
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
  } as VxeTableGridOptions<ErpPurchaseContractApi.PurchaseContract>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <TemplateModal />
    <Grid table-title="采购合同列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '合同模板',
              type: 'default',
              icon: ACTION_ICON.EDIT,
              auth: ['erp:purchase-contract:template'],
              onClick: () => templateModalApi.open(),
            },
            {
              label: $t('ui.actionTitle.create', ['采购合同']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['erp:purchase-contract:create'],
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
              auth: ['erp:purchase-contract:update'],
              ifShow: () => [0, 30, 40].includes(row.status ?? 0),
              onClick: handleEdit.bind(null, row),
            },
            {
              label: '提交审批',
              type: 'link',
              icon: ACTION_ICON.AUDIT,
              auth: ['erp:purchase-contract:submit'],
              ifShow: () => [0, 30, 40].includes(row.status ?? 0),
              popConfirm: {
                title: `确认提交${row.no}审批？`,
                confirm: handleSubmit.bind(null, row),
              },
            },
            {
              label: '审批通过',
              type: 'link',
              icon: ACTION_ICON.AUDIT,
              auth: ['erp:purchase-contract:approve'],
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
              auth: ['erp:purchase-contract:approve'],
              ifShow: () => row.status === 10,
              popConfirm: {
                title: `确认驳回${row.no}？`,
                confirm: handleReject.bind(null, row),
              },
            },
            {
              label: '导出PDF',
              type: 'link',
              icon: ACTION_ICON.DOWNLOAD,
              auth: ['erp:purchase-contract:export'],
              onClick: handleExportPdf.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['erp:purchase-contract:delete'],
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
