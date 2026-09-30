<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { HrmCertificateWarningApi } from '#/api/hrm/certificate/warning';

import { Page, useVbenModal } from '@vben/common-ui';

import { Tag, Tooltip } from 'ant-design-vue';

import { TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { getCertificateWarningPage } from '#/api/hrm/certificate/warning';

import {
  formatRemainDays,
  getNotifyStatusLabel,
  getRemainDaysColor,
  getStatusColor,
  getStatusLabel,
  getWarningTypeColor,
  getWarningTypeLabel,
  useGridColumns,
  useGridFormSchema,
} from './data';
import HandleForm from './modules/handle-form.vue';

defineOptions({ name: 'HrmCertificateWarning' });

const [HandleModal, handleModalApi] = useVbenModal({
  connectedComponent: HandleForm,
  destroyOnClose: true,
});

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 处理预警 */
function handleHandle(row: HrmCertificateWarningApi.CertificateWarning) {
  handleModalApi.setData(row).open();
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
          return await getCertificateWarningPage({
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
  } as VxeTableGridOptions<HrmCertificateWarningApi.CertificateWarning>,
});
</script>

<template>
  <Page auto-content-height>
    <HandleModal @success="handleRefresh" />

    <Grid>
      <template #type="{ row }">
        <Tag :color="getWarningTypeColor(row.type)">
          {{ getWarningTypeLabel(row.type) }}
        </Tag>
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
      <template #notifyStatus="{ row }">
        <Tooltip :title="row.notifyFailReason || ''">
          <Tag :color="row.notifyStatus === 2 ? 'error' : 'default'">
            {{ getNotifyStatusLabel(row.notifyStatus) }}
          </Tag>
        </Tooltip>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: row.status === 0 ? '处理' : '重新启用',
              type: 'link',
              auth: ['hrm:certificate:warning:handle'],
              onClick: handleHandle.bind(null, row),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
