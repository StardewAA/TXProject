<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { ErpCollaborationApi } from '#/api/erp/collaboration';

import { Tag } from 'ant-design-vue';

import { Page, useVbenModal } from '@vben/common-ui';

import { TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  getBusinessStatusColor,
  getBusinessStatusText,
  getCollaborationPage,
} from '#/api/erp/collaboration';

import { useGridColumns, useGridFormSchema } from './data';
import ConfirmForm from './modules/confirm-form.vue';
import Detail from './modules/detail.vue';

/** ERP 供应链协同单列表（一站式接单工作台） */
defineOptions({ name: 'ErpCollaborationOrder' });

const [DetailModal, detailModalApi] = useVbenModal({
  connectedComponent: Detail,
  destroyOnClose: true,
});

const [ConfirmModal, confirmModalApi] = useVbenModal({
  connectedComponent: ConfirmForm,
  destroyOnClose: true,
});

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 查看详情 */
function handleDetail(row: ErpCollaborationApi.CollaborationOrder) {
  detailModalApi.setData({ id: row.id }).open();
}

/** 业务确认（接受/拒绝/要求补充资料） */
function handleConfirm(
  row: ErpCollaborationApi.CollaborationOrder,
  action: 'ACCEPTED' | 'REJECTED' | 'NEED_SUPPLEMENT',
) {
  confirmModalApi.setData({ id: row.id, action }).open();
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
          return await getCollaborationPage({
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
  } as VxeTableGridOptions<ErpCollaborationApi.CollaborationOrder>,
});
</script>

<template>
  <Page auto-content-height>
    <DetailModal @success="handleRefresh" />
    <ConfirmModal @success="handleRefresh" />
    <Grid table-title="供应链协同单（一站式接单工作台）">
      <template #businessStatus="{ row }">
        <Tag :color="getBusinessStatusColor(row.businessStatus)">
          {{ getBusinessStatusText(row.businessStatus) }}
        </Tag>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '详情',
              type: 'link',
              icon: 'ep:view',
              auth: ['erp:collaboration-order:query'],
              onClick: handleDetail.bind(null, row),
            },
            {
              label: '接受',
              type: 'link',
              icon: 'ep:check',
              ifShow: () =>
                ['PENDING_CONFIRMATION', 'NEED_SUPPLEMENT'].includes(
                  row.businessStatus,
                ),
              auth: ['erp:collaboration-order:purchase-confirm'],
              onClick: handleConfirm.bind(null, row, 'ACCEPTED'),
            },
            {
              label: '要求补充',
              type: 'link',
              icon: 'ep:warning',
              ifShow: () => row.businessStatus === 'PENDING_CONFIRMATION',
              auth: ['erp:collaboration-order:supplement'],
              onClick: handleConfirm.bind(null, row, 'NEED_SUPPLEMENT'),
            },
            {
              label: '拒绝',
              type: 'link',
              danger: true,
              icon: 'ep:close',
              ifShow: () =>
                ['PENDING_CONFIRMATION', 'NEED_SUPPLEMENT'].includes(
                  row.businessStatus,
                ),
              auth: ['erp:collaboration-order:reject'],
              onClick: handleConfirm.bind(null, row, 'REJECTED'),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
