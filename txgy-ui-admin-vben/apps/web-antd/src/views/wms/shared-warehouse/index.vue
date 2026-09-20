<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { WmsSharedWarehouseApi } from '#/api/wms/shared-warehouse';

import { nextTick, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Button, Tag } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  getSharedWarehousePage,
  getSharedWarehouseProductPage,
} from '#/api/wms/shared-warehouse';

import {
  useProductColumns,
  useProductFormSchema,
  useWarehouseColumns,
  useWarehouseFormSchema,
} from './data';

defineOptions({ name: 'WmsSharedWarehouse' });

const selectedWarehouse = ref<WmsSharedWarehouseApi.Warehouse>();

function sourceColor(source: string) {
  return source === 'WMS' ? 'blue' : source === 'MALL' ? 'green' : 'purple';
}

function handleViewProducts(row: WmsSharedWarehouseApi.Warehouse) {
  selectedWarehouse.value = row;
  void nextTick(() => productGridApi.query());
}

const [WarehouseGrid, warehouseGridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useWarehouseFormSchema(),
  },
  gridOptions: {
    columns: useWarehouseColumns(),
    height: 360,
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) =>
          getSharedWarehousePage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
          }),
      },
    },
    rowConfig: {
      keyField: 'warehouseId',
      isHover: true,
    },
    toolbarConfig: {
      refresh: true,
      search: true,
    },
  } as VxeTableGridOptions<WmsSharedWarehouseApi.Warehouse>,
});

const [ProductGrid, productGridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useProductFormSchema(),
  },
  gridOptions: {
    columns: useProductColumns(),
    height: 360,
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          if (!selectedWarehouse.value?.warehouseId) {
            return { list: [], total: 0 };
          }
          return getSharedWarehouseProductPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            warehouseId: selectedWarehouse.value.warehouseId,
            ...formValues,
          });
        },
      },
    },
    rowConfig: {
      keyField: 'wmsSkuId',
      isHover: true,
    },
    toolbarConfig: {
      refresh: true,
      search: true,
    },
  } as VxeTableGridOptions<WmsSharedWarehouseApi.Product>,
});
</script>

<template>
  <Page auto-content-height>
    <WarehouseGrid table-title="共享仓库">
      <template #toolbar-tools>
        <span class="mr-3 text-sm text-gray-500">
          仓库和实物库存以 WMS 为准，商城与 ERP 仅展示已关联商品来源
        </span>
      </template>
      <template #ownerSystem="{ row }">
        <Tag color="blue">{{ row.ownerSystem || 'WMS' }}</Tag>
      </template>
      <template #sourceSystems="{ row }">
        <Tag
          v-for="source in row.sourceSystems || []"
          :key="source"
          :color="sourceColor(source)"
        >
          {{ source }}
        </Tag>
        <span v-if="!row.sourceSystems?.length">-</span>
      </template>
      <template #relatedErpWarehouseNames="{ row }">
        {{ row.relatedErpWarehouseNames?.join('、') || '-' }}
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '查看商品',
              type: 'link',
              icon: ACTION_ICON.VIEW,
              onClick: handleViewProducts.bind(null, row),
            },
          ]"
        />
      </template>
    </WarehouseGrid>

    <div v-if="selectedWarehouse" class="mt-4">
      <ProductGrid
        :table-title="`${selectedWarehouse.warehouseName || '仓库'}商品映射`"
      >
        <template #toolbar-tools>
          <Button type="link" @click="selectedWarehouse = undefined">
            关闭商品明细
          </Button>
        </template>
        <template #wmsItem="{ row }">
          <div>{{ row.wmsItemName || '-' }}</div>
          <div class="text-xs text-gray-500">
            {{ row.wmsItemId ? `WMS ID：${row.wmsItemId}` : '' }}
          </div>
        </template>
        <template #sourceSystems="{ row }">
          <Tag
            v-for="source in row.sourceSystems || []"
            :key="source"
            :color="sourceColor(source)"
          >
            {{ source }}
          </Tag>
          <span v-if="!row.sourceSystems?.length">-</span>
        </template>
      </ProductGrid>
    </div>
  </Page>
</template>
