<script lang="ts" setup>
import type { InventoryDimension } from './data';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { WmsInventoryApi } from '#/api/wms/inventory';

import { computed, onMounted, ref } from 'vue';

import { DocAlert, Page, useVbenModal } from '@vben/common-ui';

import { Card, Checkbox, Statistic, Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  getInventoryPage,
  getInventoryRiskSummary,
} from '#/api/wms/inventory';
import { formatQuantity } from '#/views/wms/utils/format';

import { INVENTORY_DIMENSION, useGridColumns, useGridFormSchema } from './data';
import Detail from './modules/detail.vue';

interface InventoryRow extends WmsInventoryApi.Inventory {
  skuWarehouseId?: string;
  warehouseItemId?: string;
}

defineOptions({ name: 'WmsInventory' });

const currentRows = ref<InventoryRow[]>([]);
const currentDimension = ref<InventoryDimension>(INVENTORY_DIMENSION.WAREHOUSE);
const filterZero = ref(false);
const riskSummary = ref<WmsInventoryApi.RiskSummary>({});

/** 仅统计当前已加载页，避免在没有汇总接口时展示误导性的全量数字 */
const pageSummary = computed(() => {
  const rows = currentRows.value;
  return {
    inStockCount: rows.filter((row) => Number(row.quantity || 0) > 0).length,
    outOfStockCount: rows.filter((row) => Number(row.quantity || 0) <= 0)
      .length,
    skuCount: new Set(rows.map((row) => row.skuId).filter(Boolean)).size,
    warehouseCount: new Set(
      rows.map((row) => row.warehouseId).filter(Boolean),
    ).size,
  };
});

const [DetailModal, detailModalApi] = useVbenModal({
  connectedComponent: Detail,
  destroyOnClose: true,
});

/** 补充合并单元格需要的复合 key */
function buildRows(items: WmsInventoryApi.Inventory[]) {
  return items.map((item) => ({
    ...item,
    skuWarehouseId: `${item.skuId || 0}-${item.warehouseId || 0}`,
    warehouseItemId: `${item.warehouseId || 0}-${item.itemId || 0}`,
  }));
}

/** 切换统计维度时同步表单、表头和查询参数 */
async function handleDimensionChange(dimension: InventoryDimension) {
  // spanMethod 依赖当前维度判断合并字段，先更新本地状态。
  currentDimension.value = dimension;
  // 表头结构需要立即跟随维度切换，避免请求完成前仍显示旧列。
  await gridApi.grid.reloadColumn(useGridColumns(dimension) || []);
  // VXE proxy 查询会合并最近提交值，这里显式写入新 type 并刷新提交快照。
  await gridApi.formApi.setFieldValue('type', dimension);
  const formValues = await gridApi.formApi.getValues();
  gridApi.formApi.setLatestSubmissionValues(formValues);
  // 带上最新表单值重新加载，确保本次请求使用切换后的统计维度。
  await gridApi.reload(formValues);
}

/** 切换零库存过滤时按当前搜索条件重新查询 */
function handleFilterZeroChange() {
  gridApi.query();
}

function handleDetail(row: InventoryRow) {
  if (row.skuId) {
    detailModalApi.setData({ row }).open();
  }
}

async function loadRiskSummary() {
  try {
    riskSummary.value = await getInventoryRiskSummary();
  } catch {
    // 风险汇总失败不阻断库存列表和趋势详情的使用。
    riskSummary.value = {};
  }
}

/** 按列字段读取行值，兼容 VXE 的 field/property */
function getRowPropertyValue(row: InventoryRow | undefined, property: string) {
  return row?.[property as keyof InventoryRow];
}

/** 根据统计维度返回需要合并单元格的字段 */
function getRowSpanProperties() {
  if (currentDimension.value === INVENTORY_DIMENSION.ITEM) {
    return ['itemId', 'skuId', 'skuWarehouseId'];
  }
  return ['warehouseId', 'warehouseItemId'];
}

/** 合并库存统计的维度单元格 */
function handleSpanMethod({
  column,
  rowIndex,
}: {
  column: { field?: string; property?: string };
  rowIndex: number;
}) {
  const property = column.field || column.property;
  if (!property || !getRowSpanProperties().includes(property)) {
    return { colspan: 1, rowspan: 1 };
  }
  const row = currentRows.value[rowIndex];
  if (
    rowIndex > 0 &&
    getRowPropertyValue(currentRows.value[rowIndex - 1], property) ===
      getRowPropertyValue(row, property)
  ) {
    return { colspan: 0, rowspan: 0 };
  }
  let rowspan = 1;
  for (let index = rowIndex + 1; index < currentRows.value.length; index += 1) {
    if (
      getRowPropertyValue(currentRows.value[index], property) !==
      getRowPropertyValue(row, property)
    ) {
      break;
    }
    rowspan += 1;
  }
  return { colspan: 1, rowspan };
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(handleDimensionChange),
  },
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          // 维度切换会通过 reload(formValues) 传入新 type，刷新场景用当前维度兜底。
          const nextDimension = (formValues.type ||
            currentDimension.value ||
            INVENTORY_DIMENSION.WAREHOUSE) as InventoryDimension;
          currentDimension.value = nextDimension;
          await gridApi.grid.reloadColumn(useGridColumns(nextDimension) || []);

          // type 是后端聚合维度参数，普通搜索参数里移除，避免重复透传旧值。
          const queryParams = { ...formValues };
          Reflect.deleteProperty(queryParams, 'type');
          const data = await getInventoryPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...queryParams,
            type: nextDimension,
            onlyPositiveQuantity: filterZero.value ? true : undefined,
          });

          // spanMethod 依赖当前页数据计算 rowspan，需要保存构造后的行。
          currentRows.value = buildRows(data.list || []);
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
  } as VxeTableGridOptions<InventoryRow>,
});

