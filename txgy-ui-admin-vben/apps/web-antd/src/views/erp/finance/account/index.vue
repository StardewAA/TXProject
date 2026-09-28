<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { ErpAccountApi } from '#/api/erp/finance/account';

import { computed, ref } from 'vue';

import { confirm, DocAlert, Page, useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { message, Modal, Table } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import ErpExportButton from '#/components/erp-export-button/index.vue';
import {
  deleteAccount,
  exportAccount,
  exportAccountPdf,
  getAccountPage,
  getAccountRecordList,
  updateAccountDefaultStatus,
} from '#/api/erp/finance/account';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

const recordVisible = ref(false);
const recordLoading = ref(false);
const currentAccount = ref<ErpAccountApi.Account>();
const accountRecords = ref<ErpAccountApi.AccountRecord[]>([]);
const recordColumns = computed(() => [
  { title: '时间', dataIndex: 'createTime', width: 180 },
  { title: '业务单号', dataIndex: 'bizNo', width: 190 },
  { title: '业务类型', dataIndex: 'bizType', width: 130 },
  { title: '变动金额', dataIndex: 'changeAmount', width: 120 },
  { title: '变动前', dataIndex: 'beforeBalance', width: 120 },
  { title: '变动后', dataIndex: 'afterBalance', width: 120 },
  { title: '说明', dataIndex: 'remark', minWidth: 180 },
]);

async function handleRecords(row: ErpAccountApi.Account) {
  currentAccount.value = row;
  recordVisible.value = true;
  recordLoading.value = true;
  try {
    accountRecords.value = await getAccountRecordList(row.id!);
  } finally {
    recordLoading.value = false;
  }
}

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 导出表格 */
async function handleExport(format: 'excel' | 'pdf') {
  const data =
    format === 'pdf'
      ? await exportAccountPdf(await gridApi.formApi.getValues())
      : await exportAccount(await gridApi.formApi.getValues());
  downloadFileFromBlobPart({
    fileName: format === 'pdf' ? '结算账户信息.pdf' : '结算账户信息.xls',
    source: data,
  });
}

/** 创建结算账户 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑结算账户 */
function handleEdit(row: ErpAccountApi.Account) {
  formModalApi.setData(row).open();
}

/** 删除结算账户 */
async function handleDelete(row: ErpAccountApi.Account) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.name]),
    duration: 0,
  });
  try {
    await deleteAccount(row.id as number);
    message.success($t('ui.actionMessage.deleteSuccess', [row.name]));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 修改默认状态 */
async function handleDefaultStatusChange(
  newStatus: boolean,
  row: ErpAccountApi.Account,
): Promise<boolean | undefined> {
  const text = newStatus ? '设置' : '取消';
  try {
    await confirm(`确认要${text}"${row.name}"默认吗?`);
  } catch {
    return false;
  }
  // 更新默认状态
  await updateAccountDefaultStatus(row.id!, newStatus);
  // 提示并返回成功
  message.success(`${text}默认成功`);
  handleRefresh();
  return true;
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
  },
  gridOptions: {
    columns: useGridColumns(handleDefaultStatusChange),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return await getAccountPage({
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
  } as VxeTableGridOptions<ErpAccountApi.Account>,
});
</script>

<template>
  <Page auto-content-height>
    <template #doc>
      <DocAlert
        title="【财务】采购付款、销售收款"
        url="https://doc.iocoder.cn/sale/finance-payment-receipt/"
      />
    </template>

    <FormModal @success="handleRefresh" />
    <Modal
      v-model:open="recordVisible"
      :footer="null"
      :title="`${currentAccount?.name ?? ''} - 资金流水`"
      width="960px"
    >
      <Table
        :columns="recordColumns"
        :data-source="accountRecords"
        :loading="recordLoading"
        :pagination="false"
        row-key="id"
        size="small"
      />
    </Modal>
    <Grid table-title="结算账户列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '资金流水',
              type: 'link',
              auth: ['erp:account:query'],
              onClick: handleRecords.bind(null, row),
            },
            {
              label: $t('ui.actionTitle.create', ['结算账户']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['erp:account:create'],
              onClick: handleCreate,
            },
          ]"
        />
        <ErpExportButton
          :auth="['erp:account:export']"
          :export-handler="handleExport"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['erp:account:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['erp:account:delete'],
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
