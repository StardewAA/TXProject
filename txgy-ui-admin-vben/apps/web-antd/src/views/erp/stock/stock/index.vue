<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { ErpStockApi } from '#/api/erp/stock/stock';

import { DocAlert, Page } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import ErpExportButton from '#/components/erp-export-button/index.vue';
import { exportStock, exportStockPdf, getStockPage } from '#/api/erp/stock/stock';

import { useGridColumns, useGridFormSchema } from './data';

/** 产品库存管理 */
defineOptions({ name: 'ErpStock' });

/** 导出库存 */
async function handleExport(format: 'excel' | 'pdf') {
  const data =
    format === 'pdf'
      ? await exportStockPdf(await gridApi.formApi.getValues())
      : await exportStock(await gridApi.formApi.getValues());
  downloadFileFromBlobPart({
    fileName: format === 'pdf' ? '产品库存.pdf' : '产品库存.xls',
    source: data,
  });
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
          return await getStockPage({
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
  } as VxeTableGridOptions<ErpStockApi.Stock>,
});
</script>

<template>
  <Page auto-content-height>
    <template #doc>
      <DocAlert
        title="【库存】产品库存、库存明细"
        url="https://doc.iocoder.cn/erp/stock/"
      />
    </template>

    <Grid table-title="产品库存列表">
      <template #toolbar-tools>
        <ErpExportButton
          :auth="['erp:stock:export']"
          :export-handler="handleExport"
        />
      </template>
    </Grid>
  </Page>
</template>