onMounted(() => {
  void loadRiskSummary();
});
</script>

<template>
  <Page auto-content-height>
    <template #doc>
      <DocAlert
        title="【库存】库存记录、流水、统计"
        url="https://doc.iocoder.cn/wms/inventory/"
      />
    </template>

    <div class="inventory-summary mb-4 grid grid-cols-2 gap-3 md:grid-cols-4">
      <Card size="small" class="inventory-summary-card inventory-summary-card--blue">
        <Statistic
          title="当前页商品数"
          :value="pageSummary.skuCount"
          :value-style="{ color: '#2563eb' }"
        />
      </Card>
      <Card size="small" class="inventory-summary-card inventory-summary-card--green">
        <Statistic
          title="当前页仓库数"
          :value="pageSummary.warehouseCount"
          :value-style="{ color: '#16a34a' }"
        />
      </Card>
      <Card size="small" class="inventory-summary-card inventory-summary-card--orange">
        <Statistic
          title="缺货记录"
          :value="pageSummary.outOfStockCount"
          :value-style="{ color: '#ea580c' }"
        />
      </Card>
      <Card size="small" class="inventory-summary-card inventory-summary-card--purple">
        <Statistic
          title="有库存记录"
          :value="pageSummary.inStockCount"
          :value-style="{ color: '#7c3aed' }"
        />
      </Card>
    </div>

    <div
      v-if="riskSummary.inventoryWarnings?.length || riskSummary.inventoryRisks?.length"
      class="mb-4 grid gap-3 lg:grid-cols-2"
    >
      <Card
        v-if="riskSummary.inventoryWarnings?.length"
        size="small"
        title="库存预警"
        class="risk-card risk-card--danger"
      >
        <div
          v-for="item in riskSummary.inventoryWarnings"
          :key="`warning-${item.skuId}-${item.warehouseId}`"
          class="risk-row"
        >
          <span class="risk-row__name">{{ item.itemName || item.skuName || '未命名商品' }}</span>
          <span class="risk-row__meta">{{ item.warehouseName || '未指定仓库' }}</span>
          <Tag color="error">{{ item.label || '库存不足' }}</Tag>
        </div>
      </Card>
      <Card
        v-if="riskSummary.inventoryRisks?.length"
        size="small"
        title="积压风险"
        class="risk-card risk-card--warning"
      >
        <div
          v-for="item in riskSummary.inventoryRisks"
          :key="`risk-${item.skuId}-${item.warehouseId}`"
          class="risk-row"
        >
          <span class="risk-row__name">{{ item.itemName || item.skuName || '未命名商品' }}</span>
          <span class="risk-row__meta">{{ item.warehouseName || '未指定仓库' }}</span>
          <Tag color="warning">{{ item.label || '长期未出库' }}</Tag>
        </div>
      </Card>
    </div>

    <Grid table-title="库存统计">
      <template #toolbar-tools>
        <Checkbox v-model:checked="filterZero" @change="handleFilterZeroChange">
          过滤掉库存为 0 的商品
        </Checkbox>
      </template>
      <template #warehouseName="{ row }">
        {{ row.warehouseName || '-' }}
      </template>
      <template #itemInfo="{ row }">
        <div>{{ row.itemName || '-' }}</div>
        <div v-if="row.itemCode" class="text-xs text-gray-500">
          商品编号：{{ row.itemCode }}
        </div>
      </template>
      <template #skuInfo="{ row }">
        <div>{{ row.skuName || '-' }}</div>
        <div v-if="row.skuCode" class="text-xs text-gray-500">
          规格编号：{{ row.skuCode }}
        </div>
      </template>
      <template #quantity="{ row }">
        {{ formatQuantity(row.quantity) }}
      </template>
      <template #inventoryStatus="{ row }">
        <Tag :color="Number(row.quantity || 0) > 0 ? 'success' : 'error'">
          {{ Number(row.quantity || 0) > 0 ? '有库存' : '缺货' }}
        </Tag>
      </template>
      <template #actions="{ row }">
        <a-button type="link" size="small" @click="handleDetail(row)">
          查看详情
        </a-button>
      </template>
    </Grid>
    <DetailModal />
  </Page>
</template>

<style scoped>
.inventory-summary-card {
  border-top-width: 3px;
  border-top-style: solid;
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}

.inventory-summary-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgb(15 23 42 / 8%);
}

.inventory-summary-card--blue {
  border-top-color: #2563eb;
}

.inventory-summary-card--green {
  border-top-color: #16a34a;
}

.inventory-summary-card--orange {
  border-top-color: #f59e0b;
}

.inventory-summary-card--purple {
  border-top-color: #7c3aed;
}

.risk-card {
  border-top-width: 3px;
  border-top-style: solid;
}

.risk-card--danger {
  border-top-color: #ef4444;
}

.risk-card--warning {
  border-top-color: #f59e0b;
}

.risk-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  min-height: 2.25rem;
  border-bottom: 1px solid rgb(148 163 184 / 18%);
}

.risk-row:last-child {
  border-bottom: 0;
}

.risk-row__name {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.risk-row__meta {
  color: rgb(100 116 139);
  font-size: 0.8125rem;
  white-space: nowrap;
}
</style>
