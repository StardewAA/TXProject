<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CrmProductApi } from '#/api/crm/product';

import { onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { Button } from 'antdv-next';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { exportProduct, getProductPage } from '#/api/crm/product';
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
