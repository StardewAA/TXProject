<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { HrmCertificateLedgerApi } from '#/api/hrm/certificate/ledger';

import { confirm, Page, useVbenModal } from '@vben/common-ui';

import { message, Tag } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteCertificateLedger,
  getCertificateLedgerPage,
  updateCertificateLedgerStatus,
} from '#/api/hrm/certificate/ledger';
import { $t } from '#/locales';

import { HrmCertificateStatus } from '../constants';
import {
  formatRemainDays,
  getRemainDaysColor,
  getStatusColor,
  getStatusLabel,
  useGridColumns,
  useGridFormSchema,
} from './data';
import Form from './modules/form.vue';
import RenewalForm from './modules/renewal-form.vue';
import RenewalHistory from './modules/renewal-history.vue';

defineOptions({ name: 'HrmCertificateLedger' });

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});
const [RenewalModal, renewalModalApi] = useVbenModal({
  connectedComponent: RenewalForm,
  destroyOnClose: true,
});
const [HistoryModal, historyModalApi] = useVbenModal({
  connectedComponent: RenewalHistory,
  destroyOnClose: true,
});

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 新增 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑 */
function handleEdit(row: HrmCertificateLedgerApi.CertificateLedger) {
  formModalApi.setData(row).open();
}

/** 办理续证/复审 */
function handleRenewal(row: HrmCertificateLedgerApi.CertificateLedger) {
  renewalModalApi.setData(row).open();
}

/** 查看续证/复审历史 */
function handleHistory(row: HrmCertificateLedgerApi.CertificateLedger) {
  historyModalApi.setData(row).open();
}

/** 注销证件 */
async function handleCancel(row: HrmCertificateLedgerApi.CertificateLedger) {
  await confirm(
    `确认注销证件“${row.name}”吗？注销后不再参与到期与复审预警，可在需要时恢复。`,
  );
  await updateCertificateLedgerStatus({
    id: row.id!,
    status: HrmCertificateStatus.CANCELLED,
  });
  message.success($t('ui.actionMessage.operationSuccess'));
  handleRefresh();
}

/** 恢复证件为自动计算状态 */
async function handleRestore(row: HrmCertificateLedgerApi.CertificateLedger) {
  await confirm(`确认恢复证件“${row.name}”吗？状态将按有效期与提前期重新计算。`);
  await updateCertificateLedgerStatus({
    id: row.id!,
    status: HrmCertificateStatus.VALID,
  });
  message.success($t('ui.actionMessage.operationSuccess'));
  handleRefresh();
}

/** 删除 */
async function handleDelete(row: HrmCertificateLedgerApi.CertificateLedger) {
  await confirm(`确认删除证件“${row.name}”吗？`);
  await deleteCertificateLedger(row.id!);
  message.success($t('ui.actionMessage.operationSuccess'));
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
          return await getCertificateLedgerPage({
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
  } as VxeTableGridOptions<HrmCertificateLedgerApi.CertificateLedger>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <RenewalModal @success="handleRefresh" />
    <HistoryModal />

    <Grid>
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '新增',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['hrm:certificate:create'],
              onClick: handleCreate,
            },
          ]"
        />
      </template>
      <template #remainDays="{ row }">
        <Tag :color="getRemainDaysColor(row.remainDays)">
          {{ formatRemainDays(row.remainDays) }}
        </Tag>
      </template>
      <template #status="{ row }">
        <Tag :color="getStatusColor(row.status)">
          {{ getStatusLabel(row.status) }}
        </Tag>
      </template>
      <template #pendingWarningCount="{ row }">
        <span v-if="row.pendingWarningCount">
          <Tag color="warning">{{ row.pendingWarningCount }}</Tag>
        </span>
        <span v-else>-</span>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['hrm:certificate:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: '续证/复审',
              type: 'link',
              auth: ['hrm:certificate:renewal'],
              ifShow: row.status !== 4,
              onClick: handleRenewal.bind(null, row),
            },
            {
              label: '历史',
              type: 'link',
              auth: ['hrm:certificate:query'],
              onClick: handleHistory.bind(null, row),
            },
            {
              label: '注销',
              type: 'link',
              danger: true,
              auth: ['hrm:certificate:update'],
              ifShow: row.status !== 4,
              onClick: handleCancel.bind(null, row),
            },
            {
              label: '恢复',
              type: 'link',
              auth: ['hrm:certificate:update'],
              ifShow: row.status === 4,
              onClick: handleRestore.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['hrm:certificate:delete'],
              onClick: handleDelete.bind(null, row),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
