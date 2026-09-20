<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { ErpSupplierApi } from '#/api/erp/purchase/supplier';

import { ref } from 'vue';

import { DocAlert, Page, useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import ErpExportButton from '#/components/erp-export-button/index.vue';
import {
  deleteSupplier,
  exportSupplier,
  exportSupplierPdf,
  getSupplierPage,
} from '#/api/erp/purchase/supplier';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import SupplierForm from './modules/form.vue';
import SupplierCredit from './modules/credit.vue';
import RecognitionImport from '../order/modules/recognition-import.vue';

/** 供应商管理 */
defineOptions({ name: 'ErpSupplier' });

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 创建供应商 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑供应商 */
function handleEdit(row: ErpSupplierApi.Supplier) {
  formModalApi.setData(row).open();
}

function handleCredit(row: ErpSupplierApi.Supplier) {
  creditModalApi.setData(row).open();
}

/** 删除供应商 */
async function handleDelete(row: ErpSupplierApi.Supplier) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.name]),
    duration: 0,
  });
  try {
    await deleteSupplier(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.name]));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 导出供应商 */
async function handleExport(format: 'excel' | 'pdf') {
  const data =
    format === 'pdf'
      ? await exportSupplierPdf(await gridApi.formApi.getValues())
      : await exportSupplier(await gridApi.formApi.getValues());
  downloadFileFromBlobPart({
    fileName: format === 'pdf' ? '供应商.pdf' : '供应商.xls',
    source: data,
  });
}

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: SupplierForm,
  destroyOnClose: true,
});

const [CreditModal, creditModalApi] = useVbenModal({
  connectedComponent: SupplierCredit,
  destroyOnClose: true,
});

const recognitionVisible = ref(false);

function handleImport() {
  recognitionVisible.value = true;
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
          return await getSupplierPage({
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
  } as VxeTableGridOptions<ErpSupplierApi.Supplier>,
});
</script>

<template>
  <Page auto-content-height>
    <template #doc>
      <DocAlert
        title="【采购】采购订单、入库、退货"
        url="https://doc.iocoder.cn/erp/purchase/"
      />
    </template>

    <FormModal @success="handleRefresh" />
    <CreditModal />
    <a-modal
      v-model:open="recognitionVisible"
      title="供应商图片识别导入"
      width="95%"
      :footer="null"
      :destroy-on-close="true"
      :body-style="{ height: 'min(78vh, 760px)', padding: '8px' }"
    >
      <RecognitionImport
        biz-type="SUPPLIER"
        title="供应商图片识别"
        @success="handleRefresh"
      />
    </a-modal>
    <Grid table-title="供应商列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['供应商']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['erp:supplier:create'],
              onClick: handleCreate,
            },
            {
              label: '导入',
              type: 'primary',
              icon: ACTION_ICON.UPLOAD,
              auth: ['erp:supplier:create'],
              onClick: handleImport,
            },
          ]"
        />
        <ErpExportButton
          :auth="['erp:supplier:export']"
          :export-handler="handleExport"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '资信详情',
              type: 'link',
              onClick: handleCredit.bind(null, row),
            },
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['erp:supplier:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['erp:supplier:delete'],
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
