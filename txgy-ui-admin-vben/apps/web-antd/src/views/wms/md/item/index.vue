<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { WmsItemApi } from '#/api/wms/md/item';
import type { WmsItemSkuApi } from '#/api/wms/md/item/sku';

import { ref } from 'vue';

import { confirm, DocAlert, Page, useVbenModal } from '@vben/common-ui';
import { CommonStatusEnum, DICT_TYPE } from '@vben/constants';
import { getDictLabel } from '@vben/hooks';
import { downloadFileFromBlobPart } from '@vben/utils';

import { Card, message, Tag } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteItem,
  exportItem,
  getItemPage,
  updateItemStatus,
} from '#/api/wms/md/item';
import { $t } from '#/locales';
import { WmsItemCategoryTree } from '#/views/wms/md/item/category/components';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

type ItemSkuRow = WmsItemSkuApi.ItemSku &
  Pick<
    WmsItemApi.Item,
    | 'batchManage'
    | 'dangerousGoods'
    | 'shelfLifeManage'
    | 'shelfLifeMonths'
    | 'storageCondition'
    | 'temperatureRequirement'
  > & {
    status?: number;
  };

defineOptions({ name: 'WmsItem' });

const currentRows = ref<ItemSkuRow[]>([]);

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 展开商品 SKU 列表 */
function buildItemSkuRows(items: WmsItemApi.Item[]) {
  return items.flatMap((item) => {
    const skus = item.skus?.length ? item.skus : [{}];
    return skus.map((sku) => ({
      ...sku,
      itemId: item.id,
      itemCode: item.code,
      itemName: item.name,
      categoryId: item.categoryId,
      categoryName: item.categoryName,
      unit: item.unit,
      brandId: item.brandId,
      brandName: item.brandName,
      materialType: item.materialType,
      // 【4-2 商品档案总览】新增列
      batchManage: item.batchManage,
      shelfLifeManage: item.shelfLifeManage,
      shelfLifeMonths: item.shelfLifeMonths,
      dangerousGoods: item.dangerousGoods,
      temperatureRequirement: item.temperatureRequirement,
      storageCondition: item.storageCondition,
      status: item.status ?? CommonStatusEnum.ENABLE,
    }));
  });
}

/** 创建商品 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑商品 */
function handleEdit(row: ItemSkuRow) {
  formModalApi.setData({ id: row.itemId }).open();
}

/** 删除商品 */
async function handleDelete(row: ItemSkuRow) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.itemName]),
    duration: 0,
  });
  try {
    await deleteItem(row.itemId!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.itemName]));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 更新商品状态 */
async function handleStatusChange(
  newStatus: number,
  row: ItemSkuRow,
): Promise<boolean | undefined> {
  try {
    await confirm(
      `确认要将"${row.itemName}"商品切换为【${getDictLabel(DICT_TYPE.COMMON_STATUS, newStatus)}】吗？`,
    );
  } catch {
    return false;
  }
  await updateItemStatus(row.itemId!, newStatus);
  message.success($t('ui.actionMessage.operationSuccess'));
  return true;
}

/** 导出商品 */
async function handleExport() {
  const data = await exportItem(await gridApi.formApi.getValues());
  downloadFileFromBlobPart({ fileName: '商品.xls', source: data });
}

/** 分类树点击 */
const searchCategoryId = ref<number | undefined>(undefined);
function handleCategoryNodeClick(categoryId: number | undefined) {
  searchCategoryId.value = categoryId;
  handleRefresh();
}

