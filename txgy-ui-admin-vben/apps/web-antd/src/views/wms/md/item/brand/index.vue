<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { WmsItemBrandApi } from '#/api/wms/md/item/brand';

import { Page, useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteItemBrand,
  exportItemBrand,
  getItemBrandPage,
} from '#/api/wms/md/item/brand';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

defineOptions({ name: 'WmsItemBrand' });

/** 资质文件地址解析：兼容逗号与中文顿号分隔 */
function parseFiles(value?: string): string[] {
  if (!value) return [];
  return value
    .split(/[,、]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

/** 取文件名用于展示 */
function fileName(url: string): string {
  return url.slice(Math.max(0, url.lastIndexOf('/') + 1)) || url;
}

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 创建商品品牌 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑商品品牌 */
function handleEdit(row: WmsItemBrandApi.ItemBrand) {
  formModalApi.setData(row).open();
}

/** 删除商品品牌 */
async function handleDelete(row: WmsItemBrandApi.ItemBrand) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.name]),
    duration: 0,
  });
  try {
    await deleteItemBrand(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.name]));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 导出商品品牌 */
async function handleExport() {
  const data = await exportItemBrand(await gridApi.formApi.getValues());
  downloadFileFromBlobPart({ fileName: '商品品牌.xls', source: data });
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
          return await getItemBrandPage({
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
  } as VxeTableGridOptions<WmsItemBrandApi.ItemBrand>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <Grid table-title="商品品牌列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['商品品牌']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['wms:item-brand:create'],
              onClick: handleCreate,
            },
            {
              label: $t('ui.actionTitle.export'),
              type: 'primary',
              icon: ACTION_ICON.DOWNLOAD,
              auth: ['wms:item-brand:export'],
              onClick: handleExport,
            },
          ]"
        />
      </template>
      <template #qualificationFiles="{ row }">
        <div
          v-if="parseFiles(row.qualificationFiles).length"
          class="flex flex-col gap-1 py-1 leading-5"
        >
          <a
            v-for="file in parseFiles(row.qualificationFiles)"
            :key="file"
            :href="file"
            target="_blank"
            rel="noopener noreferrer"
            class="text-xs text-primary hover:underline"
          >
            {{ fileName(file) }}
          </a>
        </div>
        <span v-else class="text-gray-400">-</span>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['wms:item-brand:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['wms:item-brand:delete'],
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
