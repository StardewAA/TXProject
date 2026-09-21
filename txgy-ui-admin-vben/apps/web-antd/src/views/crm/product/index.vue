<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CrmProductApi } from '#/api/crm/product';

import { h, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { Button, message, notification } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  exportProduct,
  getProductPage,
  syncProductFromWms,
} from '#/api/crm/product';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';

const { push } = useRouter();

/** 刷新表格（实时拉取 WMS 最新数据） */
function handleRefresh() {
  gridApi.query();
}

/** 导出表格 */
async function handleExport() {
  const data = await exportProduct(await gridApi.formApi.getValues());
  downloadFileFromBlobPart({ fileName: '产品.xls', source: data });
}

/** 打开详情 */
function handleDetail(row: CrmProductApi.Product) {
  push({ name: 'CrmProductDetail', params: { id: row.id } });
}

/** 从 WMS 同步产品（运维补数：把 WMS 商品补写到 CRM 产品表） */
async function handleSyncFromWms() {
  const hideLoading = message.loading({
    content: '正在从 WMS 同步产品，请稍候...',
    duration: 0,
  });
  try {
    const result = await syncProductFromWms();
    if (result.skipped) {
      message.warning(result.message ?? '本次同步已跳过');
      return;
    }
    message.success(
      `同步完成：拉取 ${result.total} 个规格，新增 ${result.created}，更新 ${result.updated}，` +
        `无变化 ${result.unchanged}，下架 ${result.offlined ?? 0}，编码冲突 ${result.conflict}，失败 ${result.failed}`,
    );
    // 编码冲突、失败等明细
    if (result.messages && result.messages.length > 0) {
      notification.warning({
        message: 'WMS 同步明细',
        description: () =>
          h(
            'div',
            result.messages!.map((item) =>
              h('div', { style: 'margin-bottom: 4px' }, item),
            ),
          ),
        duration: 8,
      });
    }
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
          return await getProductPage({
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
  } as VxeTableGridOptions<CrmProductApi.Product>,
});

/** 自动刷新间隔：页面可见时每 60 秒实时拉取 WMS 最新数据 */
const AUTO_REFRESH_INTERVAL = 60_000;
let refreshTimer: ReturnType<typeof setInterval> | undefined;

/** 页面重新可见时立即同步 WMS 变动 */
function handleVisibilityChange() {
  if (!document.hidden) {
    handleRefresh();
  }
}

onMounted(() => {
  refreshTimer = setInterval(() => {
    if (!document.hidden) {
      handleRefresh();
    }
  }, AUTO_REFRESH_INTERVAL);
  document.addEventListener('visibilitychange', handleVisibilityChange);
});

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer);
  }
  document.removeEventListener('visibilitychange', handleVisibilityChange);
});
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="产品列表（实时取自 WMS 仓库库存，不支持新增/修改/删除）">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '刷新',
              type: 'primary',
              icon: ACTION_ICON.REFRESH,
              auth: ['crm:product:query'],
              onClick: handleRefresh,
            },
            {
              label: 'WMS 数据同步（运维）',
              type: 'primary',
              icon: ACTION_ICON.UPLOAD,
              auth: ['crm:product:sync'],
              popConfirm: {
                title:
                  '将从 WMS 读取商品并补写 CRM 产品表（不会修改 WMS 数据），是否继续？',
                confirm: handleSyncFromWms,
              },
            },
            {
              label: $t('ui.actionTitle.export'),
              type: 'primary',
              icon: ACTION_ICON.DOWNLOAD,
              auth: ['crm:product:export'],
              onClick: handleExport,
            },
          ]"
        />
      </template>
      <template #name="{ row }">
        <Button type="link" @click="handleDetail(row)">{{ row.name }}</Button>
      </template>
    </Grid>
  </Page>
</template>
