<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CrmProductApi } from '#/api/crm/product';

import { h } from 'vue';
import { useRouter } from 'vue-router';

import { Page, useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { Button, message, notification } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteProduct,
  exportProduct,
  getProductPage,
  syncProductFromWms,
} from '#/api/crm/product';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

const { push } = useRouter();

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 导出表格 */
async function handleExport() {
  const data = await exportProduct(await gridApi.formApi.getValues());
  downloadFileFromBlobPart({ fileName: '产品.xls', source: data });
}

/** 从 WMS 同步产品 */
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
        `无变化 ${result.unchanged}，编码冲突 ${result.conflict}，失败 ${result.failed}`,
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

/** 打开详情 */
function handleDetail(row: CrmProductApi.Product) {
  push({ name: 'CrmProductDetail', params: { id: row.id } });
}

/** 创建产品 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑产品 */
function handleEdit(row: CrmProductApi.Product) {
  formModalApi.setData(row).open();
}

/** 删除产品 */
async function handleDelete(row: CrmProductApi.Product) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.name]),
    duration: 0,
  });
  try {
    await deleteProduct(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.name]));
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
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <Grid table-title="产品列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['产品']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['crm:product:create'],
              onClick: handleCreate,
            },
            {
              label: '从 WMS 同步',
              type: 'primary',
              icon: ACTION_ICON.UPLOAD,
              auth: ['crm:product:sync'],
              popConfirm: {
                title:
                  '将从 WMS 读取进仓商品与规格并导入产品管理（不会修改 WMS 数据），是否继续？',
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
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['crm:product:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['crm:product:delete'],
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
