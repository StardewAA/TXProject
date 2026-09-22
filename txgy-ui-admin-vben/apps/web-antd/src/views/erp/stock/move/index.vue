<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { ErpStockMoveApi } from '#/api/erp/stock/move';

import { ref } from 'vue';

import { DocAlert, Page, useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart, isEmpty } from '@vben/utils';

import { message, Tag } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import ErpExportButton from '#/components/erp-export-button/index.vue';
import {
  confirmStockMoveSource,
  confirmStockMoveTarget,
  deleteStockMove,
  exportStockMove,
  exportStockMovePdf,
  getStockMovePage,
  updateStockMoveStatus,
} from '#/api/erp/stock/move';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

/** ERP 库存调拨单列表 */
defineOptions({ name: 'ErpStockMove' });

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
      ? await exportStockMovePdf(await gridApi.formApi.getValues())
      : await exportStockMove(await gridApi.formApi.getValues());
  downloadFileFromBlobPart({
    fileName: format === 'pdf' ? '库存调拨单.pdf' : '库存调拨单.xls',
    source: data,
  });
}

/** 新增库存调拨单 */
function handleCreate() {
  formModalApi.setData({ formType: 'create' }).open();
}

/** 编辑库存调拨单 */
function handleEdit(row: ErpStockMoveApi.StockMove) {
  formModalApi.setData({ formType: 'edit', id: row.id }).open();
}

/** 删除库存调拨单 */
async function handleDelete(ids: number[]) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting'),
    duration: 0,
  });
  try {
    await deleteStockMove(ids);
    message.success($t('ui.actionMessage.deleteSuccess'));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

const moveStatusMeta: Record<number, { color: string; text: string }> = {
  10: { color: 'orange', text: '待调出确认' },
  15: { color: 'blue', text: '待调入确认' },
  20: { color: 'green', text: '调拨完成' },
};

/** 双确认/反确认操作 */
async function handleUpdateStatus(
  row: ErpStockMoveApi.StockMove,
  status: number,
) {
  const hideLoading = message.loading({
    content: `确定${status === 20 ? '审批' : '反审批'}该调拨单吗？`,
    duration: 0,
  });
  try {
    await updateStockMoveStatus(row.id!, status);
    message.success(`${status === 20 ? '审批' : '反审批'}成功`);
    handleRefresh();
  } finally {
    hideLoading();
  }
}

async function handleConfirm(row: ErpStockMoveApi.StockMove, side: 'source' | 'target') {
  const hideLoading = message.loading({
    content: side === 'source' ? '正在确认调出仓库...' : '正在确认调入仓库并变更库存...',
    duration: 0,
  });
  try {
    await (side === 'source'
      ? confirmStockMoveSource(row.id!)
      : confirmStockMoveTarget(row.id!));
    message.success(
      side === 'source'
        ? '调出仓库确认成功，库存尚未变动，等待调入仓库确认'
        : '双方确认完成，库存已完成调出和调入变更',
    );
    handleRefresh();
  } finally {
    hideLoading();
  }
}

const checkedIds = ref<number[]>([]);
function handleRowCheckboxChange({
  records,
}: {
  records: ErpStockMoveApi.StockMove[];
}) {
  checkedIds.value = records.map((item) => item.id!);
}

/** 查看详情 */
function handleDetail(row: ErpStockMoveApi.StockMove) {
  formModalApi.setData({ formType: 'detail', id: row.id }).open();
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
          return await getStockMovePage({
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
  } as VxeTableGridOptions<ErpStockMoveApi.StockMove>,
  gridEvents: {
    checkboxAll: handleRowCheckboxChange,
    checkboxChange: handleRowCheckboxChange,
  },
});
</script>

<template>
  <Page auto-content-height>
    <template #doc>
      <DocAlert
        title="【库存】库存调拨、库存盘点"
        url="https://doc.iocoder.cn/erp/stock-move-check/"
      />
    </template>

    <FormModal @success="handleRefresh" />
    <Grid table-title="库存调拨单列表">
      <template #status="{ row }">
        <Tag :color="moveStatusMeta[row.status]?.color || 'default'">
          {{ moveStatusMeta[row.status]?.text || `未知状态(${row.status})` }}
        </Tag>
      </template>
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['库存调拨单']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['erp:stock-move:create'],
              onClick: handleCreate,
            },
            {
              label: '批量删除',
              type: 'primary',
              danger: true,
              disabled: isEmpty(checkedIds),
              icon: ACTION_ICON.DELETE,
              auth: ['erp:stock-move:delete'],
              popConfirm: {
                title: `是否删除所选中数据？`,
                confirm: handleDelete.bind(null, checkedIds),
              },
            },
          ]"
        />
        <ErpExportButton
          :auth="['erp:stock-move:export']"
          :export-handler="handleExport"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.detail'),
              type: 'link',
              icon: ACTION_ICON.VIEW,
              auth: ['erp:stock-move:query'],
              onClick: handleDetail.bind(null, row),
            },
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['erp:stock-move:update'],
              ifShow: () => row.status === 10,
              onClick: handleEdit.bind(null, row),
            },
            {
              label: '确认调出',
              type: 'link',
              icon: ACTION_ICON.AUDIT,
              auth: ['erp:stock-move:update-status'],
              ifShow: () => row.status === 10,
              popConfirm: {
                title: `确认调出${row.no}吗？此时库存暂不变动。`,
                confirm: handleConfirm.bind(null, row, 'source'),
              },
            },
            {
              label: '确认调入',
              type: 'link',
              icon: ACTION_ICON.AUDIT,
              auth: ['erp:stock-move:update-status'],
              ifShow: () => row.status === 15,
              popConfirm: {
                title: `确认收到${row.no}吗？确认后将同时变更调出、调入库存。`,
                confirm: handleConfirm.bind(null, row, 'target'),
              },
            },
            {
              label: '反确认',
              type: 'link',
              icon: ACTION_ICON.AUDIT,
              auth: ['erp:stock-move:update-status'],
              ifShow: () => row.status === 20,
              popConfirm: {
                title: `确认反确认${row.no}吗？系统将冲销本次库存变动。`,
                confirm: handleUpdateStatus.bind(null, row, 10),
              },
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['erp:stock-move:delete'],
              ifShow: () => row.status === 10,
              popConfirm: {
                title: $t('ui.actionMessage.deleteConfirm', [row.no]),
                confirm: handleDelete.bind(null, [row.id!]),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
