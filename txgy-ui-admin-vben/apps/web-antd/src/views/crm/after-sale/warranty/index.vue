<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CrmAfterSaleWarrantyApi } from '#/api/crm/after-sale/warranty';

import { onActivated, onDeactivated, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';

import { Page, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteAfterSaleWarranty,
  getAfterSaleWarrantyPage,
} from '#/api/crm/after-sale/warranty';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

/**
 * 自动刷新间隔（毫秒）
 *
 * 质保状态会随日期自动流转（到期 → 已到期终止），后端在每次读取前也会做一次懒同步，
 * 这里再叠加定时刷新，用户无需手动点任何按钮即可看到最新状态。
 */
const AUTO_REFRESH_INTERVAL_MS = 60_000;

let autoRefreshTimer: ReturnType<typeof setInterval> | undefined;

/** 启动自动刷新 */
function startAutoRefresh() {
  stopAutoRefresh();
  autoRefreshTimer = setInterval(handleRefresh, AUTO_REFRESH_INTERVAL_MS);
}

/** 停止自动刷新（离开页面 / 组件销毁时调用，避免后台空转） */
function stopAutoRefresh() {
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer);
    autoRefreshTimer = undefined;
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

/** 创建质保档案 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑质保档案 */
function handleEdit(row: CrmAfterSaleWarrantyApi.Warranty) {
  formModalApi.setData(row).open();
}

const { push } = useRouter();

/** 查看质保档案详情（跳转详情页，待办「质保即将到期」等来源同样跳此处） */
function handleDetail(row: CrmAfterSaleWarrantyApi.Warranty) {
  push({ name: 'CrmAfterSaleWarrantyDetail', params: { id: row.id } });
}

/** 删除质保档案 */
async function handleDelete(row: CrmAfterSaleWarrantyApi.Warranty) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.no ?? row.projectName]),
    duration: 0,
  });
  try {
    await deleteAfterSaleWarranty(row.id!);
    message.success(
      $t('ui.actionMessage.deleteSuccess', [row.no ?? row.projectName]),
    );
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
          return await getAfterSaleWarrantyPage({
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
  } as VxeTableGridOptions<CrmAfterSaleWarrantyApi.Warranty>,
});

// ========== 自动刷新：无需用户手动操作即可看到最新状态 ==========
onMounted(() => {
  handleRefresh();
  startAutoRefresh();
});

/** 从详情页或其他页面返回时立即刷新一次 */
onActivated(() => {
  handleRefresh();
});

onUnmounted(() => {
  stopAutoRefresh();
});

/** keep-alive 缓存场景：离开页面时停止轮询 */
onDeactivated(() => {
  stopAutoRefresh();
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <Grid table-title="质保档案列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['质保档案']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['crm:after-sale-warranty:create'],
              onClick: handleCreate,
            },
          ]"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '详情',
              type: 'link',
              auth: ['crm:after-sale-warranty:query'],
              onClick: handleDetail.bind(null, row),
            },
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['crm:after-sale-warranty:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['crm:after-sale-warranty:delete'],
              popConfirm: {
                title: $t('ui.actionMessage.deleteConfirm', [
                  row.no ?? row.projectName,
                ]),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
