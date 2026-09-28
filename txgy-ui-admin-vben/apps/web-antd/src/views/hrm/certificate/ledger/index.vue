<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { HrmCertificateLedgerApi } from '#/api/hrm/certificate/ledger';

import { ref } from 'vue';

import { confirm, Page, useVbenModal } from '@vben/common-ui';
import { formatDate } from '@vben/utils';

import { message, Tag } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteCertificateLedger,
  getCertificateLedgerPage,
  updateCertificateLedgerStatus,
} from '#/api/hrm/certificate/ledger';
import { $t } from '#/locales';
import { exportHrmTable } from '#/views/hrm/utils/export-table';

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

const exportLoading = ref(false);

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

/** 导出当前筛选条件下的证件台账（Q-021 / I-013） */
async function handleExport() {
  exportLoading.value = true;
  try {
    const formValues = await gridApi.formApi.getValues();
    const count = await exportHrmTable<HrmCertificateLedgerApi.CertificateLedger>({
      columns: [
        { title: '员工姓名', value: (row) => row.employeeName },
        { title: '工号', value: (row) => row.employeeJobNumber },
        { title: '证件类型', value: (row) => row.typeName },
        { title: '证件名称', value: (row) => row.name },
        { title: '证件编号', value: (row) => row.no },
        { title: '有效期至', value: (row) => formatDate(row.endTime) },
        { title: '复审日期', value: (row) => formatDate(row.reviewTime) },
        { title: '剩余天数', value: (row) => row.remainDays },
        { title: '证件状态', value: (row) => getStatusLabel(row.status) },
        { title: '待处理预警', value: (row) => row.pendingWarningCount },
      ],
      fetchPage: (pageNo, pageSize) =>
        getCertificateLedgerPage({ pageNo, pageSize, ...formValues }),
      fileName: '证件台账',
    });
    message.success(`已导出 ${count} 条证件记录`);
  } finally {
    exportLoading.value = false;
  }
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
            {
              label: '导出',
              icon: ACTION_ICON.DOWNLOAD,
              auth: ['hrm:certificate:query'],
              loading: exportLoading,
              onClick: handleExport,
            },
          ]"
        />
      </template>
      <template #emptyText>
        <div class="py-8 text-center text-muted-foreground">
          暂无可导出的证件记录；可调整筛选条件，或点左上「新增」登记证件
        </div>
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