/** 合并商品维度的重复单元格（商品编号、名称、分类、单位、品牌等按商品合并） */
function handleSpanMethod({
  column,
  rowIndex,
}: {
  column: { field?: string; property?: string };
  rowIndex: number;
}) {
  const field = column.field || column.property;
  const spanFields = [
    'itemCode',
    'itemInfo',
    'categoryName',
    'unit',
    'brandName',
    'materialType',
    'batchManage',
    'shelfLife',
    'dangerousGoods',
    'temperatureRequirement',
    'storageCondition',
    'actions',
    'status',
  ];
  if (!spanFields.includes(field || '')) {
    return { colspan: 1, rowspan: 1 };
  }
  const row = currentRows.value[rowIndex];
  if (rowIndex > 0 && currentRows.value[rowIndex - 1]?.itemId === row?.itemId) {
    return { colspan: 0, rowspan: 0 };
  }
  let rowspan = 1;
  for (let index = rowIndex + 1; index < currentRows.value.length; index += 1) {
    if (currentRows.value[index]?.itemId !== row?.itemId) {
      break;
    }
    rowspan += 1;
  }
  return { colspan: 1, rowspan };
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
  },
  gridOptions: {
    columns: useGridColumns(handleStatusChange),
    height: 'auto',
    keepSource: true,
    showOverflow: false,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          const data = await getItemPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
            categoryId: searchCategoryId.value,
          });
          currentRows.value = buildItemSkuRows(data.list || []);
          return {
            ...data,
            list: currentRows.value,
          };
        },
      },
    },
    rowConfig: {
      keyField: 'id',
      isHover: true,
    },
    spanMethod: handleSpanMethod,
    toolbarConfig: {
      refresh: true,
      search: true,
    },
  } as VxeTableGridOptions<ItemSkuRow>,
});
</script>

<template>
  <Page auto-content-height>
    <template #doc>
      <DocAlert
        title="【基础】商品、SKU、分类、品牌"
        url="https://doc.iocoder.cn/wms/md/item/"
      />
    </template>

    <FormModal @success="handleRefresh" />

    <div class="flex h-full w-full">
      <Card class="mr-4 h-full w-1/6">
        <WmsItemCategoryTree @node-click="handleCategoryNodeClick" />
      </Card>
      <div class="w-5/6">
        <Grid table-title="商品列表">
          <template #toolbar-tools>
            <TableAction
              :actions="[
                {
                  label: $t('ui.actionTitle.create', ['商品']),
                  type: 'primary',
                  icon: ACTION_ICON.ADD,
                  auth: ['wms:item:create'],
                  onClick: handleCreate,
                },
                {
                  label: $t('ui.actionTitle.export'),
                  type: 'primary',
                  icon: ACTION_ICON.DOWNLOAD,
                  auth: ['wms:item:export'],
                  onClick: handleExport,
                },
              ]"
            />
          </template>
          <template #itemInfo="{ row }">
            <div class="flex flex-col gap-1 py-1 leading-5">
              <div class="text-sm">{{ row.itemName || '-' }}</div>
              <div v-if="row.name" class="text-xs text-gray-500">
                规格：{{ row.name }}
              </div>
            </div>
          </template>
          <template #batchManage="{ row }">
            <Tag :color="row.batchManage ? 'green' : 'default'">
              {{ row.batchManage ? '是' : '否' }}
            </Tag>
          </template>
          <template #shelfLife="{ row }">
            {{ row.shelfLifeMonths ? `${row.shelfLifeMonths}个月` : '无' }}
          </template>
          <template #dangerousGoods="{ row }">
            <Tag :color="row.dangerousGoods ? 'red' : 'default'">
              {{ row.dangerousGoods ? '危险品' : '普通商品' }}
            </Tag>
          </template>
          <template #actions="{ row }">
            <TableAction
              :actions="[
                {
                  label: $t('common.edit'),
                  type: 'link',
                  icon: ACTION_ICON.EDIT,
                  auth: ['wms:item:update'],
                  onClick: handleEdit.bind(null, row),
                },
                {
                  label: $t('common.delete'),
                  type: 'link',
                  danger: true,
                  icon: ACTION_ICON.DELETE,
                  auth: ['wms:item:delete'],
                  popConfirm: {
                    title: `确认删除商品【${row.itemName}】吗？`,
                    confirm: handleDelete.bind(null, row),
                  },
                },
              ]"
            />
          </template>
        </Grid>
      </div>
    </div>
  </Page>
</template>
